plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Siansa for Android TV and Google TV (issue #860): an app of its own, app.siansa.tv, installed next to the phone app
// and never over it. It builds the phone app's code as it is (the server and library, mixes, the playback service and
// the audio engine) from app/src/main, and adds its own screens for the remote from tv/src/main. Nothing in app/ depends
// on this module, so the phone app builds and ships exactly as before.
val appVersion = rootProject.file("version.txt").readText().trim()
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val shared = rootProject.file("app/src/main")

android {
    // The same namespace as the phone app: its code refers to app.siansa.R and app.siansa.BuildConfig.
    namespace = "app.siansa"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "app.siansa.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = appVersion
        // The TV app plays through the new audio engine (issue #826), the engine v1.0 brings to everyone.
        buildConfigField("boolean", "MIXER_ENGINE", "true")
        buildConfigField("boolean", "TV_APP", "true")
        val castReceiverId = (System.getenv("CAST_RECEIVER_ID")?.takeIf { it.isNotBlank() }
            ?: providers.gradleProperty("siansa.castReceiverId").orNull).orEmpty().filter { it.isLetterOrDigit() }
        buildConfigField("String", "CAST_RECEIVER_ID", "\"$castReceiverId\"")
    }

    sourceSets {
        getByName("main") {
            // The phone app's code and resources, then the TV's own. The TV's manifest is its own (tv/src/main).
            java.srcDirs(shared.resolve("java"), "src/main/java")
            res.srcDirs(shared.resolve("res"), "src/main/res")
        }
    }

    // The same key as the phone app (from CI's secrets); without one a release build is unsigned.
    val keystorePath = System.getenv("SIANSA_KEYSTORE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (keystorePath != null) create("siansa") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("SIANSA_KEYSTORE_PASSWORD").orEmpty()
            keyAlias = System.getenv("SIANSA_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "siansa"
            keyPassword = System.getenv("SIANSA_KEY_PASSWORD").orEmpty()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), rootProject.file("app/proguard-rules.pro"), "proguard-rules.pro")
            signingConfigs.findByName("siansa")?.let { signingConfig = it }
            ndk.debugSymbolLevel = "SYMBOL_TABLE"
        }
        debug {
            signingConfigs.findByName("siansa")?.let { signingConfig = it }
        }
        // Siansa TV Dev: built with Siansa Dev from the mixer branch (.github/workflows/dev-app.yml), installed next to
        // the TV app as app.siansa.tv.dev. Never sent to Google Play.
        create("dev") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            matchingFallbacks += "release"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
        // The phone app's code is linted in the app module; here only what the TV adds would be new.
        checkDependencies = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // The phone app's dependencies, at the same versions (see app/build.gradle.kts and issue #23).
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.palette:palette-ktx:1.1.0")

    implementation("androidx.media3:media3-exoplayer:1.9.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.9.0")
    implementation("androidx.media3:media3-session:1.9.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.9.0")
    implementation("androidx.media3:media3-database:1.9.0")
    implementation("androidx.media3:media3-cast:1.9.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("io.coil-kt.coil3:coil-gif:3.0.4")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // The TV's sign-in QR code (issue #860): ZXing's encoder only, plain Java with no Android parts.
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}
