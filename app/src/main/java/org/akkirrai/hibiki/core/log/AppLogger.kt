package org.akkirrai.hibiki.core.log

import android.content.ClipData
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

object AppLogger {
    private const val LOG_EXPORT_TAG = "HibikiLogExport"
    private const val MAX_ENTRIES = 600
    private val entries = ArrayDeque<String>(MAX_ENTRIES)
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val installed = AtomicBoolean(false)
    private val startedActivities = AtomicInteger(0)
    private val contextValues = ConcurrentHashMap<String, String>()
    @Volatile
    private var previousUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        contextValues["package"] = appContext.packageName
        contextValues["process"] = processName()
        contextValues["pid"] = Process.myPid().toString()
        contextValues["sdk"] = Build.VERSION.SDK_INT.toString()
        contextValues["device"] = sanitize("${Build.MANUFACTURER} ${Build.MODEL}")
        contextValues["brand"] = sanitize(Build.BRAND)

        runCatching {
            val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            contextValues["version"] = sanitize(packageInfo.versionName.orEmpty())
            contextValues["versionCode"] = PackageInfoCompat.getLongVersionCode(packageInfo).toString()
        }.onFailure { throwable ->
            w(LOG_EXPORT_TAG, "Failed to collect package info", throwable)
        }

        (appContext as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    setContext("activity", activity.localClassName)
                    d("HibikiLifecycle", "activity created savedState=${savedInstanceState != null}")
                }

                override fun onActivityStarted(activity: Activity) {
                    val count = startedActivities.incrementAndGet()
                    setContext("activity", activity.localClassName)
                    setContext("foreground", (count > 0).toString())
                    d("HibikiLifecycle", "activity started visibleActivities=$count")
                }

                override fun onActivityResumed(activity: Activity) {
                    setContext("activity", activity.localClassName)
                    setContext("resumedActivity", activity.localClassName)
                    d("HibikiLifecycle", "activity resumed")
                }

                override fun onActivityPaused(activity: Activity) {
                    d("HibikiLifecycle", "activity paused")
                }

                override fun onActivityStopped(activity: Activity) {
                    val count = startedActivities.decrementAndGet().coerceAtLeast(0)
                    startedActivities.set(count)
                    setContext("foreground", (count > 0).toString())
                    d("HibikiLifecycle", "activity stopped visibleActivities=$count")
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) {
                    d("HibikiLifecycle", "activity destroyed")
                }
            }
        )

        previousUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e(
                tag = "HibikiCrash",
                message = "Uncaught exception on thread=${thread.name}",
                throwable = throwable,
            )
            previousUncaughtExceptionHandler?.uncaughtException(thread, throwable)
                ?: run {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
        }

        i("HibikiLogger", "Logger installed")
    }

    fun setContext(key: String, value: String?) {
        val normalizedKey = key.trim().takeIf(String::isNotBlank) ?: return
        if (value.isNullOrBlank()) {
            contextValues.remove(normalizedKey)
        } else {
            contextValues[normalizedKey] = sanitize(value)
        }
    }

    fun v(tag: String, message: String) {
        val sanitized = sanitize(message)
        Log.v(tag, sanitized)
        append("V", tag, sanitized)
    }

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
        d(LOG_EXPORT_TAG, "shareLogs: started, package=${appContext.packageName}")
        val file = runCatching {
            exportToFile(appContext)
        }.onFailure { throwable ->
            e(LOG_EXPORT_TAG, "shareLogs: failed to create export file", throwable)
        }.getOrThrow()
        d(
            LOG_EXPORT_TAG,
            "shareLogs: file created path=${file.absolutePath}, exists=${file.exists()}, size=${file.length()}",
        )
        val authority = "${appContext.packageName}.fileprovider"
        val uri = runCatching {
            FileProvider.getUriForFile(appContext, authority, file)
        }.onFailure { throwable ->
            e(LOG_EXPORT_TAG, "shareLogs: failed to get FileProvider uri, authority=$authority", throwable)
        }.getOrThrow()
        d(LOG_EXPORT_TAG, "shareLogs: uri=$uri")
        val logText = runCatching {
            file.readText()
        }.onFailure { throwable ->
            e(LOG_EXPORT_TAG, "shareLogs: failed to read exported log text", throwable)
        }.getOrThrow()
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, logText)
            putExtra(Intent.EXTRA_SUBJECT, "Hibiki logs")
            clipData = ClipData.newUri(appContext.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        grantReadAccess(context, uri, sendIntent)
        val chooser = Intent.createChooser(sendIntent, "Export Hibiki logs").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        d(LOG_EXPORT_TAG, "shareLogs: starting chooser")
        runCatching {
            context.startActivity(chooser)
        }.onFailure { throwable ->
            e(LOG_EXPORT_TAG, "shareLogs: failed to start chooser", throwable)
        }.getOrThrow()
        d(LOG_EXPORT_TAG, "shareLogs: chooser started")
        Unit
    }.onFailure { throwable ->
        e(LOG_EXPORT_TAG, "shareLogs: export failed", throwable)
    }

    private fun exportToFile(context: Context): File {
        val now = Date()
        val filename = "hibiki-logs-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)}.txt"
        val exportDirectory = File(context.cacheDir, "logs").apply {
            d(LOG_EXPORT_TAG, "exportToFile: directory=$absolutePath, exists=${exists()}")
            check(exists() || mkdirs()) { "Could not create log export directory: $absolutePath" }
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
            appendLine("version_code=${PackageInfoCompat.getLongVersionCode(packageInfo)}")
            appendLine("android_sdk=${Build.VERSION.SDK_INT}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("brand=${Build.BRAND}")
            appendLine()
        }
        val body = synchronized(entries) {
            entries.joinToString(separator = System.lineSeparator())
        }
        d(LOG_EXPORT_TAG, "exportToFile: writing ${body.length} log chars to ${file.absolutePath}")
        file.writeText(header + body)
        d(LOG_EXPORT_TAG, "exportToFile: wrote file, size=${file.length()}")
        return file
    }

    private fun grantReadAccess(
        context: Context,
        uri: Uri,
        intent: Intent,
    ) {
        val targets = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        d(LOG_EXPORT_TAG, "grantReadAccess: found ${targets.size} share targets")
        targets.forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            runCatching {
                context.grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.onSuccess {
                d(LOG_EXPORT_TAG, "grantReadAccess: granted to $packageName")
            }.onFailure { throwable ->
                e(LOG_EXPORT_TAG, "grantReadAccess: failed for $packageName", throwable)
            }
        }
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
            appendContext()
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
                .filter { it.className.startsWith("org.akkirrai") }
                .take(16)
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

    private fun StringBuilder.appendContext() {
        val snapshot = buildContextSnapshot()
        if (snapshot.isEmpty()) return
        append(" | context={")
        snapshot.entries.joinTo(this, separator = ",") { (key, value) -> "$key=$value" }
        append("}")
    }

    private fun buildContextSnapshot(): Map<String, String> {
        val thread = Thread.currentThread()
        return contextValues
            .toSortedMap()
            .toMutableMap()
            .apply {
                put("thread", sanitize(thread.name))
                put("threadId", thread.id.toString())
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

    private fun processName(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            "pid-${Process.myPid()}"
        }
    }

    private const val MAX_EXPORTED_FILES = 5
}
