package org.akkirrai.beakokit.extension

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.model.AnimeSearchFilter
import org.akkirrai.beakokit.model.AnimeSearchSort

/**
 * Enum fields of a manifest, decoded so that a value this build has never heard of is skipped
 * rather than fatal.
 *
 * A manifest is published by the source repository, which moves on its own schedule: a capability
 * added for one app reaches every installed copy of the other the moment it is published. Decoding
 * an unknown constant throws, and the whole manifest goes with it - which is how ACTIVITY_SYNC,
 * declared for the desktop, made yummy-anime refuse to install here with nothing but "manifest is
 * invalid" to show for it. Unknown keys were already ignored for exactly this reason; unknown
 * values in those keys are the same problem wearing a different hat.
 *
 * Skipping is safe because each of these gates something: a capability the app cannot name gates a
 * screen it does not have, and a sort or filter it cannot name is one it could not offer anyway.
 * The capabilities a source genuinely cannot work without are checked separately, by
 * [ScriptExtensionManifest.violations].
 */
internal abstract class LenientEnumSetSerializer<T : Enum<T>>(
    private val known: Array<T>,
) : KSerializer<Set<T>> {
    private val delegate = SetSerializer(String.serializer())
    override val descriptor: SerialDescriptor get() = delegate.descriptor

    override fun deserialize(decoder: Decoder): Set<T> = delegate.deserialize(decoder)
        .mapNotNullTo(mutableSetOf()) { name -> known.firstOrNull { it.name == name } }

    override fun serialize(encoder: Encoder, value: Set<T>) =
        delegate.serialize(encoder, value.mapTo(mutableSetOf(), Enum<T>::name))
}

internal object LenientCapabilitySetSerializer :
    LenientEnumSetSerializer<SourceCapability>(SourceCapability.entries.toTypedArray())

internal object LenientSortSetSerializer :
    LenientEnumSetSerializer<AnimeSearchSort>(AnimeSearchSort.entries.toTypedArray())

internal object LenientFilterSetSerializer :
    LenientEnumSetSerializer<AnimeSearchFilter>(AnimeSearchFilter.entries.toTypedArray())

/** A single sort, falling back to the one every source supports when the name is unknown. */
internal object LenientSortSerializer : KSerializer<AnimeSearchSort> {
    override val descriptor: SerialDescriptor get() = String.serializer().descriptor

    override fun deserialize(decoder: Decoder): AnimeSearchSort {
        val name = decoder.decodeString()
        return AnimeSearchSort.entries.firstOrNull { it.name == name } ?: AnimeSearchSort.RELEVANCE
    }

    override fun serialize(encoder: Encoder, value: AnimeSearchSort) = encoder.encodeString(value.name)
}
