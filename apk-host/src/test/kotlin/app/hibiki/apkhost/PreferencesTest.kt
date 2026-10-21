package app.hibiki.apkhost

import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SharedPreferences on disk, and the settings screen an extension builds on top of them. */
class PreferencesTest {
    private val dir = Files.createTempDirectory("apk-host-prefs").toFile()

    @Test
    fun `values keep their types across a restart`() {
        val file = dir.resolve("p.json")
        FilePreferences(file).edit()
            .putString("s", "text").putInt("i", 7).putLong("l", 1L shl 40).putFloat("f", 1.5f)
            .putBoolean("b", true).putStringSet("set", linkedSetOf("a", "b")).commit()
        val again = FilePreferences(file)
        assertEquals("text", again.getString("s", null))
        assertEquals(7, again.getInt("i", 0))
        assertEquals(1L shl 40, again.getLong("l", 0))
        assertEquals(1.5f, again.getFloat("f", 0f))
        assertTrue(again.getBoolean("b", false))
        assertEquals<Set<String>?>(setOf("a", "b"), again.getStringSet("set", null))
        assertEquals("fallback", again.getString("missing", "fallback"))
    }

    @Test
    fun `a value read with the wrong type fails like it does on Android`() {
        val prefs = FilePreferences(dir.resolve("t.json"))
        prefs.edit().putString("k", "x").commit()
        kotlin.test.assertFailsWith<ClassCastException> { prefs.getInt("k", 0) }
    }

    @Test
    fun `remove clear and null values`() {
        val prefs = FilePreferences(dir.resolve("r.json"))
        prefs.edit().putString("a", "1").putString("b", "2").putString("c", "3").commit()
        prefs.edit().remove("a").putString("b", null).commit()
        assertFalse(prefs.contains("a"))
        assertFalse(prefs.contains("b"))
        assertTrue(prefs.contains("c"))
        prefs.edit().clear().commit()
        assertTrue(prefs.all.isEmpty())
        assertTrue(FilePreferences(dir.resolve("r.json")).all.isEmpty())
    }

    @Test
    fun `change listeners hear only real changes`() {
        val prefs = FilePreferences(dir.resolve("l.json"))
        val heard = mutableListOf<String>()
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key -> heard += key }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        prefs.edit().putString("a", "1").commit()
        prefs.edit().putString("a", "1").commit()
        prefs.edit().putString("a", "2").putBoolean("b", true).commit()
        assertEquals(listOf("a", "a", "b"), heard)
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        prefs.edit().putString("a", "3").commit()
        assertEquals(3, heard.size)
    }

    @Test
    fun `a damaged file starts empty instead of failing`() {
        val file = dir.resolve("bad.json").also { it.writeText("{ not json") }
        val prefs = FilePreferences(file)
        assertTrue(prefs.all.isEmpty())
        prefs.edit().putString("k", "v").commit()
        assertEquals("v", FilePreferences(file).getString("k", null))
    }

    @Test
    fun `file names made from a preferences name cannot leave the directory`() {
        assertEquals("source_123", HostContext.safeFileName("source_123"))
        assertEquals("_.._etc_passwd", HostContext.safeFileName("../../etc/passwd"))
        assertEquals("_x", HostContext.safeFileName("..\\x"))
        assertEquals("_", HostContext.safeFileName(".."))
    }

    private fun screen(name: String = "source_1"): Pair<androidx.preference.PreferenceScreen, android.content.SharedPreferences> {
        val context = HostContext(dir.resolve("data-$name").apply { mkdirs() })
        val manager = PreferenceManager(context).apply { sharedPreferencesName = name }
        return manager.createPreferenceScreen(context) to manager.sharedPreferences
    }

    @Test
    fun `a list preference applies its default once and keeps what is stored afterwards`() {
        val (screen, prefs) = screen()
        val context = screen.context
        screen.addPreference(ListPreference(context).apply {
            key = "quality"
            setEntries(arrayOf("Low", "High"))
            setEntryValues(arrayOf("360", "720"))
            setDefaultValue("720")
            summary = "Now: %s"
        })
        val list = screen.getPreference(0) as ListPreference
        assertEquals("720", list.value)
        assertEquals("720", prefs.getString("quality", null))
        assertEquals("High", list.entry)
        assertEquals("Now: High", list.summary)
        assertEquals(1, list.findIndexOfValue("720"))
        assertEquals(-1, list.findIndexOfValue("nope"))

        // Stored values win over the default on the next screen.
        prefs.edit().putString("quality", "360").commit()
        val (again) = screen()
        again.addPreference(ListPreference(again.context).apply { key = "quality"; setEntries(arrayOf("Low", "High")); setEntryValues(arrayOf("360", "720")); setDefaultValue("720") })
        assertEquals("360", (again.getPreference(0) as ListPreference).value)
    }

    @Test
    fun `switch and text preferences persist what is set`() {
        val (screen, prefs) = screen("source_2")
        val context = screen.context
        screen.addPreference(SwitchPreferenceCompat(context).apply { key = "dub"; setDefaultValue(true) })
        screen.addPreference(EditTextPreference(context).apply { key = "host"; setDefaultValue("example.com") })
        val switch = screen.getPreference(0) as SwitchPreferenceCompat
        val text = screen.getPreference(1) as EditTextPreference
        assertTrue(switch.isChecked)
        assertEquals("example.com", text.text)
        switch.isChecked = false
        text.text = "other.org"
        assertFalse(prefs.getBoolean("dub", true))
        assertEquals("other.org", prefs.getString("host", null))
    }

    @Test
    fun `a change listener can veto a change before it is stored`() {
        val (screen, prefs) = screen("source_3")
        val preference = EditTextPreference(screen.context).apply {
            key = "url"
            setDefaultValue("a")
            setOnPreferenceChangeListener { _, value -> value != "forbidden" }
        }
        screen.addPreference(preference)
        assertTrue(preference.callChangeListener("fine"))
        assertFalse(preference.callChangeListener("forbidden"))
        assertEquals("a", prefs.getString("url", null))
    }

    @Test
    fun `multi select preference stores a set`() {
        val (screen, prefs) = screen("source_4")
        screen.addPreference(MultiSelectListPreference(screen.context).apply {
            key = "hosters"
            setEntries(arrayOf("A", "B"))
            setEntryValues(arrayOf("a", "b"))
            setDefaultValue(setOf("a"))
        })
        val multi = screen.getPreference(0) as MultiSelectListPreference
        assertEquals(setOf("a"), multi.values)
        multi.setValues(setOf("a", "b"))
        assertEquals<Set<String>?>(setOf("a", "b"), prefs.getStringSet("hosters", null))
        assertNull(prefs.getString("unrelated", null))
    }

    @Test
    fun `findPreference looks inside nested groups`() {
        val (screen, _) = screen("source_5")
        val category = androidx.preference.PreferenceCategory(screen.context).apply { title = "Video" }
        screen.addPreference(category)
        category.addPreference(SwitchPreferenceCompat(screen.context).apply { key = "inner" })
        assertEquals("inner", screen.findPreference("inner")?.key)
        assertNull(screen.findPreference("absent"))
    }
}
