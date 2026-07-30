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
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    val iosTargets = listOf(iosArm64(), iosSimulatorArm64())
    val hostIsMacOs = System.getProperty("os.name").contains("mac", ignoreCase = true)
    iosTargets.forEach { target ->
        if (hostIsMacOs) {
            target.compilations.getByName("main").defaultSourceSet.kotlin.srcDir("src/iosMacMain/kotlin")
            target.compilations.getByName("main") {
                cinterops {
                    create("iosPlayer") {
                        defFile(project.file("src/nativeInterop/cinterop/iosPlayer.def"))
                    }
                }
            }
        }
        target.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)
                implementation(libs.coil.compose)
                implementation(libs.material.kolor)
                implementation(libs.kotlinx.coroutines.core)
                implementation(project(":parsers"))
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.coil.network.okhttp)
                implementation(libs.androidx.palette.ktx)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.coil.network.ktor3)
                implementation(libs.ktor.client.cio)
            }
        }
        val iosMain by creating {
            dependsOn(commonMain)
            kotlin.srcDir("src/iosMain/kotlin")
        }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.5.0")
            implementation(libs.coil.network.ktor3)
        }
    }
}
