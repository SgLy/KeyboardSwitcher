plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "me.sgly.keyboardswitcher"
    compileSdk = 34

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEYSTORE_ALIAS") ?: "keyboardswitcher"
            keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
        }
    }

    
    splits {
        abi {
            isEnable = true
            reset()
            include("x86_64", "arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    defaultConfig {
        applicationId = "me.sgly.keyboardswitcher"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    val shizuku_version = "13.1.0"
    implementation("dev.rikka.shizuku:api:$shizuku_version")
    implementation("dev.rikka.shizuku:provider:${shizuku_version}")
}

