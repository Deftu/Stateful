plugins {
    id("dev.deftu.kit.core")
    id("dev.deftu.kit.conventions.kotlin-multiplatform")
    id("dev.deftu.kit.maven-releases")
}

kitMavenReleases {
    baseArtifactId.set("${rootProject.name}-${project.name}")
}

kotlin {
    explicitApi()

    // JS only. This adapter exists to feed React hooks, so there is nothing to publish
    // for a JVM or native consumer.
    js(IR) {
        generateTypeScriptDefinitions()
        binaries.library()
        browser()
        nodejs()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                api(project(":"))
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
