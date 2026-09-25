plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.attendpro.store"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.attendpro.store"
        minSdk = 26
        targetSdk = 36
        versionCode = 155
        versionName = "2.0.0-RC29-V155-FINAL-QA-RELEASE-HARDENING"
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

    lint {
        // Lint is audited by CI after report generation. Existing findings are
        // allowed only when their source file is byte-locked by RC29/V149.
        abortOnError = false
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
