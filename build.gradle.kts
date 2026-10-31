plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
    // Screenshot tests (issue #710): renders parts of the app on the build machine and compares them with approved images.
    id("io.github.takahirom.roborazzi") version "1.77.0" apply false
}
