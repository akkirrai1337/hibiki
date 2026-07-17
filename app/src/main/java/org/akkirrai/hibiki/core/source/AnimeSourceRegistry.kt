package org.akkirrai.hibiki.core.source

import android.content.Context
import androidx.annotation.DrawableRes
import io.ktor.client.HttpClient
import org.akkirrai.animeresolver.core.MetadataSource
import org.akkirrai.animeresolver.metadata.AniLibertyMetadataSource
import org.akkirrai.animeresolver.metadata.YummyMetadataSource
import org.akkirrai.hibiki.app.settings.AnimeSourceId
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.account.AndroidKeystoreYummyApplicationTokenStore
import org.akkirrai.hibiki.core.log.AppLogger

data class AnimeSourceDescriptor(
    val id: AnimeSourceId,
    val name: String,
    val language: String,
    @param:DrawableRes val iconRes: Int,
)

object AnimeSourceRegistry {
    private data class Registration(
        val descriptor: AnimeSourceDescriptor,
        val createMetadataSource: (Context, HttpClient) -> MetadataSource,
    )

    private val registrations = listOf(
        Registration(
            descriptor = AnimeSourceDescriptor(
                AnimeSourceId.YUMMY_ANIME,
                "YummyAnime",
                "RU",
                R.drawable.source_yummy_anime,
            ),
            createMetadataSource = { context, client -> createYummySource(context, client) },
        ),
        Registration(
            descriptor = AnimeSourceDescriptor(
                AnimeSourceId.ANI_LIBERTY,
                "AniLiberty",
                "RU",
                R.drawable.source_ani_liberty,
            ),
            createMetadataSource = { _, client -> AniLibertyMetadataSource(client) },
        ),
    )

    val sources: List<AnimeSourceDescriptor> = registrations.map(Registration::descriptor)

    fun create(
        context: Context,
        client: HttpClient,
        sourceId: AnimeSourceId = AppPreferences.readState(context).animeSource,
    ): MetadataSource {
        val registration = registrations.firstOrNull { it.descriptor.id == sourceId }
            ?: error("Anime source is not registered: $sourceId")
        return registration.createMetadataSource(context.applicationContext, client)
    }

    private fun createYummySource(context: Context, client: HttpClient): MetadataSource =
        YummyMetadataSource(
            client = client,
            applicationToken = AndroidKeystoreYummyApplicationTokenStore(context)
                .getEffectiveApplicationToken(),
            debugLogger = { message -> AppLogger.d("YummyMetadataSource", message) },
            languageProvider = {
                when (AppPreferences.readState(context).languageMode) {
                    LanguageMode.ENGLISH -> "en"
                    LanguageMode.RUSSIAN -> "ru"
                    LanguageMode.SYSTEM -> if (
                        context.resources.configuration.locales[0]?.language == "en"
                    ) "en" else "ru"
                }
            },
        )
}
