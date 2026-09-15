import java.util.Properties
import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
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

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

/**
 * A value that must stay out of git: an environment variable (CI), a Gradle property
 * (~/.gradle/gradle.properties or -P), or local.properties, in that order. Empty when none is set,
 * so a build without it still works - the feature behind it simply stays off.
 */
fun buildSecret(name: String): String =
    System.getenv(name)
        ?: providers.gradleProperty(name).orNull
        ?: localProperties.getProperty(name)
        ?: ""

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
        versionCode = 271
        versionName = "2.7.1"

        buildConfigField("boolean", "GITHUB_UPDATES_ENABLED", "true")
        // MAL API client id for reading public anime data. Without it MAL is read through Jikan.
        buildConfigField("String", "MAL_CLIENT_ID", "\"${buildSecret("MAL_CLIENT_ID").trim()}\"")
        // AniList OAuth client id for optional library sync. The mobile implicit flow deliberately
        // has no client secret, so this public identifier is safe to embed in the app.
        buildConfigField("String", "ANILIST_CLIENT_ID", "\"${buildSecret("ANILIST_CLIENT_ID").trim()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The project carries a large amount of pre-existing lint debt (mostly media3's UnstableApi
    // opt-in warnings), which made `lintDebug` fail on the first of ~175 errors and therefore
    // useless as a signal. The baseline freezes what already exists so lint reports only what is
    // newly introduced; regenerate it with `./gradlew :app:updateLintBaseline` once the debt is
    // actually paid down.
    lint {
        baseline = file("lint-baseline.xml")
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
            isMinifyEnabled = true
            isShrinkResources = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("profileable") {
            initWith(getByName("release"))
            isDebuggable = false
            isProfileable = true
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ".profileable"
            signingConfig = signingConfigs.getByName("debug")
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

ksp {
    // Checked in, so a schema change shows up in review and a migration can be tested against it.
    arg("room.schemaLocation", "$projectDir/schemas")
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
    implementation(libs.play.services.cronet)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
