plugins {
    alias(libs.plugins.module.android.application)
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.plugin)
    alias(libs.plugins.ksp)
}

// A fork must build without the upstream maintainer's Firebase configuration or secrets.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// A stable private key is encrypted in the repository; only the owner controls
// its GitHub Actions decryption secret. Local unsigned builds use default debug signing.
val liveSigningSecret = System.getenv("LIVE_SIGNING_SECRET")?.takeIf { it.isNotBlank() }

android {
    namespace = "pl.jojczak.penmouses"

    defaultConfig {
        applicationId = "pl.inaccurate21.penmouses.live"
        versionNameSuffix = "-live.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("stableLiveDebug") {
            storeFile = file("signing/live-debug-keystore.p12")
            storePassword = liveSigningSecret
            keyPassword = liveSigningSecret
            keyAlias = "penmouse-live"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
        create("release") {
            storeFile = file("keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyPassword = System.getenv("KEY_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            if (liveSigningSecret != null) {
                signingConfig = signingConfigs.getByName("stableLiveDebug")
            }
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:common"))

    implementation(project(":screen:home"))
    implementation(project(":screen:manual"))
    implementation(project(":screen:settings"))

    implementation(project(":mousemode:base"))
    implementation(project(":mousemode:basecursor"))
    implementation(project(":mousemode:point"))
    implementation(project(":mousemode:scroll"))
    implementation(project(":mousemode:mouse"))

    implementation(libs.bundles.compose.core)
    implementation(libs.android.material)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation(libs.bundles.firebase)

    implementation(libs.bundles.haze)

    implementation(libs.bundles.hilt)
    ksp(libs.bundles.hilt.ksp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.bundles.compose.debug)
}
