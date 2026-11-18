package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class UpdateReleaseInfo(
  val tagName: String,
  val title: String,
  val releaseNotes: String,
  val apkDownloadUrl: String,
  val apkApiAssetUrl: String = "",
  val apkFileName: String,
  val fileSizeBytes: Long,
  val publishedAt: String,
  val isNewer: Boolean
)

sealed class UpdateDownloadState {
  object Idle : UpdateDownloadState()
  object Checking : UpdateDownloadState()
  data class UpToDate(val currentVersion: String, val latestInfo: UpdateReleaseInfo? = null) : UpdateDownloadState()
  data class UpdateAvailable(val info: UpdateReleaseInfo) : UpdateDownloadState()
  data class Downloading(val progress: Float, val downloadedText: String, val totalText: String) : UpdateDownloadState()
  data class ReadyToInstall(val apkFile: File, val info: UpdateReleaseInfo?) : UpdateDownloadState()
  data class Error(val message: String) : UpdateDownloadState()
}

class AppUpdateManager(private val context: Context) {

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  /**
   * Checks GitHub Releases (e.g. savant47/Apk-apps) or direct URL for an update.
   */
  suspend fun checkForUpdates(
    rawRepoOrUrl: String,
    currentVersionName: String,
    githubToken: String = ""
  ): Result<UpdateReleaseInfo> = withContext(Dispatchers.IO) {
    try {
      val input = rawRepoOrUrl.trim()
      if (input.isBlank()) {
        return@withContext Result.failure(Exception("يرجى إدخال اسم مستودع جيت هب (مثل: savant47/Apk-apps) أو رابط مباشر."))
      }

      // Check if it's already a direct APK link
      if (input.startsWith("http://") || input.startsWith("https://")) {
        if (input.endsWith(".apk", ignoreCase = true) || input.contains("/releases/download/")) {
          val fileName = input.substringAfterLast("/").ifBlank { "AsserTV.apk" }
          val info = UpdateReleaseInfo(
            tagName = "Direct",
            title = "تحديث مباشر من الرابط",
            releaseNotes = "تحديث APK مباشر تم جلبه من الرابط المحدد.",
            apkDownloadUrl = input,
            apkFileName = fileName,
            fileSizeBytes = -1L,
            publishedAt = "",
            isNewer = true
          )
          return@withContext Result.success(info)
        }
      }

      val (owner, repo) = extractGitHubOwnerRepo(input)
        ?: return@withContext Result.failure(Exception("صيغة اسم المستودع غير صحيحة. الصيغة المطلوبة: savant47/Apk-apps أو رابط GitHub كامل."))

      val cleanToken = githubToken.trim()

      fun buildGitHubRequest(url: String): Request {
        val builder = Request.Builder()
          .url(url)
          .header("User-Agent", "AsserTV-Android-Updater")
          .header("Accept", "application/vnd.github+json")
        if (cleanToken.isNotBlank()) {
          builder.header("Authorization", "Bearer $cleanToken")
        }
        return builder.build()
      }

      var response = httpClient.newCall(buildGitHubRequest("https://api.github.com/repos/$owner/$repo/releases/latest")).execute()
      var json: JSONObject? = null

      if (response.isSuccessful) {
        val bodyStr = response.body?.string() ?: ""
        if (bodyStr.isNotBlank()) {
          json = JSONObject(bodyStr)
        }
      } else {
        // Fallback: check /releases list in case release wasn't marked "latest"
        val listResp = httpClient.newCall(buildGitHubRequest("https://api.github.com/repos/$owner/$repo/releases")).execute()
        if (listResp.isSuccessful) {
          val arrStr = listResp.body?.string() ?: ""
          val arr = if (arrStr.isNotBlank()) JSONArray(arrStr) else JSONArray()
          if (arr.length() > 0) {
            json = arr.getJSONObject(0)
          }
        } else if (listResp.code == 404) {
          val privateHint = if (cleanToken.isBlank()) {
            "\n💡 ملاحظة هامة: إذا كان المستودع ($owner/$repo) خاصاً (Private 🔒)، يرجى إما تحويله إلى Public من إعدادات GitHub أو إدخال رمز (GitHub Token) في خانة الإعدادات بالأسفل."
          } else ""
          return@withContext Result.failure(
            Exception("لم يتم العثور على Release عام في ($owner/$repo).$privateHint")
          )
        } else {
          return@withContext Result.failure(Exception("خطأ في الاتصال بجيت هب (رمز: ${listResp.code})."))
        }
      }

      val releaseJson = json
        ?: return@withContext Result.failure(Exception("لا توجد إصدارات (Releases) منشورة حالياً في ($owner/$repo)."))

      val tagName = releaseJson.optString("tag_name", "v1.0")
      val title = releaseJson.optString("name", tagName)
      val notes = releaseJson.optString("body", "تحديث جديد لتطبيق Asser TV من قسم Releases.")
      val publishedAt = releaseJson.optString("published_at", "")

      var apkUrl: String? = null
      var apkApiAssetUrl = ""
      var apkName = "AsserTV.apk"
      var fileSize = -1L

      val assets = releaseJson.optJSONArray("assets")
      if (assets != null && assets.length() > 0) {
        for (i in 0 until assets.length()) {
          val asset = assets.getJSONObject(i)
          val name = asset.optString("name", "")
          val downloadUrl = asset.optString("browser_download_url", "")
          val apiAssetUrl = asset.optString("url", "")
          if (name.endsWith(".apk", ignoreCase = true) || downloadUrl.endsWith(".apk", ignoreCase = true)) {
            apkUrl = downloadUrl
            apkApiAssetUrl = apiAssetUrl
            apkName = name
            fileSize = asset.optLong("size", -1L)
            if (name.equals("AsserTV.apk", ignoreCase = true)) {
              break
            }
          }
        }
      }

      if (apkUrl.isNullOrBlank()) {
        return@withContext Result.failure(Exception("تم العثور على الإصدار ($tagName) ولكن لا يحتوي على ملف .apk في قائمة Assets!"))
      }

      val isNewer = compareVersions(tagName, currentVersionName)
      AppDiagnosticsLogger.log("AppUpdater", "Checked GitHub Release $owner/$repo: latest=$tagName, installed=$currentVersionName, isNewer=$isNewer")

      val info = UpdateReleaseInfo(
        tagName = tagName,
        title = title,
        releaseNotes = notes,
        apkDownloadUrl = apkUrl,
        apkApiAssetUrl = apkApiAssetUrl,
        apkFileName = apkName,
        fileSizeBytes = fileSize,
        publishedAt = publishedAt,
        isNewer = isNewer
      )

      Result.success(info)
    } catch (e: Exception) {
      AppDiagnosticsLogger.log("AppUpdater", "Update check failed: ${e.message}", isError = true)
      Result.failure(Exception("فشل فحص التحديثات: ${e.localizedMessage ?: "تأكد من الاتصال بالإنترنت"}"))
    }
  }

