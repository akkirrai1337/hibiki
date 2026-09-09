package org.akkirrai.hibiki.core.source.extension

import android.content.Context
import org.akkirrai.beakokit.api.context.ExtensionStorage

/**
 * Where a scripted extension's persistent values actually live on Android: one private
 * preferences file per source.
 *
 * Private app storage, not [androidx.security.crypto.EncryptedSharedPreferences]. The file is
 * already unreadable to other apps, and it sits on storage the device encrypts as a whole; the
 * Jetpack security library that would add a second layer on top is deprecated, and taking on a
 * deprecated dependency for it buys less than it costs. A rooted device defeats both.
 *
 * One file per source rather than one shared file with prefixed keys, so uninstalling a source can
 * take its token with it by deleting a file, with no chance of a prefix scan missing a key.
 */
class AndroidExtensionStorage(
    private val context: Context,
    private val sourceId: String,
) : ExtensionStorage {

    private val prefs by lazy {
        context.getSharedPreferences(fileNameFor(sourceId), Context.MODE_PRIVATE)
    }

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun set(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val PREFIX = "hibiki_extension_storage_"

        /** Source ids come from manifests, which the user can install from any repository - a
         * separator or a traversal in one would otherwise name a file outside its own store. */
        private fun fileNameFor(sourceId: String): String =
            PREFIX + sourceId.replace(Regex("[^A-Za-z0-9._-]"), "_")

        /** Called when a source is removed: a token for a source that is no longer installed is
         * only a secret nobody is watching. */
        fun clear(context: Context, sourceId: String) {
            context.getSharedPreferences(fileNameFor(sourceId), Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }
}
