package org.akkirrai.hibiki.core.download

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.model.PlaybackSubtitle

/**
 * Subtitle tracks are tiny text files that Media3's segment cache never sees, so an offline episode
 * keeps its own copies on disk and plays them back as `file:` URIs.
 */
object OfflineSubtitleStore {
    private const val TAG = "OfflineSubtitles"
    private const val DIRECTORY = "offline_subtitles"
    private const val MAX_BYTES = 5L * 1024 * 1024

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Downloads every track; a track that cannot be fetched is skipped rather than failing the episode. */
    fun download(
        context: Context,
        downloadId: String,
        subtitles: List<PlaybackSubtitle>,
        streamHeaders: Map<String, String>,
    ): List<PlaybackSubtitle> {
        val directory = directory(context, downloadId)
        directory.deleteRecursively()
        if (subtitles.isEmpty()) return emptyList()
        directory.mkdirs()
        return subtitles.mapIndexedNotNull { index, subtitle ->
            runCatching {
                val request = Request.Builder().url(subtitle.url).apply {
                    subtitle.headers.ifEmpty { streamHeaders }.forEach { (key, value) -> header(key, value) }
                }.build()
                val bytes = client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    response.body.source().let { source ->
                        source.request(MAX_BYTES + 1)
                        check(source.buffer.size <= MAX_BYTES) { "subtitle too large" }
                        source.readByteArray()
                    }
                }
                val file = File(directory, "$index.${extensionFor(subtitle.url, bytes)}")
                file.writeBytes(bytes)
                subtitle.copy(url = Uri.fromFile(file).toString(), headers = emptyMap())
            }.onFailure { error ->
                AppLogger.e(TAG, "Failed to save subtitle ${subtitle.label ?: subtitle.language}", error)
            }.getOrNull()
        }
    }

    fun remove(context: Context, downloadId: String) {
        directory(context, downloadId).deleteRecursively()
    }

    private fun directory(context: Context, downloadId: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(downloadId.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(File(context.applicationContext.filesDir, DIRECTORY), hash)
    }

    private fun extensionFor(url: String, bytes: ByteArray): String {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".vtt") -> "vtt"
            path.endsWith(".ass") -> "ass"
            path.endsWith(".ssa") -> "ssa"
            path.endsWith(".srt") -> "srt"
            else -> {
                val head = bytes.copyOf(minOf(bytes.size, 2048)).toString(Charsets.UTF_8).trimStart('﻿', ' ', '\n', '\r')
                when {
                    head.startsWith("WEBVTT") -> "vtt"
                    head.startsWith("[Script Info]", ignoreCase = true) -> "ass"
                    Regex("""\d,\d{3}\s*-->""").containsMatchIn(head) -> "srt"
                    else -> "vtt"
                }
            }
        }
    }
}
