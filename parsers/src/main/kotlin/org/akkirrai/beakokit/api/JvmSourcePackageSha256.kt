package org.akkirrai.beakokit.api

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

/** JVM/Android SHA-256 implementation shared by downloaded bytes and archive files. */
object JvmSourcePackageSha256 : SourcePackageSha256 {
    override fun digest(bytes: ByteArray): String =
        ByteArrayInputStream(bytes).use(::digest)

    fun digest(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String = buildString(size * 2) {
        this@toHexString.forEach { byte ->
            append((byte.toInt() and 0xff).toString(16).padStart(2, '0'))
        }
    }
}
