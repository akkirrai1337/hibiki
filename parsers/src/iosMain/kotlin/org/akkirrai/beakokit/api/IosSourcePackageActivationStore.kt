package org.akkirrai.beakokit.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

/** iOS activation state store backed by one replaceable JSON value per source. */
class IosSourcePackageActivationStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SourcePackageActivationStore {
    override fun load(sourceId: SourceId): SourcePackageActivationState {
        val raw = defaults.stringForKey(key(sourceId)) ?: return SourcePackageActivationState()
        return try {
            json.decodeFromString(raw)
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
}
