package org.akkirrai.hibiki.core.source.extension.repository

import android.content.Context
import android.content.pm.PackageManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

class SourceRepositoryClient(
    private val client: HttpClient,
) {
    suspend fun fetchIndex(): Result<SourceRepositoryIndex> = runCatching {
        val response: HttpResponse = client.get(SOURCE_REPOSITORY_INDEX_URL)
        check(response.status.isSuccess()) { "Repository index request failed: ${response.status}" }
        response.body<SourceRepositoryIndex>()
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
