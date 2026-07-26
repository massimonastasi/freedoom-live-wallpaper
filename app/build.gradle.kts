plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.massimonastasi.freedoomlw"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.massimonastasi.freedoomlw"
        // minSdk 31: Material You (onComputeColors -> system theme) arrived with Android 12.
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // ponytail: no compression on .wad files — they are read with random access from assets.
    androidResources {
        noCompress += "wad"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// ponytail: no runtime dependencies. WallpaperService and Canvas are part of the framework.
// Only the tests have dependencies, and those never reach the APK.
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}
