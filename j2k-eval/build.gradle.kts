/*
 * j2k-eval module
 *
 * Evaluates pre-converted Kotlin files (produced by IntelliJ's J2K converter)
 * against the original Java source. Checks compilation, structural fidelity,
 * and idiomatic Kotlin usage.
 */

plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Standalone Java parser for structural comparison (AST-based metrics)
    implementation("com.github.javaparser:javaparser-core:3.26.2")

    // JSON report output
    implementation("com.google.code.gson:gson:2.10.1")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("eval.MainKt")
}
