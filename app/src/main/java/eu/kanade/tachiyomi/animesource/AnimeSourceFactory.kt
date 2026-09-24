package eu.kanade.tachiyomi.animesource

/** Creates one or more catalogue sources from an Aniyomi extension APK. */
interface AnimeSourceFactory {
    fun createSources(): List<AnimeSource>
}
