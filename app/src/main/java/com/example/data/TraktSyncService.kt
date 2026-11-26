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
      val payload = JSONObject()
        .put("code", deviceCode.trim())
        .put("client_id", clientId.trim())
        .put("client_secret", clientSecret.trim())
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
    if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) return@withContext null
    try {
      val payload = JSONObject()
        .put("refresh_token", refreshToken.trim())
        .put("client_id", clientId.trim())
        .put("client_secret", clientSecret.trim())
        .put("redirect_uri", REDIRECT_URI_OOB)
        .put("grant_type", "refresh_token")
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
   * Performs a full official Trakt API v2 sync:
   *  - Username (/users/settings)
   *  - Up Next / Progress (/sync/watched/shows + /shows/{id}/progress/watched)
   *  - Upcoming 14-day Calendar (/calendars/my/shows/{today}/14)
   *  - Watchlist Shows & Movies (/sync/watchlist/shows + /movies)
   *  - Poster enrichment via lightweight public TVMaze lookup (zero API key needed)
   */
  suspend fun syncFullAccountData(
    clientId: String,
    accessToken: String
  ): TraktFullSyncResult = withContext(Dispatchers.IO) {
    if (clientId.isBlank() || accessToken.isBlank()) {
      return@withContext TraktFullSyncResult.Unauthorized
    }

    try {
      // 1. Validate token & fetch watched shows
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
        val calendarDeferred = async { fetchCalendar(clientId, accessToken) }
        val watchlistDeferred = async { fetchWatchlist(clientId, accessToken) }

        // Sort watched shows by most recently watched and fetch progress for top 28 active shows
        data class WatchedShowStub(
          val traktId: Long,
          val slug: String,
          val imdbId: String,
          val title: String,
          val lastWatchedAt: String,
          val orderIndex: Int
        )

        val stubs = mutableListOf<WatchedShowStub>()
        for (i in 0 until watchedArray.length()) {
          val entry = watchedArray.optJSONObject(i) ?: continue
          val showObj = entry.optJSONObject("show") ?: continue
          val ids = showObj.optJSONObject("ids") ?: continue
          val traktId = ids.optLong("trakt", 0L)
          val title = showObj.optString("title", "").trim()
          if (traktId <= 0L || title.isBlank()) continue
          stubs.add(
            WatchedShowStub(
              traktId = traktId,
              slug = ids.optString("slug", "").trim(),
              imdbId = ids.optString("imdb", "").trim(),
              title = title,
              lastWatchedAt = entry.optString("last_watched_at", ""),
              orderIndex = i
            )
          )
        }

        val sortedStubs = stubs
          .sortedByDescending { it.lastWatchedAt }
          .take(28)

        val semaphore = Semaphore(5)
        val upNextDeferreds = sortedStubs.mapIndexed { idx, stub ->
          async {
            semaphore.withPermit {
              fetchShowProgress(
                clientId = clientId,
                accessToken = accessToken,
                traktShowId = stub.traktId,
                slug = stub.slug,
                imdbId = stub.imdbId,
                showTitle = stub.title,
                orderIndex = idx
              )
            }
          }
        }

        val upNextItems = upNextDeferreds.awaitAll().filterNotNull()
        val username = usernameDeferred.await()
        val calendarItems = calendarDeferred.await()
        val watchlistItems = watchlistDeferred.await()

        AppDiagnosticsLogger.log(
          "TraktAPI",
          "Synced Trakt @$username: upNext=${upNextItems.size}, calendar=${calendarItems.size}, watchlist=${watchlistItems.size}"
        )

        TraktFullSyncResult.Success(
          username = username,
          upNextItems = upNextItems,
          calendarItems = calendarItems,
          watchlistItems = watchlistItems
        )
      }
    } catch (e: Exception) {
      AppDiagnosticsLogger.log("TraktAPI", "Full sync error: ${e.message}", isError = true)
      TraktFullSyncResult.Error("خطأ في المزامنة: ${e.message ?: "تحقق من الاتصال"}")
    }
  }

  private suspend fun fetchShowProgress(
    clientId: String,
    accessToken: String,
    traktShowId: Long,
    slug: String,
    imdbId: String,
    showTitle: String,
    orderIndex: Int
  ): UpNextItem? {
    return try {
      val req = buildTraktRequest(
        "$API_BASE/shows/$traktShowId/progress/watched?hidden=false&specials=false&count_specials=false",
        clientId,
        accessToken
      ).get().build()

      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val obj = JSONObject(resp.body?.string().orEmpty())
        val nextEp = obj.optJSONObject("next_episode") ?: return null
        val season = nextEp.optInt("season", 0)
        val epNum = nextEp.optInt("number", 0)
        if (season <= 0 || epNum <= 0) return null

        val aired = obj.optInt("aired", 0).coerceAtLeast(0)
        val completed = obj.optInt("completed", 0).coerceAtLeast(0)
        val epTitle = nextEp.optString("title", "").trim()
        val epTraktId = nextEp.optJSONObject("ids")?.optLong("trakt", 0L)
          ?.takeIf { it > 0L }?.toString().orEmpty()
        val poster = resolveShowPoster(imdbId, showTitle)

        UpNextItem(
          id = "trakt_$traktShowId",
          seriesKey = TvPreferencesRepository.buildNormalizedContentKey(showTitle, null, null, isSeries = false),
          showTitle = showTitle,
          seasonNumber = season,
          episodeNumber = epNum,
          episodeTitle = epTitle,
          posterUrl = poster,
          watchedCount = completed,
          totalCount = aired,
          traktSlug = slug,
          traktShowId = traktShowId,
          imdbId = imdbId,
          traktEpisodeUrl = if (slug.isNotBlank()) "https://trakt.tv/shows/$slug/seasons/$season/episodes/$epNum" else "",
          traktEpisodeId = epTraktId,
          fromTrakt = true,
          preferredPortal = StreamingPortal.WATCH_SERIES,
          updatedAtMs = System.currentTimeMillis() - (orderIndex * 20L)
        )
      }
    } catch (_: Exception) {
      null
    }
  }

  private suspend fun fetchCalendar(
    clientId: String,
    accessToken: String
  ): List<TraktCalendarItem> {
    return try {
      val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
      val req = buildTraktRequest(
        "$API_BASE/calendars/my/shows/$today/14",
        clientId,
        accessToken
      ).get().build()

      val arr = client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return emptyList()
        JSONArray(resp.body?.string().orEmpty().ifBlank { "[]" })
      }

      val results = mutableListOf<TraktCalendarItem>()
      val count = minOf(arr.length(), 20)
      for (i in 0 until count) {
        val obj = arr.optJSONObject(i) ?: continue
        val show = obj.optJSONObject("show") ?: continue
        val ep = obj.optJSONObject("episode") ?: continue
        val showIds = show.optJSONObject("ids")
        val epIds = ep.optJSONObject("ids")
        val showTitle = show.optString("title", "").trim()
        val season = ep.optInt("season", 0)
        val epNum = ep.optInt("number", 0)
        if (showTitle.isBlank() || season <= 0 || epNum <= 0) continue

        val traktShowId = showIds?.optLong("trakt", 0L) ?: 0L
        val imdbId = showIds?.optString("imdb", "").orEmpty().trim()
        val poster = resolveShowPoster(imdbId, showTitle)
        results.add(
          TraktCalendarItem(
            id = "cal_${traktShowId}_s${season}e${epNum}_$i",
            showTitle = showTitle,
            seasonNumber = season,
            episodeNumber = epNum,
            episodeTitle = ep.optString("title", "").trim(),
            firstAiredIso = obj.optString("first_aired", "").trim(),
            posterUrl = poster,
            traktShowId = traktShowId,
            traktEpisodeId = epIds?.optLong("trakt", 0L)?.takeIf { it > 0L }?.toString().orEmpty(),
            imdbId = imdbId
          )
        )
      }
      results
    } catch (_: Exception) {
      emptyList()
    }
  }

  private suspend fun fetchWatchlist(
    clientId: String,
    accessToken: String
  ): List<TraktWatchlistItem> {
    val results = mutableListOf<TraktWatchlistItem>()
    try {
      // Shows watchlist
      val showsReq = buildTraktRequest("$API_BASE/sync/watchlist/shows", clientId, accessToken).get().build()
      client.newCall(showsReq).execute().use { resp ->
        if (resp.isSuccessful) {
          val arr = JSONArray(resp.body?.string().orEmpty().ifBlank { "[]" })
          for (i in 0 until minOf(arr.length(), 20)) {
            val show = arr.optJSONObject(i)?.optJSONObject("show") ?: continue
            val ids = show.optJSONObject("ids")
            val title = show.optString("title", "").trim()
            val traktId = ids?.optLong("trakt", 0L) ?: 0L
            val imdbId = ids?.optString("imdb", "").orEmpty().trim()
            if (title.isBlank()) continue
            results.add(
              TraktWatchlistItem(
                id = "wl_show_${traktId}_$i",
                title = title,
                mediaType = "show",
                year = show.optInt("year", 0).takeIf { it > 1900 }?.toString().orEmpty(),
                posterUrl = resolveShowPoster(imdbId, title),
                traktId = traktId,
                imdbId = imdbId
              )
            )
          }
        }
      }

      // Movies watchlist
      val moviesReq = buildTraktRequest("$API_BASE/sync/watchlist/movies", clientId, accessToken).get().build()
      client.newCall(moviesReq).execute().use { resp ->
        if (resp.isSuccessful) {
          val arr = JSONArray(resp.body?.string().orEmpty().ifBlank { "[]" })
          for (i in 0 until minOf(arr.length(), 15)) {
            val movie = arr.optJSONObject(i)?.optJSONObject("movie") ?: continue
            val ids = movie.optJSONObject("ids")
            val title = movie.optString("title", "").trim()
            val traktId = ids?.optLong("trakt", 0L) ?: 0L
            val imdbId = ids?.optString("imdb", "").orEmpty().trim()
            if (title.isBlank()) continue
            results.add(
              TraktWatchlistItem(
                id = "wl_movie_${traktId}_$i",
                title = title,
                mediaType = "movie",
                year = movie.optInt("year", 0).takeIf { it > 1900 }?.toString().orEmpty(),
                posterUrl = "",
                traktId = traktId,
                imdbId = imdbId
              )
            )
          }
        }
      }
    } catch (_: Exception) {}
    return results
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
   */
  suspend fun fetchSingleShowNextUp(
    clientId: String,
    accessToken: String,
    item: UpNextItem
  ): UpNextItem? = withContext(Dispatchers.IO) {
    if (clientId.isBlank() || accessToken.isBlank() || item.traktShowId <= 0L) return@withContext null
    fetchShowProgress(
      clientId = clientId,
      accessToken = accessToken,
      traktShowId = item.traktShowId,
      slug = item.traktSlug,
      imdbId = item.imdbId,
      showTitle = item.showTitle,
      orderIndex = 0
    )?.copy(
      posterUrl = item.posterUrl.ifBlank { resolveShowPoster(item.imdbId, item.showTitle) },
      preferredPortal = item.preferredPortal
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
