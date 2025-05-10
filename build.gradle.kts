import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version("2.0.10")
    val dgt = "2.34.0"
    id("dev.deftu.gradle.tools") version(dgt)
    id("dev.deftu.gradle.tools.publishing.maven") version(dgt)
}

kotlin {
    explicitApi()

    // --- JVM (Desktop, Android, Server) ---
    jvm {
        // Compile to Java 8 bytecode
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }

        withJava()
        withSourcesJar()
    }

    // --- JavaScript (Browser, Node.js) ---
    js(IR) {
        generateTypeScriptDefinitions()
        browser()
        nodejs()
        binaries.library()
    }

    // --- WebAssembly (Experimental) ---
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        generateTypeScriptDefinitions()
        browser()
        binaries.library()
    }

    // --- Native (Commonly Used Platforms) ---
    linuxX64()       // Desktop Linux
    mingwX64()       // Windows native
    macosX64()       // macOS Intel
    macosArm64()     // macOS Apple Silicon

    iosArm64()       // iOS devices
    iosSimulatorArm64() // iOS simulator for Apple Silicon

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(kotlin("reflect"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
