plugins {
    id("com.android.application")
}

android {
    namespace = "com.smsforwarder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smsforwarder"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.10"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
}
