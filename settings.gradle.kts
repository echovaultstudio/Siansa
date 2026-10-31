pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Robolectric's files from Google's copy of Maven Central (issue #749): Maven Central refuses its big runtime
        // jar at busy times (429 Too Many Requests), which stopped a release build.
        exclusiveContent {
            forRepository {
                maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralByGoogle" }
            }
            filter { includeGroup("org.robolectric") }
        }
        mavenCentral()
    }
}

rootProject.name = "siansa"
include(":app")
