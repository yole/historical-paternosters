plugins {
    kotlin("jvm") version "1.9.21"
    application
}

group = "page.yole"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.apache.velocity:velocity-engine-core:2.3")
    implementation("org.apache.velocity.tools:velocity-tools-generic:3.1")
    implementation("org.jetbrains:markdown:0.5.2")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("com.beust:klaxon:5.5")
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    testImplementation(kotlin("test"))
    implementation(kotlin("reflect"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "page.yole.paternosters.GenerateKt"
}
