package app.hibiki.apkhost

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * Repairs what dex2jar gets wrong in the class files it writes, so the JVM's verifier accepts them
 * (Dalvik's was more forgiving). Each repair is a rename of a type that cannot exist, found by
 * loading every class of the sample extensions (see SamplesTest's verification pass).
 */
object JarFixer {
    /**
     * Types dex2jar invents in stack map frames. `java/util/Object` appears where a local holds
     * several unrelated types and it names their common supertype badly; the verifier would try to
     * load a class that does not exist.
     */
    private val renames = mapOf("java/util/Object" to "java/lang/Object")

    private val remapper = object : Remapper() {
        override fun map(internalName: String): String = renames[internalName] ?: internalName
    }

    /** Rewrites [jar] in place, touching only the classes that mention a bad name. Returns how many it changed. */
    fun fix(jar: File): Int {
        val needles = renames.keys.map { it.toByteArray(Charsets.UTF_8) }
        val temporary = File(jar.parentFile, jar.name + ".fixed")
        var changed = 0
        JarFile(jar).use { source ->
            JarOutputStream(temporary.outputStream().buffered()).use { out ->
                for (entry in source.entries()) {
                    val bytes = source.getInputStream(entry).use { it.readBytes() }
                    val fixed = if (entry.name.endsWith(".class") && needles.any { contains(bytes, it) }) {
                        changed++
                        rename(bytes)
                    } else bytes
                    out.putNextEntry(JarEntry(entry.name))
                    out.write(fixed)
                    out.closeEntry()
                }
            }
        }
        if (changed == 0) {
            temporary.delete()
            return 0
        }
        check(jar.delete() && temporary.renameTo(jar)) { "Could not replace ${jar.name} with its repaired copy" }
        return changed
    }

    private fun rename(bytes: ByteArray): ByteArray {
        val reader = ClassReader(bytes)
        // Frames are copied as they are (flags 0): only the names inside them change.
        val writer = ClassWriter(0)
        reader.accept(ClassRemapper(writer, remapper), 0)
        return writer.toByteArray()
    }

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }
}
