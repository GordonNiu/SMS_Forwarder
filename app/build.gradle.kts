plugins {
    id("com.android.application")
}

android {
    namespace = "com.smsforwarder"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.smsforwarder"
        minSdk = 26
        targetSdk = 37
        versionCode = 10
        versionName = "0.10"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
