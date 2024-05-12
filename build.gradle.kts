plugins {
    java
    kotlin("jvm") version ("1.9.0")
    val dgt = "1.22.2"
    id("dev.deftu.gradle.tools") version (dgt)
    id("dev.deftu.gradle.tools.maven-publishing") version (dgt)
}

toolkitMavenPublishing {
    artifactName.set(projectData.name.toLowerCase())
}

dependencies {
    compileOnly(kotlin("reflect"))
}
