package com.example.data

import android.os.Build
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticLogEntry(
  val timestamp: String,
  val tag: String,
  val message: String,
  val isError: Boolean = false
)

object AppDiagnosticsLogger {
  private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

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
  @Volatile var selectedQualityId: String = "auto"
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

  fun buildFullCopyableReport(): String {
    val sb = StringBuilder()
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
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
