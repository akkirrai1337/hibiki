package org.akkirrai.beakokit.matching

import org.akkirrai.beakokit.model.AnimeTitle
import kotlin.math.max
import kotlin.math.min

class TitleMatcher {
    fun confidence(
        title: AnimeTitle,
        candidateNames: List<String>,
        candidateYear: Int?,
        candidateType: String?,
        candidateEpisodes: Int?,
    ): Double {
        val sourceNames = title.allNames().map(::normalize).filter(String::isNotBlank)
        val targetNames = candidateNames.map(::normalize).filter(String::isNotBlank)
        val nameScore = sourceNames.maxOfOrNull { source ->
            targetNames.maxOfOrNull { target -> similarity(source, target) } ?: 0.0
        } ?: 0.0

        var score = nameScore * 0.78
        var weight = 0.78

        if (title.year != null && candidateYear != null) {
            score += if (title.year == candidateYear) 0.12 else 0.0
            weight += 0.12
        }
        if (title.type != null && candidateType != null) {
            score += if (normalizeType(title.type) == normalizeType(candidateType)) 0.05 else 0.0
            weight += 0.05
        }
        if (title.episodeCount != null && candidateEpisodes != null) {
            val difference = kotlin.math.abs(title.episodeCount - candidateEpisodes)
            score += when (difference) {
                0 -> 0.05
                1 -> 0.025
                else -> 0.0
            }
            weight += 0.05
        }

        return (score / weight).coerceIn(0.0, 1.0)
    }

    fun normalize(value: String): String {
        return value
            .lowercase()
            .replace('ё', 'е')
            .replace(SEASON_WORDS, " ")
            .replace(NON_ALPHANUMERIC, " ")
            .trim()
            .replace(MULTIPLE_SPACES, " ")
    }

    private fun similarity(left: String, right: String): Double {
        if (left == right) return 1.0
        if (left.isBlank() || right.isBlank()) return 0.0

        val editScore = 1.0 - levenshtein(left, right).toDouble() / max(left.length, right.length)
        val leftTokens = left.split(' ').toSet()
        val rightTokens = right.split(' ').toSet()
        val union = leftTokens union rightTokens
        val tokenScore = if (union.isEmpty()) 0.0 else (leftTokens intersect rightTokens).size.toDouble() / union.size
        val containmentScore = if (left.contains(right) || right.contains(left)) {
            min(left.length, right.length).toDouble() / max(left.length, right.length)
        } else {
            0.0
        }
        return maxOf(editScore, tokenScore, containmentScore)
    }

    private fun levenshtein(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        for (leftIndex in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            for (rightIndex in right.indices) {
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + if (left[leftIndex] == right[rightIndex]) 0 else 1,
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    private fun normalizeType(value: String): String = value.lowercase().replace("ona", "web")

    private companion object {
        val SEASON_WORDS = Regex("""\b(season|сезон)\s*\d*\b""")
        val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]+""")
        val MULTIPLE_SPACES = Regex("""\s+""")
    }
}
