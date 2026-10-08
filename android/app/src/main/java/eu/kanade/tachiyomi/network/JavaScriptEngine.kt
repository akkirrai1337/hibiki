package eu.kanade.tachiyomi.network

import android.content.Context
import app.cash.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Evaluates JavaScript for sources (extensions-lib 14). Each call gets a fresh, short-lived QuickJS runtime. */
@Suppress("unused_parameter")
class JavaScriptEngine(context: Context) {
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> evaluate(script: String): T = withContext(Dispatchers.Default) {
        withTimeout(EVALUATION_TIMEOUT_MS) {
            QuickJs.create().use { engine -> engine.evaluate(script) as T }
        }
    }

    private companion object {
        const val EVALUATION_TIMEOUT_MS = 20_000L
    }
}
