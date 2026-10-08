package eu.kanade.tachiyomi.network.interceptor

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toDuration

@Deprecated("Use the version with kotlin.time APIs instead.")
fun OkHttpClient.Builder.rateLimitHost(
    httpUrl: HttpUrl,
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS,
): OkHttpClient.Builder = addInterceptor(
    RateLimitInterceptor(httpUrl.host, permits, period.toDuration(unit.toDurationUnit())),
)

fun OkHttpClient.Builder.rateLimitHost(
    httpUrl: HttpUrl,
    permits: Int,
    period: Duration = 1.seconds,
): OkHttpClient.Builder = addInterceptor(RateLimitInterceptor(httpUrl.host, permits, period))

fun OkHttpClient.Builder.rateLimitHost(
    url: String,
    permits: Int,
    period: Duration = 1.seconds,
): OkHttpClient.Builder = rateLimitHost(url.toHttpUrl(), permits, period)
