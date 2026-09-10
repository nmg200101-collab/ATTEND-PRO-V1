plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.attendpro.employee"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.attendpro.employee"
        minSdk = 26
        targetSdk = 35
        versionCode = 90
        versionName = "2.0.0-FOUNDATION"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "ENFORCE_OFFICIAL_SIGNATURE", "false")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "ENFORCE_OFFICIAL_SIGNATURE", "true")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":foundation"))
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
