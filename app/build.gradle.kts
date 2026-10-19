import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

// Cloud-sync OAuth client IDs (public identifiers, not secrets). Optional: with
// none set the app builds and the provider rows show "Not set up yet".
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val googleClientId: String = localProperties.getProperty("google.clientId", "").trim()
val microsoftClientId: String = localProperties.getProperty("microsoft.clientId", "").trim()
val googleRedirectScheme: String =
    if (googleClientId.endsWith(".apps.googleusercontent.com"))
        "com.googleusercontent.apps." + googleClientId.removeSuffix(".apps.googleusercontent.com")
    else "dhikr.unconfigured"

android {
    namespace = "com.dhikr.app"
    compileSdk = 37

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.badhon495.dhikr"
        minSdk = 24
        targetSdk = 37
        versionCode = 8
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")
        buildConfigField("String", "MICROSOFT_CLIENT_ID", "\"$microsoftClientId\"")
        // AppAuth's own manifest needs appAuthRedirectScheme; our manifest replaces its
        // redirect activity with one that handles both providers' schemes.
        manifestPlaceholders["appAuthRedirectScheme"] = "dhikr.unconfigured"
        manifestPlaceholders["googleRedirectScheme"] = googleRedirectScheme
        manifestPlaceholders["microsoftRedirectScheme"] = "com.badhon495.dhikr.msauth"

        // Ship only the languages we actually translate. Keeps the per-app
        // language picker (locales_config.xml) honest and trims unused
        // AppCompat/lib locale resources.
        androidResources.localeFilters += listOf("en", "bn")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
        debug {
            isMinifyEnabled = false
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            // Keep R8 on so the benchmark measures a release-like binary.
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // ABI splits produce the per-architecture APKs (+ a universal APK) for
    // direct distribution — side-load, GitHub release. They are mutually
    // exclusive with `bundleRelease`: the .aab already carries per-ABI splits,
    // and building both at once fails (issuetracker.google.com/402800800). So
    // enable splits only when no bundle task is in the graph; a plain
    // `bundleRelease` run gets a clean single-artifact build.
    val buildingBundle = gradle.startParameter.taskNames.any {
        it.contains("bundle", ignoreCase = true)
    }
    splits {
        abi {
            isEnable = !buildingBundle
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)
    implementation(libs.appauth)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.android)
}
