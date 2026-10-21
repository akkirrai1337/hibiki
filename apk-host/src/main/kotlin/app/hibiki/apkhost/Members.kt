package app.hibiki.apkhost

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.jar.JarFile

/** Lists every member of Android classes the converted jars use: owner.name desc and how many jars use it. */
fun main(args: Array<String>) {
    val seen = sortedMapOf<String, MutableSet<String>>()
    fun android(o: String) = o.startsWith("android/") || o.startsWith("androidx/") || o.startsWith("dalvik/")
    for (jar in File(args[0]).listFiles { f -> f.extension == "jar" }!!) {
        JarFile(jar).use { jf ->
            for (e in jf.entries()) {
                if (!e.name.endsWith(".class")) continue
                ClassReader(jf.getInputStream(e).readBytes()).accept(object : ClassVisitor(Opcodes.ASM9) {
                    override fun visit(v: Int, a: Int, n: String, s: String?, sup: String?, ifs: Array<out String>?) {
                        if (sup != null && android(sup)) seen.getOrPut("EXTENDS $sup") { sortedSetOf() }.add(jar.name)
                        ifs?.filter(::android)?.forEach { seen.getOrPut("IMPLEMENTS $it") { sortedSetOf() }.add(jar.name) }
                    }
                    override fun visitMethod(a: Int, n: String, d: String, s: String?, x: Array<out String>?) = object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(op: Int, o: String, name: String, desc: String, itf: Boolean) {
                            if (android(o)) seen.getOrPut("M $o.$name$desc") { sortedSetOf() }.add(jar.name)
                        }
                        override fun visitFieldInsn(op: Int, o: String, name: String, desc: String) {
                            if (android(o)) seen.getOrPut("F $o.$name:$desc") { sortedSetOf() }.add(jar.name)
                        }
                    }
                }, 0)
            }
        }
    }
    File(args[1]).printWriter().use { w -> seen.forEach { (k, v) -> w.println("${v.size}\t$k") } }
    println("${seen.size} members")
}
