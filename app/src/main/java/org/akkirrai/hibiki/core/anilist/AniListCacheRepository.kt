package org.akkirrai.hibiki.core.anilist

import android.content.Context
import org.akkirrai.hibiki.catalog.model.AniListCharacter
import org.akkirrai.hibiki.catalog.model.AniListEnrichment
import org.json.JSONArray
import org.json.JSONObject

/** Durable per-anime AniList match cache: SharedPreferences + hand-rolled JSON, independent from
 *  [org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository] on purpose -- offline mode
 *  doesn't need to know about AniList data, and that file's encode/decode should stay untouched. */
class AniListCacheRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns null if nothing cached, malformed, or expired. */
    internal fun get(animeId: String): AniListCacheEntry? {
        val encoded = prefs.getString(key(animeId), null) ?: return null
        val entry = runCatching { decode(JSONObject(encoded)) }.getOrNull() ?: return null
        return entry.takeUnless { it.isStale() }
    }

    internal fun put(animeId: String, entry: AniListCacheEntry) {
        prefs.edit().putString(key(animeId), encode(entry).toString()).apply()
    }

    private fun AniListCacheEntry.isStale(): Boolean =
        System.currentTimeMillis() - resolvedAtMillis > TTL_MILLIS

    private fun encode(entry: AniListCacheEntry): JSONObject = JSONObject().apply {
        put("anilistId", entry.anilistId ?: JSONObject.NULL)
        put("bannerUrl", entry.bannerUrl)
        put("averageScore", entry.averageScore ?: JSONObject.NULL)
        put("directors", JSONArray(entry.directors))
        put("resolvedAtMillis", entry.resolvedAtMillis)
        put(
            "characters",
            JSONArray().apply {
                entry.characters.forEach { character ->
                    put(
                        JSONObject().apply {
                            put("name", character.name)
                            put("imageUrl", character.imageUrl)
                            put("role", character.role)
                            put("voiceActorName", character.voiceActorName)
                            put("voiceActorImageUrl", character.voiceActorImageUrl)
                        },
                    )
                }
            },
        )
    }

    private fun decode(json: JSONObject): AniListCacheEntry = AniListCacheEntry(
        anilistId = json.optInt("anilistId", -1).takeIf { it > 0 },
        bannerUrl = json.optString("bannerUrl").ifBlank { null },
        averageScore = json.optInt("averageScore", -1).takeIf { it >= 0 },
        directors = json.optJSONArray("directors").toStringList(),
        resolvedAtMillis = json.optLong("resolvedAtMillis"),
        characters = json.optJSONArray("characters").toCharacterList(),
    )

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private fun JSONArray?.toCharacterList(): List<AniListCharacter> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val name = item.optString("name").takeIf(String::isNotBlank) ?: continue
                add(
                    AniListCharacter(
                        name = name,
                        imageUrl = item.optString("imageUrl").ifBlank { null },
                        role = item.optString("role").ifBlank { "SUPPORTING" },
                        voiceActorName = item.optString("voiceActorName").ifBlank { null },
                        voiceActorImageUrl = item.optString("voiceActorImageUrl").ifBlank { null },
                    ),
                )
            }
        }
    }

    private fun key(id: String): String = "anilist_$id"

    companion object {
        // Bump the suffix whenever matching/query logic changes in a way that should invalidate
        // previously cached (especially negative) results -- otherwise a stale "no match found"
        // entry masks the fix for its full 30-day TTL. The old prefs file is simply abandoned.
        const val PREFS_NAME = "hibiki_anilist_cache_v2"
        const val TTL_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}

/** [anilistId] null means "resolved, no confident match found" (negative cache). */
internal data class AniListCacheEntry(
    val anilistId: Int?,
    val bannerUrl: String? = null,
    val averageScore: Int? = null,
    val characters: List<AniListCharacter> = emptyList(),
    val directors: List<String> = emptyList(),
    val resolvedAtMillis: Long,
)

internal fun AniListCacheEntry.toEnrichmentOrNull(): AniListEnrichment? {
    val id = anilistId ?: return null
    return AniListEnrichment(
        anilistId = id,
        bannerUrl = bannerUrl,
        averageScore = averageScore,
        characters = characters,
        directors = directors,
    )
}

internal fun AniListEnrichment.toCacheEntry(): AniListCacheEntry = AniListCacheEntry(
    anilistId = anilistId,
    bannerUrl = bannerUrl,
    averageScore = averageScore,
    characters = characters,
    directors = directors,
    resolvedAtMillis = System.currentTimeMillis(),
)
