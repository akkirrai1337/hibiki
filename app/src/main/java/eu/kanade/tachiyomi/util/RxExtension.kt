package eu.kanade.tachiyomi.util

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Observable
import rx.Subscriber
import rx.Subscription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** RxJava 1 bridge used by Aniyomi's suspend catalogue defaults. */
@OptIn(InternalCoroutinesApi::class)
suspend fun <T> Observable<T>.awaitSingle(): T = suspendCancellableCoroutine { continuation ->
    val subscription: Subscription = single().subscribe(
        object : Subscriber<T>() {
            override fun onStart() = request(1)

            override fun onNext(value: T) {
                continuation.resume(value)
            }

            override fun onCompleted() {
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("Observable completed without a value"))
                }
            }

            override fun onError(error: Throwable) {
                val token = continuation.tryResumeWithException(error)
                if (token != null) continuation.completeResume(token)
            }
        },
    )
    continuation.invokeOnCancellation { subscription.unsubscribe() }
}
