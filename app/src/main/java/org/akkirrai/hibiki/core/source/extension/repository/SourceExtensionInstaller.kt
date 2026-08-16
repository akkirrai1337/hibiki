package org.akkirrai.hibiki.core.source.extension.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.copyTo
import java.io.File
import java.security.MessageDigest

class SourceExtensionInstaller(
    private val androidContext: Context,
    private val client: HttpClient,
) {
    suspend fun downloadAndVerify(entry: SourceRepositoryEntry): Result<File> = runCatching {
        val downloadsDirectory = File(androidContext.cacheDir, "source-downloads").apply { mkdirs() }
        val destination = File(downloadsDirectory, "${entry.packageName}-${entry.version}.apk")

        val response: HttpResponse = client.get(entry.apkUrl)
        check(response.status.isSuccess()) { "Download failed for ${entry.id}: ${response.status}" }
        destination.outputStream().use { output ->
            response.bodyAsChannel().copyTo(output)
        }

        val actualSha256 = destination.inputStream().use(::sha256Hex)
        check(actualSha256.equals(entry.sha256, ignoreCase = true)) {
            destination.delete()
            "Checksum mismatch for ${entry.id}: expected ${entry.sha256}, got $actualSha256"
        }

        destination
    }

    fun requestInstall(apkFile: File) {
        val authority = "${androidContext.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(androidContext, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        androidContext.startActivity(intent)
    }

    private fun sha256Hex(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }
}
