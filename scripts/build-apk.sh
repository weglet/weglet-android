#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAIN_ROOT="$PROJECT_ROOT/.android-toolchain"
SDK_ROOT="$TOOLCHAIN_ROOT/android-sdk-local"
BUILD_TOOLS="$SDK_ROOT/build-tools/android-16"
PLATFORM="$SDK_ROOT/platforms/android-36/android.jar"
KOTLIN="$TOOLCHAIN_ROOT/kotlin-2.3.20/kotlinc"
D8_JAR="$TOOLCHAIN_ROOT/r8-9.2.23.jar"
RUNTIME_LIST="$PROJECT_ROOT/.deps/gecko-runtime.txt"
OUTPUT="$PROJECT_ROOT/dist/Weglet-1.3.6-arm64.apk"
ANDROID_STUDIO_OUTPUT="$PROJECT_ROOT/app/build/outputs/apk/release/Weglet-1.3.6-arm64.apk"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/weglet-build.XXXXXX")"

cleanup() {
    find "$WORK" -mindepth 1 -delete 2>/dev/null || true
    rmdir "$WORK" 2>/dev/null || true
}
trap cleanup EXIT

for required in "$BUILD_TOOLS/aapt2" "$BUILD_TOOLS/zipalign" \
    "$BUILD_TOOLS/apksigner" "$PLATFORM" "$KOTLIN/bin/kotlinc" "$D8_JAR"; do
    if [[ ! -e "$required" ]]; then
        printf 'Missing build tool: %s\n' "$required" >&2
        exit 1
    fi
done

if [[ ! -f "$RUNTIME_LIST" ]]; then
    python3 "$PROJECT_ROOT/scripts/fetch-gecko-deps.py"
fi

mkdir -p "$WORK/aar" "$WORK/classes" "$WORK/compiled" "$WORK/dex" \
    "$WORK/gen" "$WORK/assets" "$WORK/native/lib/arm64-v8a" \
    "$(dirname "$OUTPUT")" "$(dirname "$ANDROID_STUDIO_OUTPUT")" "$PROJECT_ROOT/.debug"

declare -a CLASS_JARS=()
declare -a MANIFESTS=()
declare -a RESOURCE_ZIPS=()
index=0
while IFS='|' read -r relative extension coordinate; do
    [[ -n "$relative" ]] || continue
    artifact="$PROJECT_ROOT/$relative"
    if [[ "$extension" == "aar" ]]; then
        dep_dir="$WORK/aar/$index"
        mkdir -p "$dep_dir"
        unzip -q -o "$artifact" -d "$dep_dir"
        [[ -s "$dep_dir/classes.jar" ]] && CLASS_JARS+=("$dep_dir/classes.jar")
        if [[ -d "$dep_dir/libs" ]]; then
            while IFS= read -r -d '' jar_file; do CLASS_JARS+=("$jar_file"); done \
                < <(find "$dep_dir/libs" -type f -name '*.jar' -print0)
        fi
        [[ -f "$dep_dir/AndroidManifest.xml" ]] && MANIFESTS+=("$dep_dir/AndroidManifest.xml")
        if [[ -d "$dep_dir/res" ]] && find "$dep_dir/res" -type f -print -quit | grep -q .; then
            resource_zip="$WORK/compiled/dependency-$index.zip"
            "$BUILD_TOOLS/aapt2" compile --dir "$dep_dir/res" -o "$resource_zip"
            RESOURCE_ZIPS+=("$resource_zip")
        fi
        [[ -d "$dep_dir/assets" ]] && cp -a "$dep_dir/assets/." "$WORK/assets/"
        [[ -d "$dep_dir/jni/arm64-v8a" ]] && cp -a "$dep_dir/jni/arm64-v8a/." "$WORK/native/lib/arm64-v8a/"
        index=$((index + 1))
    elif [[ "$extension" == "jar" ]]; then
        CLASS_JARS+=("$artifact")
    fi
done < "$RUNTIME_LIST"

cp -a "$PROJECT_ROOT/app/src/main/assets/." "$WORK/assets/"

APP_RESOURCES="$WORK/compiled/app.zip"
"$BUILD_TOOLS/aapt2" compile --dir "$PROJECT_ROOT/app/src/main/res" -o "$APP_RESOURCES"
RESOURCE_ZIPS+=("$APP_RESOURCES")

LIBRARY_PACKAGES="$(python3 "$PROJECT_ROOT/scripts/merge-manifests.py" \
    "$PROJECT_ROOT/app/src/main/AndroidManifest.xml" "$WORK/AndroidManifest.xml" "${MANIFESTS[@]}")"

declare -a LINK_ARGS=(
    -o "$WORK/unsigned.apk"
    -I "$PLATFORM"
    --manifest "$WORK/AndroidManifest.xml"
    --min-sdk-version 29
    --target-sdk-version 36
    --version-code 76
    --version-name 1.3.6
    --java "$WORK/gen"
    -A "$WORK/assets"
    --auto-add-overlay
)
[[ -n "$LIBRARY_PACKAGES" ]] && LINK_ARGS+=(--extra-packages "$LIBRARY_PACKAGES")
LINK_ARGS+=("${RESOURCE_ZIPS[@]}")
"$BUILD_TOOLS/aapt2" link "${LINK_ARGS[@]}"

mapfile -d '' R_SOURCES < <(find "$WORK/gen" -type f -name '*.java' -print0)
if ((${#R_SOURCES[@]})); then
    java -m jdk.compiler/com.sun.tools.javac.Main \
        -source 8 -target 8 -classpath "$PLATFORM" -d "$WORK/classes" "${R_SOURCES[@]}"
fi

CLASSPATH="$PLATFORM:$WORK/classes"
for jar_file in "${CLASS_JARS[@]}"; do CLASSPATH="$CLASSPATH:$jar_file"; done

"$KOTLIN/bin/kotlinc" \
    "$PROJECT_ROOT"/app/src/main/java/app/weglet/browser/*.kt \
    -classpath "$CLASSPATH" \
    -jvm-target 17 \
    -no-reflect \
    -Xskip-metadata-version-check \
    -d "$WORK/classes"

(cd "$WORK/classes" && zip -q -r "$WORK/app-classes.jar" .)
java -Xmx4g -cp "$D8_JAR" com.android.tools.r8.D8 \
    --release \
    --lib "$PLATFORM" \
    --min-api 29 \
    --output "$WORK/dex" \
    "$WORK/app-classes.jar" \
    "${CLASS_JARS[@]}"

cp "$WORK/unsigned.apk" "$WORK/with-code.apk"
zip -q -j "$WORK/with-code.apk" "$WORK"/dex/classes*.dex
if find "$WORK/native/lib" -type f -print -quit | grep -q .; then
    (cd "$WORK/native" && zip -q -r "$WORK/with-code.apk" lib)
fi
"$BUILD_TOOLS/zipalign" -f -p 4 "$WORK/with-code.apk" "$WORK/aligned.apk"

KEYSTORE="$PROJECT_ROOT/.debug/weglet-debug.keystore"
if [[ ! -f "$KEYSTORE" ]]; then
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -storepass android \
        -alias androiddebugkey \
        -keypass android \
        -dname "CN=Weglet Development,O=Weglet Browser,C=FI" \
        -keyalg RSA \
        -keysize 3072 \
        -validity 10000
fi

"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias androiddebugkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$OUTPUT" \
    "$WORK/aligned.apk"

"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$OUTPUT"
install -m 0644 "$OUTPUT" "$ANDROID_STUDIO_OUTPUT"
printf 'Built %s\n' "$OUTPUT"
