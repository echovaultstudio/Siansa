plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// The version people see comes from version.txt (e.g. 0.0.1beta). The hidden versionCode only has to
// grow with every build so a new APK installs over the old one.
val appVersion = rootProject.file("version.txt").readText().trim()
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "app.siansa"
    compileSdk = 36
    // Extracting the FFmpeg decoder's symbols for the app bundle needs the NDK's objcopy (issue #519): the
    // version the Android Gradle Plugin 8.13 expects, installed by Gradle when it's missing.
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "app.siansa"
        minSdk = 26
        // Google Play takes updates only for apps targeting Android 16 (API 36) since 31 August 2026 (issue #494).
        targetSdk = 36
        versionCode = buildNumber
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The release key comes from outside the repository (public since #584): SIANSA_KEYSTORE (a path),
    // SIANSA_KEYSTORE_PASSWORD, SIANSA_KEY_ALIAS and SIANSA_KEY_PASSWORD, which CI fills from its secrets. Without
    // them a release build is unsigned and the debug build uses the debug key, so anyone can build from source.
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
            // R8 removes unused code and optimises the rest (Compose runs much smoother this way);
            // names are kept so stack traces in the logs stay readable.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("siansa")?.let { signingConfig = it }
            // The FFmpeg decoder is native code: its symbols go into the app bundle, so Google Play can read a
            // crash or ANR in it (issue #519). SYMBOL_TABLE gives function names; the libraries come prebuilt, so
            // files and lines (FULL) aren't there to keep.
            ndk.debugSymbolLevel = "SYMBOL_TABLE"
        }
        debug {
            // The same key as the release when there is one, so a debug build installs over a release build.
            signingConfigs.findByName("siansa")?.let { signingConfig = it }
        }
    }

    // An emulator Gradle starts by itself for the device tests (the standard image, so the app tour can take screenshots).
    testOptions {
        // Unit tests run on a plain JVM: Android calls they touch return defaults instead of failing.
        unitTests.isReturnDefaultValues = true
        managedDevices {
            localDevices {
                create("ciPhone") {
                    device = "Pixel 6"
                    apiLevel = 33
                    systemImageSource = "aosp"
                }
                // The Android the app targets (issue #494): used by the nightly run, so what changes for apps
                // targeting Android 16 (edge to edge, predictive back) is tested without a second emulator per run.
                create("android16") {
                    device = "Pixel 6"
                    apiLevel = 36
                    systemImageSource = "aosp"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Lint errors fail the build (CI shows each with its file and line); warnings are reported only.
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose, core, navigation and Coil stay here until the move to Android SDK 37 and Android Gradle
    // Plugin 9: their newer releases need both (see the GitHub issue on that migration).
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    // Installs the startup profile (baseline-prof.txt) so Android compiles the app ahead of time.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.palette:palette-ktx:1.1.0")

    // Media3 stays on the version the FFmpeg decoder below is built for (Jellyfin's build); update both together.
    implementation("androidx.media3:media3-exoplayer:1.9.0")
    implementation("androidx.media3:media3-session:1.9.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.9.0")
    implementation("androidx.media3:media3-database:1.9.0")
    // Google Cast: plays the queue on a Chromecast or Google TV (needs Google Play services on the phone).
    implementation("androidx.media3:media3-cast:1.9.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    // FFmpeg audio decoders (ALAC, and FLAC/MP3/AAC/AC-3 as backups) for formats the phone can't play itself.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("io.coil-kt.coil3:coil-gif:3.0.4")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Fast tests of the app's own logic, run on the build machine on every push (app/src/test).
    testImplementation("junit:junit:4.13.2")

    // Tests that run on an Android emulator in CI (see .github/workflows/ci.yml).
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// CHANGELOG.md (updated by CI before the build) goes into the APK's assets, for Settings → What's new.
abstract class BundleChangelog : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val changelog: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = outputDir.get().asFile
        dir.deleteRecursively()
        dir.mkdirs()
        changelog.get().asFile.copyTo(File(dir, "CHANGELOG.md"), overwrite = true)
    }
}

val bundleChangelog = tasks.register<BundleChangelog>("bundleChangelog") {
    changelog.set(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundleChangelog, BundleChangelog::outputDir)
    }
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
