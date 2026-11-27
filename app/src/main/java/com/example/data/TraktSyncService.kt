package com.example.data

import com.example.model.StreamingPortal
import com.example.model.TraktCalendarItem
import com.example.model.TraktWatchlistItem
import com.example.model.UpNextItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class TraktOAuthTokens(
  val accessToken: String,
  val refreshToken: String,
  val expiresAtMs: Long
)

sealed class TraktDeviceCodeResult {
  data class Success(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresInSec: Int,
    val intervalSec: Int
  ) : TraktDeviceCodeResult()

  data class Error(val message: String) : TraktDeviceCodeResult()
}

sealed class TraktTokenPollResult {
  data class Authorized(val tokens: TraktOAuthTokens) : TraktTokenPollResult()
  data object Pending : TraktTokenPollResult()
  data object SlowDown : TraktTokenPollResult()
  data object Expired : TraktTokenPollResult()
  data object Denied : TraktTokenPollResult()
  data class Error(val message: String) : TraktTokenPollResult()
}

data class TraktSyncStats(
  val totalWatchedFetched: Int = 0,
  val hiddenFilteredCount: Int = 0,
  val completedFilteredCount: Int = 0,
  val noNextEpisodeFilteredCount: Int = 0,
  val activeUpNextCount: Int = 0
)

sealed class TraktFullSyncResult {
  data class Success(
    val username: String,
    val upNextItems: List<UpNextItem>,
    val calendarItems: List<TraktCalendarItem>,
    val watchlistItems: List<TraktWatchlistItem>,
    val stats: TraktSyncStats = TraktSyncStats(),
    val watchedEpisodeKeys: Set<String> = emptySet()
  ) : TraktFullSyncResult()

  data object Unauthorized : TraktFullSyncResult()
  data class Error(val message: String) : TraktFullSyncResult()
}

/**
 * Official Trakt.tv API v2 Client (OAuth Device Code Flow + Progress + Calendar + Watchlist + Scrobble/Watched Sync).
 * Runs 100% over lightweight OkHttp JSON calls — ZERO WebView, ZERO Chromium RAM overhead.
 */
class TraktSyncService {

  private val client: OkHttpClient by lazy {
    SearchAndSubtitlesService.buildSslTolerantHttpClient()
      .newBuilder()
      .followRedirects(true)
      .connectTimeout(12, TimeUnit.SECONDS)
      .readTimeout(16, TimeUnit.SECONDS)
      .writeTimeout(12, TimeUnit.SECONDS)
      .build()
  }

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  private fun buildTraktRequest(
    url: String,
    clientId: String,
    accessToken: String = ""
  ): Request.Builder {
    return Request.Builder()
      .url(url)
      .header("Content-Type", "application/json")
      .header("Accept", "application/json")
      .header("trakt-api-version", "2")
      .header("trakt-api-key", clientId.trim())
      .header("User-Agent", "AsserTV/Android")
      .apply {
        if (accessToken.isNotBlank()) {
          header("Authorization", "Bearer ${accessToken.trim()}")
        }
      }
  }

