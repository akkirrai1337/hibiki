package org.akkirrai.hibiki.core.source.extension.repository

import android.content.Context
import android.content.pm.PackageManager
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

class SourceRepositoryClient(
    private val client: HttpClient,
) {
    suspend fun fetchIndex(): Result<SourceRepositoryIndex> = runCatching {
        val response: HttpResponse = client.get(SOURCE_REPOSITORY_INDEX_URL)
        check(response.status.isSuccess()) { "Repository index request failed: ${response.status}" }
        // raw.githubusercontent.com serves .json files as text/plain, so ktor's
        // ContentNegotiation won't auto-deserialize the body -- decode it manually instead.
        JSON.decodeFromString(SourceRepositoryIndex.serializer(), response.bodyAsText())
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }

    /** Entries whose package is not currently installed on-device. */
    fun availableEntries(androidContext: Context, index: SourceRepositoryIndex): List<SourceRepositoryEntry> {
        val packageManager = androidContext.packageManager
        return index.sources.filterNot { entry -> packageManager.isPackageInstalled(entry.packageName) }
    }

    private fun PackageManager.isPackageInstalled(packageName: String): Boolean = runCatching {
        getPackageInfo(packageName, 0)
    }.isSuccess
}
