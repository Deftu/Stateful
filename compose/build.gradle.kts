import dev.deftu.kit.conventions.java.plugin.KitJavaExtension
import dev.deftu.kit.core.plugin.KitCoreExtension

plugins {
    id("dev.deftu.kit.core")
    id("dev.deftu.kit.conventions.kotlin-multiplatform")
    id("dev.deftu.kit.maven-releases")

    // Only the compiler plugin, not the full Compose Multiplatform plugin. This adapter needs
    // `@Composable` and the runtime; it ships no UI and must not drag desktop and Android
    // packaging into the build.
    id("org.jetbrains.kotlin.plugin.compose") version("2.4.0")
}

kitMavenReleases {
    baseArtifactId.set("${rootProject.name}-${project.name}")
}

// Java 11, not the library's usual 8. Compose Multiplatform's runtime is compiled for 11 and
// its inline functions cannot be inlined into 1.8 bytecode, so the floor is not ours to pick.
// Every other module still targets 8.
configure<KitCoreExtension> {
    extensions.configure<KitJavaExtension>(KitJavaExtension.NAME) {
        targetVersion.set(11)
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

    // A narrower target set than core, and not by choice: Compose Multiplatform 1.11 does not
    // publish its runtime for Intel macOS, and the Apple TV and watch targets have never been
    // published at all. A consumer on those platforms uses core directly.
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":"))
                api("org.jetbrains.compose.runtime:runtime:1.11.1")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
    }
}