  /**
   * Downloads the APK file to app external files or cache directory with progress reporting.
   * Supports both public browser_download_url and authenticated GitHub API asset URLs for private repos.
   */
  suspend fun downloadApk(
    downloadUrl: String,
    apiAssetUrl: String = "",
    githubToken: String = "",
    onProgress: (progress: Float, downloadedMB: String, totalMB: String) -> Unit
  ): Result<File> = withContext(Dispatchers.IO) {
    try {
      val cleanToken = githubToken.trim()
      val useApiAsset = cleanToken.isNotBlank() && apiAssetUrl.isNotBlank()
      val targetUrl = if (useApiAsset) apiAssetUrl else downloadUrl

      val reqBuilder = Request.Builder()
        .url(targetUrl)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android) AsserTV-Downloader")
      if (useApiAsset) {
        reqBuilder.header("Authorization", "Bearer $cleanToken")
        reqBuilder.header("Accept", "application/octet-stream")
      } else if (cleanToken.isNotBlank()) {
        reqBuilder.header("Authorization", "Bearer $cleanToken")
      }

      val response = httpClient.newCall(reqBuilder.build()).execute()
      if (!response.isSuccessful) {
        return@withContext Result.failure(Exception("فشل تنزيل ملف التحديث (رمز الخطأ: ${response.code})."))
      }

      val responseBody = response.body
        ?: return@withContext Result.failure(Exception("الملف المُنزّل فارغ."))

      val totalBytes = responseBody.contentLength()

      val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: context.cacheDir

      if (!downloadDir.exists()) {
        downloadDir.mkdirs()
      }

      val outputFile = File(downloadDir, "AsserTV_Update.apk")
      if (outputFile.exists()) {
        outputFile.delete()
      }

      var inputStream: InputStream? = null
      var outputStream: FileOutputStream? = null

      try {
        inputStream = responseBody.byteStream()
        outputStream = FileOutputStream(outputFile)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalBytesRead: Long = 0

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
          outputStream.write(buffer, 0, bytesRead)
          totalBytesRead += bytesRead

          if (totalBytes > 0) {
            val progress = totalBytesRead.toFloat() / totalBytes.toFloat()
            val downloadedMB = String.format("%.1f", totalBytesRead / (1024f * 1024f))
            val totalMB = String.format("%.1f", totalBytes / (1024f * 1024f))
            onProgress(progress, downloadedMB, totalMB)
          } else {
            val downloadedMB = String.format("%.1f", totalBytesRead / (1024f * 1024f))
            onProgress(0.5f, downloadedMB, "غير معروف")
          }
        }

        outputStream.flush()
      } finally {
        inputStream?.close()
        outputStream?.close()
      }

