plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.weglet.browser"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.weglet.browser.v2"
        minSdk = 29
        targetSdk = 36
        versionCode = 76
        versionName = "1.3.6"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview-arm64-v8a:153.0.20260803132010")
}
