plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":parsers"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.ktor.client.cio)
}

compose.desktop {
    application {
        mainClass = "org.akkirrai.hibiki.desktop.MainKt"

        nativeDistributions {
            packageName = "hibiki"
            packageVersion = "2.1.0"
            description = "A shared anime catalog for Android and Windows"
            vendor = "hibiki"
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
            )
            windows {
                menuGroup = "hibiki"
                shortcut = true
                menu = true
                perUserInstall = true
            }
        }
    }
}
