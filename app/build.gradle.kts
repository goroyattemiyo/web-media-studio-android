plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.goroyattemiyo.wms"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.goroyattemiyo.wms"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.1-a1-media-cards"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation:1.10.6")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.ui:ui:1.10.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.6")
    debugImplementation("androidx.compose.ui:ui-tooling:1.10.6")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")

    implementation("androidx.media3:media3-exoplayer:1.9.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    val youtubeDlAndroid = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubeDlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubeDlAndroid")

    testImplementation("junit:junit:4.13.2")
}
