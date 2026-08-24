import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // No version: the Kotlin plugin is already on the inherited classpath from the root build.
    kotlin("jvm")
    id("me.champeau.jmh") version("0.7.3")
}

dependencies {
    implementation(project(":"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

jmh {
    warmupIterations.set(3)
    iterations.set(5)

    // Three forks, because one cannot separate a real change from JVM-to-JVM variance, and the
    // differences that matter here are single-digit nanoseconds.
    fork.set(3)

    // A read is nanosecond scale, so a second still buys tens of millions of operations. JMH's
    // default of ten seconds each is wasted.
    warmup.set("1s")
    timeOnIteration.set("1s")

    timeUnit.set("ns")
    benchmarkMode.set(listOf("avgt"))

    // Allocation per operation matters as much as time for anything on a frame budget, where
    // garbage becomes stutter.
    profilers.set(listOf("gc"))
}
