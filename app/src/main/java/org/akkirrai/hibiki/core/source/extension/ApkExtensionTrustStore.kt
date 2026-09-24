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
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "aniyomi_extension_trust"
}
