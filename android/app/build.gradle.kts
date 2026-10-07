plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "uk.co.zenithal.carrel"
    compileSdk = 37

    defaultConfig {
        applicationId = "uk.co.zenithal.carrel"
        // supabase-kt needs Android 8.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "WEBSITE_URL", "\"https://carrel.zenithal.co.uk\"")
    }

    // The release key and its passwords live outside the repo, in ~/.gradle/gradle.properties (see the README).
    val releaseStore = providers.gradleProperty("carrel.release.storeFile").orNull
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("carrel.release.storePassword").get()
                keyAlias = providers.gradleProperty("carrel.release.keyAlias").get()
                keyPassword = providers.gradleProperty("carrel.release.keyPassword").get()
            }
        }
    }

    buildTypes {
        // Test builds use the dev Supabase project and a local API, reached over USB with
        // `adb reverse tcp:5155 tcp:5155`. They install alongside the real app.
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Carrel Dev")
            buildConfigField("String", "SUPABASE_URL", "\"https://stbhesoohtliyltjxvsd.supabase.co\"")
            buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_krJi6cM_WB0J-UiuxqkGWA_ZdFFDfWy\"")
            buildConfigField("String", "API_URL", "\"http://localhost:5155\"")
        }
        release {
            resValue("string", "app_name", "Carrel")
            buildConfigField("String", "SUPABASE_URL", "\"https://iirzotkuqblvyfvjwuta.supabase.co\"")
            buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_9P6mzBQSRUKMQYNEdjUoJA_GHK1DU-D\"")
            buildConfigField("String", "API_URL", "\"https://carrel-api-37cc53k4jq-ew.a.run.app\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.junit)
}
