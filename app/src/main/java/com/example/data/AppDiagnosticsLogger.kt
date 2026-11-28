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
import okhttp3.Protocol
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

  internal fun sanitizeAndTrimForPasteRs(
    reportText: String,
    maxUtf8Bytes: Int = 96 * 1024
  ): ByteArray {
    val sanitizedBuilder = StringBuilder(reportText.length.coerceAtMost(maxUtf8Bytes))
    var prevWasCr = false
    for (ch in reportText) {
      when {
        ch == '\r' -> {
          sanitizedBuilder.append('\n')
          prevWasCr = true
        }
        ch == '\n' -> {
          if (!prevWasCr) sanitizedBuilder.append('\n')
          prevWasCr = false
        }
        ch == '\t' -> {
          sanitizedBuilder.append('\t')
          prevWasCr = false
        }
        ch.code in 0x00..0x1F || ch.code == 0x7F -> {
          prevWasCr = false
        }
        else -> {
          sanitizedBuilder.append(ch)
          prevWasCr = false
        }
      }
    }
    val cleaned = sanitizedBuilder.toString().trim()
    if (cleaned.isEmpty()) {
      return "Asser TV Diagnostics Report (empty)\n".toByteArray(Charsets.UTF_8)
    }

    val fullBytes = cleaned.toByteArray(Charsets.UTF_8)
    if (fullBytes.size <= maxUtf8Bytes) {
      return fullBytes
    }

    // Preserve head (crash report + breadcrumbs + device header) and tail (latest logs) within UTF-8 byte budget
    val marker = "\n\n... [trimmed to fit paste.rs byte limit] ...\n\n"
    val markerBytes = marker.toByteArray(Charsets.UTF_8)
    val available = (maxUtf8Bytes - markerBytes.size).coerceAtLeast(1024)
    val headBudget = (available * 35) / 100
    val tailBudget = available - headBudget

    val headStr = takeLeadingUtf8Bytes(cleaned, headBudget)
    val tailStr = takeTrailingUtf8Bytes(cleaned, tailBudget)
    return (headStr + marker + tailStr).toByteArray(Charsets.UTF_8)
  }

  private fun takeLeadingUtf8Bytes(text: String, maxBytes: Int): String {
    if (maxBytes <= 0) return ""
    val sb = StringBuilder()
    var used = 0
    var i = 0
    while (i < text.length) {
      val cp = text.codePointAt(i)
      val charCount = Character.charCount(cp)
      val byteLen = when {
        cp <= 0x7F -> 1
        cp <= 0x7FF -> 2
        cp <= 0xFFFF -> 3
        else -> 4
      }
      if (used + byteLen > maxBytes) break
      sb.appendCodePoint(cp)
      used += byteLen
      i += charCount
    }
    return sb.toString()
  }

  private fun takeTrailingUtf8Bytes(text: String, maxBytes: Int): String {
    if (maxBytes <= 0) return ""
    var used = 0
    var startIdx = text.length
    var i = text.length
    while (i > 0) {
      val cp = text.codePointBefore(i)
      val charCount = Character.charCount(cp)
      val byteLen = when {
        cp <= 0x7F -> 1
        cp <= 0x7FF -> 2
        cp <= 0xFFFF -> 3
        else -> 4
      }
      if (used + byteLen > maxBytes) break
      used += byteLen
      startIdx = i - charCount
      i -= charCount
    }
    return text.substring(startIdx)
  }

  internal fun extractPasteRsErrorSummary(rawBody: String): String {
    if (rawBody.isBlank()) return "Empty response"
    val stripped = rawBody
      .replace(Regex("<head[^>]*>[\\s\\S]*?</head>", RegexOption.IGNORE_CASE), " ")
      .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
      .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
      .replace(Regex("<[^>]+>"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
    return stripped.ifBlank { rawBody.trim() }.take(140)
  }

  /**
   * Performs an HTTP/1.1 POST request exclusively to https://paste.rs/ with UTF-8 byte-bounded
   * raw log bytes (matching `curl --data-binary`) and returns the short paste.rs URL.
   */
  suspend fun uploadReportToPasteRs(reportText: String): Result<String> = withContext(Dispatchers.IO) {
    try {
      val client = SearchAndSubtitlesService.buildSslTolerantHttpClient()
        .newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

      val primaryBytes = sanitizeAndTrimForPasteRs(reportText, maxUtf8Bytes = 96 * 1024)
      val compactBytes = sanitizeAndTrimForPasteRs(reportText, maxUtf8Bytes = 32 * 1024)

      // Exclusively targets https://paste.rs/ with Rocket-compatible raw body variants
      val attempts = listOf(
        Triple("raw-no-ct", primaryBytes, null),
        Triple("text-plain", primaryBytes, "text/plain".toMediaType()),
        Triple("curl-form-compact", compactBytes, "application/x-www-form-urlencoded".toMediaType())
      )

      var lastCode = 0
      var lastSummary = ""

      for ((label, bytes, mediaType) in attempts) {
        val request = Request.Builder()
          .url("https://paste.rs/")
          .post(bytes.toRequestBody(mediaType))
          .header("User-Agent", "curl/8.5.0")
          .header("Accept", "*/*")
          .build()

        client.newCall(request).execute().use { response ->
          val responseText = response.body?.string()?.trim().orEmpty()
          val pasteUrl = responseText.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("https://paste.rs/") || it.startsWith("http://paste.rs/") }

          if ((response.isSuccessful || response.code == 201 || response.code == 206) && !pasteUrl.isNullOrBlank()) {
            log("LogExport", "Uploaded log to paste.rs ($label, ${bytes.size}B, HTTP ${response.code}): $pasteUrl")
            return@withContext Result.success(pasteUrl)
          }

          lastCode = response.code
          lastSummary = extractPasteRsErrorSummary(responseText)
          log(
            "LogExport",
            "paste.rs attempt [$label] (${bytes.size}B) -> HTTP ${response.code}: $lastSummary",
            isError = true
          )

          // Retry alternate framing only for HTTP 400 / 413 / 415
          if (response.code != 400 && response.code != 413 && response.code != 415) {
            val msg = "فشل الرفع إلى paste.rs (HTTP $lastCode): $lastSummary"
            return@withContext Result.failure(Exception(msg))
          }
        }
      }

      val msg = "فشل الرفع إلى paste.rs (HTTP $lastCode): $lastSummary"
      Result.failure(Exception(msg))
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
