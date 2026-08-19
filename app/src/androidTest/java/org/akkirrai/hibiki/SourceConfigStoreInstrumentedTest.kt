package org.akkirrai.hibiki

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.api.SourceConfigState
import org.akkirrai.beakokit.api.SourceConfigStateException
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.core.source.AndroidSourceConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceConfigStoreInstrumentedTest {
    @Test
    fun configSurvivesStoreRecreationAndKeepsSourceNamespacesSeparate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = SourceId("config-source-one")
        val second = SourceId("config-source-two")
        val firstState = SourceConfigState(
            values = mapOf("base_url" to "https://one.test"),
            secrets = mapOf("token" to "one-secret"),
        )
        val secondState = SourceConfigState(values = mapOf("mode" to "mirror"))

        AndroidSourceConfigStore(context).also { store ->
            store.persistAtomically(first, firstState)
            store.persistAtomically(second, secondState)
        }

        val recreated = AndroidSourceConfigStore(context)
        assertEquals(firstState, recreated.load(first))
        assertEquals(secondState, recreated.load(second))
        val third = SourceId("config-source-three")
        assertNull(recreated.loadOrNull(third))
        recreated.persistAtomically(third, SourceConfigState())
        assertEquals(SourceConfigState(), recreated.loadOrNull(third))

        recreated.remove(first)
        assertEquals(SourceConfigState(), AndroidSourceConfigStore(context).load(first))
        recreated.remove(second)
        recreated.remove(third)
    }

    @Test
    fun corruptedConfigStateUsesTypedFailure() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceId = SourceId("config-source-corrupted")
        val preferences = context.getSharedPreferences("beakokit_external_sources", 0)
        preferences.edit()
            .putString("config.${sourceId.value}", Json.encodeToString("not-an-object"))
            .commit()

        try {
            assertThrows(SourceConfigStateException::class.java) {
                AndroidSourceConfigStore(context).load(sourceId)
            }
        } finally {
            preferences.edit().remove("config.${sourceId.value}").commit()
        }
    }
}
