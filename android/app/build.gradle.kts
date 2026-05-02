import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The commit hash and short commit hash are exposed to BuildConfig and used to rename the
// release APK so every CI artifact is uniquely traceable. The CI workflow sets the
// `COMMIT_SHA` environment variable on every push.
val commitSha: String = (System.getenv("COMMIT_SHA") ?: runCatching {
    providers.exec {
        commandLine("git", "rev-parse", "HEAD")
    }.standardOutput.asText.get().trim()
}.getOrNull() ?: "local").trim()
val shortCommit: String = commitSha.take(8)

android {
    namespace = "com.tinyswords.realmwar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tinyswords.realmwar"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0+$shortCommit"
        resourceConfigurations += listOf("en")
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "GIT_COMMIT", "\"$commitSha\"")
        buildConfigField("String", "GIT_COMMIT_SHORT", "\"$shortCommit\"")
    }

    // Re-use the pixel-art assets that already ship in the repo root /assets folder so the
    // Android game uses the *exact same* sprites as the web game without duplicating files.
    sourceSets {
        getByName("main") {
            assets.srcDirs(
                "src/main/assets",
                rootProject.file("../assets")
            )
        }
    }

    signingConfigs {
        create("release") {
            // Uses the public default keystore that ships in the repo. The user explicitly
            // asked for a committed default keystore that is fine for CI to consume.
            val keystorePropsFile = rootProject.file("keystore/keystore.properties")
            val props = Properties()
            if (keystorePropsFile.exists()) {
                keystorePropsFile.inputStream().use { props.load(it) }
            }
            storeFile = rootProject.file(props.getProperty("storeFile", "keystore/release.jks"))
            storePassword = props.getProperty("storePassword", "tinyswords")
            keyAlias = props.getProperty("keyAlias", "tinyswords")
            keyPassword = props.getProperty("keyPassword", "tinyswords")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // Rename built APKs by commit hash so artifacts uploaded by CI are immediately
    // identifiable: TinySwords-RealmWar-<buildType>-<shortCommit>.apk
    applicationVariants.all {
        val variant = this
        val variantName = variant.buildType.name
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            output.outputFileName = "TinySwords-RealmWar-$variantName-$shortCommit.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
