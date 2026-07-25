package org.akkirrai.hibiki.shared.collection

import kotlin.test.Test
import kotlin.test.assertEquals

class GroupedItemsTest {
    @Test
    fun preservesKeyOrderAndEmptyGroups() {
        val result = groupItemsByKeys(
            items = listOf("ru-1", "en-1", "ru-2"),
            keys = listOf("en", "ru", "ja"),
            keyOf = { it.substringBefore('-') },
        )

        assertEquals(listOf("en", "ru", "ja"), result.keys.toList())
        assertEquals(listOf("en-1"), result["en"])
        assertEquals(listOf("ru-1", "ru-2"), result["ru"])
        assertEquals(emptyList(), result["ja"])
    }
}
