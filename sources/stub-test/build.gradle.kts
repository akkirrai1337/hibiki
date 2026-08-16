plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "org.akkirrai.hibiki.source.stub"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.akkirrai.hibiki.source.stub"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":parsers"))
    implementation(libs.kotlinx.coroutines.android)
}
