package eu.kanade.tachiyomi.network

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import rx.Observable
import rx.Subscriber
import rx.Subscription
import rx.subscriptions.Subscriptions
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun Call.asObservable(): Observable<Response> = Observable.create { subscriber: Subscriber<in Response> ->
    val call = this
    subscriber.add(Subscriptions.create { call.cancel() })
    call.enqueue(
        object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (!subscriber.isUnsubscribed) subscriber.onError(error)
            }

            override fun onResponse(call: Call, response: Response) {
                if (subscriber.isUnsubscribed) {
                    response.close()
                    return
                }
                try {
                    subscriber.onNext(response)
                } catch (error: LinkageError) {
                    // A third-party extension may reference a host library/API class we do not
                    // provide. RxJava treats LinkageError as fatal and otherwise lets it escape
                    // this OkHttp callback, terminating Hibiki's process.
                    response.close()
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onError(
                            IllegalStateException("The anime extension is missing a host runtime dependency", error),
                        )
                    }
                    return
                }
                if (!subscriber.isUnsubscribed) subscriber.onCompleted() else response.close()
            }
        },
    )
}

fun Call.asObservableSuccess(): Observable<Response> = asObservable().map { response ->
    if (response.isSuccessful) response else {
        val code = response.code
        response.close()
        throw HttpException(code)
    }
}

@OptIn(InternalCoroutinesApi::class)
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    val call = this
    call.enqueue(
        object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                val token = continuation.tryResumeWithException(error)
                if (token != null) continuation.completeResume(token)
            }

            override fun onResponse(call: Call, response: Response) {
                val token = continuation.tryResume(response)
                if (token != null) continuation.completeResume(token) else response.close()
            }
        },
    )
    continuation.invokeOnCancellation { call.cancel() }
}

suspend fun Call.awaitSuccess(): Response {
    val response = await()
    if (response.isSuccessful) return response
    val code = response.code
    response.close()
    throw HttpException(code)
}

class HttpException(val code: Int) : IllegalStateException("HTTP error $code")
