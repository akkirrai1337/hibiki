package app.hibiki.apkhost

import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.MultiDexFileReader
import com.googlecode.dex2jar.tools.BaksmaliBaseDexExceptionHandler
import net.dongliu.apk.parser.ApkFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.jar.JarFile
import java.util.zip.ZipFile

/** Converts every APK in a folder with dex2jar and lists the Android classes the jars reference. */
fun main(args: Array<String>) {
    val dir = File(args[0])
    val out = File(args[1]).also { it.mkdirs() }
    val missing = sortedMapOf<String, MutableSet<String>>()
    var ok = 0
    val failed = mutableListOf<String>()
    val started = System.currentTimeMillis()
    for (apk in dir.listFiles { f -> f.extension == "apk" }!!.sortedBy { it.name }) {
        val jar = File(out, apk.nameWithoutExtension + ".jar")
        try {
            if (!jar.exists()) {
                val bytes = ZipFile(apk).use { z -> z.getInputStream(z.getEntry("classes.dex")).readBytes() }
                val handler = BaksmaliBaseDexExceptionHandler()
                Dex2jar.from(MultiDexFileReader.open(bytes)).withExceptionHandler(handler)
                    .reUseReg(false).topoLogicalSort().skipDebug(true).optimizeSynchronized(false)
                    .printIR(false).noCode(false).skipExceptions(false).dontSanitizeNames(true)
                    .computeFrames(true).to(jar.toPath())
                if (handler.hasException()) { failed += "${apk.name}: dex2jar reported errors"; continue }
            }
            JarFile(jar).use { jf ->
                for (e in jf.entries()) {
                    if (!e.name.endsWith(".class")) continue
                    val cn = ClassNode()
                    ClassReader(jf.getInputStream(e).readBytes()).accept(ClassRemapper(cn, object : Remapper() {
                        override fun map(internalName: String): String {
                            if (internalName.startsWith("android/") || internalName.startsWith("androidx/") ||
                                internalName.startsWith("dalvik/") || internalName.startsWith("com/google/android/"))
                                missing.getOrPut(internalName) { sortedSetOf() }.add(apk.nameWithoutExtension)
                            return internalName
                        }
                    }), 0)
                }
            }
            ok++
        } catch (t: Throwable) {
            failed += "${apk.name}: $t"
        }
    }
    println("converted $ok, failed ${failed.size}, ${(System.currentTimeMillis() - started) / 1000}s")
    failed.forEach { println("FAIL $it") }
    println("--- referenced Android classes (class: number of APKs)")
    missing.entries.sortedByDescending { it.value.size }.forEach { println("${it.value.size}\t${it.key}") }
}
