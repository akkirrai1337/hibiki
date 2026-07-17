package org.akkirrai.hibiki.core.source

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchStateStorageKeyTest {
    @Test
    fun `parses source scoped progress key without truncating title id`() {
        assertEquals(
            ProgressStorageKey(
                titleId = "source:ANI_LIBERTY:10213",
                episodeId = "episode-5",
            ),
            parseProgressStorageKey(
                "progress_source:ANI_LIBERTY:10213|episode|episode-5",
            ),
        )
    }

    @Test
    fun `reads source scoped key written by legacy colon format`() {
        assertEquals(
            ProgressStorageKey(
                titleId = "source:ANI_LIBERTY:10213",
                episodeId = "episode-5",
            ),
            parseProgressStorageKey("progress_source:ANI_LIBERTY:10213:episode-5"),
        )
    }

    @Test
    fun `keeps legacy unscoped progress readable`() {
        assertEquals(
            ProgressStorageKey(titleId = "42", episodeId = "7"),
            parseProgressStorageKey("progress_42:7"),
        )
    }
}
