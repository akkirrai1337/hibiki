package eu.kanade.tachiyomi.network

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * System DNS first; when it cannot resolve a host, ask public DNS-over-HTTPS resolvers.
 *
 * Some networks poison or drop lookups for streaming CDNs and embed hosts (the name resolves
 * fine elsewhere). Extensions swallow the resulting UnknownHostException and just return no
 * videos, so the failure would otherwise be invisible. The resolvers are reached by fixed IP,
 * so no DNS is needed to bootstrap them.
 */
internal class FallbackDns : Dns {
    private val resolvers: List<DnsOverHttps> by lazy {
        val bootstrap = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        listOf(
            "https://cloudflare-dns.com/dns-query" to listOf("1.1.1.1", "1.0.0.1"),
            "https://dns.google/dns-query" to listOf("8.8.8.8", "8.8.4.4"),
        ).map { (url, addresses) ->
            DnsOverHttps.Builder()
                .client(bootstrap)
                .url(url.toHttpUrl())
                .bootstrapDnsHosts(addresses.map(InetAddress::getByName))
                .build()
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val systemFailure = try {
            return Dns.SYSTEM.lookup(hostname)
        } catch (error: UnknownHostException) {
            error
        }
        for (resolver in resolvers) {
            val addresses = try {
                resolver.lookup(hostname)
            } catch (_: Exception) {
                continue
            }
            if (addresses.isNotEmpty()) return addresses
        }
        throw systemFailure
    }
}
