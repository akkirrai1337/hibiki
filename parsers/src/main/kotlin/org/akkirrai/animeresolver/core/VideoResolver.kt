package org.akkirrai.animeresolver.core

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.Episode
import org.akkirrai.animeresolver.model.ProviderFailure
import org.akkirrai.animeresolver.model.ProviderMatch
import org.akkirrai.animeresolver.model.SourceDiscovery
import org.akkirrai.animeresolver.model.StreamValidationResult

class VideoResolver(
    providers: List<VideoProvider>,
    private val extractors: List<PlayerExtractor>,
    private val validator: StreamValidator,
) {
    private val providersById = providers.associateBy(VideoProvider::id)

    suspend fun findSources(title: AnimeTitle): List<ProviderMatch> =
        discoverSources(title).matches

    suspend fun discoverSources(title: AnimeTitle): SourceDiscovery = coroutineScope {
        val attempts = providersById.values
            .map { provider ->
                async {
                    provider.name to runCatching { provider.search(title) }
                }
            }
            .awaitAll()

        SourceDiscovery(
            matches = attempts
                .flatMap { it.second.getOrElse { emptyList() } }
                .sortedByDescending(ProviderMatch::confidence),
            failures = attempts.mapNotNull { (name, result) ->
                val error = result.exceptionOrNull() ?: return@mapNotNull null
                ProviderFailure(
                    providerName = name,
                    message = error.message ?: "неизвестная ошибка",
                    statusCode = (error as? SourceException)?.statusCode,
                )
            },
        )
    }

    suspend fun getEpisodes(match: ProviderMatch): List<Episode> =
        provider(match).getEpisodes(match).sortedBy(Episode::number)

    suspend fun resolveAndValidate(
        match: ProviderMatch,
        episode: Episode,
    ): StreamValidationResult {
        val links = provider(match).getPlayerLinks(match, episode)
            .sortedByDescending { qualityValue(it.quality) }
        if (links.isEmpty()) {
            throw SourceException("Источник не вернул ссылок для серии ${episode.number}")
        }

        val failures = mutableListOf<String>()
        for (link in links) {
            val extractor = extractors.firstOrNull { it.supports(link) }
            if (extractor == null) {
                failures += "Нет extractor для ${link.type}"
                continue
            }
            val attempt = runCatching {
                val stream = extractor.extract(link)
                validator.validate(stream)
            }
            val result = attempt.getOrNull()
            if (result?.success == true) {
                return result.copy(
                    playerName = link.playerName,
                    translation = link.translation,
                )
            }
            val player = listOfNotNull(link.playerName, link.translation)
                .joinToString(" / ")
                .ifBlank { link.type.name }
            failures += "$player: ${result?.message ?: attempt.exceptionOrNull()?.message.orEmpty()}"
        }
        throw SourceException(failures.filter(String::isNotBlank).joinToString("; ").ifBlank {
            "Не удалось получить рабочий поток"
        })
    }

    private fun provider(match: ProviderMatch): VideoProvider =
        providersById[match.providerId]
            ?: throw SourceException("Неизвестный provider: ${match.providerId}")

    private fun qualityValue(quality: String?): Int =
        quality?.filter(Char::isDigit)?.toIntOrNull() ?: 0
}
