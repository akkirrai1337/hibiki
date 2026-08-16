package org.akkirrai.beakokit.api

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** JSON boundary for repository indexes downloaded by the host. */
object SourceRepositoryIndexCodec {
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    fun decode(value: String): SourceRepositoryIndex = try {
        json.decodeFromString<SourceRepositoryIndex>(value)
    } catch (error: SerializationException) {
        throw SourceRepositoryIndexException(
            listOf("Repository index JSON is invalid: ${error.message ?: "unknown error"}"),
        )
    } catch (error: IllegalArgumentException) {
        throw SourceRepositoryIndexException(
            listOf("Repository index contains invalid values: ${error.message ?: "unknown error"}"),
        )
    }

    fun decodeAndValidate(
        value: String,
        clientVersion: Int,
        supportedSourceApiVersion: Int = SourceApi.VERSION,
        supportedHostApiVersion: Int = SourceHostApi.VERSION,
    ): SourceRepositoryIndex = decode(value).also {
        it.requireValid(clientVersion, supportedSourceApiVersion, supportedHostApiVersion)
    }

    fun encode(index: SourceRepositoryIndex): String = json.encodeToString(index)
}
