import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tinyswords.realmwar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tinyswords.realmwar"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("releasePublic") {
            storeFile = file("signing/tinyswords-public-release.jks")
            storePassword = providers.gradleProperty("TINYSWORDS_STORE_PASSWORD").orElse("tinyswords").get()
            keyAlias = providers.gradleProperty("TINYSWORDS_KEY_ALIAS").orElse("tinyswords").get()
            keyPassword = providers.gradleProperty("TINYSWORDS_KEY_PASSWORD").orElse("tinyswords").get()
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("releasePublic")
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDir("../assets")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
