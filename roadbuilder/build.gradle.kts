import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

application {
    mainClass.set("io.github.lrosa2.speedbeep.roadbuilder.MainKt")
    applicationDefaultJvmArgs = listOf("-Xmx4g")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.parallelpbf) // GPLv3: only in this PC tool, never in :core or the app
    runtimeOnly(libs.slf4j.simple)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// Relative paths on the command line resolve against the repository root.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
