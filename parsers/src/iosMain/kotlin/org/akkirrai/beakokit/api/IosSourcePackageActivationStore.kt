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
    private val packagePathValidator: (String) -> String = { it },
) : SourcePackageActivationStore {
    init {
        require(maxStateBytes > 0) { "Maximum activation state size must be positive" }
    }

    override fun load(sourceId: SourceId): SourcePackageActivationState {
        val stored = defaults.objectForKey(key(sourceId)) ?: return SourcePackageActivationState()
        val raw = stored as? String ?: throw SourcePackageStateException(
            "Source package activation state is corrupted: expected a string value",
        )
        return try {
            if (raw.encodeToByteArray().size.toLong() > maxStateBytes) {
                throw SourcePackageStateException(
                    "Source package activation state exceeds $maxStateBytes bytes: ${sourceId.value}",
                )
            }
            checkedState(sourceId, json.decodeFromString(raw))
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
        checkedState(sourceId, state)
        val raw = json.encodeToString(state)
        require(raw.encodeToByteArray().size.toLong() <= maxStateBytes) {
            "Source package activation state exceeds $maxStateBytes bytes: ${sourceId.value}"
        }
        defaults.setObject(raw, forKey = key(sourceId))
    }

    /** Returns only validated package paths; corrupt records are left untouched for diagnostics. */
    fun activePackagePaths(): Set<String> = defaults.dictionaryRepresentation()
        .entries
        .asSequence()
        .mapNotNull { (key, raw) ->
            val name = key as? String ?: return@mapNotNull null
            if (!name.startsWith(KEY_PREFIX)) return@mapNotNull null
            val sourceId = runCatching { SourceId(name.removePrefix(KEY_PREFIX)) }.getOrNull()
                ?: return@mapNotNull null
            runCatching { checkedState(sourceId, json.decodeFromString<SourcePackageActivationState>(raw as? String ?: return@mapNotNull null)) }
                .getOrNull()
        }
        .flatMap { state -> sequenceOf(state.active?.packagePath, state.previous?.packagePath) }
        .filterNotNull()
        .toSet()

    private fun key(sourceId: SourceId): String = KEY_PREFIX + sourceId.value

    private fun checkedState(sourceId: SourceId, state: SourcePackageActivationState): SourcePackageActivationState {
        if (state.active?.sourceId != null && state.active.sourceId != sourceId) {
            throw SourcePackageStateException(
                "Active package source ID does not match activation store: ${state.active.sourceId}",
            )
        }
        if (state.previous?.sourceId != null && state.previous.sourceId != sourceId) {
            throw SourcePackageStateException(
                "Previous package source ID does not match activation store: ${state.previous.sourceId}",
            )
        }
        state.active?.let { packagePathValidator(it.packagePath) }
        state.previous?.let { packagePathValidator(it.packagePath) }
        return state
    }

    private companion object {
        const val KEY_PREFIX = "beakokit.source_package."
        const val DEFAULT_MAX_STATE_BYTES: Long = 2L * 1024L * 1024L
    }
}
