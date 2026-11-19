package com.example.data

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class DiagnosticLogEntry(
  val timestamp: String,
  val tag: String,
  val message: String,
  val isError: Boolean = false
)

object AppDiagnosticsLogger {
  const val CRASH_LOG_FILE_NAME = "last_crash_log.txt"
  const val BREADCRUMB_FILE_NAME = "last_breadcrumb.txt"
  private const val MAX_BREADCRUMB_LINES = 24

  private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
  private val breadcrumbTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

  private val _logsFlow = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
  val logsFlow: StateFlow<List<DiagnosticLogEntry>> = _logsFlow.asStateFlow()

  @Volatile var activePortalName: String = ""
  @Volatile var activeWatchUrl: String = ""
  @Volatile var currentWebViewUrl: String = ""
  @Volatile var activeServerName: String = ""
  @Volatile var activePlayerMode: String = "Native ExoPlayer (المشغل الأساسي)"
  @Volatile var extractedStreamUrl: String = ""
  @Volatile var streamReferer: String = ""
  @Volatile var detectedQualitiesSummary: String = "لم يتم التقاط جودات بعد"
  @Volatile var selectedQualityId: String = "lowest"
  @Volatile var actualPlayingResolution: String = "غير محدد"
  @Volatile var lastPlayerError: String = "لا توجد أخطاء"

  @Synchronized
  fun log(tag: String, message: String, isError: Boolean = false) {
    val now = timeFormat.format(Date())
    val entry = DiagnosticLogEntry(
      timestamp = now,
      tag = tag,
      message = message,
      isError = isError
    )
    if (isError) {
      lastPlayerError = "[$now] $tag: $message"
    }
    val updated = (_logsFlow.value + entry).takeLast(140)
    _logsFlow.value = updated
  }

  @Synchronized
  fun clear() {
    _logsFlow.value = emptyList()
    lastPlayerError = "لا توجد أخطاء"
  }

