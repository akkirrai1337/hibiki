package org.akkirrai.hibiki.core.anilist

import kotlinx.serialization.Serializable

internal const val ANILIST_SEARCH_QUERY = """
query (${'$'}search: String, ${'$'}page: Int, ${'$'}perPage: Int) {
  Page(page: ${'$'}page, perPage: ${'$'}perPage) {
    media(search: ${'$'}search, type: ANIME) {
      id
      seasonYear
      format
      episodes
      title { romaji english native }
    }
  }
}
"""

internal const val ANILIST_DETAILS_QUERY = """
query (${'$'}id: Int) {
  Media(id: ${'$'}id, type: ANIME) {
    bannerImage
    averageScore
    characters(sort: [ROLE, RELEVANCE], perPage: 25) {
      edges {
        role
        node { name { full native } image { medium } }
        voiceActors(language: JAPANESE) {
          name { full }
          image { medium }
        }
      }
    }
    staff(perPage: 25) {
      edges {
        role
        node { name { full native } }
      }
    }
  }
}
"""

@Serializable
internal data class AniListGraphQlResponse<T>(
    val data: T? = null,
)

@Serializable
internal data class AniListSearchData(
    val Page: AniListPage? = null,
)

@Serializable
internal data class AniListPage(
    val media: List<AniListSearchMedia> = emptyList(),
)

@Serializable
internal data class AniListSearchMedia(
    val id: Int,
    val seasonYear: Int? = null,
    val format: String? = null,
    val episodes: Int? = null,
    val title: AniListTitle = AniListTitle(),
)

@Serializable
internal data class AniListTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)

@Serializable
internal data class AniListMediaData(
    val Media: AniListMediaDetail? = null,
)

@Serializable
internal data class AniListMediaDetail(
    val bannerImage: String? = null,
    val averageScore: Int? = null,
    val characters: AniListCharacterConnection? = null,
    val staff: AniListStaffConnection? = null,
)

@Serializable
internal data class AniListCharacterConnection(
    val edges: List<AniListCharacterEdge> = emptyList(),
)

@Serializable
internal data class AniListCharacterEdge(
    val role: String? = null,
    val node: AniListPersonNode? = null,
    val voiceActors: List<AniListPersonNode> = emptyList(),
)

@Serializable
internal data class AniListStaffConnection(
    val edges: List<AniListStaffEdge> = emptyList(),
)

@Serializable
internal data class AniListStaffEdge(
    val role: String? = null,
    val node: AniListPersonNode? = null,
)

@Serializable
internal data class AniListPersonNode(
    val name: AniListPersonName? = null,
    val image: AniListImage? = null,
)

@Serializable
internal data class AniListPersonName(
    val full: String? = null,
    val native: String? = null,
)

@Serializable
internal data class AniListImage(
    val medium: String? = null,
)
