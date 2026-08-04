package org.akkirrai.beakokit.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceHostStorageTest {
    @Test
    fun `storage requires declared capability`() = runBlocking {
        val storage = FakeStorage(SourceHostRequirements())

        assertFailsWith<SourceHostCapabilityException> {
            storage.read("cookie")
        }
    }

    @Test
    fun `storage is source scoped and supports lifecycle`() = runBlocking {
        val storage = FakeStorage(SourceHostRequirements(setOf(SourceHostCapability.STORAGE)))

        storage.write("token", "value")
        assertEquals("value", storage.read("token"))
        storage.remove("token")
        assertEquals(null, storage.read("token"))
    }

    @Test
    fun `storage limits keys and values`() = runBlocking {
        val storage = FakeStorage(SourceHostRequirements(setOf(SourceHostCapability.STORAGE)))

        assertFailsWith<IllegalArgumentException> { storage.read("") }
        assertFailsWith<IllegalArgumentException> { storage.read("bad\nkey") }
        assertFailsWith<IllegalArgumentException> {
            storage.write("x".repeat(SourceHostStorage.MAX_KEY_LENGTH + 1), "value")
        }
        assertFailsWith<IllegalArgumentException> {
            storage.write("large", "x".repeat(SourceHostStorage.MAX_VALUE_LENGTH + 1))
        }
        storage.seed("oversized", "x".repeat(SourceHostStorage.MAX_VALUE_LENGTH + 1))
        assertFailsWith<IllegalArgumentException> { storage.read("oversized") }
    }

    @Test
    fun `storage enforces total quota and entry count`() = runBlocking {
        val requirements = SourceHostRequirements(setOf(SourceHostCapability.STORAGE))
        val storage = FakeStorage(requirements, maxStorageBytes = 4, maxEntryCount = 1)

        storage.write("one", "1234")
        assertFailsWith<IllegalArgumentException> { storage.write("two", "x") }
        assertFailsWith<IllegalArgumentException> { storage.write("one", "12345") }
    }

    private class FakeStorage(
        override val requirements: SourceHostRequirements,
        maxStorageBytes: Long = SourceHostStorage.DEFAULT_MAX_STORAGE_BYTES,
        maxEntryCount: Int = SourceHostStorage.DEFAULT_MAX_ENTRY_COUNT,
    ) : SourceHostStorage(maxStorageBytes, maxEntryCount) {
        private val values = mutableMapOf<String, String>()

        protected override suspend fun readValue(key: String): String? = values[key]

        protected override suspend fun writeValue(key: String, value: String) {
            values[key] = value
        }

        protected override suspend fun removeValue(key: String) {
            values.remove(key)
        }

        protected override suspend fun storedSizeBytes(): Long = values.values.sumOf { it.encodeToByteArray().size.toLong() }

        protected override suspend fun storedEntryCount(): Int = values.size

        fun seed(key: String, value: String) {
            values[key] = value
        }
    }
}