  /**
   * Persists fatal uncaught crash details synchronously to `filesDir/last_crash_log.txt`
   * before the Android process terminates so low-RAM TV Stick crashes are never lost.
   */
  fun saveCrashLog(context: Context, threadName: String, throwable: Throwable) {
    try {
      val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
      val sw = StringWriter()
      val pw = PrintWriter(sw)
      throwable.printStackTrace(pw)
      pw.flush()
      val stackTrace = sw.toString()

      val sb = StringBuilder()
      sb.appendLine("=== FATAL CRASH REPORT (Asser TV) ===")
      sb.appendLine("Crash Time: $dateStr")
      sb.appendLine("App Version: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
      sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
      sb.appendLine("Thread: $threadName")
      sb.appendLine("Last Active Portal: ${activePortalName.ifBlank { "N/A" }}")
      sb.appendLine("Last Watch URL: ${activeWatchUrl.ifBlank { "N/A" }}")
      sb.appendLine("Last WebView URL: ${currentWebViewUrl.ifBlank { "N/A" }}")
      sb.appendLine("Last Server: ${activeServerName.ifBlank { "Default" }}")
      sb.appendLine("Active Player Mode: $activePlayerMode")
      sb.appendLine("Last Direct Stream: ${extractedStreamUrl.ifBlank { "NONE" }}")
      sb.appendLine("-----------------------------------------")
      sb.appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
      sb.appendLine(stackTrace)
      val recentEvents = _logsFlow.value.takeLast(15)
      if (recentEvents.isNotEmpty()) {
        sb.appendLine("-----------------------------------------")
        sb.appendLine("Recent Events Before Crash:")
        for (entry in recentEvents) {
          val prefix = if (entry.isError) "[ERR]" else "[LOG]"
          sb.appendLine("${entry.timestamp} $prefix [${entry.tag}] ${entry.message}")
        }
      }

      val crashFile = File(context.filesDir, CRASH_LOG_FILE_NAME)
      FileOutputStream(crashFile, false).use { fos ->
        fos.write(sb.toString().toByteArray(Charsets.UTF_8))
        fos.flush()
        try {
          fos.fd.sync()
        } catch (_: Throwable) {}
      }
    } catch (_: Throwable) {
      // Never throw inside UncaughtExceptionHandler
    }
  }

  fun loadLastCrashLog(context: Context): String? {
    return try {
      val crashFile = File(context.filesDir, CRASH_LOG_FILE_NAME)
      if (crashFile.exists() && crashFile.length() > 0L) {
        crashFile.readText(Charsets.UTF_8).trim().ifBlank { null }
      } else {
        null
      }
    } catch (_: Exception) {
      null
    }
  }

  fun clearLastCrashLog(context: Context): Boolean {
    return try {
      val crashFile = File(context.filesDir, CRASH_LOG_FILE_NAME)
      if (crashFile.exists()) crashFile.delete() else true
    } catch (_: Exception) {
      false
    }
  }

  /**
   * Writes a lightweight lifecycle breadcrumb synchronously to `filesDir/last_breadcrumb.txt`
   * so that if the Android TV OS kills the process via LMKD (SIGKILL without a Java exception),
   * the Diagnostics dialog reveals the exact last portal/stream/WebView step and heap usage.
   */
  @Synchronized
  fun recordBreadcrumb(context: Context, stage: String, detail: String = "") {
    try {
      val appCtx = context.applicationContext ?: context
      val breadcrumbFile = File(appCtx.filesDir, BREADCRUMB_FILE_NAME)
      val existingLines = if (breadcrumbFile.exists() && breadcrumbFile.length() > 0L) {
        breadcrumbFile.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
      } else {
        emptyList()
      }
      val rt = Runtime.getRuntime()
      val usedMb = ((rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)).coerceAtLeast(0L)
      val maxMb = (rt.maxMemory() / (1024L * 1024L)).coerceAtLeast(1L)
      val pid = android.os.Process.myPid()
      val ts = breadcrumbTimeFormat.format(Date())
      val cleanDetail = detail.replace("\n", " ").trim().take(160)
      val newLine = "$ts | PID=$pid | Heap=${usedMb}/${maxMb}MB | [$stage] $cleanDetail"
      val updated = (existingLines + newLine).takeLast(MAX_BREADCRUMB_LINES)
      FileOutputStream(breadcrumbFile, false).use { fos ->
        fos.write(updated.joinToString("\n").toByteArray(Charsets.UTF_8))
        fos.flush()
      }
    } catch (_: Throwable) {
      // Never crash while recording diagnostics
    }
  }

  fun loadLastBreadcrumbs(context: Context): String? {
    return try {
      val appCtx = context.applicationContext ?: context
      val breadcrumbFile = File(appCtx.filesDir, BREADCRUMB_FILE_NAME)
      if (breadcrumbFile.exists() && breadcrumbFile.length() > 0L) {
        breadcrumbFile.readText(Charsets.UTF_8).trim().ifBlank { null }
      } else {
        null
      }
    } catch (_: Exception) {
      null
    }
  }

  fun clearLastBreadcrumbs(context: Context): Boolean {
    return try {
      val appCtx = context.applicationContext ?: context
      val breadcrumbFile = File(appCtx.filesDir, BREADCRUMB_FILE_NAME)
      if (breadcrumbFile.exists()) breadcrumbFile.delete() else true
    } catch (_: Exception) {
      false
    }
  }

  /**
   * Exports the full diagnostics + saved crash report to `cacheDir/logs/asser_tv_log.txt`
   * for sharing via FileProvider (e.g. Send Files to TV).
   */
  fun exportReportToCacheFile(context: Context): File {
    val logsDir = File(context.cacheDir, "logs")
    if (!logsDir.exists()) {
      logsDir.mkdirs()
    }
    val logFile = File(logsDir, "asser_tv_log.txt")
    val reportContent = buildFullCopyableReport(context)
    logFile.writeText(reportContent, Charsets.UTF_8)
    return logFile
  }

  /**
   * Performs a lightweight HTTP POST request to https://paste.rs/ with raw log text in the body
   * and returns the short paste URL so Android TV users can scan a QR code on their phone.
   */
  suspend fun uploadReportToPasteRs(reportText: String): Result<String> = withContext(Dispatchers.IO) {
    try {
      val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

      val payload = reportText.take(250_000)
      val body = payload.toRequestBody("text/plain; charset=utf-8".toMediaType())
      val request = Request.Builder()
        .url("https://paste.rs/")
        .post(body)
        .header("User-Agent", "AsserTV-Diagnostics/1.0")
        .build()

      client.newCall(request).execute().use { response ->
        val responseText = response.body?.string()?.trim().orEmpty()
        if ((response.isSuccessful || response.code == 201 || response.code == 206) &&
            responseText.startsWith("http")
        ) {
          val cleanUrl = responseText.lineSequence().firstOrNull { it.startsWith("http") }?.trim()
            ?: responseText
          log("LogExport", "Uploaded log to paste.rs: $cleanUrl")
          Result.success(cleanUrl)
        } else {
          val msg = "فشل الرفع إلى paste.rs (HTTP ${response.code}): ${responseText.take(120)}"
          log("LogExport", msg, isError = true)
          Result.failure(Exception(msg))
        }
      }
    } catch (e: Exception) {
      val msg = "تعذر الاتصال بـ paste.rs: ${e.localizedMessage ?: e.javaClass.simpleName}"
      log("LogExport", msg, isError = true)
      Result.failure(Exception(msg))
    }
  }

  fun buildFullCopyableReport(context: Context? = null): String {
    val sb = StringBuilder()
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    val savedCrash = context?.let { loadLastCrashLog(it) }
    if (!savedCrash.isNullOrBlank()) {
      sb.appendLine(savedCrash)
      sb.appendLine()
      sb.appendLine("=========================================")
      sb.appendLine()
    }

    val savedBreadcrumbs = context?.let { loadLastBreadcrumbs(it) }
    if (!savedBreadcrumbs.isNullOrBlank()) {
      sb.appendLine("=== Persistent Lifecycle Breadcrumbs (SIGKILL Trail) ===")
      sb.appendLine(savedBreadcrumbs)
      sb.appendLine("=========================================")
      sb.appendLine()
    }

    sb.appendLine("=== Asser TV Diagnostics & Log Report ===")
    sb.appendLine("Time: $dateStr")
    sb.appendLine("App Version: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
    sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
    sb.appendLine("-----------------------------------------")
    sb.appendLine("Active Portal: ${activePortalName.ifBlank { "N/A" }}")
    sb.appendLine("Watch Page URL: ${activeWatchUrl.ifBlank { "N/A" }}")
    sb.appendLine("Current WebView URL: ${currentWebViewUrl.ifBlank { "N/A" }}")
    sb.appendLine("Selected Server: ${activeServerName.ifBlank { "Default" }}")
    sb.appendLine("Active Player Mode: $activePlayerMode")
    sb.appendLine("Extracted Direct Stream: ${extractedStreamUrl.ifBlank { "NONE (Not extracted yet)" }}")
    sb.appendLine("Stream Referer: ${streamReferer.ifBlank { "N/A" }}")
    sb.appendLine("Detected Video Qualities: $detectedQualitiesSummary")
    sb.appendLine("Selected Quality: $selectedQualityId | Actual Video Size: $actualPlayingResolution")
    sb.appendLine("Last Error: $lastPlayerError")
    sb.appendLine("-----------------------------------------")
    sb.appendLine("=== App Events Log (${_logsFlow.value.size} entries) ===")
    for (entry in _logsFlow.value.takeLast(80)) {
      val prefix = if (entry.isError) "❌ [ERR]" else "ℹ️ [LOG]"
      sb.appendLine("${entry.timestamp} $prefix [${entry.tag}] ${entry.message}")
    }

    sb.appendLine("-----------------------------------------")
    sb.appendLine("=== System Logcat (ExoPlayer / WebView / Errors) ===")
    try {
      val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "90", "-v", "time"))
      val reader = BufferedReader(InputStreamReader(process.inputStream))
      val relevantLines = mutableListOf<String>()
      var line: String?
      while (reader.readLine().also { line = it } != null) {
        val l = line ?: continue
        val lower = l.lowercase()
        if (lower.contains("exoplayer") ||
            lower.contains("media3") ||
            lower.contains("httpdatasource") ||
            lower.contains("chromium") ||
            lower.contains("assertv") ||
            lower.contains("exception") ||
            lower.contains("error") ||
            lower.contains(" E/")
        ) {
          if (!lower.contains("drmhalhidl")) {
            relevantLines.add(l)
          }
        }
      }
      reader.close()
      for (logLine in relevantLines.takeLast(35)) {
        sb.appendLine(logLine)
      }
    } catch (e: Exception) {
      sb.appendLine("Logcat read note: ${e.message}")
    }

    sb.appendLine("=== End of Report ===")
    return sb.toString()
  }
}
