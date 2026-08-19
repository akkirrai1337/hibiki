package org.akkirrai.hibiki.shared.player

enum class VideoScaleMode {
    FIT,
    CROP,
    STRETCH;

    fun next(): VideoScaleMode = entries[(ordinal + 1) % entries.size]
}