  /**
   * Step 1 of Official Trakt OAuth Device Flow:
   * Requests a user activation code (`ABCD1234`) & verification URL (`https://trakt.tv/activate`).
   */
  suspend fun requestDeviceCode(clientId: String): TraktDeviceCodeResult = withContext(Dispatchers.IO) {
    val cleanClientId = clientId.trim()
    if (cleanClientId.isBlank()) {
      return@withContext TraktDeviceCodeResult.Error("يرجى إدخال Trakt Client ID أولاً")
    }
    try {
      val body = JSONObject().put("client_id", cleanClientId).toString().toRequestBody(jsonMediaType)
      val req = buildTraktRequest("$API_BASE/oauth/device/code", cleanClientId)
        .post(body)
        .build()

      client.newCall(req).execute().use { resp ->
        val raw = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
          val errMsg = when (resp.code) {
            401, 403 -> "مفتاح Trakt Client ID غير صحيح — تأكد من نسخه بشكل كامل"
            429 -> "طلبات كثيرة مؤقتاً — انتظر ثوانٍ ثم حاول مجدداً"
            else -> "فشل توليد الكود (HTTP ${resp.code})"
          }
          return@withContext TraktDeviceCodeResult.Error(errMsg)
        }
        val obj = JSONObject(raw)
        val deviceCode = obj.optString("device_code", "").trim()
        val userCode = obj.optString("user_code", "").trim()
        val verificationUrl = obj.optString("verification_url", "https://trakt.tv/activate").trim()
        val expiresIn = obj.optInt("expires_in", 600).coerceIn(60, 1800)
        val interval = obj.optInt("interval", 5).coerceIn(3, 15)

        if (deviceCode.isBlank() || userCode.isBlank()) {
          return@withContext TraktDeviceCodeResult.Error("استجابة غير مكتملة من Trakt")
        }
        AppDiagnosticsLogger.log("TraktAPI", "Generated official Trakt device code successfully")
        TraktDeviceCodeResult.Success(
          deviceCode = deviceCode,
          userCode = userCode,
          verificationUrl = verificationUrl,
          expiresInSec = expiresIn,
          intervalSec = interval
        )
      }
    } catch (e: Exception) {
      AppDiagnosticsLogger.log("TraktAPI", "Device code error: ${e.message}", isError = true)
      TraktDeviceCodeResult.Error("تعذر الاتصال بـ Trakt: ${e.message ?: "تحقق من الإنترنت"}")
    }
  }

  /**
   * Step 2 of Official Trakt OAuth Device Flow:
   * Polls until the user enters the code at trakt.tv/activate and approves access.
   */
  suspend fun pollDeviceToken(
    clientId: String,
    clientSecret: String,
    deviceCode: String
  ): TraktTokenPollResult = withContext(Dispatchers.IO) {
    try {
      val cleanId = clientId.trim()
      val cleanSecret = clientSecret.trim()
      val payloadObj = JSONObject()
        .put("code", deviceCode.trim())
        .put("client_id", cleanId)
      if (cleanSecret.isNotBlank() && cleanSecret != cleanId) {
        payloadObj.put("client_secret", cleanSecret)
      }
      val payload = payloadObj
        .toString()
        .toRequestBody(jsonMediaType)

      val req = buildTraktRequest("$API_BASE/oauth/device/token", clientId)
        .post(payload)
        .build()

      client.newCall(req).execute().use { resp ->
        val raw = resp.body?.string().orEmpty()
        when (resp.code) {
          200 -> {
            val obj = JSONObject(raw)
            val accessToken = obj.optString("access_token", "").trim()
            val refreshToken = obj.optString("refresh_token", "").trim()
            val expiresInSec = obj.optLong("expires_in", 7_776_000L) // ~90 days default
            val expiresAtMs = System.currentTimeMillis() + (expiresInSec * 1000L)
            if (accessToken.isBlank()) {
              TraktTokenPollResult.Error("لم يتم استلام رمز الوصول من Trakt")
            } else {
              AppDiagnosticsLogger.log("TraktAPI", "Trakt OAuth Device authorization succeeded")
              TraktTokenPollResult.Authorized(
                TraktOAuthTokens(
                  accessToken = accessToken,
                  refreshToken = refreshToken,
                  expiresAtMs = expiresAtMs
                )
              )
            }
          }
          400 -> TraktTokenPollResult.Pending
          404, 410 -> TraktTokenPollResult.Expired
          409 -> TraktTokenPollResult.Expired
          418 -> TraktTokenPollResult.Denied
          429 -> TraktTokenPollResult.SlowDown
          401, 403 -> TraktTokenPollResult.Error("تأكد من صحة Client ID و Client Secret")
          else -> TraktTokenPollResult.Error("خطأ أثناء التحقق (HTTP ${resp.code})")
        }
      }
    } catch (e: Exception) {
      TraktTokenPollResult.Error(e.message ?: "خطأ في الشبكة")
    }
  }

  /**
   * Refreshes an expired Trakt OAuth access_token silently using the stored refresh_token.
   */
  suspend fun refreshAccessToken(
    clientId: String,
    clientSecret: String,
    refreshToken: String
  ): TraktOAuthTokens? = withContext(Dispatchers.IO) {
    val cleanId = clientId.trim()
    val cleanSecret = clientSecret.trim()
    if (cleanId.isBlank() || refreshToken.isBlank()) return@withContext null
    try {
      val payloadObj = JSONObject()
        .put("refresh_token", refreshToken.trim())
        .put("client_id", cleanId)
        .put("redirect_uri", REDIRECT_URI_OOB)
        .put("grant_type", "refresh_token")
      if (cleanSecret.isNotBlank() && cleanSecret != cleanId) {
        payloadObj.put("client_secret", cleanSecret)
      }
      val payload = payloadObj
        .toString()
        .toRequestBody(jsonMediaType)

      val req = buildTraktRequest("$API_BASE/oauth/token", clientId)
        .post(payload)
        .build()

      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@withContext null
        val obj = JSONObject(resp.body?.string().orEmpty())
        val access = obj.optString("access_token", "").trim()
        val refresh = obj.optString("refresh_token", refreshToken).trim()
        val expiresInSec = obj.optLong("expires_in", 7_776_000L)
        if (access.isBlank()) null
        else TraktOAuthTokens(access, refresh, System.currentTimeMillis() + expiresInSec * 1000L)
      }
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Fetches the authenticated user's real Trakt username via GET /users/settings.
   */
  suspend fun fetchUserProfile(clientId: String, accessToken: String): String = withContext(Dispatchers.IO) {
    try {
      val req = buildTraktRequest("$API_BASE/users/settings", clientId, accessToken).get().build()
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@withContext ""
        val obj = JSONObject(resp.body?.string().orEmpty())
        val user = obj.optJSONObject("user")
        user?.optString("username", "").orEmpty().trim()
          .ifBlank { user?.optString("name", "").orEmpty().trim() }
      }
    } catch (_: Exception) {
      ""
    }
  }

  /**
   * Parses Trakt ISO-8601 UTC timestamps (e.g. "2026-11-26T18:20:00.000Z") into epoch milliseconds.
   */
  private fun parseIsoUtcMs(iso: String): Long {
    val clean = iso.trim()
    if (clean.isBlank()) return 0L
    val patterns = listOf(
      "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
      "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )
    for (p in patterns) {
      try {
        val sdf = SimpleDateFormat(p, Locale.US).apply {
          timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val parsed = sdf.parse(clean)
        if (parsed != null) return parsed.time
      } catch (_: Exception) {}
    }
    return 0L
  }

  /**
   * Fetches Trakt show IDs explicitly hidden or dropped at the show level (`type == "show"`).
   */
  private fun fetchHiddenShowIds(clientId: String, accessToken: String): Set<Long> {
    val hidden = mutableSetOf<Long>()
    val endpoints = listOf(
      "$API_BASE/users/hidden/progress_watched?type=show&limit=100",
      "$API_BASE/users/hidden/dropped?type=show&limit=100"
    )
    for (url in endpoints) {
      try {
        val req = buildTraktRequest(url, clientId, accessToken).get().build()
        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) return@use
          val arr = JSONArray(resp.body?.string().orEmpty().ifBlank { "[]" })
          for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val type = item.optString("type", "show")
            if (type == "show" && !item.has("season")) {
              val id = item.optJSONObject("show")?.optJSONObject("ids")?.optLong("trakt", 0L) ?: 0L
              if (id > 0L) hidden.add(id)
            }
          }
        }
      } catch (_: Exception) {}
    }
    return hidden
  }

  private data class RawUpNextProgress(
    val traktShowId: Long,
    val slug: String,
    val imdbId: String,
    val showTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String,
    val firstAiredIso: String,
    val lastWatchedAtIso: String,
    val epTraktId: String,
    val watchedCount: Int,
    val airedCount: Int,
    val sortTimestampMs: Long
  )

  private sealed class ProgressCheckOutcome {
    data class Active(val progress: RawUpNextProgress) : ProgressCheckOutcome()
    data object FilteredCompleted : ProgressCheckOutcome()
    data object FilteredNoNextEpisode : ProgressCheckOutcome()
    data class Failed(val reason: String) : ProgressCheckOutcome()
  }

  private sealed class PaginatedEndpointOutcome {
    data class Success(val items: List<JSONObject>) : PaginatedEndpointOutcome()
    data object NotFound : PaginatedEndpointOutcome()
    data object Unauthorized : PaginatedEndpointOutcome()
    data class Error(val message: String) : PaginatedEndpointOutcome()
  }

  /**
   * Paginates a Trakt progress endpoint (`extended=full&page=1&limit=100`, `page=2`, ...)
   * until an empty array comes back.
   */
  private fun fetchPaginatedTraktArray(
    endpointPath: String,
    clientId: String,
    accessToken: String
  ): PaginatedEndpointOutcome {
    val allItems = mutableListOf<JSONObject>()
    var page = 1
    val limit = 100
    while (page <= 50) {
      val sep = if (endpointPath.contains("?")) "&" else "?"
      val url = "$API_BASE$endpointPath${sep}extended=full&page=$page&limit=$limit"
      val req = buildTraktRequest(url, clientId, accessToken).get().build()
      client.newCall(req).execute().use { resp ->
        when (resp.code) {
          401, 403 -> return PaginatedEndpointOutcome.Unauthorized
          404 -> return if (page == 1) PaginatedEndpointOutcome.NotFound else PaginatedEndpointOutcome.Success(allItems)
        }
        if (!resp.isSuccessful) {
          return PaginatedEndpointOutcome.Error("تعذر جلب التقدم من Trakt (HTTP ${resp.code})")
        }
        val bodyStr = resp.body?.string().orEmpty().trim()
        if (bodyStr.isBlank()) {
          return PaginatedEndpointOutcome.Success(allItems)
        }
        val arr = JSONArray(bodyStr)
        if (arr.length() == 0) {
          return PaginatedEndpointOutcome.Success(allItems)
        }
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          allItems.add(obj)
        }
        val pageCountHeader = resp.header("X-Pagination-Page-Count")?.trim()?.toIntOrNull()
        if (pageCountHeader != null && page >= pageCountHeader) {
          return PaginatedEndpointOutcome.Success(allItems)
        }
        if (pageCountHeader == null && arr.length() < limit) {
          return PaginatedEndpointOutcome.Success(allItems)
        }
      }
      page++
    }
    return PaginatedEndpointOutcome.Success(allItems)
  }

  /**
   * Parses a single `{ show, progress }` entry strictly according to rules:
   * - Uses ONLY `progress.next_episode` (`season`, `number`, `title`, `first_aired`).
   * - Uses `progress.aired` and `progress.completed` directly for the counters.
   * - Uses `progress.last_watched_at` for the "last watched" label and sorting.
   * - Drops every item whose `progress.next_episode` is null.
   * - Drops every item whose `next_episode.first_aired` is null/blank or in the future (`firstAiredMs > nowMs`).
   * - Drops every item where `progress.completed <= 0` or `progress.completed >= progress.aired`.
   */
  private fun parseStrictUpNextEntry(
    showObj: JSONObject,
    progressObj: JSONObject,
    fallbackLastWatchedIso: String,
    nowMs: Long,
    watchedEpisodeKeys: MutableSet<String>?
  ): ProgressCheckOutcome {
    val ids = showObj.optJSONObject("ids") ?: return ProgressCheckOutcome.FilteredNoNextEpisode
    val traktShowId = ids.optLong("trakt", 0L)
    val showTitle = showObj.optString("title", "").trim()
    if (traktShowId <= 0L || showTitle.isBlank()) {
      return ProgressCheckOutcome.FilteredNoNextEpisode
    }
    val slug = ids.optString("slug", "").trim()
    val imdbId = ids.optString("imdb", "").trim()

    val aired = progressObj.optInt("aired", 0)
    val completed = progressObj.optInt("completed", 0)

    // Record completed episodes for local watched checkmarks if seasons array is present
    val seasonsArr = progressObj.optJSONArray("seasons")
    if (watchedEpisodeKeys != null && seasonsArr != null) {
      for (si in 0 until seasonsArr.length()) {
        val sObj = seasonsArr.optJSONObject(si) ?: continue
        val sNum = sObj.optInt("number", 0)
        if (sNum <= 0) continue
        val epsArr = sObj.optJSONArray("episodes") ?: continue
        for (ei in 0 until epsArr.length()) {
          val eObj = epsArr.optJSONObject(ei) ?: continue
          if (eObj.optBoolean("completed", false) || !eObj.has("completed")) {
            val eNum = eObj.optInt("number", 0)
            if (eNum > 0) {
              val key = TvPreferencesRepository.buildEpisodeWatchedKey(showTitle, sNum, eNum)
              if (key.isNotBlank()) watchedEpisodeKeys.add(key)
            }
          }
        }
      }
    }

    // Rule 5: A show the user has not watched a single episode of must not appear; completed shows must not appear
    if (completed <= 0 || aired <= 0 || completed >= aired) {
      return ProgressCheckOutcome.FilteredCompleted
    }

    // Rule 3 & Rule 4: Use ONLY progress.next_episode; drop if null or if first_aired is null or in the future
    val nextEp = progressObj.optJSONObject("next_episode")
      ?: return ProgressCheckOutcome.FilteredNoNextEpisode

    val season = nextEp.optInt("season", 0)
    val number = nextEp.optInt("number", 0)
    if (season <= 0 || number <= 0) {
      return ProgressCheckOutcome.FilteredNoNextEpisode
    }

    val rawFirstAired = nextEp.optString("first_aired", "").trim()
    val firstAiredIso = if (rawFirstAired.equals("null", ignoreCase = true)) "" else rawFirstAired
    val firstAiredMs = parseIsoUtcMs(firstAiredIso)
    if (firstAiredIso.isBlank() || firstAiredMs <= 0L || firstAiredMs > nowMs) {
      // Unaired or unknown-air-date episode — must NEVER appear
      return ProgressCheckOutcome.FilteredNoNextEpisode
    }

    val rawTitle = nextEp.optString("title", "").trim()
    val epTitle = if (rawTitle.equals("null", ignoreCase = true)) "" else rawTitle
    val epTraktId = nextEp.optJSONObject("ids")?.optLong("trakt", 0L)
      ?.takeIf { it > 0L }?.toString().orEmpty()

    val rawLastWatched = progressObj.optString("last_watched_at", "").trim()
      .takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
      ?: fallbackLastWatchedIso
    val lastWatchedAtIso = if (rawLastWatched.equals("null", ignoreCase = true)) "" else rawLastWatched
    val lastWatchedMs = parseIsoUtcMs(lastWatchedAtIso)

    return ProgressCheckOutcome.Active(
      RawUpNextProgress(
        traktShowId = traktShowId,
        slug = slug,
        imdbId = imdbId,
        showTitle = showTitle,
        seasonNumber = season,
        episodeNumber = number,
        episodeTitle = epTitle,
        firstAiredIso = firstAiredIso,
        lastWatchedAtIso = lastWatchedAtIso,
        epTraktId = epTraktId,
        watchedCount = completed,
        airedCount = aired,
        sortTimestampMs = lastWatchedMs
      )
    )
  }

  /**
   * Fetches a single show's `{ progress }` object from `GET /shows/{id}/progress/watched?hidden=false&specials=false&count_specials=false&extended=full`
   * and applies `parseStrictUpNextEntry`.
   */
  private suspend fun fetchShowProgressOutcome(
    clientId: String,
    accessToken: String,
    showObj: JSONObject,
    fallbackLastWatchedIso: String,
    nowMs: Long,
    watchedEpisodeKeys: MutableSet<String>?
  ): ProgressCheckOutcome {
    val traktId = showObj.optJSONObject("ids")?.optLong("trakt", 0L) ?: 0L
    if (traktId <= 0L) return ProgressCheckOutcome.FilteredNoNextEpisode
    val sortTimestampMs = parseIsoUtcMs(fallbackLastWatchedIso)
    if (sortTimestampMs > 0L && verifiedCompletedShowsCache[traktId] == sortTimestampMs) {
      return ProgressCheckOutcome.FilteredCompleted
    }

    val url = "$API_BASE/shows/$traktId/progress/watched?hidden=false&specials=false&count_specials=false&extended=full"
    for (attempt in 0..3) {
      try {
        val req = buildTraktRequest(url, clientId, accessToken).get().build()
        var retryAfterSec = 0L
        val statusAndBody = client.newCall(req).execute().use { resp ->
          retryAfterSec = resp.header("Retry-After")?.trim()?.toLongOrNull() ?: 0L
          resp.code to resp.body?.string().orEmpty()
        }
        val code = statusAndBody.first
        val body = statusAndBody.second
        if ((code == 429 || code in 500..599) && attempt < 3) {
          val waitMs = if (retryAfterSec > 0L) (retryAfterSec * 1000L).coerceAtMost(5000L) else (1000L * (attempt + 1))
          kotlinx.coroutines.delay(waitMs)
          continue
        }
        if (code !in 200..299 || body.isBlank()) {
          return ProgressCheckOutcome.Failed("HTTP $code")
        }

        val progressObj = JSONObject(body)
        val outcome = parseStrictUpNextEntry(
          showObj = showObj,
          progressObj = progressObj,
          fallbackLastWatchedIso = fallbackLastWatchedIso,
          nowMs = nowMs,
          watchedEpisodeKeys = watchedEpisodeKeys
        )
        when (outcome) {
          is ProgressCheckOutcome.Active -> verifiedCompletedShowsCache.remove(traktId)
          ProgressCheckOutcome.FilteredCompleted -> {
            val aired = progressObj.optInt("aired", 0)
            val completed = progressObj.optInt("completed", 0)
            if (sortTimestampMs > 0L && aired > 0 && completed >= aired) {
              verifiedCompletedShowsCache[traktId] = sortTimestampMs
            }
          }
          else -> {}
        }
        return outcome
      } catch (e: Exception) {
        if (attempt < 3) {
          kotlinx.coroutines.delay(800L * (attempt + 1))
          continue
        }
        return ProgressCheckOutcome.Failed(e.message ?: "Network error")
      }
    }
    return ProgressCheckOutcome.Failed("Timeout")
  }

  /**
   * Invalidates the 10-minute in-memory Up Next cache so the next sync fetches immediately from Trakt.
   */
  fun invalidateUpNextCache() {
    cachedUpNextResult = null
    cachedUpNextToken = ""
    cachedUpNextTimestampMs = 0L
  }

  /**
   * Rewritten Next to Watch synchronization driven strictly by Trakt's own up-next progress:
   * 1. Calls `GET https://api.trakt.tv/sync/progress/up_next?extended=full&page={page}&limit=100` until empty array.
   * 2. If `/sync/progress/up_next` returns 404, falls back to `GET https://api.trakt.tv/sync/progress/watched?extended=full&page={page}&limit=100`
   *    (and if 404 or missing embedded `progress`, resolves each watched show's `progress` via `/shows/{id}/progress/watched?extended=full`).
   * 3. Uses `progress.next_episode` (`season`, `number`, `title`, `first_aired`), `progress.aired`, `progress.completed`, and `progress.last_watched_at`.
   * 4. Drops every item whose `progress.next_episode` is null or whose `next_episode.first_aired` is null or in the future.
   * 5. Never adds any show not in this response or with 0 watched episodes.
   * 6. Sorts by `progress.last_watched_at` descending.
   * 7. Caches the response for 10 minutes and refreshes immediately after marking an episode watched.
   */
  suspend fun syncFullAccountData(
    clientId: String,
    accessToken: String,
    forceRefresh: Boolean = false
  ): TraktFullSyncResult = withContext(Dispatchers.IO) {
    if (clientId.isBlank() || accessToken.isBlank()) {
      return@withContext TraktFullSyncResult.Unauthorized
    }

    val nowMs = System.currentTimeMillis()
    val cached = cachedUpNextResult
    if (
      !forceRefresh &&
      cached != null &&
      cachedUpNextToken == accessToken &&
      (nowMs - cachedUpNextTimestampMs) in 0 until UP_NEXT_CACHE_TTL_MS
    ) {
      AppDiagnosticsLogger.log("TraktAPI", "Returning 10-min cached Trakt Up Next list (${cached.upNextItems.size} items)")
      return@withContext cached
    }

    try {
      coroutineScope {
        val usernameDeferred = async { fetchUserProfile(clientId, accessToken) }
        val hiddenIdsDeferred = async { fetchHiddenShowIds(clientId, accessToken) }

        // Step 1: Call GET /sync/progress/up_next?extended=full&page=1&limit=100 (looping pages until empty array)
        var sourceEndpoint = "/sync/progress/up_next"
        var pageOutcome = fetchPaginatedTraktArray("/sync/progress/up_next", clientId, accessToken)

        // Step 9: If /sync/progress/up_next returns 404, fall back to GET /sync/progress/watched with extended=full
        if (pageOutcome is PaginatedEndpointOutcome.NotFound) {
          sourceEndpoint = "/sync/progress/watched"
          pageOutcome = fetchPaginatedTraktArray("/sync/progress/watched", clientId, accessToken)
        }

        // If /sync/progress/watched also returns 404 on this Trakt host, fetch /sync/watched/shows and resolve each show's progress object
        if (pageOutcome is PaginatedEndpointOutcome.NotFound) {
          sourceEndpoint = "/sync/watched/shows"
          pageOutcome = fetchPaginatedTraktArray("/sync/watched/shows", clientId, accessToken)
        }

        val entryObjects = when (val res = pageOutcome) {
          is PaginatedEndpointOutcome.Unauthorized -> return@coroutineScope TraktFullSyncResult.Unauthorized
          is PaginatedEndpointOutcome.Error -> return@coroutineScope TraktFullSyncResult.Error(res.message)
          is PaginatedEndpointOutcome.NotFound -> return@coroutineScope TraktFullSyncResult.Error("تعذر العثور على نقطة مزامنة التقدم في Trakt (HTTP 404)")
          is PaginatedEndpointOutcome.Success -> res.items
        }

        val hiddenShowIds = hiddenIdsDeferred.await()
        val totalWatchedFetched = entryObjects.size
        var hiddenFilteredCount = 0
        var completedFilteredCount = 0
        var noNextEpisodeFilteredCount = 0

        val seenIds = mutableSetOf<Long>()
        val watchedEpisodeKeys = ConcurrentHashMap.newKeySet<String>()
        val directOutcomes = mutableListOf<ProgressCheckOutcome>()
        val needsShowProgressFetch = mutableListOf<Pair<JSONObject, String>>()

        for (entry in entryObjects) {
          val showObj = entry.optJSONObject("show") ?: continue
          val ids = showObj.optJSONObject("ids") ?: continue
          val traktId = ids.optLong("trakt", 0L)
          val title = showObj.optString("title", "").trim()
          if (traktId <= 0L || title.isBlank() || !seenIds.add(traktId)) continue

          if (hiddenShowIds.contains(traktId)) {
            hiddenFilteredCount++
            continue
          }

          val fallbackLastWatched = entry.optString("last_watched_at", "").trim()
            .ifBlank { entry.optString("last_updated_at", "").trim() }

          // Record any watched episodes present on the entry itself
          val entrySeasons = entry.optJSONArray("seasons")
          if (entrySeasons != null) {
            for (si in 0 until entrySeasons.length()) {
              val sObj = entrySeasons.optJSONObject(si) ?: continue
              val sNum = sObj.optInt("number", 0)
              if (sNum <= 0) continue
              val eps = sObj.optJSONArray("episodes") ?: continue
              for (ei in 0 until eps.length()) {
                val eObj = eps.optJSONObject(ei) ?: continue
                if (!eObj.has("completed") || eObj.optBoolean("completed", false)) {
                  val epNum = eObj.optInt("number", 0)
                  if (epNum > 0) {
                    val key = TvPreferencesRepository.buildEpisodeWatchedKey(title, sNum, epNum)
                    if (key.isNotBlank()) watchedEpisodeKeys.add(key)
                  }
                }
              }
            }
          }

          val embeddedProgress = entry.optJSONObject("progress")
            ?: entry.takeIf { it.has("next_episode") || (it.has("aired") && it.has("completed")) }

          if (embeddedProgress != null) {
            directOutcomes.add(
              parseStrictUpNextEntry(
                showObj = showObj,
                progressObj = embeddedProgress,
                fallbackLastWatchedIso = fallbackLastWatched,
                nowMs = nowMs,
                watchedEpisodeKeys = watchedEpisodeKeys
              )
            )
          } else {
            needsShowProgressFetch.add(showObj to fallbackLastWatched)
          }
        }

        val isLowRamDevice = android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.N_MR1
        val progressSemaphore = Semaphore(if (isLowRamDevice) 4 else 8)
        val fetchedOutcomes = needsShowProgressFetch.map { (showObj, fallbackIso) ->
          async {
            progressSemaphore.withPermit {
              fetchShowProgressOutcome(
                clientId = clientId,
                accessToken = accessToken,
                showObj = showObj,
                fallbackLastWatchedIso = fallbackIso,
                nowMs = nowMs,
                watchedEpisodeKeys = watchedEpisodeKeys
              )
            }
          }
        }.awaitAll()

        val allOutcomes = directOutcomes + fetchedOutcomes
        val rawResults = mutableListOf<RawUpNextProgress>()
        var networkFailures = 0

        for (outcome in allOutcomes) {
          when (outcome) {
            is ProgressCheckOutcome.Active -> rawResults.add(outcome.progress)
            ProgressCheckOutcome.FilteredCompleted -> completedFilteredCount++
            ProgressCheckOutcome.FilteredNoNextEpisode -> noNextEpisodeFilteredCount++
            is ProgressCheckOutcome.Failed -> networkFailures++
          }
        }

        if (allOutcomes.isNotEmpty() && rawResults.isEmpty() && networkFailures > 0) {
          return@coroutineScope TraktFullSyncResult.Error("تعذر جلب تفاصيل الحلقات القادمة من Trakt — يرجى إعادة المحاولة")
        }

        // Rule 6: Sort by progress.last_watched_at descending
        val orderedRaw = rawResults.sortedByDescending { it.sortTimestampMs }

        val posterSemaphore = Semaphore(if (isLowRamDevice) 3 else 6)
        val baseNowMs = System.currentTimeMillis()
        val upNextItems = orderedRaw.mapIndexed { idx, raw ->
          async {
            val poster = posterSemaphore.withPermit {
              resolveShowPoster(raw.imdbId, raw.showTitle)
            }
            UpNextItem(
              id = "trakt_${raw.traktShowId}",
              seriesKey = TvPreferencesRepository.buildNormalizedContentKey(raw.showTitle, null, null, isSeries = false),
              showTitle = raw.showTitle,
              seasonNumber = raw.seasonNumber,
              episodeNumber = raw.episodeNumber,
              episodeTitle = raw.episodeTitle,
              firstAiredIso = raw.firstAiredIso,
              lastWatchedAtIso = raw.lastWatchedAtIso,
              lastWatchedAtMs = raw.sortTimestampMs,
              posterUrl = poster,
              watchedCount = raw.watchedCount,
              totalCount = raw.airedCount,
              traktSlug = raw.slug,
              traktShowId = raw.traktShowId,
              imdbId = raw.imdbId,
              traktEpisodeUrl = if (raw.slug.isNotBlank()) "https://trakt.tv/shows/${raw.slug}/seasons/${raw.seasonNumber}/episodes/${raw.episodeNumber}" else "",
              traktEpisodeId = raw.epTraktId,
              fromTrakt = true,
              preferredPortal = StreamingPortal.WATCH_SERIES,
              updatedAtMs = baseNowMs - (idx * 1000L)
            )
          }
        }.awaitAll()

        val username = usernameDeferred.await()
        val stats = TraktSyncStats(
          totalWatchedFetched = totalWatchedFetched,
          hiddenFilteredCount = hiddenFilteredCount,
          completedFilteredCount = completedFilteredCount,
          noNextEpisodeFilteredCount = noNextEpisodeFilteredCount,
          activeUpNextCount = upNextItems.size
        )

        AppDiagnosticsLogger.log(
          "TraktAPI",
          "Synced Trakt @$username via $sourceEndpoint: fetched=$totalWatchedFetched, filteredCompleted=$completedFilteredCount, filteredHidden=$hiddenFilteredCount, filteredNoNextEp=$noNextEpisodeFilteredCount -> activeUpNext=${upNextItems.size}"
        )

        val successResult = TraktFullSyncResult.Success(
          username = username,
          upNextItems = upNextItems,
          calendarItems = emptyList(),
          watchlistItems = emptyList(),
          stats = stats,
          watchedEpisodeKeys = watchedEpisodeKeys
        )
        cachedUpNextResult = successResult
        cachedUpNextToken = accessToken
        cachedUpNextTimestampMs = System.currentTimeMillis()
        successResult
      }
    } catch (e: Exception) {
      AppDiagnosticsLogger.log("TraktAPI", "Full sync error: ${e.message}", isError = true)
      TraktFullSyncResult.Error("خطأ في المزامنة مع Trakt: ${e.message ?: "تحقق من الاتصال"}")
    }
  }

  /**
   * Marks an episode as watched directly on the user's Trakt account via POST /sync/history
   * and immediately invalidates the 10-minute Up Next cache.
   */
  suspend fun markEpisodeWatched(
    clientId: String,
    accessToken: String,
    item: UpNextItem
  ): Boolean = withContext(Dispatchers.IO) {
    if (clientId.isBlank() || accessToken.isBlank()) return@withContext false
    try {
      val watchedAtIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
      }.format(Date())

      val bodyJson = JSONObject()
      val epTraktIdLong = item.traktEpisodeId.toLongOrNull() ?: 0L
      if (epTraktIdLong > 0L) {
        bodyJson.put(
          "episodes",
          JSONArray().put(
            JSONObject()
              .put("watched_at", watchedAtIso)
              .put("ids", JSONObject().put("trakt", epTraktIdLong))
          )
        )
      } else {
        val showObj = JSONObject().put("title", item.showTitle)
        val idsObj = JSONObject()
        if (item.traktShowId > 0L) idsObj.put("trakt", item.traktShowId)
        if (item.traktSlug.isNotBlank()) idsObj.put("slug", item.traktSlug)
        if (item.imdbId.isNotBlank()) idsObj.put("imdb", item.imdbId)
        if (idsObj.length() > 0) showObj.put("ids", idsObj)

        val epObj = JSONObject()
          .put("number", item.episodeNumber)
          .put("watched_at", watchedAtIso)
        val seasonObj = JSONObject()
          .put("number", item.seasonNumber)
          .put("episodes", JSONArray().put(epObj))
        showObj.put("seasons", JSONArray().put(seasonObj))

        bodyJson.put("shows", JSONArray().put(showObj))
      }

      val req = buildTraktRequest("$API_BASE/sync/history", clientId, accessToken)
        .post(bodyJson.toString().toRequestBody(jsonMediaType))
        .build()

      client.newCall(req).execute().use { resp ->
        AppDiagnosticsLogger.log("TraktAPI", "POST /sync/history -> HTTP ${resp.code}")
        if (item.traktShowId > 0L) verifiedCompletedShowsCache.remove(item.traktShowId) else verifiedCompletedShowsCache.clear()
        invalidateUpNextCache()
        resp.isSuccessful || resp.code == 201
      }
    } catch (e: Exception) {
      AppDiagnosticsLogger.log("TraktAPI", "markEpisodeWatched failed: ${e.message}", isError = true)
      false
    }
  }

  /**
   * Removes an episode from watched history on the user's Trakt account via POST /sync/history/remove
   * and immediately invalidates the 10-minute Up Next cache.
   */
  suspend fun unmarkEpisodeWatched(
    clientId: String,
    accessToken: String,
    item: UpNextItem
  ): Boolean = withContext(Dispatchers.IO) {
    if (clientId.isBlank() || accessToken.isBlank()) return@withContext false
    try {
      val bodyJson = JSONObject()
      val epTraktIdLong = item.traktEpisodeId.toLongOrNull() ?: 0L
      if (epTraktIdLong > 0L) {
        bodyJson.put(
          "episodes",
          JSONArray().put(
            JSONObject().put("ids", JSONObject().put("trakt", epTraktIdLong))
          )
        )
      } else {
        val showObj = JSONObject().put("title", item.showTitle)
        val idsObj = JSONObject()
        if (item.traktShowId > 0L) idsObj.put("trakt", item.traktShowId)
        if (item.traktSlug.isNotBlank()) idsObj.put("slug", item.traktSlug)
        if (item.imdbId.isNotBlank()) idsObj.put("imdb", item.imdbId)
        if (idsObj.length() > 0) showObj.put("ids", idsObj)

        val epObj = JSONObject().put("number", item.episodeNumber)
        val seasonObj = JSONObject()
          .put("number", item.seasonNumber)
          .put("episodes", JSONArray().put(epObj))
        showObj.put("seasons", JSONArray().put(seasonObj))

        bodyJson.put("shows", JSONArray().put(showObj))
      }

      val req = buildTraktRequest("$API_BASE/sync/history/remove", clientId, accessToken)
        .post(bodyJson.toString().toRequestBody(jsonMediaType))
        .build()

      client.newCall(req).execute().use { resp ->
        AppDiagnosticsLogger.log("TraktAPI", "POST /sync/history/remove -> HTTP ${resp.code}")
        if (item.traktShowId > 0L) verifiedCompletedShowsCache.remove(item.traktShowId) else verifiedCompletedShowsCache.clear()
        invalidateUpNextCache()
        resp.isSuccessful
      }
    } catch (e: Exception) {
      AppDiagnosticsLogger.log("TraktAPI", "unmarkEpisodeWatched failed: ${e.message}", isError = true)
      false
    }
  }

  /**
   * Official Trakt Scrobble API v2 (`POST /scrobble/start`, `POST /scrobble/pause`, `POST /scrobble/stop`).
   * Calling `stop` with progress >= 80% automatically marks the episode as watched on Trakt.
   */
  suspend fun scrobbleEpisode(
    clientId: String,
    accessToken: String,
    action: String, // "start", "pause", or "stop"
    item: UpNextItem,
    progressPercent: Float = 100f
  ): Boolean = withContext(Dispatchers.IO) {
    if (clientId.isBlank() || accessToken.isBlank()) return@withContext false
    val cleanAction = action.lowercase(Locale.US).trim()
    if (cleanAction !in listOf("start", "pause", "stop")) return@withContext false
    try {
      val clampedProgress = progressPercent.coerceIn(1f, 100f)
      val bodyJson = JSONObject().put("progress", clampedProgress.toDouble())
      val epTraktIdLong = item.traktEpisodeId.toLongOrNull() ?: 0L
      if (epTraktIdLong > 0L) {
        bodyJson.put(
          "episode",
          JSONObject().put("ids", JSONObject().put("trakt", epTraktIdLong))
        )
      } else {
        val showObj = JSONObject().put("title", item.showTitle)
        val idsObj = JSONObject()
        if (item.traktShowId > 0L) idsObj.put("trakt", item.traktShowId)
        if (item.traktSlug.isNotBlank()) idsObj.put("slug", item.traktSlug)
        if (item.imdbId.isNotBlank()) idsObj.put("imdb", item.imdbId)
        if (idsObj.length() > 0) showObj.put("ids", idsObj)
        bodyJson.put("show", showObj)
        bodyJson.put(
          "episode",
          JSONObject()
            .put("season", item.seasonNumber.coerceAtLeast(1))
            .put("number", item.episodeNumber.coerceAtLeast(1))
        )
      }

      val req = buildTraktRequest("$API_BASE/scrobble/$cleanAction", clientId, accessToken)
        .post(bodyJson.toString().toRequestBody(jsonMediaType))
        .build()

      val scrobbleOk = client.newCall(req).execute().use { resp ->
        AppDiagnosticsLogger.log("TraktAPI", "POST /scrobble/$cleanAction (${clampedProgress.toInt()}%) -> HTTP ${resp.code}")
        if (cleanAction == "stop") {
          if (item.traktShowId > 0L) verifiedCompletedShowsCache.remove(item.traktShowId) else verifiedCompletedShowsCache.clear()
          invalidateUpNextCache()
        }
        // 409 Conflict on /scrobble/stop means Trakt already scrobbled this episode recently
        resp.isSuccessful || resp.code == 201 || (cleanAction == "stop" && resp.code == 409)
      }
      if (!scrobbleOk && cleanAction == "stop" && clampedProgress >= 80f) {
        return@withContext markEpisodeWatched(clientId, accessToken, item)
      }
      scrobbleOk
    } catch (e: Exception) {
      AppDiagnosticsLogger.log("TraktAPI", "scrobbleEpisode($cleanAction) failed: ${e.message}", isError = true)
      if (cleanAction == "stop" && progressPercent >= 80f) {
        markEpisodeWatched(clientId, accessToken, item)
      } else {
        false
      }
    }
  }

  /**
   * Resolves a real high-res show poster via TVMaze's free public API (no API key required)
   * using IMDb ID first, then title fallback, with in-memory caching.
   */
  private fun resolveShowPoster(imdbId: String, showTitle: String): String {
    val cacheKey = imdbId.ifBlank { showTitle.lowercase(Locale.US) }
    posterCache[cacheKey]?.let { return it }

    val candidateUrls = buildList {
      if (imdbId.startsWith("tt")) {
        add("https://api.tvmaze.com/lookup/shows?imdb=${URLEncoder.encode(imdbId, "UTF-8")}")
      }
      if (showTitle.isNotBlank()) {
        add("https://api.tvmaze.com/singlesearch/shows?q=${URLEncoder.encode(showTitle, "UTF-8")}")
      }
    }

    for (url in candidateUrls) {
      try {
        val req = Request.Builder()
          .url(url)
          .header("Accept", "application/json")
          .build()
        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) return@use
          val obj = JSONObject(resp.body?.string().orEmpty())
          val img = obj.optJSONObject("image")
          val poster = img?.optString("medium", "")?.ifBlank { img.optString("original", "") }.orEmpty().trim()
          if (poster.startsWith("http")) {
            val httpsPoster = poster.replaceFirst("http://", "https://")
            posterCache[cacheKey] = httpsPoster
            return httpsPoster
          }
        }
      } catch (_: Exception) {}
    }

    posterCache[cacheKey] = ""
    return ""
  }

  companion object {
    const val API_BASE = "https://api.trakt.tv"
    const val REDIRECT_URI_OOB = "urn:ietf:wg:oauth:2.0:oob"
    const val TRAKT_CREATE_APP_URL = "https://trakt.tv/oauth/applications/new"
    const val TRAKT_ACTIVATE_URL = "https://trakt.tv/activate"
    const val UP_NEXT_CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes cache TTL

    private val posterCache = ConcurrentHashMap<String, String>()
    private val verifiedCompletedShowsCache = ConcurrentHashMap<Long, Long>()

    @Volatile
    private var cachedUpNextResult: TraktFullSyncResult.Success? = null

    @Volatile
    private var cachedUpNextToken: String = ""

    @Volatile
    private var cachedUpNextTimestampMs: Long = 0L
  }
}
