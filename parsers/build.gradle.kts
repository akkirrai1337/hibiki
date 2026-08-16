import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-test-fixtures`
    `maven-publish`
}

group = "org.akkirrai.hibiki"
version = "0.1.0"

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.ktor:ktor-client-core:3.5.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")
    implementation("no.synth:kmp-zip:0.9.1")
    implementation("org.jsoup:jsoup:1.21.2")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:3.5.0")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.5.0")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")

    testFixturesImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testFixturesImplementation("io.ktor:ktor-client-core:3.5.0")
    testFixturesImplementation("io.ktor:ktor-client-mock:3.5.0")
    testFixturesImplementation("io.ktor:ktor-client-content-negotiation:3.5.0")
    testFixturesImplementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "parsers"
            version = project.version.toString()
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/akkirrai1337/hibiki")
            credentials {
                username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
        }
    }
}
