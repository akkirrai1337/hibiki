package eu.kanade.tachiyomi.animesource.model

/** extensions-lib 17: a named group of related titles, e.g. "Sequels" or "Same studio". */
class AnimeRelation(
    val name: String,
    val animes: List<SAnime>,
)
