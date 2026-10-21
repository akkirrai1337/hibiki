package app.hibiki.apkhost

import eu.kanade.tachiyomi.network.JavaScriptEngine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The host's own parts: the JavaScript engine, the jar repair, the class loader, the extension store. */
class HostPartsTest {
    private val dir = Files.createTempDirectory("apk-host-parts").toFile()
    private val context get() = HostSetup.initialize(dir.resolve("setup")).also { }

    // --- JavaScript -----------------------------------------------------------------------------------

    private fun <T> js(script: String, timeoutMs: Long = 5_000): T = runBlocking {
        JavaScriptEngine(context, timeoutMs).evaluate<T>(script)
    }

    @Test
    fun `scripts evaluate to the types QuickJS returned`() {
        assertEquals(3, js<Any?>("1 + 2"))
        assertEquals(2.5, js<Any?>("5 / 2"))
        assertEquals("ab", js<Any?>("'a' + 'b'"))
        assertEquals(true, js<Any?>("1 < 2"))
        assertNull(js<Any?>("undefined"))
        assertNull(js<Any?>("null"))
        assertEquals("x=1", js<Any?>("(function () { var o = { x: 1 }; return 'x=' + o.x; })()"))
    }

    @Test
    fun `a packed script unpacks like the sources expect`() {
        // The shape of a p,a,c,k,e,d script: eval of a function applied to a dictionary.
        val packed = "(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\\\b'+c.toString(a)+'\\\\b','g'),k[c]);return p})" +
            "('0 1=\"2\"',3,3,'var|file|https://x.test/v.m3u8'.split('|'),0,{})"
        assertEquals("var file=\"https://x.test/v.m3u8\"", js<Any?>(packed))
    }

    @Test
    fun `a script that never ends is stopped`() {
        val started = System.currentTimeMillis()
        assertFailsWith<Throwable> { js<Any?>("while (true) {}", timeoutMs = 400) }
        assertTrue(System.currentTimeMillis() - started < 5_000, "the loop was not stopped in time")
    }

    @Test
    fun `a script error reaches the caller`() {
        assertFailsWith<Throwable> { js<Any?>("nope.nothing()") }
    }

    // --- the jar repair ---------------------------------------------------------------------------------

    private fun jarWith(vararg classes: Pair<String, ByteArray>, resource: Pair<String, String>? = null): File {
        val file = File.createTempFile("fixer", ".jar", dir)
        JarOutputStream(file.outputStream()).use { out ->
            classes.forEach { (name, bytes) -> out.putNextEntry(JarEntry("$name.class")); out.write(bytes); out.closeEntry() }
            resource?.let { (name, text) -> out.putNextEntry(JarEntry(name)); out.write(text.toByteArray()); out.closeEntry() }
        }
        return file
    }

