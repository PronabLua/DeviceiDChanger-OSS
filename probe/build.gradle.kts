plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.pronablua.deviceidchanger.probe"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.pronablua.deviceidchanger.probe"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-probe"
    }

    buildFeatures {
        resValues = true
    }

    flavorDimensions += "role"
    productFlavors {
        create("target") {
            dimension = "role"
            resValue("string", "app_name", "ID Probe Target")
        }
        create("control") {
            dimension = "role"
            applicationIdSuffix = ".control"
            resValue("string", "app_name", "ID Probe Control")
        }
    }

    buildTypes {
        create("compatibility") {
            initWith(getByName("release"))
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".compat"
            versionNameSuffix = "-compat"
            resValue("string", "app_name", "ID Probe Release")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
