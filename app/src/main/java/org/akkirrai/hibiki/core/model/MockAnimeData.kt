package org.akkirrai.hibiki.core.model

object MockAnimeData {
    val trending = listOf(
        Anime("1", "Sousou no Frieren", "Adventure, Fantasy", "28 episodes", "Completed"),
        Anime("2", "Dandadan", "Action, Supernatural", "12 episodes", "Ongoing"),
        Anime("3", "Dungeon Meshi", "Adventure, Comedy", "24 episodes", "Completed"),
        Anime("4", "Blue Box", "Romance, Sports", "24 episodes", "Ongoing")
    )

    val recent = listOf(
        Anime("5", "Kaiju No. 8", "Action, Sci-Fi", "12 episodes", "Watching now"),
        Anime("6", "Orb: On the Movements of the Earth", "Drama, Historical", "25 episodes", "Planned"),
        Anime("7", "Wistoria", "Fantasy, School", "12 episodes", "Watching now"),
        Anime("8", "The Apothecary Diaries", "Mystery, Drama", "24 episodes", "Completed")
    )

    val library = listOf(
        Anime("9", "Mob Psycho 100", "Action, Comedy", "37 episodes", "In library"),
        Anime("10", "Vinland Saga", "Drama, Historical", "48 episodes", "In library"),
        Anime("11", "Violet Evergarden", "Drama, Slice of Life", "13 episodes", "In library")
    )
}
