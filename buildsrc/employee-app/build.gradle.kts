plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.attendpro.employee"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.attendpro.employee"
        minSdk = 26
        targetSdk = 36
        versionCode = 94
        versionName = "2.0.0-RC4"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("direct") {
            dimension = "distribution"
            buildConfigField("boolean", "DIRECT_DISTRIBUTION", "true")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "DIRECT_DISTRIBUTION", "false")
        }
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
    add("playImplementation", "com.google.android.play:app-update:2.1.0")
    implementation(project(":core"))
    implementation(project(":foundation"))
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.core:core-ktx:1.13.1")
}
