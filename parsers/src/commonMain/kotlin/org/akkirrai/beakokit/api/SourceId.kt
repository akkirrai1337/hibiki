package org.akkirrai.beakokit.api

import kotlinx.serialization.Serializable

/** Stable, storage-safe identifier of a BeakoKit source. */
@JvmInline
@Serializable
value class SourceId(val value: String) {
    init {
        require(PATTERN.matches(value)) {
            "Source ID must be a lowercase slug: $value"
        }
    }

    override fun toString(): String = value

    companion object {
        private val PATTERN = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        private val LEGACY_ENUM_PATTERN = Regex("[A-Z0-9]+(?:_[A-Z0-9]+)*")

        /**
         * Reads both the canonical slug and the enum names persisted by pre-BeakoKit Hibiki builds.
         * New values are always written as lowercase slugs.
         */
        fun parseStored(value: String?): SourceId? {
            val candidate = value?.trim().orEmpty()
            return when {
                PATTERN.matches(candidate) -> SourceId(candidate)
                LEGACY_ENUM_PATTERN.matches(candidate) -> SourceId(
                    candidate.lowercase().replace('_', '-'),
                )
                else -> null
            }
        }
    }
}
