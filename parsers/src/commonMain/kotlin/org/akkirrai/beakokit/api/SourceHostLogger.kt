package org.akkirrai.beakokit.api

/** Runtime-facing logger that does not expose host exceptions or stack traces. */
abstract class SourceHostLogger : SourceHostAccess {
    suspend fun log(level: SourceLogLevel, message: String) {
        require(message.isNotBlank()) { "Source log message must not be blank" }
        require(message.length <= MAX_MESSAGE_LENGTH) {
            "Source log message must be at most $MAX_MESSAGE_LENGTH characters"
        }
        require(SourceHostCapability.LOGGING)
        emit(level, message)
    }

    protected abstract suspend fun emit(level: SourceLogLevel, message: String)

    companion object {
        const val MAX_MESSAGE_LENGTH: Int = 8 * 1024
    }
}
