package org.akkirrai.hibiki.core.source.extension

import android.content.Context

object ApkExtensionTrustStore {
    fun isTrusted(context: Context, packageName: String, signingFingerprint: String): Boolean =
        preferences(context).getString(packageName, null) == signingFingerprint

    fun trust(context: Context, packageName: String, signingFingerprint: String) {
        preferences(context).edit().putString(packageName, signingFingerprint).apply()
    }

    fun forget(context: Context, packageName: String) {
        preferences(context).edit().remove(packageName).apply()
        origins(context).edit().remove(packageName).remove(PENDING_PREFIX + packageName).apply()
    }

    /**
     * Where an extension's trust comes from: a repository the user added, or the user's own say-so.
     * Trust that came from a repository lasts only while that repository is still added, so removing
     * a repository leaves its extensions installed but untrusted, as in Aniyomi.
     */
    fun setOrigin(context: Context, packageName: String, repositoryUrl: String?) {
        origins(context).edit().putString(packageName, repositoryUrl.orEmpty()).apply()
    }

    /** Remembers which repository an APK was downloaded from until Android confirms the install. */
    fun setPendingOrigin(context: Context, packageName: String, repositoryUrl: String) {
        origins(context).edit().putString(PENDING_PREFIX + packageName, repositoryUrl).apply()
    }

    fun commitPendingOrigin(context: Context, packageName: String) {
        val pending = origins(context).getString(PENDING_PREFIX + packageName, null)
        origins(context).edit().remove(PENDING_PREFIX + packageName).apply()
        setOrigin(context, packageName, pending)
    }

    /** Extensions trusted before origins were recorded, and ones the user trusted, are not tied to a repository. */
    fun isOriginTrusted(context: Context, packageName: String, configuredRepositories: Collection<String>): Boolean {
        val origin = origins(context).getString(packageName, null) ?: return true
        return origin.isEmpty() || origin in configuredRepositories
    }

    private fun origins(context: Context) =
        context.applicationContext.getSharedPreferences(ORIGINS_NAME, Context.MODE_PRIVATE)

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "aniyomi_extension_trust"
    private const val ORIGINS_NAME = "aniyomi_extension_origin"
    private const val PENDING_PREFIX = "pending:"
}
