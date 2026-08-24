plugins {
    id("dev.deftu.kit.core")

    // Versioned because the root applies the multiplatform convention, not this one, so it is not
    // already on the inherited plugin classpath the way the others are.
    id("dev.deftu.kit.conventions.kotlin-jvm") version("0.6.0")

    id("dev.deftu.kit.maven-releases")
}

kitMavenReleases {
    baseArtifactId.set("${rootProject.name}-${project.name}")
}

dependencies {
    api(project(":"))

    // The state system ships as its own artifact and depends on nothing but the Kotlin stdlib.
    api("gg.essential:elementa-unstable-statev2:762")

    // `ReferenceHolder` lives in the main artifact rather than the state one, and `effect` cannot
    // be called without it. Compile-only because a consumer of this adapter is a Minecraft mod
    // that already has Elementa on its classpath, and resolving it here must not pull a UI
    // toolkit into an unrelated build.
    compileOnly("gg.essential:elementa:762")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("gg.essential:elementa:762")
}
