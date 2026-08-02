package org.akkirrai.beakokit.api

/** Platform boundary for reading the declared runtime module from an installed package. */
fun interface SourcePackageModuleReader {
    fun read(packagePath: String, entrypoint: String): ByteArray

    companion object {
        const val DEFAULT_MAX_MODULE_BYTES: Long = 16L * 1024L * 1024L
    }
}
