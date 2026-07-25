package org.akkirrai.hibiki.shared.prototype

import org.akkirrai.hibiki.shared.model.Anime

/** Deterministic catalog used by the Desktop prototype until a real repository is wired. */
object PrototypeCatalog {
    val items: List<Anime> = listOf(
        Anime(
            id = "hibiki-1",
            title = "Frieren: Beyond Journey's End",
            subtitle = "Sousou no Frieren",
            episodesLabel = "28 episodes",
            status = "Finished",
            description = "A calm, character-driven fantasy about time, memory, and the journey after the final battle.",
            genres = listOf("Fantasy", "Adventure"),
        ),
        Anime(
            id = "hibiki-2",
            title = "The Apothecary Diaries",
            subtitle = "Kusuriya no Hitorigoto",
            episodesLabel = "24 episodes",
            status = "Ongoing",
            description = "MaoMao solves palace mysteries with sharp observation and a talent for medicine.",
            genres = listOf("Mystery", "Drama"),
        ),
        Anime(
            id = "hibiki-3",
            title = "Delicious in Dungeon",
            subtitle = "Dungeon Meshi",
            episodesLabel = "14 episodes",
            status = "Ongoing",
            description = "An inventive dungeon expedition where every monster might become dinner.",
            genres = listOf("Comedy", "Fantasy"),
        ),
        Anime(
            id = "hibiki-4",
            title = "Violet Evergarden",
            subtitle = "Violet Evergarden",
            episodesLabel = "13 episodes",
            status = "Finished",
            description = "A former soldier learns to understand love while writing letters for others.",
            genres = listOf("Drama", "Romance"),
        ),
        Anime(
            id = "hibiki-5",
            title = "Dungeon People",
            subtitle = "Dungeon no Naka no Hito",
            episodesLabel = "12 episodes",
            status = "Finished",
            description = "A quiet look behind the scenes of a dungeon and the people who keep it running.",
            genres = listOf("Fantasy", "Slice of life"),
        ),
        Anime(
            id = "hibiki-6",
            title = "Odd Taxi",
            subtitle = "Odd Taxi",
            episodesLabel = "13 episodes",
            status = "Finished",
            description = "A Tokyo taxi driver gets tangled in a disappearance that connects everyone around him.",
            genres = listOf("Thriller", "Mystery"),
        ),
    )
}