    private fun classMentioning(name: String, typeName: String): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        writer.visitField(Opcodes.ACC_PUBLIC, "value", "L$typeName;", null, null).visitEnd()
        writer.visitEnd()
        return writer.toByteArray()
    }

    @Test
    fun `the jar repair renames the invented type and leaves the rest alone`() {
        val jar = jarWith(
            "Bad" to classMentioning("Bad", "java/util/Object"),
            "Good" to classMentioning("Good", "java/lang/String"),
            resource = "assets/note.txt" to "java/util/Object is only text here",
        )
        assertEquals(1, JarFixer.fix(jar))
        JarFile(jar).use { jf ->
            val bad = jf.getInputStream(jf.getJarEntry("Bad.class")).readBytes()
            assertTrue(String(bad, Charsets.ISO_8859_1).contains("java/lang/Object"))
            assertFalse(String(bad, Charsets.ISO_8859_1).contains("java/util/Object"))
            assertTrue(String(jf.getInputStream(jf.getJarEntry("Good.class")).readBytes(), Charsets.ISO_8859_1).contains("java/lang/String"))
            // Resources are copied untouched, even when they happen to contain the text.
            assertEquals("java/util/Object is only text here", jf.getInputStream(jf.getJarEntry("assets/note.txt")).readBytes().decodeToString())
        }
        // A second run has nothing left to do.
        assertEquals(0, JarFixer.fix(jar))
    }

    // --- the class loader ---------------------------------------------------------------------------------

    @Test
    fun `the extension class loader shares the platform and prefers its own classes`() {
        val jar = jarWith("com/example/Local" to classMentioning("com/example/Local", "java/lang/String"))
        val loader = ExtensionClassLoader(arrayOf(jar.toURI().toURL()), javaClass.classLoader)
        val local = loader.loadClass("com.example.Local")
        assertTrue(local.classLoader === loader)
        // Host classes come from the host even if the jar carried a copy of them.
        assertTrue(loader.loadClass("android.net.Uri").classLoader === javaClass.classLoader)
        assertTrue(loader.loadClass("java.lang.String").classLoader == null)
        assertFailsWith<ClassNotFoundException> { loader.loadClass("com.example.Missing") }
    }

    // --- the store ----------------------------------------------------------------------------------------

    private val sample: File? = System.getProperty("apk.samples")?.let(::File)
        ?.listFiles { f -> f.name.startsWith("aniyomi-ru.animevost-") }?.firstOrNull()

    @Test
    fun `the store reads, installs, trusts and removes an extension`() {
        val apk = sample ?: return
        val store = ExtensionStore(dir.resolve("store"))
        val inspected = assertNotNull(store.inspect(apk))
        assertEquals("eu.kanade.tachiyomi.animeextension.ru.animevost", inspected.packageName)
        assertEquals(14, inspected.apiVersion)
        assertNotNull(inspected.signingFingerprint)
        assertFalse(inspected.isTrusted)
        assertTrue(store.list().isEmpty())

        val copy = dir.resolve("copy.apk").also { apk.copyTo(it, overwrite = true) }
        val installed = store.install(copy, inspected.packageName)
        assertFalse(copy.exists(), "the downloaded file is moved in")
        assertTrue(installed.file.isFile)
        assertEquals(listOf(inspected.packageName), store.list().map { it.packageName })
        assertFalse(store.list().single().isTrusted)

        store.trust(inspected.packageName, installed.signingFingerprint!!)
        assertTrue(store.list().single().isTrusted)
        // A different fingerprint means the signer changed: no longer trusted.
        store.trust(inspected.packageName, "00")
        assertFalse(store.list().single().isTrusted)

        store.uninstall(inspected.packageName)
        store.forget(inspected.packageName)
        assertTrue(store.list().isEmpty())
        assertNull(store.trustedFingerprint(inspected.packageName))
    }

    @Test
    fun `the store refuses what is not an extension of a supported API`() {
        val store = ExtensionStore(dir.resolve("store2"))
        val notAnApk = dir.resolve("junk.apk").also { it.writeText("not an apk") }
        assertNull(store.inspect(notAnApk))
        assertFailsWith<IllegalStateException> { store.install(notAnApk) }
        val apk = sample ?: return
        val copy = dir.resolve("copy2.apk").also { apk.copyTo(it, overwrite = true) }
        assertFailsWith<IllegalArgumentException> { store.install(copy, "some.other.package") }
        assertTrue(copy.exists(), "a refused file is left where it was")
    }

    @Test
    fun `an update signed by someone else is refused and the installed one stays`() {
        val apk = sample ?: return
        val host = ApkHost(dir.resolve("host"))
        val first = dir.resolve("first.apk").also { apk.copyTo(it, overwrite = true) }
        val installed = host.install(first, null)
        // The person trusted a different signer earlier; this file is signed by another one.
        host.store.trust(installed.packageName, "ffff")
        val update = dir.resolve("update.apk").also { apk.copyTo(it, overwrite = true) }
        val error = assertFailsWith<IllegalStateException> { host.install(update, null) }
        assertTrue(error.message!!.contains("signed by someone else"))
        assertTrue(host.store.find(installed.packageName)!!.file.isFile)
        host.uninstall(installed.packageName)
        assertTrue(host.registry.extensions().isEmpty())
    }

    @Test
    fun `installing over a loaded extension replaces it while it is open`() {
        val apk = sample ?: return
        val host = ApkHost(dir.resolve("host3"))
        host.install(dir.resolve("one.apk").also { apk.copyTo(it, overwrite = true) }, null)
        val before = host.registry.extensions().single()
        // The first copy is loaded and holds its file open; Windows refuses to overwrite such a file.
        host.install(dir.resolve("two.apk").also { apk.copyTo(it, overwrite = true) }, null)
        val after = host.registry.extensions().single()
        assertEquals(before.extension.packageName, after.extension.packageName)
        assertTrue(before !== after, "the extension was loaded again")
        assertTrue(after.extension.isTrusted)
    }

    @Test
    fun `an installed trusted extension loads, and removing it unloads it`() {
        val apk = sample ?: return
        val host = ApkHost(dir.resolve("host2"))
        val incoming = dir.resolve("incoming.apk").also { apk.copyTo(it, overwrite = true) }
        val installed = host.install(incoming, null)
        val loaded = host.registry.extensions().single()
        assertEquals(installed.packageName, loaded.extension.packageName)
        // An APK can hold several sources (this one has two), each addressed by its own id.
        assertTrue(loaded.adapters.isNotEmpty())
        val source = loaded.adapters.first()
        assertTrue(source.id.startsWith("apk:"))
        val description = host.describe()
        assertEquals(loaded.adapters.size, description.getJSONArray("sources").length())
        assertTrue(description.getJSONArray("extensions").getJSONObject(0).getBoolean("trusted"))
        assertNotNull(host.registry.adapter(source.id))
        host.uninstall(installed.packageName)
        assertNull(host.registry.adapter(source.id))
        assertEquals(0, host.describe().getJSONArray("extensions").length())
    }
}
