package org.akkirrai.beakokit.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Small, dependency-free accessors for reading loosely-typed API responses without a full
 * `@Serializable` model. Every hand-rolled source JSON client (AniLiberty, AnimePahe,
 * KickAssAnime, AnimeGo) had its own private copy of these before they were extracted here --
 * use these instead of redeclaring them in a new source.
 *
 * Every accessor is lenient: a missing or wrong-shaped key returns null/empty rather than
 * throwing, since the upstream API's shape is not a contract Hibiki controls. The one exception
 * is a key whose value is present but is a JsonObject/JsonArray where a primitive was expected
 * (e.g. calling [string] on an object-valued key) -- that still throws, matching how
 * `kotlinx.serialization`'s own `.jsonPrimitive` behaves, since that shape mismatch usually means
 * the source's own field mapping is wrong and should fail loudly rather than silently return null.
 */
fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

fun JsonElement?.asArray(): List<JsonElement> = (this as? JsonArray).orEmpty()

fun JsonObject.obj(key: String): JsonObject? = get(key).asObject()

fun JsonObject.array(key: String): JsonArray? = get(key) as? JsonArray

/** Trimmed, blank-filtered string -- "" and whitespace-only values are treated as absent. */
fun JsonObject.string(key: String): String? = get(key)
    ?.jsonPrimitive
    ?.contentOrNull
    ?.trim()
    ?.takeIf(String::isNotBlank)

/** Trimmed, blank-filtered strings from an array value; missing/wrong-shaped key yields empty. */
fun JsonObject.strings(key: String): List<String> = array(key)
    .orEmpty()
    .mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank) }

fun JsonObject.int(key: String): Int? = get(key)?.jsonPrimitive?.intOrNull

fun JsonObject.double(key: String): Double? = get(key)?.jsonPrimitive?.doubleOrNull

fun JsonObject.bool(key: String): Boolean? = get(key)?.jsonPrimitive?.booleanOrNull
