plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"
    application
}

repositories {
    mavenCentral()
    // apksig (the APK signature verifier Android itself uses) is published here only.
    google()
    // Injekt, which Aniyomi extensions resolve their dependencies through.
    maven("https://jitpack.io")
}

kotlin { jvmToolchain(21) }

application { mainClass.set("app.hibiki.apkhost.MainKt") }

/**
 * The Android build's own sources for the part that is plain Kotlin: the Aniyomi source API, the
 * network helpers and the adapter that answers the page in the shared JSON shapes. They compile
 * here against src/main/java's android.* stand-ins, so the two builds cannot drift apart. What
 * needs the real Android (the network helper's WebView, the package manager) is replaced by this
 * project's own file of the same name.
 */
val androidSources = rootDir.resolve("../android/app/src/main/java")
val syncShared = tasks.register<Sync>("syncSharedSources") {
    from(androidSources) {
        include("eu/**/*.kt", "app/hibiki/apk/ApkSourceAdapter.kt", "app/hibiki/apk/ApkFilters.kt", "app/hibiki/apk/ApkPreferences.kt")
        exclude(
            "eu/kanade/tachiyomi/AppInfo.kt",
            "eu/kanade/tachiyomi/network/NetworkHelper.kt",
            "eu/kanade/tachiyomi/network/JavaScriptEngine.kt",
            "eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt",
        )
    }
    into(layout.buildDirectory.dir("shared-src"))
}
sourceSets.main { kotlin.srcDir(files(layout.buildDirectory.dir("shared-src")).builtBy(syncShared)) }

dependencies {
    implementation("de.femtopedia.dex2jar:dex-translator:2.4.38")
    implementation("de.femtopedia.dex2jar:dex-tools:2.4.38")
    implementation("net.dongliu:apk-parser:2.6.10")
    implementation("com.android.tools.build:apksig:8.7.3")
    implementation("org.ow2.asm:asm:9.9.1")
    implementation("org.ow2.asm:asm-tree:9.9.1")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.squareup.okhttp3:okhttp-brotli:5.3.2")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:5.3.2")
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("org.json:json:20250517")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.mozilla:rhino:1.7.15")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.7.3")
    implementation("io.reactivex:rxjava:1.3.8")
    implementation("com.github.null2264.injekt:injekt-core:4135455a2a")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
}

tasks.test {
    useJUnitPlatform()
    // The survey tests read the APK collection the extension ABI check downloads.
    System.getProperty("apk.only")?.let { systemProperty("apk.only", it) }
    System.getProperty("apk.live")?.let { systemProperty("apk.live", it) }
    testLogging { showStandardStreams = true }
    systemProperty("apk.samples", rootDir.resolve("../.tmp/extension-abi/apk-yuzono").absolutePath)
    maxHeapSize = "1g"
}

tasks.register<JavaExec>("survey") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("app.hibiki.apkhost.SurveyKt")
}

tasks.register<JavaExec>("members") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("app.hibiki.apkhost.MembersKt")
}
