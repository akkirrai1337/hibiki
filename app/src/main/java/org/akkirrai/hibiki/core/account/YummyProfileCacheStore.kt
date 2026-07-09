package org.akkirrai.hibiki.core.account

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.akkirrai.animeresolver.metadata.YummyOldNickname
import org.akkirrai.animeresolver.metadata.YummyProfile
import org.akkirrai.animeresolver.metadata.YummyProfileAvatars
import org.akkirrai.animeresolver.metadata.YummyProfileBanner
import org.akkirrai.animeresolver.metadata.YummyProfileCounts
import org.akkirrai.animeresolver.metadata.YummyProfileIds
import org.akkirrai.animeresolver.metadata.YummyProfileSex
import org.akkirrai.animeresolver.metadata.YummyProfileSocialCounts
import org.akkirrai.animeresolver.metadata.YummyProfileTexts
import org.akkirrai.animeresolver.metadata.YummyProfileWatchHistoryItem
import org.akkirrai.animeresolver.metadata.YummyProfileWatchSum
import org.akkirrai.animeresolver.metadata.YummyProfileWatches

interface YummyProfileCacheStore {
    fun getProfile(): YummyProfile?
    fun saveProfile(profile: YummyProfile)
    fun clearProfile()
}

class SharedPreferencesYummyProfileCacheStore(
    context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : YummyProfileCacheStore {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getProfile(): YummyProfile? {
        val rawValue = preferences.getString(KEY_PROFILE, null) ?: return null
        return runCatching {
            json.decodeFromString(CachedYummyProfile.serializer(), rawValue).toModel()
        }.getOrNull()
    }

    override fun saveProfile(profile: YummyProfile) {
        val rawValue = json.encodeToString(CachedYummyProfile.serializer(), profile.toCachedModel())
        preferences.edit().putString(KEY_PROFILE, rawValue).apply()
    }

    override fun clearProfile() {
        preferences.edit().remove(KEY_PROFILE).apply()
    }

    private companion object {
        const val PREFS_NAME = "yummy_profile_cache"
        const val KEY_PROFILE = "profile"
    }
}

@Serializable
private data class CachedYummyProfile(
    val id: Long,
    val nickname: String,
    val about: String? = null,
    val banned: Boolean = false,
    val ids: CachedYummyProfileIds? = null,
    val avatars: CachedYummyProfileAvatars = CachedYummyProfileAvatars(),
    val avatarUrl: String? = null,
    val birthDate: Long? = null,
    val lastOnline: Long? = null,
    val sex: Int? = null,
    val roles: List<String> = emptyList(),
    val registerDate: Long? = null,
    val texts: CachedYummyProfileTexts? = null,
    val banner: CachedYummyProfileBanner? = null,
    val watches: CachedYummyProfileWatches? = null,
    val counts: CachedYummyProfileCounts = CachedYummyProfileCounts(),
    val socialCounts: CachedYummyProfileSocialCounts = CachedYummyProfileSocialCounts(),
    val oldNicknames: List<CachedYummyOldNickname> = emptyList(),
) {
    fun toModel(): YummyProfile {
        return YummyProfile(
            id = id,
            nickname = nickname,
            about = about,
            banned = banned,
            ids = ids?.toModel(),
            avatars = avatars.toModel(),
            avatarUrl = avatarUrl,
            birthDate = birthDate,
            lastOnline = lastOnline,
            sex = sex?.let(YummyProfileSex::fromValue),
            roles = roles,
            registerDate = registerDate,
            texts = texts?.toModel(),
            banner = banner?.toModel(),
            watches = watches?.toModel(),
            counts = counts.toModel(),
            socialCounts = socialCounts.toModel(),
            oldNicknames = oldNicknames.map(CachedYummyOldNickname::toModel),
        )
    }
}

@Serializable
private data class CachedYummyProfileIds(
    val shikimoriId: Long? = null,
    val shikimoriNickname: String? = null,
    val vkId: Long? = null,
    val telegramNickname: String? = null,
) {
    fun toModel(): YummyProfileIds {
        return YummyProfileIds(
            shikimoriId = shikimoriId,
            shikimoriNickname = shikimoriNickname,
            vkId = vkId,
            telegramNickname = telegramNickname,
        )
    }
}

@Serializable
private data class CachedYummyProfileAvatars(
    val small: String? = null,
    val big: String? = null,
    val full: String? = null,
) {
    fun toModel(): YummyProfileAvatars {
        return YummyProfileAvatars(
            small = small,
            big = big,
            full = full,
        )
    }
}

@Serializable
private data class CachedYummyProfileTexts(
    val color: Int? = null,
    val left: String? = null,
    val right: String? = null,
) {
    fun toModel(): YummyProfileTexts {
        return YummyProfileTexts(
            color = color,
            left = left,
            right = right,
        )
    }
}

@Serializable
private data class CachedYummyProfileBanner(
    val cropped: String? = null,
    val full: String? = null,
) {
    fun toModel(): YummyProfileBanner {
        return YummyProfileBanner(
            cropped = cropped,
            full = full,
        )
    }
}

@Serializable
private data class CachedYummyProfileWatches(
    val sum: List<CachedYummyProfileWatchSum> = emptyList(),
    val history: List<CachedYummyProfileWatchHistoryItem> = emptyList(),
) {
    fun toModel(): YummyProfileWatches {
        return YummyProfileWatches(
            sum = sum.map(CachedYummyProfileWatchSum::toModel),
            history = history.map(CachedYummyProfileWatchHistoryItem::toModel),
        )
    }
}

@Serializable
private data class CachedYummyProfileWatchSum(
    val shortName: String? = null,
    val name: String? = null,
    val spentTime: Long? = null,
    val value: Int? = null,
    val alias: String? = null,
) {
    fun toModel(): YummyProfileWatchSum {
        return YummyProfileWatchSum(
            shortName = shortName,
            name = name,
            spentTime = spentTime,
            value = value,
            alias = alias,
        )
    }
}

@Serializable
private data class CachedYummyProfileWatchHistoryItem(
    val date: Long? = null,
    val episodeCount: Int? = null,
    val duration: Long? = null,
) {
    fun toModel(): YummyProfileWatchHistoryItem {
        return YummyProfileWatchHistoryItem(
            date = date,
            episodeCount = episodeCount,
            duration = duration,
        )
    }
}

@Serializable
private data class CachedYummyOldNickname(
    val nickname: String,
    val date: Long? = null,
) {
    fun toModel(): YummyOldNickname {
        return YummyOldNickname(
            nickname = nickname,
            date = date,
        )
    }
}

@Serializable
private data class CachedYummyProfileCounts(
    val watching: Int = 0,
    val planned: Int = 0,
    val completed: Int = 0,
    val dropped: Int = 0,
    val onHold: Int = 0,
    val favorite: Int = 0,
) {
    fun toModel(): YummyProfileCounts {
        return YummyProfileCounts(
            watching = watching,
            planned = planned,
            completed = completed,
            dropped = dropped,
            onHold = onHold,
            favorite = favorite,
        )
    }
}

@Serializable
private data class CachedYummyProfileSocialCounts(
    val friends: Int = 0,
    val reviews: Int = 0,
    val comments: Int = 0,
    val posts: Int = 0,
    val collections: Int = 0,
) {
    fun toModel(): YummyProfileSocialCounts {
        return YummyProfileSocialCounts(
            friends = friends,
            reviews = reviews,
            comments = comments,
            posts = posts,
            collections = collections,
        )
    }
}

private fun YummyProfile.toCachedModel(): CachedYummyProfile {
    return CachedYummyProfile(
        id = id,
        nickname = nickname,
        about = about,
        banned = banned,
        ids = ids?.toCachedModel(),
        avatars = avatars.toCachedModel(),
        avatarUrl = avatarUrl,
        birthDate = birthDate,
        lastOnline = lastOnline,
        sex = sex?.value,
        roles = roles,
        registerDate = registerDate,
        texts = texts?.toCachedModel(),
        banner = banner?.toCachedModel(),
        watches = watches?.toCachedModel(),
        counts = counts.toCachedModel(),
        socialCounts = socialCounts.toCachedModel(),
        oldNicknames = oldNicknames.map(YummyOldNickname::toCachedModel),
    )
}

private fun YummyProfileIds.toCachedModel(): CachedYummyProfileIds {
    return CachedYummyProfileIds(
        shikimoriId = shikimoriId,
        shikimoriNickname = shikimoriNickname,
        vkId = vkId,
        telegramNickname = telegramNickname,
    )
}

private fun YummyProfileAvatars.toCachedModel(): CachedYummyProfileAvatars {
    return CachedYummyProfileAvatars(
        small = small,
        big = big,
        full = full,
    )
}

private fun YummyProfileTexts.toCachedModel(): CachedYummyProfileTexts {
    return CachedYummyProfileTexts(
        color = color,
        left = left,
        right = right,
    )
}

private fun YummyProfileBanner.toCachedModel(): CachedYummyProfileBanner {
    return CachedYummyProfileBanner(
        cropped = cropped,
        full = full,
    )
}

private fun YummyProfileWatches.toCachedModel(): CachedYummyProfileWatches {
    return CachedYummyProfileWatches(
        sum = sum.map(YummyProfileWatchSum::toCachedModel),
        history = history.map(YummyProfileWatchHistoryItem::toCachedModel),
    )
}

private fun YummyProfileWatchSum.toCachedModel(): CachedYummyProfileWatchSum {
    return CachedYummyProfileWatchSum(
        shortName = shortName,
        name = name,
        spentTime = spentTime,
        value = value,
        alias = alias,
    )
}

private fun YummyProfileWatchHistoryItem.toCachedModel(): CachedYummyProfileWatchHistoryItem {
    return CachedYummyProfileWatchHistoryItem(
        date = date,
        episodeCount = episodeCount,
        duration = duration,
    )
}

private fun YummyOldNickname.toCachedModel(): CachedYummyOldNickname {
    return CachedYummyOldNickname(
        nickname = nickname,
        date = date,
    )
}

private fun YummyProfileCounts.toCachedModel(): CachedYummyProfileCounts {
    return CachedYummyProfileCounts(
        watching = watching,
        planned = planned,
        completed = completed,
        dropped = dropped,
        onHold = onHold,
        favorite = favorite,
    )
}

private fun YummyProfileSocialCounts.toCachedModel(): CachedYummyProfileSocialCounts {
    return CachedYummyProfileSocialCounts(
        friends = friends,
        reviews = reviews,
        comments = comments,
        posts = posts,
        collections = collections,
    )
}