      if (!outputFile.exists() || outputFile.length() == 0L) {
        return@withContext Result.failure(Exception("فشل حفظ ملف الـ APK أو أن الملف تالف."))
      }

      Result.success(outputFile)
    } catch (e: Exception) {
      Result.failure(Exception("حدث خطأ أثناء تنزيل التحديث: ${e.localizedMessage}"))
    }
  }

  /**
   * Prompts the Android OS package installer to install the APK file.
   */
  fun installApk(apkFile: File): Result<Unit> {
    return try {
      if (!apkFile.exists() || apkFile.length() == 0L) {
        return Result.failure(Exception("ملف التحديث غير موجود أو فارغ."))
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (!context.packageManager.canRequestPackageInstalls()) {
          val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
          context.startActivity(permissionIntent)
          return Result.failure(Exception("يرجى تفعيل خيار (السماح بتثبيت التطبيقات من هذا المصدر) من الشاشة التي ظهرت أمامك، ثم اضغط تثبيت مجدداً."))
        }
      }

      val apkUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
      )

      val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(apkUri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
      }

      context.startActivity(installIntent)
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(Exception("فشل فتح مثبت الحزم: ${e.localizedMessage}"))
    }
  }

  private fun extractGitHubOwnerRepo(input: String): Pair<String, String>? {
    val clean = input.trim()
      .removePrefix("https://")
      .removePrefix("http://")
      .removePrefix("github.com/")
      .removeSuffix(".git")
      .removeSuffix("/")

    val parts = clean.split("/")
    if (parts.size >= 2) {
      return Pair(parts[0].trim(), parts[1].trim())
    }
    return null
  }

  private fun compareVersions(tag: String, currentVer: String): Boolean {
    val cleanTag = tag.trim().lowercase().removePrefix("v").trim()
    val cleanCurrent = currentVer.trim().lowercase().removePrefix("v").trim()

    if (cleanTag == cleanCurrent) return false

    try {
      val tagParts = cleanTag.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
      val curParts = cleanCurrent.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }

      val maxLen = maxOf(tagParts.size, curParts.size)
      for (i in 0 until maxLen) {
        val t = tagParts.getOrElse(i) { 0 }
        val c = curParts.getOrElse(i) { 0 }
        if (t > c) return true
        if (t < c) return false
      }
    } catch (_: Exception) {}

    return cleanTag != cleanCurrent
  }
}
