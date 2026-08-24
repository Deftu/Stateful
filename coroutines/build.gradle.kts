import dev.deftu.kit.conventions.java.plugin.KitJavaExtension
import dev.deftu.kit.core.plugin.KitCoreExtension

plugins {
    id("dev.deftu.kit.core")
    id("dev.deftu.kit.conventions.kotlin-multiplatform")
    id("dev.deftu.kit.maven-releases")
}

kitMavenReleases {
    baseArtifactId.set("${rootProject.name}-${project.name}")
}

// Repeated from the root build for the same reason it exists there: Kit's multiplatform
// convention reads the JVM target from its Java extension, but only `conventions.java` installs
// that extension's default, and applying that here would pull the Java plugin into a Kotlin
// Multiplatform build.
configure<KitCoreExtension> {
    extensions.configure<KitJavaExtension>(KitJavaExtension.NAME) {
        targetVersion.set(providers.gradleProperty("kit.java.version").map { raw ->
            KitJavaExtension.parseMajorVersion(raw)
        })
    }
}

kotlin {
    explicitApi()

    jvm {
        withSourcesJar()
    }

    js(IR) {
        generateTypeScriptDefinitions()
        binaries.library()
    }

    wasmJs {
        generateTypeScriptDefinitions()
        binaries.library()
    }

    linuxX64()
    mingwX64()
    macosX64()
    macosArm64()

    iosArm64()
    iosSimulatorArm64()

    tvosArm64()
    tvosX64()
    tvosSimulatorArm64()

    watchosArm64()
    watchosX64()
    watchosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":"))
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            }
        }
    }
}
