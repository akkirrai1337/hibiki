package org.akkirrai.beakokit.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** A source-qualified anime identifier. The native part is opaque outside its source. */
@Serializable(with = AnimeKeySerializer::class)
data class AnimeKey(
    val sourceId: SourceId,
    val nativeId: String,
) {
    init {
        require(nativeId.isNotBlank()) { "Native anime ID must not be blank" }
    }

    val value: String
        get() = "$PREFIX${sourceId.value}:$nativeId"

    override fun toString(): String = value

    companion object {
        private const val PREFIX = "source:"

        /** Accepts canonical IDs and legacy IDs such as `source:ANI_LIBERTY:42`. */
        fun parse(value: String): AnimeKey? {
            if (!value.startsWith(PREFIX)) return null
            val separator = value.indexOf(':', startIndex = PREFIX.length)
            if (separator < 0 || separator == value.lastIndex) return null
            val sourceId = SourceId.parseStored(value.substring(PREFIX.length, separator)) ?: return null
            val nativeId = value.substring(separator + 1)
            if (nativeId.isBlank()) return null
            return AnimeKey(sourceId, nativeId)
        }
    }
}

object AnimeKeySerializer : KSerializer<AnimeKey> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        serialName = "org.akkirrai.beakokit.api.AnimeKey",
        kind = PrimitiveKind.STRING,
    )

    override fun serialize(encoder: Encoder, value: AnimeKey) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): AnimeKey {
        val value = decoder.decodeString()
        return AnimeKey.parse(value)
            ?: throw SerializationException("Invalid anime key: $value")
    }
}
