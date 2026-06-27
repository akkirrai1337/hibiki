package org.akkirrai.hibiki.core.log

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val MAX_ENTRIES = 600
    private val entries = ArrayDeque<String>(MAX_ENTRIES)
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun d(tag: String, message: String) {
        val sanitized = sanitize(message)
        Log.d(tag, sanitized)
        append("D", tag, sanitized)
    }

    fun i(tag: String, message: String) {
        val sanitized = sanitize(message)
        Log.i(tag, sanitized)
        append("I", tag, sanitized)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val sanitized = sanitize(message)
        Log.w(tag, sanitized, throwable)
        append("W", tag, sanitized, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val sanitized = sanitize(message)
        Log.e(tag, sanitized, throwable)
        append("E", tag, sanitized, throwable)
    }

    fun shareLogs(context: Context): Result<Unit> = runCatching {
        val appContext = context.applicationContext
        val file = exportToFile(appContext)
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file,
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Hibiki logs")
            clipData = ClipData.newUri(appContext.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "Export Hibiki logs").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context === appContext) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }

    private fun exportToFile(context: Context): File {
        val now = Date()
        val filename = "hibiki-logs-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)}.txt"
        val exportDirectory = File(context.cacheDir, "logs").apply {
            check(exists() || mkdirs()) { "Could not create log export directory" }
        }
        exportDirectory.listFiles()
            ?.filter { it.isFile && it.name.startsWith("hibiki-logs-") }
            ?.sortedByDescending(File::lastModified)
            ?.drop(MAX_EXPORTED_FILES - 1)
            ?.forEach(File::delete)
        val file = File(exportDirectory, filename)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val header = buildString {
            appendLine("Hibiki log export")
            appendLine("generated_at=${formatTimestamp(now)}")
            appendLine("app_id=${context.packageName}")
            appendLine("version_name=${packageInfo.versionName.orEmpty()}")
            appendLine("version_code=${packageInfo.longVersionCode}")
            appendLine("android_sdk=${Build.VERSION.SDK_INT}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("brand=${Build.BRAND}")
            appendLine()
        }
        val body = synchronized(entries) {
            entries.joinToString(separator = System.lineSeparator())
        }
        file.writeText(header + body)
        return file
    }

    private fun append(
        level: String,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        val line = buildString {
            append(formatTimestamp(Date()))
            append(" ")
            append(level)
            append("/")
            append(tag)
            append(": ")
            append(message)
            throwable?.let {
                append(" | ")
                append(sanitizeThrowable(it))
            }
        }
        synchronized(entries) {
            if (entries.size >= MAX_ENTRIES) {
                entries.removeFirst()
            }
            entries.addLast(line)
        }
    }

    private fun sanitizeThrowable(throwable: Throwable): String {
        return buildString {
            append(throwable::class.java.simpleName)
            throwable.message?.takeIf(String::isNotBlank)?.let {
                append(": ")
                append(sanitize(it))
            }
            throwable.stackTrace
                .asSequence()
                .filter { it.className.startsWith("org.akkirrai.hibiki") }
                .take(8)
                .forEach { frame ->
                    append(" <- ")
                    append(frame.className.substringAfterLast('.'))
                    append(".")
                    append(frame.methodName)
                    append(":")
                    append(frame.lineNumber)
                }
        }
    }

    private fun sanitize(value: String): String {
        return value
            .replace(Regex("""(?i)(authorization[:=]\s*)([^\s,;]+)"""), "$1<redacted>")
            .replace(Regex("""(?i)(set-cookie[:=]\s*[^=]+=)([^;,\s]+)"""), "$1<redacted>")
            .replace(Regex("""(?i)(yummy_token=)([^;,\s]+)"""), "$1<redacted>")
            .replace(Regex("""(?i)(access_token=)([^&\s]+)"""), "$1<redacted>")
            .replace(Regex("""(?i)(refresh_token=)([^&\s]+)"""), "$1<redacted>")
            .replace(Regex("""(?i)([?&](?:token|auth|authorization|access_token|refresh_token|key|sign|hash|d_sign|pd_sign|ref_sign)=)([^&\s]+)"""), "$1<redacted>")
            .replace(Regex("""(?i)(Yummy\s+)([A-Za-z0-9._-]+)"""), "$1<redacted>")
    }

    private fun formatTimestamp(date: Date): String = synchronized(timestampFormat) {
        timestampFormat.format(date)
    }

    private const val MAX_EXPORTED_FILES = 5
}
