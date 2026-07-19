package org.akkirrai.hibiki.core.source

import android.content.Context
import androidx.annotation.DrawableRes
import io.ktor.client.HttpClient
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.MapSourceConfig
import org.akkirrai.beakokit.api.LatestSource
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.SourceCatalog
import org.akkirrai.beakokit.api.SourceConfig
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceContractValidator
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceLogLevel
import org.akkirrai.beakokit.api.SourceLogger
import org.akkirrai.beakokit.source.aniliberty.AniLibertySource
import org.akkirrai.beakokit.source.yummy.YummyAnimeSource
import org.akkirrai.animeresolver.model.AnimeSearchFilterCatalog
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.account.AndroidKeystoreYummyApplicationTokenStore
import org.akkirrai.hibiki.core.log.AppLogger

data class AnimeSourceDescriptor(
    val info: SourceInfo,
    @param:DrawableRes val iconRes: Int,
) {
    val id: SourceId
        get() = info.id

    val name: String
        get() = info.name

    val language: String
        get() = info.languages.first().tag.uppercase()

    val supportsPlayback: Boolean
        get() = SourceCapability.PLAYBACK in info.capabilities

    val contentFeatures: Set<SourceCapability>
        get() = info.capabilities.intersect(CONTENT_CAPABILITIES)

    private companion object {
        val CONTENT_CAPABILITIES = setOf(
            SourceCapability.RELATED_TITLES,
            SourceCapability.SIMILAR_TITLES,
        )
    }
}

object AnimeSourceRegistry {
    private data class Registration(
        val descriptor: AnimeSourceDescriptor,
        val createSource: (Context, HttpClient) -> AnimeSource,
        val localizeFilters: (AnimeSearchFilterCatalog, Boolean) -> AnimeSearchFilterCatalog = { catalog, _ -> catalog },
        val normalizeTitleId: (String) -> String = { it },
    )

    private val registrations = listOf(
        Registration(
            descriptor = AnimeSourceDescriptor(
                info = YummyAnimeSource.INFO,
                iconRes = R.drawable.source_yummy_anime,
            ),
            createSource = { context, client -> createYummySource(context, client) },
            localizeFilters = YummySearchFilterLocalizer::localize,
            normalizeTitleId = YummyIdMigration::normalizeTitleId,
        ),
        Registration(
            descriptor = AnimeSourceDescriptor(
                info = AniLibertySource.INFO,
                iconRes = R.drawable.source_ani_liberty,
            ),
            createSource = { context, client ->
                AniLibertySource(createSourceContext(context, client, AniLibertySource.INFO.id))
            },
        ),
    )

    val sources: List<AnimeSourceDescriptor> = registrations.map(Registration::descriptor)
    val catalog = SourceCatalog(sources.map(AnimeSourceDescriptor::info))

    fun createRuntime(
        context: Context,
        client: HttpClient,
        sourceId: SourceId = AppPreferences.readState(context).animeSource,
    ): AnimeSourceRuntime {
        val appContext = context.applicationContext
        val registration = registration(sourceId)
        val source = registration.createSource(appContext, client)
        check(source.info == registration.descriptor.info) {
            "Source metadata does not match its registration: $sourceId"
        }
        SourceContractValidator.requireValid(source)
        val runtime = AnimeSourceRuntime(
            descriptor = registration.descriptor,
            metadata = source,
            latestSource = source as? LatestSource,
            playbackSource = source as? PlaybackSource,
            localizeFilters = registration.localizeFilters,
            normalizeTitleId = registration.normalizeTitleId,
        )
        return runtime
    }

    fun descriptor(sourceId: SourceId): AnimeSourceDescriptor = registration(sourceId).descriptor

    fun descriptorForTitle(titleId: String, fallbackSourceId: SourceId): AnimeSourceDescriptor =
        descriptor(AnimeKey.parse(titleId)?.sourceId ?: fallbackSourceId)

    fun descriptorForStoredTitle(titleId: String): AnimeSourceDescriptor =
        descriptor(
            AnimeKey.parse(titleId)?.sourceId
                ?: AppPreferences.DEFAULT_ANIME_SOURCE_ID,
        )

    private fun registration(sourceId: SourceId): Registration =
        registrations.firstOrNull { it.descriptor.id == sourceId }
            ?: error("Anime source is not registered: $sourceId")

    private fun createSourceContext(
        context: Context,
        client: HttpClient,
        sourceId: SourceId,
        config: SourceConfig = SourceConfig.EMPTY,
    ): DefaultSourceContext = DefaultSourceContext(
        httpClient = client,
        preferredLanguages = listOf(
            when (AppPreferences.readState(context).languageMode) {
                LanguageMode.ENGLISH -> SourceLanguage.ENGLISH
                LanguageMode.RUSSIAN -> SourceLanguage.RUSSIAN
                LanguageMode.SYSTEM -> if (
                    context.resources.configuration.locales[0]?.language == "en"
                ) SourceLanguage.ENGLISH else SourceLanguage.RUSSIAN
            },
        ),
        config = config,
        logger = SourceLogger { level, message, throwable ->
            val tag = "BeakoKit/${sourceId.value}"
            when (level) {
                SourceLogLevel.DEBUG -> AppLogger.d(tag, message)
                SourceLogLevel.WARNING -> AppLogger.w(tag, message, throwable)
                SourceLogLevel.ERROR -> AppLogger.e(tag, message, throwable)
            }
        },
    )

    private fun createYummySource(context: Context, client: HttpClient): AnimeSource {
        val applicationToken = AndroidKeystoreYummyApplicationTokenStore(context)
            .getEffectiveApplicationToken()
        val config = MapSourceConfig(
            secrets = buildMap {
                applicationToken.takeIf(String::isNotBlank)?.let { token ->
                    put(YummyAnimeSource.APPLICATION_TOKEN_KEY, token)
                }
            },
        )
        return YummyAnimeSource(
            createSourceContext(context, client, YummyAnimeSource.INFO.id, config),
        )
    }

}
