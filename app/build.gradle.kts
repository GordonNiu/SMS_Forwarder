plugins {
    id("com.android.application")
}

android {
    namespace = "com.smsforwarder"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.gordonniu.smsforwarder"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.11"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    val signingStoreFile = System.getenv("SMS_FORWARDER_STORE_FILE")?.takeIf { it.isNotBlank() }
    val signingStorePassword = System.getenv("SMS_FORWARDER_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
    val signingKeyAlias = System.getenv("SMS_FORWARDER_KEY_ALIAS")?.takeIf { it.isNotBlank() }
    val signingKeyPassword = System.getenv("SMS_FORWARDER_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
    val hasEnvSigning = signingStoreFile != null
            && signingStorePassword != null
            && signingKeyAlias != null
            && signingKeyPassword != null
            && file(signingStoreFile).exists()

    signingConfigs {
        if (hasEnvSigning) {
            create("release") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasEnvSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
