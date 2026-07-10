package org.akkirrai.animeresolver.metadata

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.parametersOf
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.AnimeSearchSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class YummyMetadataSourceTest {
    @Test
    fun `search maps aliases`() = runBlocking {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/anime" -> respond(
                    content = SEARCH_JSON,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> error("Unexpected URL: ${request.url}")
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }

        val source = YummyMetadataSource(
            client = client,
            baseUrl = "https://yummy.test",
        )

        val result = source.search("Фрирен").single()

        assertEquals("Фрирен, провожающая в последний путь", result.russianName)
        assertEquals("Frieren: Beyond Journey's End", result.englishName)
        assertEquals("Sousou no Frieren", result.originalName)
        client.close()
    }

    @Test
    fun `details maps episodes object and description`() = runBlocking {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/anime/11371" -> respond(
                    content = DETAILS_JSON,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> error("Unexpected URL: ${request.url}")
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }

        val source = YummyMetadataSource(
            client = client,
            baseUrl = "https://yummy.test",
        )

        val result = source.getById("11371")

        assertEquals(12, result.episodeCount)
        assertEquals("Казалось бы, Момо Аясэ ничем не отличается от обычных старшеклассниц.", result.description?.substringBefore('\n'))
        assertEquals(listOf("Yummy", "MAL", "Shiki", "KP", "WA"), result.ratings.map { it.source })
        assertEquals("R-17+", result.ageRating)
        assertEquals(2_615_332L, result.viewCount)
        assertEquals(1, result.screenshots.size)
        client.close()
    }

    @Test
    fun `filtered search builds yummy query params`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("/anime", request.url.encodedPath)
            assertEquals(
                parametersOf(
                    "q" to listOf("dandadan"),
                    "limit" to listOf("24"),
                    "offset" to listOf("12"),
                    "sort" to listOf("views"),
                    "types" to listOf("tv,movie"),
                    "statuses" to listOf("ongoing,released"),
                    "genres" to listOf("action,comedy"),
                    "genres_exclude" to listOf("horror"),
                    "year_from" to listOf("2020"),
                    "year_to" to listOf("2025"),
                ),
                request.url.parameters
            )
            respond(
                content = SEARCH_JSON,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }

        val source = YummyMetadataSource(
            client = client,
            baseUrl = "https://yummy.test",
        )

        source.search(
            AnimeSearchRequest(
                query = "dandadan",
                limit = 24,
                offset = 12,
                sort = AnimeSearchSort.VIEWS,
                typeAliases = listOf("tv", "movie"),
                statusAliases = listOf("ongoing", "released"),
                includedGenreAliases = listOf("action", "comedy"),
                excludedGenreAliases = listOf("horror"),
                yearFrom = 2020,
                yearTo = 2025,
            )
        )

        client.close()
    }

    @Test
    fun `filter catalog exposes key yummy options`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/swagger.json" -> respond(
                    content = SWAGGER_JSON,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> error("Unexpected request: ${request.url}")
            }
        }) {
            install(ContentNegotiation) { json() }
        }
        val source = YummyMetadataSource(
            client = client,
            baseUrl = "https://yummy.test",
        )

        val catalog = source.getSearchFilterCatalog()

        assertContains(catalog.sortOptions.map { it.id }, "top")
        assertContains(catalog.sortOptions.map { it.id }, "relevance")
        assertContains(catalog.typeOptions.map { it.id }, "tv")
        assertContains(catalog.statusOptions.map { it.id }, "ongoing")
        assertContains(catalog.genreOptions.map { it.id }, "drama")
        assertContains(catalog.genreOptions.map { it.id }, "komediya")
        client.close()
    }

    private companion object {
        val SEARCH_JSON = """
            {
              "response": [{
                "anime_id": 303,
                "title": "Фрирен, провожающая в последний путь",
                "title_en": "Frieren: Beyond Journey's End",
                "title_orig": "Sousou no Frieren",
                "alternative_titles": ["Frieren", "Sousou no Frieren"],
                "type": {"alias": "tv"},
                "year": 2023
              }]
            }
        """.trimIndent()

        val DETAILS_JSON = """
            {
              "response": {
                "anime_id": 11371,
                "title": "Дандадан",
                "description": "Казалось бы, Момо Аясэ ничем не отличается от обычных старшеклассниц.\nНо это именно то описание, которое должно загрузиться.",
                "year": 2024,
                "type": {"alias": "tv"},
                "episodes": {
                  "count": 24,
                  "aired": 12
                },
                "poster": {
                  "fullsize": "//static.yani.tv/posters/full/1636760604.jpg"
                },
                "rating": {
                  "average": 8.65746893906902,
                  "counters": 6922,
                  "kp_rating": 8.7,
                  "shikimori_rating": 8.4275,
                  "myanimelist_rating": 8.4,
                  "worldart_rating": 8.1
                },
                "min_age": {
                  "title": "R-17+",
                  "title_long": "R-17+ (насилие и/или нецензурная лексика)"
                },
                "views": 2615332,
                "random_screenshots": [
                  {
                    "sizes": {
                      "small": "https://i.kodikres.com/screenshots/seria/1340414/3.jpg"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val SWAGGER_JSON = """
            {
              "components": {
                "schemas": {
                  "GetAnimeGenresIdResponse": {
                    "properties": {
                      "response": {
                        "properties": {
                          "alias": {
                            "enum": ["drama", "komediya", "senen"]
                          }
                        }
                      }
                    }
                  },
                  "GetAnimeCatalogResponse": {
                    "properties": {
                      "response": {
                        "properties": {
                          "data": {
                            "items": {
                              "properties": {
                                "anime_status": {
                                  "properties": {
                                    "alias": {
                                      "enum": ["released", "ongoing", "announcement"]
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              },
              "paths": {
                "/anime": {
                  "get": {
                    "parameters": [
                      {
                        "name": "sort",
                        "schema": {
                          "enum": ["top", "title", "year", "votes", "views", "comments"]
                        }
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()
    }
}
