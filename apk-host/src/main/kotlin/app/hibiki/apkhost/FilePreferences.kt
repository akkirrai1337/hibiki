package app.hibiki.apkhost

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * SharedPreferences kept in one JSON file, each value tagged with its type so an int read back is
 * still an int (a settings screen and an extension must agree on it, as on Android where a wrong
 * type is a ClassCastException). Every change is written out at once - these files are small, and
 * a source's setting must survive the process being killed.
 */
class FilePreferences(private val file: File) : SharedPreferences {
    private val lock = Any()
    private val values = LinkedHashMap<String, Any>()
    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

    init {
        if (file.isFile) {
            runCatching {
                val json = JSONObject(file.readText())
                for (key in json.keys()) {
                    val entry = json.getJSONObject(key)
                    decode(entry.getString("t"), entry.get("v"))?.let { values[key] = it }
                }
            }
            // A damaged file starts empty rather than refusing the source: its settings reset, it still works.
        }
    }

    private fun decode(type: String, raw: Any): Any? = when (type) {
        "s" -> raw.toString()
        "b" -> raw as Boolean
        "i" -> (raw as Number).toInt()
        "l" -> (raw as Number).toLong()
        "f" -> (raw as Number).toFloat()
        "set" -> (raw as JSONArray).let { array -> LinkedHashSet<String>().apply { for (i in 0 until array.length()) add(array.getString(i)) } }
        else -> null
    }

    private fun encode(value: Any): JSONObject = when (value) {
        is String -> JSONObject().put("t", "s").put("v", value)
        is Boolean -> JSONObject().put("t", "b").put("v", value)
        is Int -> JSONObject().put("t", "i").put("v", value)
        is Long -> JSONObject().put("t", "l").put("v", value)
        is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
        is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(value.map { it.toString() }))
        else -> error("Unsupported preference value: ${value.javaClass}")
    }

    private fun persist() {
        val json = JSONObject()
        values.forEach { (key, value) -> json.put(key, encode(value)) }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(json.toString())
        if (!temporary.renameTo(file)) {
            file.delete()
            temporary.renameTo(file)
        }
    }

    override fun getAll(): MutableMap<String, *> = synchronized(lock) { LinkedHashMap(values) }

    override fun getString(key: String, defValue: String?): String? = synchronized(lock) { values[key] as String? ?: defValue }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        synchronized(lock) { (values[key] as Set<String>?)?.let { LinkedHashSet(it) } ?: defValues }

    override fun getInt(key: String, defValue: Int): Int = synchronized(lock) { values[key] as Int? ?: defValue }

    override fun getLong(key: String, defValue: Long): Long = synchronized(lock) { values[key] as Long? ?: defValue }

    override fun getFloat(key: String, defValue: Float): Float = synchronized(lock) { values[key] as Float? ?: defValue }

    override fun getBoolean(key: String, defValue: Boolean): Boolean = synchronized(lock) { values[key] as Boolean? ?: defValue }

    override fun contains(key: String): Boolean = synchronized(lock) { key in values }

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners -= listener
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, Any?>()
        private var clear = false

        private fun put(key: String, value: Any?) = apply { changes[key] = value }

        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = put(key, values?.let { LinkedHashSet(it) })
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String) = put(key, null)
        override fun clear() = apply { clear = true }

        override fun commit(): Boolean {
            val changed = mutableListOf<String>()
            synchronized(lock) {
                if (clear) { changed += values.keys; values.clear() }
                for ((key, value) in changes) {
                    // Putting null removes the key, as on Android.
                    if (value == null) { if (values.remove(key) != null) changed += key }
                    else if (values.put(key, value) != value) changed += key
                }
                if (changed.isNotEmpty() || clear) persist()
            }
            changed.distinct().forEach { key -> listeners.forEach { it.onSharedPreferenceChanged(this@FilePreferences, key) } }
            return true
        }

        override fun apply() { commit() }
    }
}
