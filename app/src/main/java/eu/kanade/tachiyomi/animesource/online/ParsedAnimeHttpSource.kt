package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** API v14-v16 parser base for catalogue sources backed by HTML pages. */
abstract class ParsedAnimeHttpSource : AnimeHttpSource() {
    override fun popularAnimeParse(response: Response): AnimesPage = response.asJsoup().let { document ->
        AnimesPage(
            document.select(popularAnimeSelector()).map(::popularAnimeFromElement),
            hasNextPage(document, popularAnimeNextPageSelector()),
        )
    }

    protected abstract fun popularAnimeSelector(): String
    protected abstract fun popularAnimeFromElement(element: Element): SAnime
    protected abstract fun popularAnimeNextPageSelector(): String?

    override fun searchAnimeParse(response: Response): AnimesPage = response.asJsoup().let { document ->
        AnimesPage(
            document.select(searchAnimeSelector()).map(::searchAnimeFromElement),
            hasNextPage(document, searchAnimeNextPageSelector()),
        )
    }

    protected abstract fun searchAnimeSelector(): String
    protected abstract fun searchAnimeFromElement(element: Element): SAnime
    protected abstract fun searchAnimeNextPageSelector(): String?

    override fun latestUpdatesParse(response: Response): AnimesPage = response.asJsoup().let { document ->
        AnimesPage(
            document.select(latestUpdatesSelector()).map(::latestUpdatesFromElement),
            hasNextPage(document, latestUpdatesNextPageSelector()),
        )
    }

    protected abstract fun latestUpdatesSelector(): String
    protected abstract fun latestUpdatesFromElement(element: Element): SAnime
    protected abstract fun latestUpdatesNextPageSelector(): String?

    override fun animeDetailsParse(response: Response): SAnime = animeDetailsParse(response.asJsoup())
    protected abstract fun animeDetailsParse(document: Document): SAnime

    override fun episodeListParse(response: Response): List<SEpisode> = response.asJsoup()
        .select(episodeListSelector())
        .map(::episodeFromElement)

    protected abstract fun episodeListSelector(): String
    protected abstract fun episodeFromElement(element: Element): SEpisode

    override fun seasonListParse(response: Response): List<SAnime> = response.asJsoup()
        .select(seasonListSelector())
        .map(::seasonFromElement)

    protected open fun seasonListSelector(): String = throw UnsupportedOperationException()
    protected open fun seasonFromElement(element: Element): SAnime = throw UnsupportedOperationException()

    override fun relatedAnimeListParse(response: Response): List<SAnime> = response.asJsoup()
        .select(relatedAnimeListSelector())
        .map(::relatedAnimeFromElement)

    protected open fun relatedAnimeListSelector(): String = throw UnsupportedOperationException()
    protected open fun relatedAnimeFromElement(element: Element): SAnime = throw UnsupportedOperationException()

    // v14 video parsing. v16 sources parse hosters instead and leave these unimplemented.
    override fun videoListParse(response: Response): List<Video> = response.asJsoup()
        .select(videoListSelector())
        .map(::videoFromElement)

    protected open fun videoListSelector(): String = throw UnsupportedOperationException()
    protected open fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()

    override fun videoUrlParse(response: Response): String = videoUrlParse(response.asJsoup())
    protected open fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    private fun hasNextPage(document: Document, selector: String?): Boolean =
        selector?.let { document.selectFirst(it) != null } ?: false
}
