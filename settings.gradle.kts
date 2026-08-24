import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    // Kit cannot bootstrap its own repository out of itself.
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.deftu.dev/snapshots/")
    }
}

plugins {
    val kitVersion = "0.6.0"
    id("dev.deftu.kit.settings") version(kitVersion)
    id("dev.deftu.kit.settings.repositories") version(kitVersion)
}

dependencyResolutionManagement {
    // Kit's repositories plugin sets PREFER_SETTINGS. The Kotlin/JS toolchain adds its own
    // ivy repositories for the Node and Yarn distributions from inside the project, which that
    // mode discards, and the build then fails looking for `org.nodejs:node` on Maven Central.
    // This project declares no repositories of its own, so nothing can shadow Kit's set.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)

    repositories {
        // Compose Multiplatform's runtime resolves to androidx artifacts on JVM and Android,
        // and those are published to Google's repository rather than Maven Central. Scoped to
        // the groups that actually live there so it is not consulted for anything else.
        google {
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.google\\..*")
                includeGroupByRegex("com\\.android.*")
            }
        }
    }
}

include("coroutines")
include("elementa")
include("compose")
