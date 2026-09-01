package org.akkirrai.beakokit.extension

import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Scriptable

/**
 * Bounds how long a scripted extension's JS can run before it's forcibly killed.
 *
 * Nothing in [RhinoExtensionRuntime] previously stopped a runaway script - a buggy or malicious
 * `while (true) {}` in `Provider.search` (or any other entry point) would hang the calling call
 * forever, permanently removing one [RhinoRuntimePool] slot (there are only [RUNTIME_POOL_SIZE]
 * per extension) until the app process restarts. This only guards CPU-bound loops: a slow/hanging
 * network call inside `fetch()`/`challenge()`/`browserFetch()` isn't interpreter instructions, so
 * it isn't counted here - that's bounded separately by [org.akkirrai.beakokit.http.BeakoKitHttpPolicy]'s
 * own connect/request/socket timeouts on the shared `HttpClient`.
 *
 * This is Rhino's own documented mechanism for the job (see `ContextFactory`'s class doc): a
 * custom [Context] subclass records when the current top-level script call started,
 * [HibikiContextFactory.observeInstructionCount] is polled by the interpreter every
 * [instructionThreshold] bytecode instructions, and once [scriptTimeoutMillis] has elapsed it
 * throws a plain [Error] - not an [Exception] - specifically so a script's own `try { while(true){} }
 * catch (e) { }` can't swallow the interrupt and keep looping.
 *
 * [scriptTimeoutMillis]'s default is deliberately generous rather than tight: it only needs to
 * guarantee eventual termination, not react quickly, and a real `Provider` call can legitimately
 * chain several sequential `fetch()`s (e.g. AniKappa's source x dubbing group discovery, or
 * AnimePahe re-solving a Cloudflare challenge) - each already individually allowed up to
 * `requestTimeoutMillis`/`socketTimeoutMillis` (30s) plus up to `maxRetries` retries honoring a
 * server's `Retry-After` (up to `maxRetryAfterMillis`, another 30s each) by
 * [org.akkirrai.beakokit.http.BeakoKitHttpPolicy]. A single slow-but-legitimate request can
 * already approach 90s under that policy, so anything close to Rhino's own 10s example would kill
 * real extensions on a bad network long before the HTTP layer's own timeouts would.
 */
/**
 * Deliberately a [java.lang.Error], not a [RuntimeException] - Rhino's interpreter only lets a
 * script's own `try`/`catch`/`finally` intercept ordinary script exceptions, precisely so a
 * misbehaving payload can't wrap its infinite loop in a `catch` and swallow the interrupt. It's
 * still caught (and turned into an ordinary [org.akkirrai.beakokit.api.SourceException]) one frame
 * up in [RhinoExtensionRuntime.callRaw], which sits outside the interpreter entirely.
 */
internal class RhinoScriptTimeoutError(elapsedMillis: Long) : Error(
    "Script execution exceeded ${scriptTimeoutMillis}ms (ran for ${elapsedMillis}ms)",
)

private class HibikiScriptContext(factory: ContextFactory) : Context(factory) {
    @Volatile
    var topCallStartedAtMillis: Long = 0L
}

private object HibikiContextFactory : ContextFactory() {
    override fun makeContext(): Context =
        HibikiScriptContext(this).apply { setInstructionObserverThreshold(instructionThreshold) }

    override fun observeInstructionCount(cx: Context, instructionCount: Int) {
        val hibikiContext = cx as? HibikiScriptContext ?: return
        val elapsed = System.currentTimeMillis() - hibikiContext.topCallStartedAtMillis
        if (elapsed > scriptTimeoutMillis) {
            throw RhinoScriptTimeoutError(elapsed)
        }
    }

    override fun doTopCall(
        callable: Callable,
        cx: Context,
        scope: Scriptable,
        thisObj: Scriptable,
        args: Array<Any?>,
    ): Any {
        (cx as? HibikiScriptContext)?.topCallStartedAtMillis = System.currentTimeMillis()
        return super.doTopCall(callable, cx, scope, thisObj, args)
    }
}

/** Installs [HibikiContextFactory] as Rhino's global factory exactly once; safe to call from every [RhinoExtensionRuntime]. */
internal fun installRhinoTimeoutGuard() {
    if (ContextFactory.hasExplicitGlobal()) return
    try {
        ContextFactory.initGlobal(HibikiContextFactory)
    } catch (_: IllegalStateException) {
        // Lost a race with another thread's first RhinoExtensionRuntime (each extension's
        // RhinoRuntimePool is its own lazily-created pool, so two different extensions used for
        // the first time concurrently can both reach here before either has installed the
        // factory) - initGlobal() is synchronized internally, so exactly one caller wins and the
        // other lands here; either way the factory ends up installed, which is all this needs.
    }
}

/**
 * `var`, not `const val`, purely so [RhinoTimeoutGuardTest] can dial these down to make a
 * runaway-script test finish in milliseconds instead of actually waiting out the production
 * timeout - every real [RhinoExtensionRuntime] uses these defaults.
 */
internal var instructionThreshold = 10_000
internal var scriptTimeoutMillis = 120_000L
