package org.akkirrai.beakokit.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

/** iOS activation state store backed by one replaceable JSON value per source. */
class IosSourcePackageActivationStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val maxStateBytes: Long = DEFAULT_MAX_STATE_BYTES,
) : SourcePackageActivationStore {
    init {
        require(maxStateBytes > 0) { "Maximum activation state size must be positive" }
    }

    override fun load(sourceId: SourceId): SourcePackageActivationState {
        val raw = defaults.stringForKey(key(sourceId)) ?: return SourcePackageActivationState()
        return try {
            if (raw.encodeToByteArray().size.toLong() > maxStateBytes) {
                throw SourcePackageStateException(
                    "Source package activation state exceeds $maxStateBytes bytes: ${sourceId.value}",
                )
            }
            json.decodeFromString(raw)
        } catch (error: SourcePackageStateException) {
            throw error
        } catch (error: Exception) {
            throw SourcePackageStateException(
                message = "Source package activation state is corrupted: ${sourceId.value}",
                cause = error,
            )
        }
    }

    override fun persistAtomically(
        sourceId: SourceId,
        state: SourcePackageActivationState,
    ) {
        defaults.setObject(json.encodeToString(state), forKey = key(sourceId))
    }

    private fun key(sourceId: SourceId): String = "beakokit.source_package.${sourceId.value}"

    private companion object {
        const val DEFAULT_MAX_STATE_BYTES: Long = 2L * 1024L * 1024L
    }
}
