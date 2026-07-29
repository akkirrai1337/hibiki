import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    `java-test-fixtures`
    id("com.google.devtools.ksp")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation("io.ktor:ktor-client-core:3.5.0")
                implementation("io.ktor:ktor-client-content-negotiation:3.5.0")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")
            }
        }
        val jvmMain by getting {
            kotlin.srcDir("src/jvmMain/kotlin")
            dependencies {
                implementation("io.ktor:ktor-client-core:3.5.0")
                implementation("io.ktor:ktor-client-content-negotiation:3.5.0")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation("org.jsoup:jsoup:1.21.2")
            }
        }
        val jvmTest by getting {
            kotlin.srcDir("src/test/kotlin")
            kotlin.srcDir("src/testFixtures/kotlin")
            resources.srcDir("src/test/resources")
            resources.srcDir("src/testFixtures/resources")
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-client-mock:3.5.0")
                implementation("io.ktor:ktor-client-content-negotiation:3.5.0")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")
            }
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", project(":beakokit-processor"))
    add("kspJvm", project(":beakokit-processor"))
}

ksp {
    arg("beakokit.excludeCommonSourceEntries", "true")
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
