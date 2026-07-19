package org.akkirrai.hibiki.core.network

import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.WebSettings
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.akkirrai.beakokit.api.ChallengeSession
import org.akkirrai.beakokit.api.ChallengeSessionProvider
import org.akkirrai.beakokit.api.ChallengeSessionRequest
import org.akkirrai.beakokit.api.SourceUnavailableException

class AndroidChallengeSessionProvider(
    context: Context,
) : ChallengeSessionProvider {
    private val appContext = context.applicationContext

    override suspend fun acquire(request: ChallengeSessionRequest): ChallengeSession {
        val cached = withContext(Dispatchers.Main.immediate) { readSession(request) }
        if (!request.forceRefresh && cached != null) return cached

        return try {
            withTimeout(SESSION_TIMEOUT_MS) {
                val requestId = UUID.randomUUID().toString()
                val result = ChallengeSessionCoordinator.register(requestId, request)
                try {
                    withContext(Dispatchers.Main.immediate) {
                        if (request.forceRefresh) expireCompletionCookies(request)
                        appContext.startActivity(
                            Intent(appContext, ChallengeSessionActivity::class.java)
                                .putExtra(ChallengeSessionActivity.EXTRA_REQUEST_ID, requestId)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    result.await()
                } finally {
                    ChallengeSessionCoordinator.remove(requestId)
                }
            }
        } catch (_: TimeoutCancellationException) {
            throw SourceUnavailableException("Browser verification timed out")
        }
    }

    private fun readSession(request: ChallengeSessionRequest): ChallengeSession? {
        val cookies = parseBrowserCookies(CookieManager.getInstance().getCookie(request.url))
        if (!cookies.keys.containsAll(request.requiredCookieNames)) return null
        return ChallengeSession(
            cookies = cookies,
            userAgent = WebSettings.getDefaultUserAgent(appContext),
        )
    }

    private fun expireCompletionCookies(request: ChallengeSessionRequest) {
        val cookieManager = CookieManager.getInstance()
        request.requiredCookieNames.forEach { name ->
            cookieManager.setCookie(
                request.url,
                "$name=; Max-Age=0; Path=/; Secure; SameSite=Lax",
            )
        }
        cookieManager.flush()
    }

    private companion object {
        const val SESSION_TIMEOUT_MS = 3 * 60 * 1_000L
    }
}

internal data class PendingChallengeSession(
    val request: ChallengeSessionRequest,
    val result: CompletableDeferred<ChallengeSession>,
)

internal object ChallengeSessionCoordinator {
    private val pending = ConcurrentHashMap<String, PendingChallengeSession>()

    fun register(id: String, request: ChallengeSessionRequest): CompletableDeferred<ChallengeSession> {
        val result = CompletableDeferred<ChallengeSession>()
        check(pending.putIfAbsent(id, PendingChallengeSession(request, result)) == null) {
            "Challenge request is already registered: $id"
        }
        return result
    }

    fun pending(id: String): PendingChallengeSession? = pending[id]

    fun complete(id: String, session: ChallengeSession) {
        pending[id]?.result?.complete(session)
    }

    fun cancel(id: String) {
        pending[id]?.result?.completeExceptionally(
            SourceUnavailableException("Browser verification was cancelled"),
        )
    }

    fun remove(id: String) {
        pending.remove(id)
    }
}
