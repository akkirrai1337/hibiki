package org.akkirrai.hibiki.core.source

import org.junit.Assert.assertEquals
import org.junit.Test

class YummyIdMigrationTest {
    @Test
    fun `keeps plain numeric ids unchanged`() {
        assertEquals("12345", YummyIdMigration.normalizeTitleId("12345"))
    }

    @Test
    fun `keeps simple non composite ids unchanged`() {
        assertEquals("anime-go:bleach", YummyIdMigration.normalizeTitleId("anime-go:bleach"))
    }

    @Test
    fun `prefers explicit yummy id from composite payload`() {
        val rawId = "primary=yummy:321|animego=bleach-2004|yummy=654"

        assertEquals("654", YummyIdMigration.normalizeTitleId(rawId))
    }

    @Test
    fun `falls back to primary yummy id when explicit yummy field is missing`() {
        val rawId = "primary=yummy:321|animego=bleach-2004"

        assertEquals("321", YummyIdMigration.normalizeTitleId(rawId))
    }

    @Test
    fun `falls back to primary raw value when no numeric yummy id is available`() {
        val rawId = "primary=animego:bleach-2004|animego=bleach-2004"

        assertEquals("bleach-2004", YummyIdMigration.normalizeTitleId(rawId))
    }

    @Test
    fun `canonicalizes legacy source scoped id`() {
        assertEquals(
            "source:ani-liberty:10213",
            YummyIdMigration.normalizeTitleId("source:ANI_LIBERTY:10213"),
        )
    }

    @Test
    fun `keeps legacy scoped id as storage alias`() {
        assertEquals(
            listOf("source:ani-liberty:10213", "source:ANI_LIBERTY:10213"),
            YummyIdMigration.compatibleTitleIds("source:ani-liberty:10213"),
        )
    }
}
