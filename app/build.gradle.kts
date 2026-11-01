plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.homeflix.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.homeflix.tv"
        minSdk = 23
        targetSdk = 36
        versionCode = 12
        versionName = "5.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Stable sideload key committed to the repo so every CI-built APK has the
    // same signature and installs as an update over the previous one.
    // Replace with a secret-backed keystore before publishing to a store.
    signingConfigs {
        create("sideload") {
            storeFile = file("keystore/homeflix-sideload.jks")
            storePassword = "homeflix"
            keyAlias = "homeflix"
            keyPassword = "homeflix"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
            buildConfigField("String", "BASE_URL", "\"http://192.168.0.109:8252/api/\"")
            buildConfigField("String", "FALLBACK_BASE_URL", "\"https://homeflix.domain.com/api/\"")
        }
        release {
            signingConfig = signingConfigs.getByName("sideload")
            // R8 is opt-in (-PwithR8) until a minified build is verified on a TV:
            // crashes were reported right after 5.6 enabled it, while the same
            // code without R8 passes the end-to-end run against a real backend.
            isMinifyEnabled = project.hasProperty("withR8")
            isShrinkResources = project.hasProperty("withR8")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"http://192.168.0.109:8252/api/\"")
            buildConfigField("String", "FALLBACK_BASE_URL", "\"https://homeflix.domain.com/api/\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "2g"
            // End-to-end smoke test against a real backend: -Phomeflix.e2e.server=host:port
            project.findProperty("homeflix.e2e.server")?.let { v -> it.systemProperty("homeflix.e2e.server", v) }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// `./gradlew assembleRelease -PcomposeReports` writes Compose stability reports.
composeCompiler {
    if (project.hasProperty("composeReports")) {
        reportsDestination = layout.buildDirectory.dir("compose_reports")
        metricsDestination = layout.buildDirectory.dir("compose_reports")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.serialization.json)

    // All dependencies from your old project:
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.loggingInterceptor)
    implementation(libs.gson)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Debug builds only: reports any leaked Activity/ViewModel/View on the TV itself.
    debugImplementation(libs.leakcanary)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}