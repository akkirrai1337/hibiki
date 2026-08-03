package org.akkirrai.beakokit.api

/** Host-owned persistent key/value storage scoped to one source package. */
abstract class SourceHostStorage(
    private val maxStorageBytes: Long = DEFAULT_MAX_STORAGE_BYTES,
    private val maxEntryCount: Int = DEFAULT_MAX_ENTRY_COUNT,
) : SourceHostAccess, ExternalSourceHostStorageAccess {
    init {
        require(maxStorageBytes > 0) { "Maximum source storage size must be positive" }
        require(maxEntryCount > 0) { "Maximum source storage entry count must be positive" }
    }

    override suspend fun read(key: String): String? {
        SourceHostStorage.requireKey(key)
        require(SourceHostCapability.STORAGE)
        return readValue(key)
    }

    override suspend fun write(key: String, value: String) {
        SourceHostStorage.requireKey(key)
        SourceHostStorage.requireValue(value)
        require(SourceHostCapability.STORAGE)
        val previous = readValue(key)
        if (previous == null) {
            require(storedEntryCount() < maxEntryCount) { "Source storage entry limit exceeded" }
        }
        val previousSizeBytes = previous?.encodeToByteArray()?.size?.toLong() ?: 0L
        val retainedSizeBytes = storedSizeBytes() - previousSizeBytes
        require(retainedSizeBytes >= 0 && retainedSizeBytes <= maxStorageBytes) {
            "Source storage quota exceeded"
        }
        val newValueSizeBytes = value.encodeToByteArray().size.toLong()
        require(newValueSizeBytes <= maxStorageBytes - retainedSizeBytes) {
            "Source storage quota exceeded"
        }
        writeValue(key, value)
    }

    override suspend fun remove(key: String) {
        SourceHostStorage.requireKey(key)
        require(SourceHostCapability.STORAGE)
        removeValue(key)
    }

    protected abstract suspend fun readValue(key: String): String?

    protected abstract suspend fun writeValue(key: String, value: String)

    protected abstract suspend fun removeValue(key: String)

    protected abstract suspend fun storedSizeBytes(): Long

    protected abstract suspend fun storedEntryCount(): Int

    companion object {
        const val MAX_KEY_LENGTH: Int = 128
        const val MAX_VALUE_LENGTH: Int = 64 * 1024
        const val DEFAULT_MAX_STORAGE_BYTES: Long = 1024L * 1024L
        const val DEFAULT_MAX_ENTRY_COUNT: Int = 256

        fun requireKey(key: String) {
            require(key.isNotBlank() && key.length <= MAX_KEY_LENGTH) {
                "Source storage key must be non-blank and at most $MAX_KEY_LENGTH characters"
            }
        }

        fun requireValue(value: String) {
            require(value.length <= MAX_VALUE_LENGTH) {
                "Source storage value must be at most $MAX_VALUE_LENGTH characters"
            }
        }
    }
}
