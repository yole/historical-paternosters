plugins {
    kotlin("jvm") version "1.9.20"
}

group = "page.yole"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.apache.velocity:velocity-engine-core:2.3")
    implementation("org.jetbrains:markdown:0.5.2")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    testImplementation(kotlin("test"))
    implementation(kotlin("reflect"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}