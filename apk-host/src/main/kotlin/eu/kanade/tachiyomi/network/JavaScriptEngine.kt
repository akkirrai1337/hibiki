package eu.kanade.tachiyomi.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Undefined
import android.content.Context as AndroidContext
import org.mozilla.javascript.Context as RhinoContext

/**
 * Evaluates JavaScript for sources (extensions-lib 14). Each call gets a fresh, short-lived Rhino
 * scope in the interpreter (no class generation, so a script cannot be slow to compile and nothing
 * is cached); the result comes back as QuickJS gave it: text, a number, a boolean, or null.
 */
@Suppress("unused_parameter")
class JavaScriptEngine(context: AndroidContext, private val timeoutMs: Long = EVALUATION_TIMEOUT_MS) {
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> evaluate(script: String): T = withContext(Dispatchers.Default) {
        withTimeout(timeoutMs) {
            runInterruptible { run(script) as T }
        }
    }

    private fun run(script: String): Any? {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        val factory = object : ContextFactory() {
            override fun makeContext(): RhinoContext = super.makeContext().apply {
                optimizationLevel = -1
                instructionObserverThreshold = 10_000
            }

            // A script that never ends is stopped here: the coroutine timeout cannot interrupt Rhino by itself.
            override fun observeInstructionCount(cx: RhinoContext, instructionCount: Int) {
                if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted) throw Error("The script ran out of time")
            }
        }
        return factory.call { cx ->
            val scope = cx.initSafeStandardObjects()
            convert(cx.evaluateString(scope, script, "extension", 1, null))
        }
    }

    private fun convert(value: Any?): Any? = when (value) {
        null, Undefined.instance -> null
        is Boolean -> value
        is ConsString -> value.toString()
        is CharSequence -> value.toString()
        is Number -> value.toDouble().let { d -> if (d == Math.rint(d) && d >= Int.MIN_VALUE && d <= Int.MAX_VALUE) d.toInt() else d }
        else -> RhinoContext.toString(value)
    }

    companion object {
        const val EVALUATION_TIMEOUT_MS = 20_000L
    }
}
