plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    androidLibrary {
        namespace = "org.akkirrai.hibiki.shared"
        compileSdk = 37
        minSdk = 26
        androidResources {
            enable = true
        }
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
        withHostTestBuilder {}.configure {}
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(libs.ktor.client.core)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)
                implementation(libs.coil.compose)
                implementation(libs.material.kolor)
                implementation(libs.kotlinx.coroutines.core)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation(project(":parsers"))
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation(libs.coil.network.okhttp)
                implementation(libs.androidx.palette.ktx)
                implementation(libs.androidx.activity.compose)
            }
        }
    }
}
