plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-test-fixtures`
    id("com.google.devtools.ksp")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    implementation("io.ktor:ktor-client-core:3.5.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jsoup:jsoup:1.21.2")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:3.5.0")

    testFixturesImplementation(kotlin("test"))
    ksp(project(":beakokit-processor"))
}

tasks.test {
    useJUnitPlatform()
}

// The built-in catalog belongs to main; tests consume that compiled output instead of regenerating it.
tasks.matching { it.name == "kspTestKotlin" || it.name == "kspTestFixturesKotlin" }.configureEach {
    enabled = false
}
