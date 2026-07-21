import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization")
}

val hibikiIconResDir = layout.buildDirectory.dir("generated/res/hibikiIcon").get().asFile
val syncHibikiIcon = tasks.register<Copy>("syncHibikiIcon") {
    from(rootProject.file("fastlane/metadata/android/en-US/images/icon.png"))
    into(hibikiIconResDir.resolve("drawable-nodpi"))
    rename { "hibiki_app_icon.png" }
}

fun releaseSigningValue(name: String): String? =
    System.getenv(name)
        ?: providers.gradleProperty(name).orNull

val releaseStoreFile = releaseSigningValue("HIBIKI_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningValue("HIBIKI_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("HIBIKI_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("HIBIKI_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    sourceSets["main"].res.srcDir(hibikiIconResDir)
    namespace = "org.akkirrai.hibiki"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.akkirrai.hibiki"
        minSdk = 26
        targetSdk = 36
        versionCode = 200
        versionName = "2.0.0"

        buildConfigField("boolean", "GITHUB_UPDATES_ENABLED", "true")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (!releaseStoreFile.isNullOrBlank()) {
                storeFile = file(releaseStoreFile)
            }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("fdroid") {
            initWith(getByName("release"))
            buildConfigField("boolean", "GITHUB_UPDATES_ENABLED", "false")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.named("preBuild") {
    dependsOn(syncHibikiIcon)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":parsers"))
    implementation(libs.androidx.core.ktx)
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.animation.graphics)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material3:material3:1.5.0-alpha23")
    implementation(libs.androidx.compose.material.icons.extended)
    implementation("me.saket.cascade:cascade-compose:2.3.0")
    implementation(libs.androidx.palette.ktx)
    implementation(libs.material.kolor)
    implementation(libs.kizzy.rpc) {
        exclude(group = "io.ktor", module = "ktor-client-cio")
    }
    implementation(libs.androidx.navigation.compose)
    implementation(enforcedPlatform("io.ktor:ktor-bom:${libs.versions.ktor.get()}"))
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.database)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    testImplementation(libs.junit)
    testImplementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
