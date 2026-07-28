package org.akkirrai.hibiki.shared.details

data class SourceMaterialLabels(
    val manga: String,
    val manhwa: String,
    val manhua: String,
    val lightNovel: String,
    val webNovel: String,
    val visualNovel: String,
    val game: String,
    val original: String,
)

fun resolveSourceMaterialLabel(
    sourceMaterial: String?,
    labels: SourceMaterialLabels,
): String? {
    val normalized = sourceMaterial?.trim()?.lowercase() ?: return null
    return when (normalized) {
        "манга", "manga" -> labels.manga
        "манхва", "manhwa" -> labels.manhwa
        "маньхуа", "manhua" -> labels.manhua
        "ранобэ", "light novel" -> labels.lightNovel
        "веб-новелла", "web novel" -> labels.webNovel
        "визуальная новелла", "visual novel" -> labels.visualNovel
        "игра", "game" -> labels.game
        "оригинал", "original" -> labels.original
        else -> sourceMaterial
    }
}
