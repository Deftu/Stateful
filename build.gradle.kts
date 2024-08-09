import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTask
import java.time.Year

plugins {
    java
    kotlin("jvm") version ("2.0.0")
    val dgt = "2.6.0"
    id("dev.deftu.gradle.tools") version (dgt)
    id("dev.deftu.gradle.tools.dokka") version (dgt)
    id("dev.deftu.gradle.tools.publishing.maven") version (dgt)
}

kotlin.explicitApi()

toolkitMavenPublishing {
    forceLowercase.set(true)
}

dependencies {
    //// Main dependencies
    compileOnly(kotlin("reflect"))

    //// Test dependencies
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

tasks {

    withType<DokkaTask> {
        dokkaSourceSets {
            named("main") {
                moduleName.set(projectData.name)
                moduleVersion.set(projectData.version)
            }
        }

        pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
            separateInheritedMembers = true
            mergeImplicitExpectActualDeclarations = true
            footerMessage = "© ${Year.now().value} Deftu"

            customStyleSheets = listOf(rootProject.file("dokka").resolve("styles.css"))
        }
    }

}
