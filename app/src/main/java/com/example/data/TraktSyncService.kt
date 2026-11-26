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

sealed class TraktFullSyncResult {
  data class Success(
    val username: String,
    val upNextItems: List<UpNextItem>,
    val calendarItems: List<TraktCalendarItem>,
    val watchlistItems: List<TraktWatchlistItem>
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
  private val posterCache = ConcurrentHashMap<String, String>()

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
   * Fetches Trakt IDs of shows hidden from watched progress or dropped by the user,
   * so they never appear in "Next to Watch" (matching Showly).
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
            val id = arr.optJSONObject(i)?.optJSONObject("show")?.optJSONObject("ids")?.optLong("trakt", 0L) ?: 0L
            if (id > 0L) hidden.add(id)
          }
        }
      } catch (_: Exception) {}
    }
    return hidden
  }

  private data class CandidateShowStub(
    val traktId: Long,
    val slug: String,
    val imdbId: String,
    val title: String,
    val sortTimestampMs: Long,
    val fromWatchlistOnly: Boolean
  )

  private data class RawUpNextProgress(
    val traktShowId: Long,
    val slug: String,
    val imdbId: String,
    val showTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String,
    val epTraktId: String,
    val watchedCount: Int,
    val airedCount: Int,
    val sortTimestampMs: Long
  )

  /**
   * Performs a complete official Trakt API v2 "Next to Watch" (Progress) sync matching Showly:
   *  - Fetches ALL watched shows (/sync/watched/shows) + Watchlist shows (/sync/watchlist/shows)
   *  - Excludes hidden/dropped shows (/users/hidden/progress_watched + /users/hidden/dropped)
   *  - Keeps ONLY shows that have at least 1 already-aired unwatched episode (aired > 0 && completed < aired)
   *    — NEVER includes finished (0 left) or unaired/upcoming shows!
   *  - Sorts strictly by most recently watched on Trakt (watched shows by last_watched_at DESC,
   *    followed by unwatched aired watchlist shows by listed_at DESC)
   *  - Resolves show posters only for the surviving Up Next shows for fast sync performance.
   */
  suspend fun syncFullAccountData(
    clientId: String,
    accessToken: String
  ): TraktFullSyncResult = withContext(Dispatchers.IO) {
    if (clientId.isBlank() || accessToken.isBlank()) {
      return@withContext TraktFullSyncResult.Unauthorized
    }

    try {
      // 1. Validate token & fetch ALL watched shows
      val watchedReq = buildTraktRequest(
        "$API_BASE/sync/watched/shows?extended=noseasons",
        clientId,
        accessToken
      ).get().build()

      val watchedArray = client.newCall(watchedReq).execute().use { resp ->
        if (resp.code == 401 || resp.code == 403) {
          return@withContext TraktFullSyncResult.Unauthorized
        }
        if (!resp.isSuccessful) {
          return@withContext TraktFullSyncResult.Error("تعذر جلب بيانات Trakt (HTTP ${resp.code})")
        }
        JSONArray(resp.body?.string().orEmpty().ifBlank { "[]" })
      }

      coroutineScope {
        val usernameDeferred = async { fetchUserProfile(clientId, accessToken) }
        val hiddenIdsDeferred = async { fetchHiddenShowIds(clientId, accessToken) }
        val watchlistShowsDeferred = async {
          try {
            val wlReq = buildTraktRequest("$API_BASE/sync/watchlist/shows", clientId, accessToken).get().build()
            client.newCall(wlReq).execute().use { resp ->
              if (resp.isSuccessful) JSONArray(resp.body?.string().orEmpty().ifBlank { "[]" })
              else JSONArray()
            }
          } catch (_: Exception) {
            JSONArray()
          }
        }

        val hiddenIds = hiddenIdsDeferred.await()
        val watchlistArray = watchlistShowsDeferred.await()

        val seenIds = mutableSetOf<Long>()
        val watchedStubs = mutableListOf<CandidateShowStub>()
        for (i in 0 until watchedArray.length()) {
          val entry = watchedArray.optJSONObject(i) ?: continue
          val showObj = entry.optJSONObject("show") ?: continue
          val ids = showObj.optJSONObject("ids") ?: continue
          val traktId = ids.optLong("trakt", 0L)
          val title = showObj.optString("title", "").trim()
          if (traktId <= 0L || title.isBlank() || traktId in hiddenIds || !seenIds.add(traktId)) continue
          val lastWatchedMs = parseIsoUtcMs(entry.optString("last_watched_at", ""))
            .takeIf { it > 0L }
            ?: parseIsoUtcMs(entry.optString("last_updated_at", ""))
          watchedStubs.add(
            CandidateShowStub(
              traktId = traktId,
              slug = ids.optString("slug", "").trim(),
              imdbId = ids.optString("imdb", "").trim(),
              title = title,
              sortTimestampMs = lastWatchedMs,
              fromWatchlistOnly = false
            )
          )
        }

        val watchlistStubs = mutableListOf<CandidateShowStub>()
        for (i in 0 until watchlistArray.length()) {
          val entry = watchlistArray.optJSONObject(i) ?: continue
          val showObj = entry.optJSONObject("show") ?: continue
          val ids = showObj.optJSONObject("ids") ?: continue
          val traktId = ids.optLong("trakt", 0L)
          val title = showObj.optString("title", "").trim()
          if (traktId <= 0L || title.isBlank() || traktId in hiddenIds || !seenIds.add(traktId)) continue
          val listedAtMs = parseIsoUtcMs(entry.optString("listed_at", ""))
          watchlistStubs.add(
            CandidateShowStub(
              traktId = traktId,
              slug = ids.optString("slug", "").trim(),
              imdbId = ids.optString("imdb", "").trim(),
              title = title,
              sortTimestampMs = listedAtMs,
              fromWatchlistOnly = true
            )
          )
        }

        val allCandidates = buildList {
          addAll(watchedStubs.sortedByDescending { it.sortTimestampMs }.take(150))
          addAll(watchlistStubs.sortedByDescending { it.sortTimestampMs }.take(80))
        }

        val progressSemaphore = Semaphore(8)
        val rawResults = allCandidates.map { stub ->
          async {
            progressSemaphore.withPermit {
              fetchShowProgressRaw(
                clientId = clientId,
                accessToken = accessToken,
                stub = stub
              )
            }
          }
        }.awaitAll().filterNotNull()

        // Sort strictly like Showly "Recently Watched":
        // 1. Shows with watched episodes (completed > 0) sorted by Trakt last_watched_at DESC
        // 2. Unwatched watchlist shows with aired episodes (completed == 0) sorted by listed_at DESC
        val orderedRaw = rawResults.sortedWith(
          compareByDescending<RawUpNextProgress> { it.watchedCount > 0 }
            .thenByDescending { it.sortTimestampMs }
        )

        // Resolve posters concurrently ONLY for surviving unwatched shows
        val posterSemaphore = Semaphore(8)
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

        AppDiagnosticsLogger.log(
          "TraktAPI",
          "Synced Trakt @$username: checked=${allCandidates.size} (watched=${watchedStubs.size}, wl=${watchlistStubs.size}, hidden=${hiddenIds.size}) -> activeUpNext=${upNextItems.size}"
        )

        TraktFullSyncResult.Success(
          username = username,
          upNextItems = upNextItems,
          calendarItems = emptyList(),
          watchlistItems = emptyList()
        )
      }
    } catch (e: Exception) {
      AppDiagnosticsLogger.log("TraktAPI", "Full sync error: ${e.message}", isError = true)
      TraktFullSyncResult.Error("خطأ في المزامنة: ${e.message ?: "تحقق من الاتصال"}")
    }
  }

  /**
   * Fetches a single show's Trakt watched progress and STRICTLY filters to shows with
   * at least one already-aired unwatched episode (`aired > 0 && completed < aired`).
   * Finished shows (`0 left`) and upcoming unaired shows return null immediately.
   */
  private suspend fun fetchShowProgressRaw(
    clientId: String,
    accessToken: String,
    stub: CandidateShowStub
  ): RawUpNextProgress? {
    val url = "$API_BASE/shows/${stub.traktId}/progress/watched?hidden=false&specials=false&count_specials=false"
    for (attempt in 0..1) {
      try {
        val req = buildTraktRequest(url, clientId, accessToken).get().build()
        val statusAndBody = client.newCall(req).execute().use { resp ->
          resp.code to resp.body?.string().orEmpty()
        }
        val code = statusAndBody.first
        val body = statusAndBody.second
        if (code == 429 && attempt == 0) {
          kotlinx.coroutines.delay(650L)
          continue
        }
        if (code !in 200..299 || body.isBlank()) return null

        val obj = JSONObject(body)
        val aired = obj.optInt("aired", 0).coerceAtLeast(0)
        val completed = obj.optInt("completed", 0).coerceAtLeast(0)

        // STRICT FILTER: Must have aired episodes AND at least 1 unwatched aired episode!
        // Excludes finished shows (35/35, 14/14, 30/30 -> 0 left) and unaired upcoming premieres (0/0).
        if (aired <= 0 || completed >= aired) return null

        val nextEp = obj.optJSONObject("next_episode")
        var season = nextEp?.optInt("season", 0) ?: 0
        var epNum = nextEp?.optInt("number", 0) ?: 0
        var epTitle = nextEp?.optString("title", "").orEmpty().trim()
        var epTraktId = nextEp?.optJSONObject("ids")?.optLong("trakt", 0L)
          ?.takeIf { it > 0L }?.toString().orEmpty()

        // Fallback: if next_episode is missing despite completed < aired, find first unwatched aired episode in seasons
        if (season <= 0 || epNum <= 0) {
          val seasonsArr = obj.optJSONArray("seasons")
          if (seasonsArr != null) {
            outer@ for (si in 0 until seasonsArr.length()) {
              val sObj = seasonsArr.optJSONObject(si) ?: continue
              val sNum = sObj.optInt("number", 0)
              val sAired = sObj.optInt("aired", 0)
              val sCompleted = sObj.optInt("completed", 0)
              if (sNum <= 0 || sAired <= 0 || sCompleted >= sAired) continue
              val epsArr = sObj.optJSONArray("episodes") ?: continue
              for (ei in 0 until epsArr.length()) {
                val eObj = epsArr.optJSONObject(ei) ?: continue
                if (!eObj.optBoolean("completed", false)) {
                  val eNum = eObj.optInt("number", 0)
                  if (eNum > 0) {
                    season = sNum
                    epNum = eNum
                    break@outer
                  }
                }
              }
            }
          }
        }
        if (season <= 0 || epNum <= 0) return null

        val progressLastWatchedMs = parseIsoUtcMs(obj.optString("last_watched_at", ""))
        val finalSortMs = if (completed > 0) {
          maxOf(stub.sortTimestampMs, progressLastWatchedMs)
        } else {
          stub.sortTimestampMs
        }

        return RawUpNextProgress(
          traktShowId = stub.traktId,
          slug = stub.slug,
          imdbId = stub.imdbId,
          showTitle = stub.title,
          seasonNumber = season,
          episodeNumber = epNum,
          episodeTitle = epTitle,
          epTraktId = epTraktId,
          watchedCount = completed,
          airedCount = aired,
          sortTimestampMs = finalSortMs
        )
      } catch (_: Exception) {
        return null
      }
    }
    return null
  }

  /**
   * Marks an episode as watched directly on the user's Trakt account via POST /sync/history.
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
        resp.isSuccessful || resp.code == 201
      }
    } catch (e: Exception) {
      AppDiagnosticsLogger.log("TraktAPI", "markEpisodeWatched failed: ${e.message}", isError = true)
      false
    }
  }

  /**
   * Immediately fetches the updated next_episode for a single show right after marking an episode watched
   * so season rollovers (e.g., S01E10 -> S02E01) and real episode titles appear immediately.
   * Returns null if all aired episodes of this show are now watched (so UI removes it from Up Next).
   */
  suspend fun fetchSingleShowNextUp(
    clientId: String,
    accessToken: String,
    item: UpNextItem
  ): UpNextItem? = withContext(Dispatchers.IO) {
    if (clientId.isBlank() || accessToken.isBlank() || item.traktShowId <= 0L) return@withContext null
    val raw = fetchShowProgressRaw(
      clientId = clientId,
      accessToken = accessToken,
      stub = CandidateShowStub(
        traktId = item.traktShowId,
        slug = item.traktSlug,
        imdbId = item.imdbId,
        title = item.showTitle,
        sortTimestampMs = System.currentTimeMillis(),
        fromWatchlistOnly = false
      )
    ) ?: return@withContext null

    val poster = item.posterUrl.ifBlank { resolveShowPoster(raw.imdbId, raw.showTitle) }
    UpNextItem(
      id = "trakt_${raw.traktShowId}",
      seriesKey = TvPreferencesRepository.buildNormalizedContentKey(raw.showTitle, null, null, isSeries = false),
      showTitle = raw.showTitle,
      seasonNumber = raw.seasonNumber,
      episodeNumber = raw.episodeNumber,
      episodeTitle = raw.episodeTitle,
      posterUrl = poster,
      watchedCount = raw.watchedCount,
      totalCount = raw.airedCount,
      traktSlug = raw.slug,
      traktShowId = raw.traktShowId,
      imdbId = raw.imdbId,
      traktEpisodeUrl = if (raw.slug.isNotBlank()) "https://trakt.tv/shows/${raw.slug}/seasons/${raw.seasonNumber}/episodes/${raw.episodeNumber}" else "",
      traktEpisodeId = raw.epTraktId,
      fromTrakt = true,
      preferredPortal = item.preferredPortal,
      updatedAtMs = System.currentTimeMillis()
    )
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
  }
}
