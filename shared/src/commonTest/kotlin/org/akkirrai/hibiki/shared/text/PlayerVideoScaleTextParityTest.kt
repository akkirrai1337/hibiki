package org.akkirrai.hibiki.shared.text

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.player.VideoScaleMode
import org.akkirrai.hibiki.shared.player.textKey
import org.akkirrai.hibiki.shared.settings.LanguageMode

class PlayerVideoScaleTextParityTest {
    @Test
    fun scaleLabelsMatchAndroidReference() {
        val resolver = DefaultAppTextResolver(LanguageMode.ENGLISH)
        assertEquals("Video scale: Fit", resolver.resolve(VideoScaleMode.FIT.textKey()))
        assertEquals("Video scale: Crop", resolver.resolve(VideoScaleMode.CROP.textKey()))
        assertEquals("Video scale: Stretch", resolver.resolve(VideoScaleMode.STRETCH.textKey()))
    }
}
