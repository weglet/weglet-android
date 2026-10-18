# Weglet for Android

Weglet is an open-source browser for Android 10 and later, written in Kotlin and
targeting Android 16 (API 36). Version **1.3.6** (`versionCode 76`) combines two
production rendering engines with a fully configurable new-tab page:

- **Firefox Gecko** through Mozilla GeckoView 153, with JavaScript, modern media,
  strict tracking protection, isolated third-party cookies, Global Privacy Control,
  query-parameter stripping, and safe browsing;
- **Android WebView** as the system compatibility mode, with Android Safe Browsing
  and the same Weglet navigation checks.

The old experimental Canvas renderer is not part of the release path. Selecting an
engine changes the component that loads websites, not merely the interface label.

## Customization

The home screen can be changed without rebuilding the app:

- preset or custom HEX colors for the accent, background, interface text, clock,
  search fill/text/placeholder/outline, shortcut tiles, and storm;
- a private local wallpaper with cover, contain, stretch, tile, and shade controls;
- five search-bar shapes, four outline widths, three heights, and three home widths;
- browser address controls at the top or bottom;
- home content aligned to the top, center, or bottom, with independently hidden
  clock, engine caption, shortcuts, and protection caption;
- selectable clock size and Onest or JetBrains Mono clock face;
- bottom, top, left, right, scale, fade, or disabled entrance motion with three speeds;
- disabled, calm, normal, or heavy lightning.

Settings are stored locally. A selected wallpaper is copied into the app's private
storage and is never uploaded by Weglet.

## Browser features

- DuckDuckGo, Bing, Google, or a custom HTTPS search template;
- tabs, bookmarks, quick launch, downloads, and session restoration;
- lazy restored-tab loading so only the selected page consumes network and engine resources at startup;
- restoration capped at eight tabs to avoid a memory spike after Android recreates the app;
- local audio playback and public Audius catalog search;
- Russian and English interface;
- no app-maintained browsing-history list or search suggestions.

## Security model

Weglet applies certificate validation, high-confidence look-alike-domain blocking,
URL heuristics, a local user blocklist, known tracker rules, and an optional local
OpenPhish indicator database before navigation. HTTP requires explicit confirmation.
Certificate and safe-browsing failures cannot be bypassed through the Gecko fallback.

The Protection Center reports tracker requests blocked during the current process and
shows the most recent matched tracker domain. In WebView mode, a local post-load check
examines password-form structure, form destination, limited headings, and brand cues.
It never reads entered field values or sends page HTML to Weglet.

The selected engine also applies its own safe-browsing and storage behavior. These
layers reduce risk but do not replace antivirus software and cannot detect every new
threat. The exact user-facing terms are bundled in
`app/src/main/assets/legal/terms_privacy_*.txt`.

On an Android emulator, Weglet automatically uses system WebView and does not start
GeckoView. Physical devices retain the selected engine. Gecko is initialized only
when a Gecko page is actually opened, and the app does not request package-install
permission.

The bundled revision 10 combines terms of use, acceptable-use rules, limitations
of liability, third-party service disclosures, and a detailed privacy notice.
It supplements but does not replace the Apache License 2.0 rights in `LICENSE`.
Distributors should add the legally required publisher identity and contact details
for their jurisdiction and have the terms reviewed before a public release.
Fork maintainers should also read `FORK_POLICY.md`, `PRIVACY.md`, and `SECURITY.md`.

## Build

Open the repository in Android Studio with JDK 17 and Android SDK 36, then build the
`app` module. The project uses Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin
2.3.21, and the ARM64 GeckoView artifact pinned in `app/build.gradle.kts`.

For the standalone release build used by this repository:

```bash
python3 scripts/fetch-gecko-deps.py
scripts/build-apk.sh
```

The standalone script expects Android platform 36, build-tools 36.0.0, and the
Kotlin 2.3 compiler under `.android-toolchain/`. Android Studio resolves the same
dependencies normally and does not need the standalone cache.

## Licensing

Weglet application code is licensed under Apache License 2.0. Mozilla GeckoView is
distributed under Mozilla Public License 2.0. Onest and JetBrains Mono are under
SIL Open Font License 1.1. Interface icons are from Tabler Icons under the MIT
License. See `NOTICE`, `THIRD_PARTY_NOTICES.md`, and the license files bundled
with the app.

Android version developer: `@Durov500`  
Desktop version developer: `@avrety`
