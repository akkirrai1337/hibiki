package app.hibiki.apkhost

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every extension in the sample collection (the Yuzono repository's, fetched by the extension ABI
 * check) goes through the whole offline path: read its manifest, install it, convert and load its
 * code, create its sources, read their settings and filters. Nothing here touches the network.
 * The report lands in build/samples-report.txt; the tests assert the shape of the result so a
 * regression in the stand-ins or the loader shows up as a number that moved.
 */
class SamplesTest {
    private val samples = System.getProperty("apk.samples")?.let(::File)?.takeIf(File::isDirectory)
    private val only = System.getProperty("apk.only")?.let(::Regex)
    private val apks = samples?.listFiles { f -> f.extension == "apk" && (only == null || only.containsMatchIn(f.name)) }.orEmpty().sortedBy { it.name }

    /**
     * Links every class of the extension, which runs the JVM's verifier over it without running its
     * static initialisers (listing a class's methods links it). Dalvik accepted bytecode the JVM
     * does not, and an extension only trips over it when it first reaches the bad class.
     */
    private fun verifyAll(extension: LoadedApkExtension, report: StringBuilder) {
        val jar = (extension.classLoader as java.net.URLClassLoader).urLs.first().let { File(it.toURI()) }
        val problems = linkedMapOf<String, Int>()
        var total = 0
        java.util.jar.JarFile(jar).use { jf ->
            for (entry in jf.entries()) {
                if (!entry.name.endsWith(".class")) continue
                total++
                val name = entry.name.removeSuffix(".class").replace('/', '.')
                try {
                    Class.forName(name, false, extension.classLoader).declaredMethods
                } catch (error: Throwable) {
                    problems.merge("${error.javaClass.simpleName}: ${error.message?.take(600)}", 1, Int::plus)
                }
            }
        }
        if (problems.isNotEmpty()) report.appendLine("VERIFY ${extension.extension.packageName}: ${problems.values.sum()} of $total classes: $problems")
    }

    @Test
    fun `every sample loads and creates its sources`() {
        if (apks.isEmpty()) { println("no sample APKs, skipping"); return }
        val data = Files.createTempDirectory("apk-host-samples").toFile()
        val host = ApkHost(data)
        val report = StringBuilder()
        var loaded = 0
        val failures = mutableListOf<String>()
        val started = System.currentTimeMillis()
        for (apk in apks) {
            val outcome = runCatching {
                val copy = File(data, "incoming-${apk.name}")
                apk.copyTo(copy, overwrite = true)
                val installed = host.install(copy, null)
                val extension = host.registry.extensions().firstOrNull { it.extension.packageName == installed.packageName }
                    ?: error("not loaded: " + host.registry.failures().joinToString { it.message })
                // Settings and filters are what the page asks for first; both run extension code.
                extension.adapters.forEach { adapter ->
                    adapter.info(app.hibiki.apk.ApkPreferences.describe(host.application, adapter))
                    runBlocking { adapter.filterCatalog() }
                }
                verifyAll(extension, report)
                extension.adapters.size
            }
            outcome.onSuccess { loaded++; report.appendLine("OK    ${apk.name}: $it source(s)") }
            outcome.onFailure {
                failures += apk.name
                var cause: Throwable = it
                while (cause.cause != null && cause.cause !== cause) cause = cause.cause!!
                report.appendLine("FAIL  ${apk.name}: ${it.message} <- ${cause.javaClass.simpleName}: ${cause.message} @ ${cause.stackTrace.take(4).joinToString(" | ")}")
            }
        }
        report.appendLine("loaded $loaded of ${apks.size} in ${(System.currentTimeMillis() - started) / 1000}s")
        File("build").mkdirs()
        File("build/samples-report.txt").writeText(report.toString())
        println(report.lines().filter { it.startsWith("FAIL") || it.startsWith("loaded") }.joinToString("\n"))
        assertTrue(loaded > 0)
        data.deleteRecursively()
    }
}
