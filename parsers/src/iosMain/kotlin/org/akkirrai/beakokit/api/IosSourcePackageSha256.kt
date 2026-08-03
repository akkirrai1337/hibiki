package org.akkirrai.beakokit.api

/** Kotlin/Native SHA-256 implementation for verified source-package artifacts. */
object IosSourcePackageSha256 : SourcePackageSha256 {
    override fun digest(bytes: ByteArray): String {
        val paddedLength = ((bytes.size + 9 + 63) / 64) * 64
        val padded = ByteArray(paddedLength)
        bytes.copyInto(padded)
        padded[bytes.size] = 0x80.toByte()
        val bitLength = bytes.size.toLong() * 8
        for (index in 0 until 8) {
            padded[padded.lastIndex - index] = (bitLength ushr (index * 8)).toByte()
        }

        var h0 = 0x6a09e667
        var h1 = 0xbb67ae85.toInt()
        var h2 = 0x3c6ef372
        var h3 = 0xa54ff53a.toInt()
        var h4 = 0x510e527f
        var h5 = 0x9b05688c.toInt()
        var h6 = 0x1f83d9ab
        var h7 = 0x5be0cd19
        val schedule = IntArray(64)

        for (chunkStart in padded.indices step 64) {
            for (index in 0 until 16) {
                val offset = chunkStart + index * 4
                schedule[index] = (padded[offset].toInt() and 0xff shl 24) or
                    (padded[offset + 1].toInt() and 0xff shl 16) or
                    (padded[offset + 2].toInt() and 0xff shl 8) or
                    (padded[offset + 3].toInt() and 0xff)
            }
            for (index in 16 until 64) {
                val value = schedule[index - 15]
                val s0 = value.rotateRight(7) xor value.rotateRight(18) xor (value ushr 3)
                val previous = schedule[index - 2]
                val s1 = previous.rotateRight(17) xor previous.rotateRight(19) xor (previous ushr 10)
                schedule[index] = schedule[index - 16] + s0 + schedule[index - 7] + s1
            }

            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4
            var f = h5
            var g = h6
            var h = h7
            for (index in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val choose = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + choose + ROUND_CONSTANTS[index] + schedule[index]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val majority = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + majority
                h = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }
            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e
            h5 += f
            h6 += g
            h7 += h
        }

        return listOf(h0, h1, h2, h3, h4, h5, h6, h7)
            .joinToString(separator = "") { value -> value.toUInt().toString(16).padStart(8, '0') }
    }

    private fun Int.rotateRight(distance: Int): Int = (this ushr distance) or (this shl (32 - distance))

    private val ROUND_CONSTANTS = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )
}
