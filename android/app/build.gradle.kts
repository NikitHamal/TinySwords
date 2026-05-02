import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ------------------------------------------------------------------
// Signing configuration.
//
// The repo ships with a default *public* keystore at /android/keystore/
// so any push to any branch can produce installable, signed release APKs.
// CI may override these values via env vars or by writing
// /android/keystore/keystore.properties at build time.
// ------------------------------------------------------------------
val keystoreProps = Properties().apply {
    val props = rootProject.file("keystore/keystore.properties")
    if (props.exists()) {
        props.inputStream().use { load(it) }
    } else {
        // Fallback defaults baked into the repo.
        setProperty("storeFile", "../keystore/tinyswords.jks")
        setProperty("storePassword", "tinyswords")
        setProperty("keyAlias", "tinyswords")
        setProperty("keyPassword", "tinyswords")
    }
}

// Resolve the short commit hash for APK renaming. CI exports
// COMMIT_SHA explicitly; locally we shell out to git.
val commitSha: String = System.getenv("COMMIT_SHA")?.takeIf { it.isNotBlank() }
    ?: providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { "local" }

android {
    namespace = "com.tinyswords.realmwar"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tinyswords.realmwar"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("en")
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "COMMIT_SHA", "\"$commitSha\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/*.kotlin_module"
            )
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }

    androidResources {
        noCompress += setOf("png", "ogg", "mp3", "json")
    }

    // Rename APK output to include the commit hash + buildType.
    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val flavor = if (variant.buildType.name == "release") "release" else "debug"
            output.outputFileName = "TinySwords-$flavor-$commitSha.apk"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
