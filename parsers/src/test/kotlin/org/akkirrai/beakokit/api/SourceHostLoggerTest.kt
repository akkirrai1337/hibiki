package org.akkirrai.beakokit.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceHostLoggerTest {
    @Test
    fun `logger requires declared capability`() = runBlocking {
        val logger = FakeLogger(SourceHostRequirements())

        assertFailsWith<SourceHostCapabilityException> {
            logger.log(SourceLogLevel.WARNING, "warning")
        }
    }

    @Test
    fun `logger forwards level and message without throwable`() = runBlocking {
        val logger = FakeLogger(SourceHostRequirements(setOf(SourceHostCapability.LOGGING)))

        logger.log(SourceLogLevel.ERROR, "source failed")

        assertEquals(SourceLogLevel.ERROR, logger.level)
        assertEquals("source failed", logger.message)
    }

    @Test
    fun `logger rejects blank and oversized messages`() = runBlocking {
        val logger = FakeLogger(SourceHostRequirements(setOf(SourceHostCapability.LOGGING)))

        assertFailsWith<IllegalArgumentException> { logger.log(SourceLogLevel.DEBUG, "") }
        assertFailsWith<IllegalArgumentException> {
            logger.log(SourceLogLevel.DEBUG, "x".repeat(SourceHostLogger.MAX_MESSAGE_LENGTH + 1))
        }
        assertFailsWith<IllegalArgumentException> {
            logger.log(SourceLogLevel.DEBUG, "warning\nforged-entry")
        }
    }

    private class FakeLogger(
        override val requirements: SourceHostRequirements,
    ) : SourceHostLogger() {
        var level: SourceLogLevel? = null
        var message: String? = null

        protected override suspend fun emit(level: SourceLogLevel, message: String) {
            this.level = level
            this.message = message
        }
    }
}
