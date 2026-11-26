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
   * Fetches Trakt IDs of shows hidden from watched progress, calendar, recommendations, or dropped by the user,
   * so they never appear in "Next to Watch" (matching official Trakt Continue Watching & Showly).
   */
  private fun fetchHiddenShowIds(clientId: String, accessToken: String): Set<Long> {
    val hidden = mutableSetOf<Long>()
    val endpoints = listOf(
      "$API_BASE/users/hidden/progress_watched?limit=100",
      "$API_BASE/users/hidden/progress_watched_reset?limit=100",
      "$API_BASE/users/hidden/progress_collected?limit=100",
      "$API_BASE/users/hidden/calendar?limit=100",
      "$API_BASE/users/hidden/dropped?limit=100"
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
            // Only hide the whole show if the hidden entry is of type "show"
            // (hidden seasons are already handled by ?hidden=false on /progress/watched)
            if (type == "show" || !item.has("season")) {
              val id = item.optJSONObject("show")?.optJSONObject("ids")?.optLong("trakt", 0L) ?: 0L
              if (id > 0L) hidden.add(id)
            }
          }
        }
      } catch (_: Exception) {}
    }
    return hidden
  }

  /**
   * Fetches shows from all of the user's custom Trakt personal lists (`/users/me/lists` -> `/users/me/lists/{id}/items`),
   * because many users (and apps like Showly "My Shows" / Trakt Lists) store their "Next to Watch" shows in custom lists
   * in addition to or instead of the default Watchlist!
   */
  private fun fetchCustomListsShows(clientId: String, accessToken: String): JSONArray {
    val combined = JSONArray()
    try {
      val listsReq = buildTraktRequest("$API_BASE/users/me/lists", clientId, accessToken).get().build()
      val listIds = mutableListOf<String>()
      client.newCall(listsReq).execute().use { resp ->
        if (resp.isSuccessful) {
          val arr = JSONArray(resp.body?.string().orEmpty().ifBlank { "[]" })
          for (i in 0 until arr.length()) {
            val idsObj = arr.optJSONObject(i)?.optJSONObject("ids")
            val slugOrId = idsObj?.optString("slug", "").orEmpty().ifBlank {
              idsObj?.optLong("trakt", 0L)?.takeIf { it > 0L }?.toString().orEmpty()
            }
            if (slugOrId.isNotBlank()) listIds.add(slugOrId)
          }
        }
      }
      for (listId in listIds.take(12)) {
        try {
          val itemsReq = buildTraktRequest("$API_BASE/users/me/lists/$listId/items", clientId, accessToken).get().build()
          client.newCall(itemsReq).execute().use { resp ->
            if (resp.isSuccessful) {
              val itemsArr = JSONArray(resp.body?.string().orEmpty().ifBlank { "[]" })
              for (j in 0 until itemsArr.length()) {
                val itemObj = itemsArr.optJSONObject(j) ?: continue
                if (itemObj.optJSONObject("show") != null) {
                  combined.put(itemObj)
                }
              }
            }
          }
        } catch (_: Exception) {}
      }
    } catch (_: Exception) {}
    return combined
  }

  /**
   * Fetches the user's collected shows (`/sync/collection/shows?extended=noseasons`) so any show
   * added to the user's Trakt Collection / My Shows in Showly is also checked for unwatched episodes.
   */
  private fun fetchCollectionShows(clientId: String, accessToken: String): JSONArray {
    return try {
      val req = buildTraktRequest("$API_BASE/sync/collection/shows?extended=noseasons", clientId, accessToken).get().build()
      client.newCall(req).execute().use { resp ->
        if (resp.isSuccessful) JSONArray(resp.body?.string().orEmpty().ifBlank { "[]" })
        else JSONArray()
      }
    } catch (_: Exception) {
      JSONArray()
    }
  }

  private data class CandidateShowStub(
    val traktId: Long,
    val slug: String,
    val imdbId: String,
    val title: String,
    val sortTimestampMs: Long,
    val hasUnwatchedHint: Boolean = true,
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
   * Performs a complete official Trakt API v2 "Next to Watch" (Progress) sync matching Showly & Trakt Continue Watching:
   *  - Fetches ALL watched shows (/sync/watched/shows?extended=full) + Watchlist (/sync/watchlist)
   *    + Personal Custom Lists (/users/me/lists/{id}/items) + Collection (/sync/collection/shows) + Favorites
   *  - Pre-filters long-finished shows using `show.aired_episodes` vs watched regular episodes so Trakt's rate limit is never hit
   *  - Excludes hidden/dropped shows (/users/hidden/progress_watched + /users/hidden/dropped + /users/hidden/calendar)
   *  - Keeps ONLY shows that have a real, already-aired `next_episode` (`aired > 0 && completed < aired && next_episode != null`)
   *  - Sorts strictly by most recently watched on Trakt (`last_watched_at` DESC, followed by unwatched aired watchlist/list shows)
   */
  suspend fun syncFullAccountData(
    clientId: String,
    accessToken: String
  ): TraktFullSyncResult = withContext(Dispatchers.IO) {
    if (clientId.isBlank() || accessToken.isBlank()) {
      return@withContext TraktFullSyncResult.Unauthorized
    }

    try {
      // 1. Validate token & fetch ALL watched shows WITH seasons & extended=full
      // This single request gives us show.aired_episodes + watched seasons/episodes for all shows!
      val watchedReq = buildTraktRequest(
        "$API_BASE/sync/watched/shows?extended=full",
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
            // Use /sync/watchlist (all types: show, season, episode) so watchlisted seasons/episodes also yield their parent show!
            val wlReq = buildTraktRequest("$API_BASE/sync/watchlist", clientId, accessToken).get().build()
            client.newCall(wlReq).execute().use { resp ->
              if (resp.isSuccessful) JSONArray(resp.body?.string().orEmpty().ifBlank { "[]" })
              else JSONArray()
            }
          } catch (_: Exception) {
            JSONArray()
          }
        }
        val customListsShowsDeferred = async { fetchCustomListsShows(clientId, accessToken) }
        val collectionShowsDeferred = async { fetchCollectionShows(clientId, accessToken) }
        val favoritesShowsDeferred = async {
          try {
            val favReq = buildTraktRequest("$API_BASE/sync/favorites/shows", clientId, accessToken).get().build()
            client.newCall(favReq).execute().use { resp ->
              if (resp.isSuccessful) JSONArray(resp.body?.string().orEmpty().ifBlank { "[]" })
              else JSONArray()
            }
          } catch (_: Exception) {
            JSONArray()
          }
        }

        val hiddenIds = hiddenIdsDeferred.await()
        val watchlistArray = watchlistShowsDeferred.await()
        val customListsArray = customListsShowsDeferred.await()
        val collectionArray = collectionShowsDeferred.await()
        val favoritesArray = favoritesShowsDeferred.await()

        val seenIds = mutableSetOf<Long>()
        val finishedWatchedIds = mutableSetOf<Long>()
        val watchedStubs = mutableListOf<CandidateShowStub>()
        val nowMs = System.currentTimeMillis()
        val recentThresholdMs = nowMs - (14L * 24L * 3600_000L) // 14 days

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

          val totalAiredFromShow = showObj.optInt("aired_episodes", 0)
          val seasonsArr = entry.optJSONArray("seasons")
          var watchedRegularEps = 0
          if (seasonsArr != null) {
            for (si in 0 until seasonsArr.length()) {
              val sObj = seasonsArr.optJSONObject(si) ?: continue
              val sNum = sObj.optInt("number", 0)
              if (sNum > 0) {
                watchedRegularEps += (sObj.optJSONArray("episodes")?.length() ?: 0)
              }
            }
          }

          val likelyHasUnwatched = totalAiredFromShow <= 0 || watchedRegularEps < totalAiredFromShow
          val watchedRecently = lastWatchedMs >= recentThresholdMs

          // If the user has already watched 100% of aired episodes and hasn't touched this finished show recently,
          // skip calling /shows/{id}/progress/watched so we don't waste 80+ HTTP requests on old completed shows!
          if (!likelyHasUnwatched && !watchedRecently) {
            finishedWatchedIds.add(traktId)
            continue
          }

          watchedStubs.add(
            CandidateShowStub(
              traktId = traktId,
              slug = ids.optString("slug", "").trim(),
              imdbId = ids.optString("imdb", "").trim(),
              title = title,
              sortTimestampMs = lastWatchedMs,
              hasUnwatchedHint = likelyHasUnwatched,
              fromWatchlistOnly = false
            )
          )
        }

        val watchlistStubs = mutableListOf<CandidateShowStub>()
        fun collectAdditionalShows(arr: JSONArray, timestampField: String) {
          for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            val showObj = entry.optJSONObject("show") ?: continue
            val ids = showObj.optJSONObject("ids") ?: continue
            val traktId = ids.optLong("trakt", 0L)
            val title = showObj.optString("title", "").trim()
            if (traktId <= 0L || title.isBlank() || traktId in hiddenIds || traktId in finishedWatchedIds || !seenIds.add(traktId)) continue
            val tsMs = parseIsoUtcMs(entry.optString(timestampField, ""))
              .takeIf { it > 0L }
              ?: parseIsoUtcMs(entry.optString("listed_at", ""))
              .takeIf { it > 0L }
              ?: parseIsoUtcMs(entry.optString("last_collected_at", ""))
            watchlistStubs.add(
              CandidateShowStub(
                traktId = traktId,
                slug = ids.optString("slug", "").trim(),
                imdbId = ids.optString("imdb", "").trim(),
                title = title,
                sortTimestampMs = tsMs,
                hasUnwatchedHint = true,
                fromWatchlistOnly = true
              )
            )
          }
        }

        collectAdditionalShows(watchlistArray, "listed_at")
        collectAdditionalShows(customListsArray, "listed_at")
        collectAdditionalShows(favoritesArray, "listed_at")
        collectAdditionalShows(collectionArray, "last_collected_at")

        // Prioritize shows that definitely have unwatched episodes first, sorted by most recently watched
        val allCandidates = buildList {
          addAll(
            watchedStubs.sortedWith(
              compareByDescending<CandidateShowStub> { it.hasUnwatchedHint }
                .thenByDescending { it.sortTimestampMs }
            )
          )
          addAll(watchlistStubs.sortedByDescending { it.sortTimestampMs }.take(80))
        }

        // Concurrency capped at 4 with automatic Retry-After backoff so Trakt never drops a single show!
        val progressSemaphore = Semaphore(4)
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

        // Sort strictly like Showly & Trakt "Continue Watching" (Recently Watched):
        // 1. Shows with watched episodes (completed > 0) sorted by Trakt last_watched_at DESC
        // 2. Unwatched watchlist/custom-list shows with aired episodes (completed == 0) sorted by listed_at DESC
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
          "Synced Trakt @$username: totalWatched=${watchedArray.length()}, preSkippedFinished=${finishedWatchedIds.size}, queried=${allCandidates.size} (activeWatched=${watchedStubs.size}, extraLists=${watchlistStubs.size}, hidden=${hiddenIds.size}) -> activeUpNext=${upNextItems.size}"
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
   * Fetches a single show's Trakt watched progress with `&extended=full` and STRICTLY filters to shows with
   * a real, already-aired `next_episode` (`aired > 0 && completed < aired && next_episode != null`).
   * Finished shows (`0 left`), shows with `next_episode == null` (e.g. remaining episodes are unaired/hidden),
   * and future/TBA episodes return null immediately.
   */
  private suspend fun fetchShowProgressRaw(
    clientId: String,
    accessToken: String,
    stub: CandidateShowStub
  ): RawUpNextProgress? {
    val url = "$API_BASE/shows/${stub.traktId}/progress/watched?hidden=false&specials=false&count_specials=false&extended=full"
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
        if (code !in 200..299 || body.isBlank()) return null

        val obj = JSONObject(body)
        val aired = obj.optInt("aired", 0).coerceAtLeast(0)
        val completed = obj.optInt("completed", 0).coerceAtLeast(0)

        // STRICT FILTER 1: Must have aired episodes AND at least 1 unwatched aired episode!
        if (aired <= 0 || completed >= aired) return null

        // STRICT FILTER 2: Trakt MUST provide a non-null `next_episode`!
        // When `next_episode` is null on Trakt (even if completed < aired), it means the remaining episode
        // has not aired yet or is in a hidden season (like The Boys S05E04). Never synthesize a fake episode!
        val nextEp = obj.optJSONObject("next_episode") ?: return null
        val season = nextEp.optInt("season", 0)
        val epNum = nextEp.optInt("number", 0)
        if (season <= 0 || epNum <= 0) return null

        val rawTitle = nextEp.optString("title", "").trim()
        val epTitle = if (rawTitle.equals("null", ignoreCase = true)) "" else rawTitle
        val epTraktId = nextEp.optJSONObject("ids")?.optLong("trakt", 0L)
          ?.takeIf { it > 0L }?.toString().orEmpty()

        // STRICT FILTER 3: Check `first_aired` from `&extended=full` on `next_episode`.
        // If `first_aired` is in the future, or if it has no air date AND a blank/TBA title,
        // the episode has NOT aired yet — exclude it from "Next to Watch"!
        val firstAiredIso = nextEp.optString("first_aired", "").trim()
        val hasValidFirstAired = firstAiredIso.isNotBlank() && !firstAiredIso.equals("null", ignoreCase = true)
        if (hasValidFirstAired) {
          val firstAiredMs = parseIsoUtcMs(firstAiredIso)
          if (firstAiredMs > System.currentTimeMillis() + 3600_000L) {
            return null
          }
        } else {
          // No air date on Trakt: if title is also blank or TBA, it's an unaired placeholder
          if (epTitle.isBlank() || epTitle.equals("TBA", ignoreCase = true)) {
            return null
          }
        }

        val progressLastWatchedMs = parseIsoUtcMs(obj.optString("last_watched_at", ""))
        val finalSortMs = if (completed > 0) {
          if (progressLastWatchedMs > 0L) progressLastWatchedMs else stub.sortTimestampMs
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
        if (attempt < 3) {
          kotlinx.coroutines.delay(800L * (attempt + 1))
          continue
        }
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
