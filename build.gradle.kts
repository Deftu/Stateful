import dev.deftu.kit.conventions.java.plugin.KitJavaExtension
import dev.deftu.kit.core.plugin.KitCoreExtension

plugins {
    val kitVersion = "0.6.0"

    // Neither the multiplatform convention nor maven-releases applies core themselves, and
    // core is what reads `project.group` and `project.version`. Without it both are unset and
    // every artifact publishes as "unspecified". Declared without a version because the
    // conventions below already put it on the classpath, and requesting one then fails.
    id("dev.deftu.kit.core")

    id("dev.deftu.kit.conventions.kotlin-multiplatform") version(kitVersion)
    id("dev.deftu.kit.maven-releases") version(kitVersion)
}

// Kit's multiplatform convention reads the JVM target from its Java extension, but only
// `conventions.java` installs that extension's default — and applying that here would pull the
// Java plugin into a Kotlin Multiplatform build. Without a value the convention sets
// `jvmTarget` to an empty provider and `compileKotlinJvm` fails configuration validation, so
// seed it from the same property Kit would have read.
configure<KitCoreExtension> {
    extensions.configure<KitJavaExtension>(KitJavaExtension.NAME) {
        targetVersion.set(providers.gradleProperty("kit.java.version").map { raw ->
            KitJavaExtension.parseMajorVersion(raw)
        })
    }
}

kotlin {
    explicitApi()

    // Kit's convention brings up jvm, js, wasmJs and the four desktop native targets from the
    // `kit.kmp.*` properties. Everything below either adds a target it does not cover or adds
    // configuration to one it does.

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

    // --- iOS ---
    iosArm64()
    iosSimulatorArm64()

    // --- tvOS ---
    tvosArm64()
    tvosX64()
    tvosSimulatorArm64()

    // --- watchOS ---
    watchosArm64()
    watchosX64()
    watchosSimulatorArm64()

    sourceSets {
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
