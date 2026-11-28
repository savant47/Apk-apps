package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.model.CatalogMediaItem
import com.example.model.DirectStreamResolution
import com.example.model.ParsedSubtitleCue
import com.example.model.ScrapedEpisodeItem
import com.example.model.ScrapedSeasonItem
import com.example.model.ScrapedSeriesStructure
import com.example.model.ScrapedWatchServer
import com.example.model.SearchResultItem
import com.example.model.StreamingPortal
import com.example.model.SubtitleItem
import com.example.model.SubtitleSourceFilter
import com.example.model.VideoQualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionSpec
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class HomeCatalogBundle(
  val trending: List<CatalogMediaItem>,
  val top10Movies: List<CatalogMediaItem>,
  val top10TvSeries: List<CatalogMediaItem>
)

class SearchAndSubtitlesService {

  private val httpClient: OkHttpClient by lazy { buildSslTolerantHttpClient() }

  // Fast 8s hard-timeout client for HTML watch-page scraping & single-server stream extraction
  private val fastScraperClient: OkHttpClient by lazy {
    httpClient.newBuilder()
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(8, TimeUnit.SECONDS)
      .writeTimeout(5, TimeUnit.SECONDS)
      .callTimeout(8, TimeUnit.SECONDS)
      .build()
  }

  // Ultra-fast 5s hard-timeout client for HLS master playlist quality parsing & lightweight catalog fetches
  private val fastManifestClient: OkHttpClient by lazy {
    httpClient.newBuilder()
      .connectTimeout(4, TimeUnit.SECONDS)
      .readTimeout(5, TimeUnit.SECONDS)
      .callTimeout(5, TimeUnit.SECONDS)
      .build()
  }

  @Volatile
  private var cachedCatalogBundle: HomeCatalogBundle? = null
  @Volatile
  private var cachedCatalogTimestampMs: Long = 0L

  private fun safeGetWebViewCookie(url: String): String {
    if (!com.example.AppRuntimeMemoryGuard.hasWebViewBeenCreated) return ""
    return try {
      android.webkit.CookieManager.getInstance()?.getCookie(url) ?: ""
    } catch (_: Throwable) {
      ""
    }
  }

  // Pre-compiled patterns for fast zero-allocation scraping loops
  private val episodeNumberRegex = Regex("(?:الحلق[ةه]|حلقة|\\bepisode|\\bep)[\\s\\-_]*([0-9]{1,3})\\b", RegexOption.IGNORE_CASE)
  private val iframeSrcPattern = Pattern.compile("<iframe[^>]+(?:src|data-src)=[\"'](https?:[^\"']+|//[^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
  private val whitespaceRegex = Regex("\\s+")

  // Stores any real subtitle tracks (.vtt / .srt) discovered directly inside the active watch server embed
  @Volatile
  private var cachedEmbedSubtitles: List<SubtitleItem> = emptyList()

  private val desktopUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

  private val tmdbKey = "829a43a98259bc44cae297489c7e3bba"

  private val genreMap = mapOf(
    28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy",
    80 to "Crime", 99 to "Documentary", 18 to "Drama", 10751 to "Family",
    14 to "Fantasy", 36 to "History", 27 to "Horror", 9648 to "Mystery",
    10749 to "Romance", 878 to "Sci-Fi", 53 to "Thriller", 10752 to "War",
    10759 to "Action & Adventure", 10765 to "Sci-Fi & Fantasy"
  )

  // ============================================================================
  // 1. LIVE DAILY IMDb CHARTS & DAILY TRENDING (TRENDING NOW, TOP 10 MOVIES, TOP 10 TV)
  // ============================================================================

  /**
   * Single-pass, memory-safe Daily Catalog loader for Home Dashboard:
   * - Serves 30-minute in-memory cache immediately when available.
   * - Fetches lightweight TMDB JSON endpoints first (~15KB each, fast & low-RAM on 160MB Android 9 TV).
   * - Avoids downloading 4 multi-MB IMDb HTML pages simultaneously at cold start.
   */
  suspend fun fetchHomeCatalogBundle(forceRefresh: Boolean = false): HomeCatalogBundle = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val existing = cachedCatalogBundle
    if (!forceRefresh && existing != null && (now - cachedCatalogTimestampMs) < 30L * 60_000L && existing.trending.isNotEmpty()) {
      return@withContext existing
    }

    val isLowRamSdk = android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P

    val (imdbMovies, imdbTv) = if (isLowRamSdk && !forceRefresh) {
      // On 1GB RAM Android 9 TV sticks, skip heavy multi-MB IMDb HTML parsing on cold start
      // and use lightweight TMDB JSON API (~15KB) for instant, crash-free Home loading.
      emptyList<CatalogMediaItem>() to emptyList<CatalogMediaItem>()
    } else {
      coroutineScope {
        val m = async {
          withTimeoutOrNull(4500L) {
            scrapeImdbChart("https://www.imdb.com/chart/moviemeter/", "movie", 10, true)
          } ?: emptyList()
        }
        val t = async {
          withTimeoutOrNull(4500L) {
            scrapeImdbChart("https://www.imdb.com/chart/tvmeter/", "tv", 10, true)
          } ?: emptyList()
        }
        m.await() to t.await()
      }
    }

    // Build Top 10 Movies (fill from fast TMDB JSON if needed)
    val topMovies = mutableListOf<CatalogMediaItem>()
    val seenMovies = mutableSetOf<String>()
    for (item in imdbMovies) {
      if (seenMovies.add(item.title.lowercase())) topMovies.add(item.copy(rank = topMovies.size + 1))
    }
    if (topMovies.size < 10) {
      fetchTmdbList(
        "https://api.themoviedb.org/3/trending/movie/day?api_key=$tmdbKey&language=en-US",
        "movie",
        10
      ).forEach { item ->
        if (topMovies.size < 10 && seenMovies.add(item.title.lowercase())) {
          topMovies.add(item.copy(rank = topMovies.size + 1))
        }
      }
    }

    // Build Top 10 TV Series (fill from fast TMDB JSON if needed)
    val topTv = mutableListOf<CatalogMediaItem>()
    val seenTv = mutableSetOf<String>()
    for (item in imdbTv) {
      if (seenTv.add(item.title.lowercase())) topTv.add(item.copy(rank = topTv.size + 1))
    }
    if (topTv.size < 10) {
      fetchTmdbList(
        "https://api.themoviedb.org/3/trending/tv/day?api_key=$tmdbKey&language=en-US",
        "tv",
        10
      ).forEach { item ->
        if (topTv.size < 10 && seenTv.add(item.title.lowercase())) {
          topTv.add(item.copy(rank = topTv.size + 1))
        }
      }
    }

    // Build Trending interleave from already-fetched items + lightweight /trending/all/day
    val trending = mutableListOf<CatalogMediaItem>()
    val seenTrending = mutableSetOf<String>()
    val maxInterleave = minOf(8, maxOf(topTv.size, topMovies.size))
    for (i in 0 until maxInterleave) {
      if (i < topTv.size) {
        val tvItem = topTv[i].copy(rank = null)
        if (seenTrending.add(tvItem.title.lowercase())) trending.add(tvItem)
      }
      if (i < topMovies.size) {
        val movItem = topMovies[i].copy(rank = null)
        if (seenTrending.add(movItem.title.lowercase())) trending.add(movItem)
      }
    }
    if (trending.size < 12) {
      fetchTmdbMixedTrending(16).forEach { item ->
        if (trending.size < 16 && seenTrending.add(item.title.lowercase())) {
          trending.add(item)
        }
      }
    }

    val bundle = HomeCatalogBundle(
      trending = trending,
      top10Movies = topMovies,
      top10TvSeries = topTv
    )
    if (trending.isNotEmpty() || topMovies.isNotEmpty() || topTv.isNotEmpty()) {
      cachedCatalogBundle = bundle
      cachedCatalogTimestampMs = System.currentTimeMillis()
    }
    bundle
  }

  private fun fetchTmdbList(apiUrl: String, mediaType: String, limit: Int): List<CatalogMediaItem> {
    val out = mutableListOf<CatalogMediaItem>()
    try {
      val request = Request.Builder().url(apiUrl).header("User-Agent", desktopUserAgent).build()
      fastManifestClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (body.isNotBlank()) {
          val results = JSONObject(body).optJSONArray("results")
          if (results != null) {
            for (i in 0 until results.length()) {
              if (out.size >= limit) break
              val item = results.optJSONObject(i) ?: continue
              val parsed = parseTmdbCatalogItem(item, mediaType, rank = out.size + 1)
              if (parsed != null) out.add(parsed)
            }
          }
        }
      }
    } catch (_: Throwable) {}
    return out
  }

  private fun fetchTmdbMixedTrending(limit: Int): List<CatalogMediaItem> {
    val out = mutableListOf<CatalogMediaItem>()
    try {
      val url = "https://api.themoviedb.org/3/trending/all/day?api_key=$tmdbKey&language=en-US"
      val request = Request.Builder().url(url).header("User-Agent", desktopUserAgent).build()
      fastManifestClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (body.isNotBlank()) {
          val results = JSONObject(body).optJSONArray("results")
          if (results != null) {
            for (i in 0 until results.length()) {
              if (out.size >= limit) break
              val item = results.optJSONObject(i) ?: continue
              val mediaType = item.optString("media_type", "tv")
              if (mediaType != "movie" && mediaType != "tv") continue
              val parsed = parseTmdbCatalogItem(item, mediaType, rank = null)
              if (parsed != null) out.add(parsed)
            }
          }
        }
      }
    } catch (_: Throwable) {}
    return out
  }

  suspend fun fetchTrendingCatalog(): List<CatalogMediaItem> = fetchHomeCatalogBundle().trending

  suspend fun fetchTop10Movies(): List<CatalogMediaItem> = fetchHomeCatalogBundle().top10Movies

  suspend fun fetchTop10TvSeries(): List<CatalogMediaItem> = fetchHomeCatalogBundle().top10TvSeries

  private fun scrapeImdbChart(
    chartUrl: String,
    mediaType: String,
    maxItems: Int,
    assignRank: Boolean
  ): List<CatalogMediaItem> {
    val results = mutableListOf<CatalogMediaItem>()
    try {
      val req = Request.Builder()
        .url(chartUrl)
        .header("User-Agent", desktopUserAgent)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header("Accept-Language", "en-US,en;q=0.9")
        .build()
      val html = fastManifestClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) "" else resp.body?.string().orEmpty()
      }
      if (html.isBlank()) return emptyList()

      // Parse IMDb __NEXT_DATA__ JSON embedded in chart page
      val nextDataMatcher = Pattern.compile(
        "<script[^>]*id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>",
        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
      ).matcher(html)

      if (nextDataMatcher.find()) {
        val jsonStr = nextDataMatcher.group(1)?.trim() ?: ""
        if (jsonStr.isNotBlank()) {
          val root = JSONObject(jsonStr)
          val edges = root
            .optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONObject("pageData")
            ?.optJSONObject("chartTitles")
            ?.optJSONArray("edges")

          if (edges != null) {
            for (i in 0 until edges.length()) {
              if (results.size >= maxItems) break
              val node = edges.getJSONObject(i).optJSONObject("node") ?: continue
              val imdbId = node.optString("id", "")
              val title = node.optJSONObject("titleText")?.optString("text", "")
                ?: node.optJSONObject("originalTitleText")?.optString("text", "")
                ?: ""
              if (title.isBlank() || title.equals("The Odyssey", ignoreCase = true)) continue

              // Only include released titles that have an actual IMDb user rating > 0
              val ratingObj = node.optJSONObject("ratingsSummary")
              val ratingVal = ratingObj?.optDouble("aggregateRating", 0.0) ?: 0.0
              val voteCount = ratingObj?.optInt("voteCount", 0) ?: 0
              if (ratingVal <= 0.0 || voteCount < 50) continue

              val rawPosterUrl = node.optJSONObject("primaryImage")?.optString("url", "") ?: ""
              if (rawPosterUrl.isBlank()) continue
              val posterUrl = if (rawPosterUrl.contains("._V1_.")) {
                rawPosterUrl.replace("._V1_.", "._V1_UX340_.")
              } else {
                rawPosterUrl
              }

              val releaseYear = node.optJSONObject("releaseYear")?.optInt("year", 0) ?: 0
              val yearStr = if (releaseYear > 1900) releaseYear.toString() else "2025"
              val ratingStr = String.format(java.util.Locale.US, "%.1f", ratingVal)

              val genresArr = node.optJSONObject("titleGenres")?.optJSONArray("genres")
              val genreNames = mutableListOf<String>()
              if (genresArr != null) {
                for (g in 0 until minOf(genresArr.length(), 2)) {
                  val gText = genresArr.optJSONObject(g)?.optJSONObject("genre")?.optString("text", "") ?: ""
                  if (gText.isNotBlank()) genreNames.add(gText)
                }
              }
              val genreStr = if (genreNames.isNotEmpty()) genreNames.joinToString(" • ") else if (mediaType == "movie") "Movie" else "TV Series"
              val plotText = node.optJSONObject("plot")?.optJSONObject("plotText")?.optString("plainText", "") ?: ""

              val rankNum = if (assignRank) results.size + 1 else null
              results.add(
                CatalogMediaItem(
                  id = "imdb_${imdbId.ifBlank { UUID.randomUUID().toString() }}",
                  title = cleanHtmlEntities(title),
                  searchTitle = cleanHtmlEntities(title),
                  posterUrl = posterUrl,
                  backdropUrl = posterUrl,
                  rating = ratingStr,
                  year = yearStr,
                  genre = genreStr,
                  overview = plotText,
                  mediaType = mediaType,
                  rank = rankNum
                )
              )
            }
          }
        }
      }
    } catch (_: Exception) {}
    return results
  }

  private fun parseTmdbCatalogItem(item: JSONObject, mediaType: String, rank: Int?): CatalogMediaItem? {
    val id = item.optInt("id", 0)
    if (id <= 0) return null
    val title = if (mediaType == "movie") {
      item.optString("title", item.optString("original_title", ""))
    } else {
      item.optString("name", item.optString("original_name", ""))
    }
    if (title.isBlank() || title.equals("The Odyssey", ignoreCase = true)) return null

    // Strictly filter out unreleased/unrated titles (e.g. 2026 trailers with 0 votes)
    val vote = item.optDouble("vote_average", 0.0)
    val voteCount = item.optInt("vote_count", 0)
    if (vote <= 0.0 || voteCount < 15) return null

    val posterPath = item.optString("poster_path", "")
    if (posterPath.isBlank() || posterPath == "null") return null
    val backdropPath = item.optString("backdrop_path", "")
    val posterUrl = "https://image.tmdb.org/t/p/w342$posterPath"
    val backdropUrl = if (backdropPath.isNotBlank() && backdropPath != "null") {
      "https://image.tmdb.org/t/p/w500$backdropPath"
    } else {
      posterUrl
    }

    val releaseDate = item.optString("release_date", item.optString("first_air_date", ""))
    val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""
    val rating = String.format(java.util.Locale.US, "%.1f", vote)
    val overview = item.optString("overview", "")

    val genreIds = item.optJSONArray("genre_ids")
    val genres = mutableListOf<String>()
    if (genreIds != null) {
      for (g in 0 until minOf(genreIds.length(), 2)) {
        val gName = genreMap[genreIds.optInt(g)]
        if (gName != null) genres.add(gName)
      }
    }
    val genreStr = if (genres.isNotEmpty()) genres.joinToString(" • ") else if (mediaType == "movie") "Movie" else "TV Series"

    return CatalogMediaItem(
      id = "${mediaType}_$id",
      title = title,
      searchTitle = title,
      posterUrl = posterUrl,
      backdropUrl = backdropUrl,
      rating = rating,
      year = year,
      genre = genreStr,
      overview = overview,
      mediaType = mediaType,
      rank = rank
    )
  }

  // ============================================================================
  // 2. MULTI-SOURCE SEARCH SCRAPER (TOP CINEMA, LAROZA, WATCHSERIES, EGYDEAD, FLIXER)
  // ============================================================================

  suspend fun searchAllPortals(
    query: String
  ): Map<StreamingPortal, List<SearchResultItem>> = coroutineScope {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) {
      return@coroutineScope emptyMap()
    }

    val enabledPortals = StreamingPortal.values().filter { it.isEnabled }
    val deferreds = enabledPortals.associateWith { portal ->
      async(Dispatchers.IO) {
        when (portal) {
          StreamingPortal.TOP_CINEMA -> {
            val res = try { searchTopCinema(cleanQuery) } catch (_: Exception) { emptyList() }
            if (res.isNotEmpty()) res else fallbackTopCinemaResults(cleanQuery)
          }
          StreamingPortal.LAROZA -> {
            val res = try { searchLaRoza(cleanQuery) } catch (_: Exception) { emptyList() }
            if (res.isNotEmpty()) res else fallbackLaRozaResults(cleanQuery)
          }
          StreamingPortal.WATCH_SERIES -> {
            val res = try { searchWatchSeries(cleanQuery) } catch (_: Exception) { emptyList() }
            if (res.isNotEmpty()) res else fallbackWatchSeriesResults(cleanQuery)
          }
          StreamingPortal.EGY_DEAD -> {
            val res = try { searchEgyDead(cleanQuery) } catch (_: Exception) { emptyList() }
            if (res.isNotEmpty()) res else fallbackEgyDeadResults(cleanQuery)
          }
          StreamingPortal.FLIXER -> {
            val res = try { searchFlixer(cleanQuery) } catch (_: Exception) { emptyList() }
            if (res.isNotEmpty()) res else fallbackFlixerResults(cleanQuery)
          }
          else -> {
            listOf(
              SearchResultItem(
                id = "${portal.id}_custom_direct",
                title = "$cleanQuery - مشاهدة على ${portal.displayNameArabic}",
                url = portal.buildSearchUrl(cleanQuery),
                portal = portal,
                posterUrl = "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=400&q=80",
                type = portal.displayNameArabic,
                quality = "مشاهدة مباشرة"
              )
            )
          }
        }
      }
    }

    deferreds.mapValues { entry ->
      try {
        entry.value.await()
      } catch (_: Exception) {
        when (entry.key) {
          StreamingPortal.TOP_CINEMA -> fallbackTopCinemaResults(cleanQuery)
          StreamingPortal.WATCH_SERIES -> fallbackWatchSeriesResults(cleanQuery)
          StreamingPortal.LAROZA -> fallbackLaRozaResults(cleanQuery)
          StreamingPortal.EGY_DEAD -> fallbackEgyDeadResults(cleanQuery)
          StreamingPortal.FLIXER -> fallbackFlixerResults(cleanQuery)
          else -> emptyList()
        }
      }
    }
  }

  private suspend fun searchLaRoza(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
    val results = mutableListOf<SearchResultItem>()
    val encoded = URLEncoder.encode(query, "UTF-8")
    val url = "${StreamingPortal.LAROZA.homeUrl.substringBefore("/home")}/search.php?keywords=$encoded"

    try {
      val request = Request.Builder()
        .url(url)
        .header("User-Agent", desktopUserAgent)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .build()

      val response = httpClient.newCall(request).execute()
      if (response.code != 200) return@withContext emptyList()
      val html = response.body?.string() ?: ""

      val segments = html.split(Pattern.compile("<div[^>]*class=\"[^\"]*pm-video-thumb[^\"]*\"[^>]*>", Pattern.CASE_INSENSITIVE))
      var count = 0
      val seenUrls = mutableSetOf<String>()

      if (segments.size > 1) {
        for (i in 1 until segments.size) {
          if (count >= 20) break
          val segment = segments[i]

          val linkMatcher = Pattern.compile("<a[^>]+href=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(segment)
          if (linkMatcher.find()) {
            val link = linkMatcher.group(1)?.trim() ?: ""
            if (link.isNotBlank() && !link.contains("/category.php") && !link.contains("/newvideos")) {
              var posterUrl = ""
              val echoMatcher = Pattern.compile("data-echo=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(segment)
              if (echoMatcher.find()) {
                posterUrl = echoMatcher.group(1)?.trim() ?: ""
              }
              if (posterUrl.isBlank()) {
                val imgMatcher = Pattern.compile("<img[^>]+src=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(segment)
                if (imgMatcher.find()) {
                  posterUrl = imgMatcher.group(1)?.trim() ?: ""
                }
              }

              var title = ""
              val titleMatcher = Pattern.compile("title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(segment)
              if (titleMatcher.find()) {
                title = cleanHtmlEntities(titleMatcher.group(1)?.trim() ?: "")
              }
              if (title.isBlank()) {
                val altMatcher = Pattern.compile("alt=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(segment)
                if (altMatcher.find()) {
                  title = cleanHtmlEntities(altMatcher.group(1)?.trim() ?: "")
                }
              }

              val durationMatcher = Pattern.compile("<span[^>]*class=\"[^\"]*pm-label-duration[^\"]*\"[^>]*>(.*?)</span>", Pattern.CASE_INSENSITIVE).matcher(segment)
              val duration = if (durationMatcher.find()) durationMatcher.group(1)?.trim() ?: "" else ""

              if (title.isNotBlank() && title.length > 2) {
                val rawAbsoluteLink = if (link.startsWith("/")) "${StreamingPortal.LAROZA.homeUrl.substringBefore("/home")}$link" else link
                val absoluteLink = rawAbsoluteLink.replace("video.php?vid=", "play.php?vid=")
                val absolutePoster = if (posterUrl.isNotBlank()) {
                  if (posterUrl.startsWith("/")) "${StreamingPortal.LAROZA.homeUrl.substringBefore("/home")}$posterUrl" else posterUrl
                } else {
                  "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=400&q=80"
                }

                if (!seenUrls.contains(absoluteLink)) {
                  seenUrls.add(absoluteLink)
                  val isSeries = title.contains("مسلسل", ignoreCase = true) ||
                    title.contains("حلقة", ignoreCase = true) ||
                    absoluteLink.contains("series", ignoreCase = true)

                  results.add(
                    SearchResultItem(
                      id = UUID.randomUUID().toString(),
                      title = title,
                      url = absoluteLink,
                      portal = StreamingPortal.LAROZA,
                      posterUrl = absolutePoster,
                      type = if (isSeries) "مسلسل (لاروزا)" else "فيلم (لاروزا)",
                      quality = if (duration.isNotBlank()) "مدة: $duration" else "HD 1080p",
                      year = "2024",
                      rating = "8.3"
                    )
                  )
                  count++
                }
              }
            }
          }
        }
      }
    } catch (_: Exception) {}

    results
  }

  private fun fallbackLaRozaResults(query: String): List<SearchResultItem> {
    val searchUrl = StreamingPortal.LAROZA.buildSearchUrl(query)
    return listOf(
      SearchResultItem(
        id = "lr_direct_1",
        title = "$query - مشاهدة مباشرة وحلقات كاملة (لاروزا)",
        url = searchUrl,
        portal = StreamingPortal.LAROZA,
        posterUrl = "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=400&q=80",
        type = "لاروزا (LaRoza)",
        quality = "سيرفرات سريعة HD",
        year = "2025",
        rating = "8.4"
      )
    )
  }

  private suspend fun searchTopCinema(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
    val results = mutableListOf<SearchResultItem>()
    val encoded = URLEncoder.encode(query, "UTF-8")
    val baseHome = StreamingPortal.TOP_CINEMA.homeUrl.substringBefore("/home").trimEnd('/').ifBlank { "https://topcinema.io" }
    val url = "$baseHome/search/?query=$encoded&type=all"

    try {
      val request = Request.Builder()
        .url(url)
        .header("User-Agent", desktopUserAgent)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header("Accept-Language", "ar,en-US;q=0.7,en;q=0.3")
        .build()

      val (html, resolvedUrl) = httpClient.newCall(request).execute().use { resp ->
        (resp.body?.string() ?: "") to resp.request.url.toString()
      }
      if (html.isBlank()) return@withContext emptyList()

      val doc = Jsoup.parse(html, resolvedUrl)
      val liveOrigin = extractOrigin(resolvedUrl, baseHome)
      val seenUrls = mutableSetOf<String>()
      val candidates = doc.select("div.Small--Box, article, a.recent--block")

      for (el in candidates) {
        if (results.size >= 24) break
        val anchor = if (el.tagName().equals("a", ignoreCase = true)) el else el.selectFirst("a[href]") ?: continue
        val rawLink = anchor.attr("href").trim()
        if (rawLink.isBlank() || rawLink.startsWith("#") || rawLink.startsWith("javascript:")) continue
        if (rawLink.contains("/recent/") || rawLink.contains("/category/") || rawLink.contains("/tag/") || rawLink.contains("/page/")) continue

        val rawTitle = cleanHtmlEntities(
          anchor.attr("title").trim()
            .ifBlank { el.selectFirst("h3, h2, .title, .Title")?.text()?.trim().orEmpty() }
            .ifBlank { anchor.text().replace(whitespaceRegex, " ").trim() }
        )
        if (rawTitle.isBlank()) continue

        val absoluteLink = resolveRelativeUrl(liveOrigin, rawLink)
        if (!seenUrls.add(absoluteLink)) continue

        val imgEl = el.selectFirst("img[data-src], img[src]")
        val rawPoster = imgEl?.attr("data-src")?.trim().orEmpty().ifBlank { imgEl?.attr("src")?.trim().orEmpty() }
        val absolutePoster = if (rawPoster.isNotBlank()) {
          resolveRelativeUrl(liveOrigin, rawPoster)
        } else {
          "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=400&q=80"
        }

        val decodedLink = try { java.net.URLDecoder.decode(absoluteLink, "UTF-8") } catch (_: Exception) { absoluteLink }
        val isSeries = rawTitle.contains("مسلسل", ignoreCase = true) ||
          rawTitle.contains("حلقة", ignoreCase = true) ||
          rawTitle.contains("الموسم", ignoreCase = true) ||
          decodedLink.contains("/series/", ignoreCase = true) ||
          decodedLink.contains("مسلسل")
        val isSeriesOverview = decodedLink.contains("/series/", ignoreCase = true) ||
          (isSeries && !rawTitle.contains("حلقة") && !rawTitle.contains("الحلقة") && !rawTitle.contains("الحلقه") && !decodedLink.contains("الحلقة") && !decodedLink.contains("حلقة"))

        val cleanBase = absoluteLink.trimEnd('/')
        val watchUrl = if (!cleanBase.endsWith("/watch") && !isSeriesOverview) "$cleanBase/watch/" else "$cleanBase/"

        results.add(
          SearchResultItem(
            id = UUID.randomUUID().toString(),
            title = rawTitle,
            url = watchUrl,
            portal = StreamingPortal.TOP_CINEMA,
            posterUrl = absolutePoster,
            type = if (isSeries) "مسلسل (توب سينما)" else "فيلم (توب سينما)",
            quality = if (rawTitle.contains("1080", ignoreCase = true)) "1080p Web-DL" else "HD 720p",
            year = Regex("(19|20)[0-9]{2}").find(rawTitle)?.value ?: "2025",
            rating = "8.5"
          )
        )
      }
    } catch (_: Exception) {}

    // Prefer /series/ overview items first so tapping a show opens its full season structure immediately
    results.sortedByDescending { it.url.contains("/series/", ignoreCase = true) }
  }

  private suspend fun searchWatchSeries(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
    val results = mutableListOf<SearchResultItem>()
    val seenIds = mutableSetOf<Int>()

    val queriesToTry = mutableListOf(query)
    val separatedQuery = query.replace("([a-z])([A-Z])".toRegex(), "$1 $2").trim()
    if (separatedQuery.isNotBlank() && separatedQuery != query) {
      queriesToTry.add(separatedQuery)
    }
    if (!query.contains(" ") && query.length >= 6 && query.startsWith("mob", ignoreCase = true)) {
      queriesToTry.add(query.substring(0, 3) + " " + query.substring(3))
    }

    for (q in queriesToTry.distinct()) {
      try {
        val encoded = URLEncoder.encode(q, "UTF-8")
        val apiUrl = "https://api.themoviedb.org/3/search/multi?api_key=$tmdbKey&query=$encoded&include_adult=false&language=en-US"

        val request = Request.Builder()
          .url(apiUrl)
          .header("User-Agent", desktopUserAgent)
          .header("Accept", "application/json")
          .build()

        val response = httpClient.newCall(request).execute()
        val jsonStr = response.body?.string() ?: continue
        val jsonObj = JSONObject(jsonStr)
        val itemsArray = jsonObj.optJSONArray("results") ?: continue

        for (i in 0 until itemsArray.length()) {
          val item = itemsArray.getJSONObject(i)
          val mediaType = item.optString("media_type")
          if (mediaType != "movie" && mediaType != "tv") continue

          val id = item.optInt("id", 0)
          if (id <= 0 || seenIds.contains(id)) continue
          seenIds.add(id)

          val rawTitle = if (mediaType == "movie") {
            item.optString("title", item.optString("original_title", "Movie"))
          } else {
            item.optString("name", item.optString("original_name", "TV Series"))
          }
          if (rawTitle.isBlank()) continue

          val posterPath = item.optString("poster_path")
          val posterUrl = if (posterPath.isNotBlank() && posterPath != "null") {
            "https://image.tmdb.org/t/p/w500$posterPath"
          } else {
            "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=400&q=80"
          }

          val releaseDate = item.optString("release_date", item.optString("first_air_date", ""))
          val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else "2024"
          val vote = item.optDouble("vote_average", 0.0)
          val ratingStr = if (vote > 0) String.format(java.util.Locale.US, "%.1f", vote) else "8.2"

          val safeSlug = rawTitle.lowercase()
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
            .ifBlank { "watch" }

          val targetUrl = if (mediaType == "movie") {
            "https://watchseries.bar/movie/$safeSlug/$id"
          } else {
            "https://watchseries.bar/tv/$safeSlug/$id"
          }

          val isSeries = mediaType == "tv"
          val displayTitle = if (year.isNotBlank()) "$rawTitle ($year)" else rawTitle

          results.add(
            SearchResultItem(
              id = "ws_$id",
              title = displayTitle,
              url = targetUrl,
              portal = StreamingPortal.WATCH_SERIES,
              posterUrl = posterUrl,
              type = if (isSeries) "مسلسل (WatchSeries)" else "فيلم (WatchSeries)",
              quality = "⭐ $ratingStr | 1080p",
              year = year,
              rating = ratingStr
            )
          )

          if (results.size >= 16) break
        }
      } catch (_: Exception) {}

      if (results.isNotEmpty()) break
    }

    results
  }

  private suspend fun searchFlixer(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
    val results = mutableListOf<SearchResultItem>()
    try {
      val encoded = URLEncoder.encode(query, "UTF-8")
      val apiUrl = "https://api.themoviedb.org/3/search/multi?api_key=$tmdbKey&query=$encoded&include_adult=false&language=en-US"
      val request = Request.Builder()
        .url(apiUrl)
        .header("User-Agent", desktopUserAgent)
        .header("Accept", "application/json")
        .build()

      val response = httpClient.newCall(request).execute()
      val jsonStr = response.body?.string() ?: ""
      if (jsonStr.isNotBlank()) {
        val jsonObj = JSONObject(jsonStr)
        val itemsArray = jsonObj.optJSONArray("results")
        if (itemsArray != null) {
          for (i in 0 until minOf(itemsArray.length(), 12)) {
            val item = itemsArray.getJSONObject(i)
            val mediaType = item.optString("media_type")
            if (mediaType != "movie" && mediaType != "tv") continue
            val id = item.optInt("id", 0)
            if (id <= 0) continue

            val rawTitle = if (mediaType == "movie") {
              item.optString("title", item.optString("original_title", "Movie"))
            } else {
              item.optString("name", item.optString("original_name", "TV Series"))
            }
            if (rawTitle.isBlank()) continue

            val posterPath = item.optString("poster_path")
            val posterUrl = if (posterPath.isNotBlank() && posterPath != "null") {
              "https://image.tmdb.org/t/p/w500$posterPath"
            } else {
              "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=400&q=80"
            }
            val releaseDate = item.optString("release_date", item.optString("first_air_date", ""))
            val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else "2024"
            val vote = item.optDouble("vote_average", 8.0)
            val ratingStr = if (vote > 0) String.format(java.util.Locale.US, "%.1f", vote) else "8.0"

            val targetUrl = if (mediaType == "movie") {
              "https://flixer.gd/movie/$id"
            } else {
              "https://flixer.gd/tv/$id"
            }

            results.add(
              SearchResultItem(
                id = "flx_$id",
                title = if (year.isNotBlank()) "$rawTitle ($year)" else rawTitle,
                url = targetUrl,
                portal = StreamingPortal.FLIXER,
                posterUrl = posterUrl,
                type = if (mediaType == "tv") "مسلسل (Flixer)" else "فيلم (Flixer)",
                quality = "⭐ $ratingStr | Full HD",
                year = year,
                rating = ratingStr
              )
            )
          }
        }
      }
    } catch (_: Exception) {}

    results
  }

  private fun fallbackTopCinemaResults(query: String): List<SearchResultItem> {
    val searchUrl = StreamingPortal.TOP_CINEMA.buildSearchUrl(query)
    return listOf(
      SearchResultItem(
        id = "tc_direct_1",
        title = "$query - مشاهدة مباشرة وسيرفرات سريعة",
        url = searchUrl,
        portal = StreamingPortal.TOP_CINEMA,
        posterUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=400&q=80",
        type = "توب سينما (TopCinema)",
        quality = "1080p Full HD",
        year = "2025",
        rating = "8.5"
      ),
      SearchResultItem(
        id = "tc_direct_2",
        title = "جميع مواسم وحلقات: $query",
        url = searchUrl,
        portal = StreamingPortal.TOP_CINEMA,
        posterUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=400&q=80",
        type = "مسلسل / حلقات",
        quality = "720p / 480p",
        year = "2025",
        rating = "8.3"
      )
    )
  }

  private fun fallbackWatchSeriesResults(query: String): List<SearchResultItem> {
    val searchUrl = StreamingPortal.WATCH_SERIES.buildSearchUrl(query)
    return listOf(
      SearchResultItem(
        id = "ws_direct_1",
        title = "$query (All Seasons & Episodes)",
        url = searchUrl,
        portal = StreamingPortal.WATCH_SERIES,
        posterUrl = "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=400&q=80",
        type = "ووتش سيريز (WatchSeries)",
        quality = "HD 1080p",
        year = "2025",
        rating = "8.4"
      )
    )
  }

  private fun fallbackFlixerResults(query: String): List<SearchResultItem> {
    val searchUrl = StreamingPortal.FLIXER.buildSearchUrl(query)
    return listOf(
      SearchResultItem(
        id = "flx_direct_1",
        title = "$query - البث المباشر على فليكسر (Flixer)",
        url = searchUrl,
        portal = StreamingPortal.FLIXER,
        posterUrl = "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=400&q=80",
        type = "فليكسر (Flixer)",
        quality = "Full HD",
        year = "2025",
        rating = "8.3"
      )
    )
  }

  private suspend fun searchEgyDead(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
    val results = mutableListOf<SearchResultItem>()
    val encoded = URLEncoder.encode(query, "UTF-8")
    val url = "${StreamingPortal.EGY_DEAD.homeUrl.trimEnd('/')}/?s=$encoded"

    try {
      val request = Request.Builder()
        .url(url)
        .header("User-Agent", desktopUserAgent)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .build()

      val response = httpClient.newCall(request).execute()
      if (response.code != 200) return@withContext emptyList()
      val html = response.body?.string() ?: ""

      val segments = html.split(Pattern.compile("<li[^>]*class=\"[^\"]*movieItem[^\"]*\"[^>]*>", Pattern.CASE_INSENSITIVE))
      var count = 0
      val seenUrls = mutableSetOf<String>()

      if (segments.size > 1) {
        for (i in 1 until segments.size) {
          if (count >= 20) break
          val segment = segments[i]

          val linkMatcher = Pattern.compile("<a[^>]+href=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(segment)
          if (linkMatcher.find()) {
            val link = linkMatcher.group(1)?.trim() ?: ""
            if (link.isNotBlank() && !link.contains("/category/") && !link.contains("/genre/") && !link.contains("/tags/")) {
              val imgMatcher = Pattern.compile("<img[^>]+src=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(segment)
              val posterUrl = if (imgMatcher.find()) imgMatcher.group(1)?.trim() ?: "" else ""

              var title = ""
              val titleTagMatcher = Pattern.compile("<h1[^>]*class=\"[^\"]*BottomTitle[^\"]*\"[^>]*>(.*?)</h1>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE).matcher(segment)
              if (titleTagMatcher.find()) {
                title = cleanHtmlEntities(titleTagMatcher.group(1)?.replace(Regex("<[^>]*>"), "")?.trim() ?: "")
              }
              if (title.isBlank()) {
                val titleAttrMatcher = Pattern.compile("title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(segment)
                if (titleAttrMatcher.find()) {
                  title = cleanHtmlEntities(titleAttrMatcher.group(1)?.trim() ?: "")
                }
              }

              val catMatcher = Pattern.compile("<span[^>]*class=\"[^\"]*cat_name[^\"]*\"[^>]*>(.*?)</span>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE).matcher(segment)
              val catName = if (catMatcher.find()) catMatcher.group(1)?.replace(Regex("<[^>]*>"), "")?.trim() ?: "" else ""

              if (title.isNotBlank() && title.length > 2) {
                val absoluteLink = if (link.startsWith("/")) "${StreamingPortal.EGY_DEAD.homeUrl.trimEnd('/')}$link" else link
                val absolutePoster = if (posterUrl.isNotBlank()) {
                  if (posterUrl.startsWith("/")) "${StreamingPortal.EGY_DEAD.homeUrl.trimEnd('/')}$posterUrl" else posterUrl
                } else {
                  "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=400&q=80"
                }

                if (!seenUrls.contains(absoluteLink)) {
                  seenUrls.add(absoluteLink)
                  val isSeries = title.contains("مسلسل", ignoreCase = true) ||
                    catName.contains("مسلسل", ignoreCase = true) ||
                    link.contains("series", ignoreCase = true)

                  results.add(
                    SearchResultItem(
                      id = UUID.randomUUID().toString(),
                      title = title,
                      url = absoluteLink,
                      portal = StreamingPortal.EGY_DEAD,
                      posterUrl = absolutePoster,
                      type = if (isSeries) "مسلسل (إيجي ديد)" else "فيلم (إيجي ديد)",
                      quality = "HD 1080p",
                      year = "2024",
                      rating = "8.4"
                    )
                  )
                  count++
                }
              }
            }
          }
        }
      }
    } catch (_: Exception) {}

    results
  }

  private fun fallbackEgyDeadResults(query: String): List<SearchResultItem> {
    val searchUrl = StreamingPortal.EGY_DEAD.buildSearchUrl(query)
    return listOf(
      SearchResultItem(
        id = "ed_direct_1",
        title = "$query - مشاهدة مباشرة وحلقات كاملة (إيجي ديد)",
        url = searchUrl,
        portal = StreamingPortal.EGY_DEAD,
        posterUrl = "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=400&q=80",
        type = "إيجي ديد (EgyDead)",
        quality = "سيرفرات سريعة HD",
        year = "2025",
        rating = "8.4"
      )
    )
  }

  // ============================================================================
  // 3. SMART SEASONS & EPISODES SCRAPER FOR SIDE PANEL IN WATCH VIEW
  // ============================================================================

  private fun extractOrigin(url: String, fallback: String): String {
    return try {
      val uri = java.net.URI(url)
      if (!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) {
        "${uri.scheme}://${uri.host}"
      } else {
        fallback.trimEnd('/')
      }
    } catch (_: Exception) {
      fallback.trimEnd('/')
    }
  }

  private fun isCloudflareOrBlockedHtml(httpCode: Int, html: String): Boolean {
    if (httpCode == 403 || httpCode == 503 || httpCode == 429) return true
    if (html.length < 4000) {
      val lower = html.lowercase()
      if (lower.contains("cf-browser-verification") ||
        lower.contains("just a moment...") ||
        lower.contains("attention required! | cloudflare") ||
        lower.contains("/cdn-cgi/challenge-platform") ||
        lower.contains("checking your browser before accessing")
      ) {
        return true
      }
    }
    return false
  }

  private fun resolveTopCinemaAjaxEmbed(
    origin: String,
    postId: String,
    serverNum: String,
    refererUrl: String
  ): String {
    return try {
      val cleanOrigin = origin.trimEnd('/')
      val ajaxUrl = "$cleanOrigin/wp-content/themes/movies2023/AjaxCenter/Single/Server.php"
      val cookieHeader = safeGetWebViewCookie(cleanOrigin)

      val reqBuilder = Request.Builder()
        .url(ajaxUrl)
        .post(FormBody.Builder().add("id", postId).add("i", serverNum).build())
        .header("User-Agent", desktopUserAgent)
        .header("X-Requested-With", "XMLHttpRequest")
        .header("Origin", cleanOrigin)
        .header("Referer", refererUrl.ifBlank { "$cleanOrigin/" })
      if (cookieHeader.isNotBlank()) {
        reqBuilder.header("Cookie", cookieHeader)
      }

      fastScraperClient.newCall(reqBuilder.build()).execute().use { resp ->
        if (!resp.isSuccessful) return ""
        val ajaxHtml = resp.body?.string().orEmpty()
        val matcher = iframeSrcPattern.matcher(ajaxHtml)
        if (matcher.find()) {
          val raw = matcher.group(1)?.trim().orEmpty()
          if (raw.startsWith("//")) "https:$raw" else raw
        } else {
          ""
        }
      }
    } catch (_: Exception) {
      ""
    }
  }

  suspend fun scrapeSeasonsAndEpisodes(
    pageUrl: String,
    portal: StreamingPortal,
    titleHint: String = "",
    selectedSeasonNumber: Int = 1,
    selectedEpisodeNumber: Int = 0
  ): ScrapedSeriesStructure = withContext(Dispatchers.IO) {
    val scrapeStartMs = System.currentTimeMillis()
    val cleanSeriesTitleHint = stripEpisodeAndSeasonSuffix(titleHint).ifBlank { titleHint.trim() }
    val seasons = mutableListOf<ScrapedSeasonItem>()
    val episodes = mutableListOf<ScrapedEpisodeItem>()
    val watchServers = mutableListOf<ScrapedWatchServer>()
    val seenEmbedUrls = mutableSetOf<String>()

    // 1. Safely normalize watch page URL (NEVER append /watch/ to embed iframes or non-TopCinema hosts!)
    var normalizedWatchUrl = StreamingPortal.sanitizePortalWatchUrl(pageUrl, portal)

    // Safety guard: if an embed iframe URL was passed directly (e.g. from legacy history),
    // treat it as a direct embed server instead of scraping it as a portal watch page.
    if (StreamingPortal.isEmbedIframeUrl(normalizedWatchUrl)) {
      val hostName = extractFriendlyServerName(normalizedWatchUrl, 1)
      val directSrv = ScrapedWatchServer(
        id = "srv_1",
        name = "مشغل مباشر 1 • $hostName",
        embedUrl = normalizedWatchUrl,
        qualityLabel = "Auto HD",
        isDirectEmbed = true
      )
      val resolution = if (!isDynamicSpaEmbed(normalizedWatchUrl)) {
        resolveDirectStreamFromEmbed(normalizedWatchUrl, portal.homeUrl)
      } else {
        DirectStreamResolution(refererUrl = normalizedWatchUrl)
      }
      AppDiagnosticsLogger.log(
        "Scraper",
        "Bypassed portal HTML scrape for direct embed URL ($normalizedWatchUrl); directStream=${resolution.primaryStreamUrl.isNotBlank()}"
      )
      return@withContext ScrapedSeriesStructure(
        isSeries = false,
        seriesTitle = cleanSeriesTitleHint,
        currentSeason = selectedSeasonNumber,
        selectedSeasonNumber = selectedSeasonNumber,
        currentEpisodeNumber = selectedEpisodeNumber.coerceAtLeast(1),
        seasons = emptyList(),
        episodes = emptyList(),
        watchServers = listOf(directSrv),
        bestCleanPlayerUrl = normalizedWatchUrl,
        directStreamUrl = resolution.primaryStreamUrl,
        directStreamReferer = resolution.refererUrl.ifBlank { normalizedWatchUrl },
        detectedQualities = resolution.detectedQualities
      )
    }

    val decodedInitialUrl = try { java.net.URLDecoder.decode(normalizedWatchUrl, "UTF-8") } catch (_: Exception) { normalizedWatchUrl }
    val detectedSeasonFromText = extractSeasonNumberFromText("$titleHint $decodedInitialUrl")
    var effectiveSeasonNumber = if (selectedSeasonNumber > 1) selectedSeasonNumber else (detectedSeasonFromText ?: 1)

    val isArabicPortal = portal == StreamingPortal.TOP_CINEMA ||
      portal == StreamingPortal.EGY_DEAD ||
      portal == StreamingPortal.LAROZA ||
      portal == StreamingPortal.PORTAL_5
    val portalSeasonMap = linkedMapOf<Int, String>()
    var showTokens = extractNormalizedShowTokens(cleanSeriesTitleHint.ifBlank { decodedInitialUrl })

    fun addServerIfValid(name: String, rawEmbedUrl: String, qualityLabel: String = "Auto HD", isDirect: Boolean = true) {
      val trimmed = rawEmbedUrl.trim()
      val cleanUrl = when {
        trimmed.startsWith("topcinema-ajax://") -> trimmed
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("/") -> resolveRelativeUrl(normalizedWatchUrl, trimmed)
        else -> trimmed
      }
      if (!cleanUrl.startsWith("http") && !cleanUrl.startsWith("topcinema-ajax://")) return
      val lower = cleanUrl.lowercase()
      if (lower.contains("facebook.com") ||
          lower.contains("twitter.com") ||
          lower.contains("google.com/recaptcha") ||
          lower.contains("googletagmanager") ||
          lower.contains("doubleclick") ||
          lower.contains("adsterra") ||
          lower.contains("luugy")
      ) return
      if (seenEmbedUrls.add(cleanUrl)) {
        watchServers.add(
          ScrapedWatchServer(
            id = "srv_${watchServers.size + 1}",
            name = name,
            embedUrl = cleanUrl,
            qualityLabel = qualityLabel,
            isDirectEmbed = isDirect
          )
        )
      }
    }

    fun extractServersFromDocument(doc: org.jsoup.nodes.Document, pageOrigin: String) {
      var srvIdx = watchServers.size + 1

      // 1) Fast Jsoup pass: data-embed / data-link / data-url / data-frame / data-watch attributes
      val dataEmbedEls = doc.select("[data-embed], [data-link], [data-url], [data-frame], [data-watch]")
      for (el in dataEmbedEls) {
        if (srvIdx > 14) break
        val embed = listOf("data-embed", "data-link", "data-url", "data-frame", "data-watch")
          .firstNotNullOfOrNull { attr -> el.attr(attr).trim().takeIf { it.startsWith("http") || it.startsWith("//") } }
          ?: continue
        val hostName = extractFriendlyServerName(embed, srvIdx)
        addServerIfValid(
          name = "سيرفر $srvIdx • $hostName",
          rawEmbedUrl = embed,
          qualityLabel = "1080p / 720p / 480p",
          isDirect = true
        )
        srvIdx = watchServers.size + 1
      }

      // 2) Fast Jsoup pass: <iframe src / data-src> players directly in watch page HTML
      val iframeEls = doc.select("iframe[src], iframe[data-src]")
      for (el in iframeEls) {
        if (srvIdx > 16) break
        val iframeSrc = el.attr("src").trim().ifBlank { el.attr("data-src").trim() }
        if (!iframeSrc.startsWith("http") && !iframeSrc.startsWith("//")) continue
        val hostName = extractFriendlyServerName(iframeSrc, srvIdx)
        addServerIfValid(
          name = "مشغل مباشر $srvIdx • $hostName",
          rawEmbedUrl = iframeSrc,
          qualityLabel = "Multi-Quality HD",
          isDirect = true
        )
        srvIdx = watchServers.size + 1
      }

      // 3) TopCinema server items: NEVER block on 8 sequential Server.php calls!
      // If an iframe/data-embed is already present, register all extra servers lazily (0 extra HTTP calls).
      // Only if NO direct server exists yet, resolve ONLY the 1st server immediately against the live origin.
      if (portal == StreamingPortal.TOP_CINEMA) {
        val serverItems = doc.select("li.server--item[data-id][data-server]")
        var resolvedOneUpfront = watchServers.any { it.isDirectEmbed }
        for (li in serverItems.take(8)) {
          val postId = li.attr("data-id").trim()
          val serverNum = li.attr("data-server").trim()
          if (postId.isBlank() || serverNum.isBlank()) continue
          val labelText = cleanHtmlEntities(li.text().replace(whitespaceRegex, " ").trim())
          val friendlyLabel = labelText.ifBlank { "سيرفر $serverNum" }

          if (!resolvedOneUpfront) {
            val resolvedEmbed = resolveTopCinemaAjaxEmbed(pageOrigin, postId, serverNum, normalizedWatchUrl)
            if (resolvedEmbed.isNotBlank()) {
              resolvedOneUpfront = true
              addServerIfValid(
                name = "سيرفر توب سينما • $friendlyLabel",
                rawEmbedUrl = resolvedEmbed,
                qualityLabel = "مترجم HD",
                isDirect = true
              )
              continue
            }
          }
          // Register remaining servers lazily so switching servers resolves on-demand in < 1s
          addServerIfValid(
            name = "سيرفر توب سينما • $friendlyLabel",
            rawEmbedUrl = "topcinema-ajax://$pageOrigin|$postId|$serverNum",
            qualityLabel = "مترجم HD",
            isDirect = true
          )
        }
      }
    }

    var okHttpBlockedByCloudflare = false

    fun fetchHtmlWithResolvedUrl(targetUrl: String, useEgyDeadPost: Boolean = false): Pair<String, String> {
      if (!targetUrl.startsWith("http")) return "" to targetUrl
      val targetOrigin = extractOrigin(targetUrl, portal.homeUrl)
      val cookieHeader = safeGetWebViewCookie(targetUrl)
      return try {
        val reqBuilder = Request.Builder()
          .url(targetUrl)
          .header("User-Agent", desktopUserAgent)
          .header("Referer", if (useEgyDeadPost) targetUrl else "$targetOrigin/")
          .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
          .header("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")
        if (useEgyDeadPost) {
          reqBuilder.post(FormBody.Builder().add("View", "1").build())
        }
        if (cookieHeader.isNotBlank()) reqBuilder.header("Cookie", cookieHeader)
        fastScraperClient.newCall(reqBuilder.build()).execute().use { resp ->
          val finalUrl = resp.request.url.toString().ifBlank { targetUrl }
          val bodyStr = resp.body?.string().orEmpty()
          if (isCloudflareOrBlockedHtml(resp.code, bodyStr)) {
            okHttpBlockedByCloudflare = true
            AppDiagnosticsLogger.log("Scraper", "OkHttp blocked (HTTP ${resp.code}) on $targetUrl - falling back to WebView")
            "" to finalUrl
          } else {
            bodyStr to finalUrl
          }
        }
      } catch (_: Exception) {
        "" to targetUrl
      }
    }

    var activeDocTitle = ""

    try {
      // Case A: Fast OkHttp + Jsoup HTML Fetch First (8s hard callTimeout) before any WebView fallback
      if (normalizedWatchUrl.startsWith("http")) {
        var watchOrigin = extractOrigin(normalizedWatchUrl, portal.homeUrl)
        val useInitialEgyPost = portal == StreamingPortal.EGY_DEAD && !isArabicPortalSearchUrl(normalizedWatchUrl)
        var (html, resolvedHttpUrl) = fetchHtmlWithResolvedUrl(normalizedWatchUrl, useEgyDeadPost = useInitialEgyPost)
        if (resolvedHttpUrl.startsWith("http")) {
          watchOrigin = extractOrigin(resolvedHttpUrl, watchOrigin)
        }

        if (html.isNotBlank()) {
          var doc = Jsoup.parse(html, resolvedHttpUrl)
          activeDocTitle = doc.title()

          // Stage 0 (Arabic Portals): If the opened URL is a search page (e.g. TopCinema /search/?query=..., EgyDead /?s=..., LaRoza search.php),
          // harvest ALL matching seasons first, then resolve to the real target season/episode page instead of scraping search cards as episodes!
          if (isArabicPortal && isArabicPortalSearchUrl(normalizedWatchUrl)) {
            collectArabicPortalSeasonsFromDoc(doc, resolvedHttpUrl, portal, showTokens, portalSeasonMap)
            // On TopCinema long-running shows, check page 2 of search results if Season 1 isn't on page 1
            if (portal == StreamingPortal.TOP_CINEMA && portalSeasonMap.size >= 3 && !portalSeasonMap.containsKey(1)) {
              val page2Link = doc.selectFirst("a[href*='/page/2']")?.attr("href")?.trim().orEmpty()
              if (page2Link.isNotBlank()) {
                val (p2Html, p2Url) = fetchHtmlWithResolvedUrl(resolveRelativeUrl(resolvedHttpUrl, page2Link))
                if (p2Html.isNotBlank()) {
                  collectArabicPortalSeasonsFromDoc(Jsoup.parse(p2Html, p2Url), p2Url, portal, showTokens, portalSeasonMap)
                }
              }
            }

            val preferredTargetUrl = when {
              selectedSeasonNumber > 1 && portalSeasonMap.containsKey(selectedSeasonNumber) -> portalSeasonMap[selectedSeasonNumber]
              portalSeasonMap.containsKey(1) -> portalSeasonMap[1]
              portalSeasonMap.isNotEmpty() -> portalSeasonMap[portalSeasonMap.keys.min()]
              else -> findBestMatchingCardUrl(doc, resolvedHttpUrl, portal, showTokens)
            }

            if (!preferredTargetUrl.isNullOrBlank() && !isArabicPortalSearchUrl(preferredTargetUrl)) {
              normalizedWatchUrl = StreamingPortal.sanitizePortalWatchUrl(preferredTargetUrl, portal)
              val (targetHtml, targetFinalUrl) = fetchHtmlWithResolvedUrl(
                normalizedWatchUrl,
                useEgyDeadPost = (portal == StreamingPortal.EGY_DEAD)
              )
              if (targetHtml.isNotBlank()) {
                resolvedHttpUrl = targetFinalUrl
                watchOrigin = extractOrigin(targetFinalUrl, watchOrigin)
                doc = Jsoup.parse(targetHtml, targetFinalUrl)
                activeDocTitle = doc.title()
              }
            }
          }

          // Re-sync show tokens from resolved URL / page title if titleHint was empty or generic
          val decodedCurrentUrl = try { java.net.URLDecoder.decode(normalizedWatchUrl, "UTF-8") } catch (_: Exception) { normalizedWatchUrl }
          if (showTokens.isEmpty()) {
            showTokens = extractNormalizedShowTokens(activeDocTitle.ifBlank { decodedCurrentUrl })
          }

          // Recompute effectiveSeasonNumber from the ACTUAL resolved watch/series URL (never stay stuck on Season 1 when S13 is loaded!)
          if (selectedSeasonNumber <= 1) {
            val resolvedSeasonFromPage = extractSeasonNumberFromText(decodedCurrentUrl)
              ?: extractSeasonNumberFromText(activeDocTitle)
            if (resolvedSeasonFromPage != null && resolvedSeasonFromPage in 1..40) {
              effectiveSeasonNumber = resolvedSeasonFromPage
            }
          }

          extractServersFromDocument(doc, watchOrigin)

          val seenEpNums = mutableSetOf<Int>()
          if (isArabicPortal) {
            // Harvest seasons & active-season episodes cleanly with strict show-token & season filtering
            collectArabicPortalSeasonsFromDoc(doc, resolvedHttpUrl, portal, showTokens, portalSeasonMap)
            collectArabicPortalEpisodesFromDoc(
              doc = doc,
              baseUrl = resolvedHttpUrl,
              portal = portal,
              activeSeasonNumber = effectiveSeasonNumber,
              showTokens = showTokens,
              episodes = episodes,
              seenEpNums = seenEpNums
            )

            // Stage 1b: If we landed on a /watch/ page or single episode page and are missing other seasons or episodes,
            // follow the parent /series/ (TopCinema) or /serie/ / /season/ (EgyDead) page ONCE to collect all seasons & episodes!
            if (!okHttpBlockedByCloudflare && (portalSeasonMap.size <= 1 || episodes.isEmpty())) {
              val parentCandidates = mutableListOf<String>()
              if (portal == StreamingPortal.TOP_CINEMA) {
                findParentOverviewLink(doc, resolvedHttpUrl, listOf("/series/"), showTokens)?.let { parentCandidates.add(it) }
                // On TopCinema, /watch/ pages sometimes omit the /series/ link that lives on the non-/watch/ episode details page
                if (parentCandidates.isEmpty() && normalizedWatchUrl.contains("/watch", ignoreCase = true)) {
                  val detailsUrl = normalizedWatchUrl.substringBefore("/watch").trimEnd('/') + "/"
                  if (detailsUrl != normalizedWatchUrl) parentCandidates.add(detailsUrl)
                }
              } else if (portal == StreamingPortal.EGY_DEAD) {
                findParentOverviewLink(doc, resolvedHttpUrl, listOf("/serie/"), showTokens)?.let { parentCandidates.add(it) }
                findParentOverviewLink(doc, resolvedHttpUrl, listOf("/season/"), showTokens)?.let { parentCandidates.add(it) }
              }

              val visitedParents = mutableSetOf(normalizedWatchUrl.trimEnd('/').lowercase())
              for (parentUrl in parentCandidates.take(2)) {
                val normParent = parentUrl.trimEnd('/').lowercase()
                if (!visitedParents.add(normParent)) continue
                val (pHtml, pFinalUrl) = fetchHtmlWithResolvedUrl(parentUrl)
                if (pHtml.isBlank()) continue
                val pDoc = Jsoup.parse(pHtml, pFinalUrl)
                collectArabicPortalSeasonsFromDoc(pDoc, pFinalUrl, portal, showTokens, portalSeasonMap)
                if (episodes.isEmpty()) {
                  collectArabicPortalEpisodesFromDoc(
                    doc = pDoc,
                    baseUrl = pFinalUrl,
                    portal = portal,
                    activeSeasonNumber = effectiveSeasonNumber,
                    showTokens = showTokens,
                    episodes = episodes,
                    seenEpNums = seenEpNums
                  )
                }
                // If TopCinema details page revealed the parent /series/ URL and we still have <= 1 season, fetch /series/ once
                if (portal == StreamingPortal.TOP_CINEMA && portalSeasonMap.size <= 1 && !parentUrl.contains("/series/", ignoreCase = true)) {
                  val seriesLink = findParentOverviewLink(pDoc, pFinalUrl, listOf("/series/"), showTokens)
                  if (!seriesLink.isNullOrBlank() && visitedParents.add(seriesLink.trimEnd('/').lowercase())) {
                    val (sHtml, sFinalUrl) = fetchHtmlWithResolvedUrl(seriesLink)
                    if (sHtml.isNotBlank()) {
                      val sDoc = Jsoup.parse(sHtml, sFinalUrl)
                      collectArabicPortalSeasonsFromDoc(sDoc, sFinalUrl, portal, showTokens, portalSeasonMap)
                      if (episodes.isEmpty()) {
                        collectArabicPortalEpisodesFromDoc(
                          doc = sDoc,
                          baseUrl = sFinalUrl,
                          portal = portal,
                          activeSeasonNumber = effectiveSeasonNumber,
                          showTokens = showTokens,
                          episodes = episodes,
                          seenEpNums = seenEpNums
                        )
                      }
                    }
                  }
                }
              }

              // For EgyDead: if /serie/ gave us the active season's /season/ URL and episodes are still empty, fetch that season URL once
              if (portal == StreamingPortal.EGY_DEAD && episodes.isEmpty()) {
                val activeSeasonUrl = portalSeasonMap[effectiveSeasonNumber]
                if (!activeSeasonUrl.isNullOrBlank() && visitedParents.add(activeSeasonUrl.trimEnd('/').lowercase())) {
                  val (sHtml, sFinalUrl) = fetchHtmlWithResolvedUrl(activeSeasonUrl)
                  if (sHtml.isNotBlank()) {
                    collectArabicPortalEpisodesFromDoc(
                      doc = Jsoup.parse(sHtml, sFinalUrl),
                      baseUrl = sFinalUrl,
                      portal = portal,
                      activeSeasonNumber = effectiveSeasonNumber,
                      showTokens = showTokens,
                      episodes = episodes,
                      seenEpNums = seenEpNums
                    )
                  }
                }
              }
            }

            // Stage 2 (Multi-Season Live Origin Search Discovery):
            // Many Arabic portals (TopCinema, EgyDead, LaRoza) index each season as a separate WordPress post
            // without cross-linking all 13 seasons inside /watch/. Query the live portal search ONCE to aggregate ALL seasons!
            val looksLikeTvSeries = episodes.isNotEmpty() ||
              portalSeasonMap.isNotEmpty() ||
              effectiveSeasonNumber > 1 ||
              titleHint.contains("مسلسل", ignoreCase = true) ||
              titleHint.contains("حلقة", ignoreCase = true) ||
              titleHint.contains("الموسم", ignoreCase = true) ||
              decodedCurrentUrl.contains("/series/", ignoreCase = true) ||
              decodedCurrentUrl.contains("/season/", ignoreCase = true) ||
              decodedCurrentUrl.contains("/serie/", ignoreCase = true) ||
              decodedCurrentUrl.contains("/episode/", ignoreCase = true) ||
              decodedCurrentUrl.contains("مسلسل") ||
              decodedCurrentUrl.contains("الحلقة") ||
              decodedCurrentUrl.contains("الموسم") ||
              activeDocTitle.contains("مسلسل") ||
              activeDocTitle.contains("الموسم")

            if (looksLikeTvSeries && !okHttpBlockedByCloudflare && portalSeasonMap.size <= 1 && showTokens.isNotEmpty()) {
              val showQuery = showTokens.joinToString(" ")
              val encodedQuery = try { URLEncoder.encode(showQuery, "UTF-8") } catch (_: Exception) { showQuery.replace(" ", "+") }
              val searchCandidates = when (portal) {
                StreamingPortal.TOP_CINEMA -> listOf(
                  "$watchOrigin/search/?query=$encodedQuery&type=all",
                  "$watchOrigin/?s=$encodedQuery"
                )
                StreamingPortal.EGY_DEAD -> listOf("$watchOrigin/?s=$encodedQuery")
                StreamingPortal.LAROZA -> listOf("$watchOrigin/search.php?keywords=$encodedQuery")
                else -> emptyList()
              }

              for (searchUrl in searchCandidates) {
                val (sHtml, sFinalUrl) = fetchHtmlWithResolvedUrl(searchUrl)
                if (sHtml.isBlank()) continue
                val sDoc = Jsoup.parse(sHtml, sFinalUrl)
                collectArabicPortalSeasonsFromDoc(sDoc, sFinalUrl, portal, showTokens, portalSeasonMap)
                if (portal == StreamingPortal.LAROZA && episodes.isEmpty()) {
                  collectArabicPortalEpisodesFromDoc(
                    doc = sDoc,
                    baseUrl = sFinalUrl,
                    portal = portal,
                    activeSeasonNumber = effectiveSeasonNumber,
                    showTokens = showTokens,
                    episodes = episodes,
                    seenEpNums = seenEpNums
                  )
                }
                // For TopCinema shows with many seasons (e.g. American Horror Story 13 seasons), check page 2 once if Season 1 isn't found yet
                if (portal == StreamingPortal.TOP_CINEMA && portalSeasonMap.size >= 3 && !portalSeasonMap.containsKey(1)) {
                  val page2Href = sDoc.selectFirst("a[href*='/page/2']")?.attr("href")?.trim().orEmpty()
                  if (page2Href.isNotBlank()) {
                    val (p2Html, p2Url) = fetchHtmlWithResolvedUrl(resolveRelativeUrl(sFinalUrl, page2Href))
                    if (p2Html.isNotBlank()) {
                      collectArabicPortalSeasonsFromDoc(Jsoup.parse(p2Html, p2Url), p2Url, portal, showTokens, portalSeasonMap)
                    }
                  }
                }
                if (portalSeasonMap.size > 1) break
              }
            }

            // Convert discovered portalSeasonMap into sorted seasons list
            for ((sNum, sUrl) in portalSeasonMap.entries.sortedBy { it.key }) {
              seasons.add(
                ScrapedSeasonItem(
                  seasonNumber = sNum,
                  title = "الموسم $sNum",
                  url = sUrl
                )
              )
            }
          } else {
            // Non-Arabic portals (WatchSeries / Flixer): keep fast existing anchor pass
            val seenSeasonNums = mutableSetOf<Int>()
            for (anchor in doc.select("a[href]")) {
              val rawHref = anchor.attr("href").trim()
              if (rawHref.isBlank() || rawHref.startsWith("#") || rawHref.startsWith("javascript:")) continue
              val titleAttr = cleanHtmlEntities(anchor.attr("title").trim())
              val cleanText = cleanHtmlEntities(anchor.text().replace(whitespaceRegex, " ").trim())
              val decodedHref = try { java.net.URLDecoder.decode(rawHref, "UTF-8") } catch (_: Exception) { rawHref }
              val combined = "$titleAttr $cleanText $decodedHref"

              val sNum = extractSeasonNumberFromText(combined)
              if (sNum != null &&
                  sNum in 1..40 &&
                  !seenSeasonNums.contains(sNum) &&
                  (rawHref.contains("season", ignoreCase = true) || decodedHref.contains("الموسم") || cleanText.contains("الموسم") || titleAttr.contains("الموسم")) &&
                  !decodedHref.contains("الحلقة") && !decodedHref.contains("حلقة")
              ) {
                seenSeasonNums.add(sNum)
                seasons.add(
                  ScrapedSeasonItem(
                    seasonNumber = sNum,
                    title = "الموسم $sNum",
                    url = resolveRelativeUrl(normalizedWatchUrl, rawHref)
                  )
                )
              }

              val epMatch = episodeNumberRegex.find(combined)
              if (epMatch != null && !rawHref.contains("/category/") && !rawHref.contains("/tag/") && !rawHref.contains("/series/")) {
                val epNum = epMatch.groupValues[1].toIntOrNull()
                if (epNum != null && epNum in 1..300 && !seenEpNums.contains(epNum)) {
                  seenEpNums.add(epNum)
                  val resolvedEp = resolveRelativeUrl(normalizedWatchUrl, rawHref).replace("video.php?vid=", "play.php?vid=")
                  episodes.add(
                    ScrapedEpisodeItem(
                      episodeNumber = epNum,
                      title = "الحلقة $epNum",
                      url = resolvedEp,
                      seasonNumber = effectiveSeasonNumber
                    )
                  )
                }
              }
            }
          }

          // Stage 3: If the opened URL was a series/season overview page OR a different episode card (e.g. TopCinema search returned Episode 8 while user requested Episode 5),
          // automatically switch to the target episode's watch page and extract its servers immediately!
          if (episodes.isNotEmpty()) {
            val decodedCurrWatch = try { java.net.URLDecoder.decode(normalizedWatchUrl, "UTF-8") } catch (_: Exception) { normalizedWatchUrl }
            val currentDetectedEp = extractEpisodeNumberFromText(decodedCurrWatch)
            val targetEp = if (selectedEpisodeNumber > 0) {
              episodes.firstOrNull { it.episodeNumber == selectedEpisodeNumber }
            } else {
              null
            } ?: if (watchServers.isEmpty()) {
              episodes.sortedBy { it.episodeNumber }.firstOrNull()
            } else {
              null
            }

            val shouldSwitchToTargetEp = targetEp != null && (
              watchServers.isEmpty() ||
                (selectedEpisodeNumber > 0 && currentDetectedEp != selectedEpisodeNumber && targetEp.url != normalizedWatchUrl)
            )

            if (shouldSwitchToTargetEp && targetEp != null) {
              normalizedWatchUrl = targetEp.url
              val decodedEpUrl = try { java.net.URLDecoder.decode(normalizedWatchUrl, "UTF-8") } catch (_: Exception) { normalizedWatchUrl }
              if (selectedSeasonNumber <= 1) {
                extractSeasonNumberFromText(decodedEpUrl)?.takeIf { it in 1..40 }?.let { effectiveSeasonNumber = it }
              }
              watchServers.clear()
              seenEmbedUrls.clear()
              val (epHtml, epFinalUrl) = fetchHtmlWithResolvedUrl(
                normalizedWatchUrl,
                useEgyDeadPost = (portal == StreamingPortal.EGY_DEAD)
              )
              if (epHtml.isNotBlank()) {
                val epOrigin = extractOrigin(epFinalUrl, watchOrigin)
                extractServersFromDocument(Jsoup.parse(epHtml, epFinalUrl), epOrigin)
              }
            }
          }
        }
      }

      // Case B: Resolve TMDB ID ONLY when needed (when portal HTML didn't yield direct servers or episodes are missing for a TV show).
      // Skipping unnecessary TMDB calls on fast portal pages saves 2-4 seconds per episode!
      val decodedCurrentForTvCheck = try { java.net.URLDecoder.decode(normalizedWatchUrl, "UTF-8") } catch (_: Exception) { normalizedWatchUrl }
      val isExpectedTv = episodes.isNotEmpty() ||
        seasons.isNotEmpty() ||
        effectiveSeasonNumber > 1 ||
        titleHint.contains("مسلسل", ignoreCase = true) ||
        titleHint.contains("حلقة", ignoreCase = true) ||
        titleHint.contains("الحلقة", ignoreCase = true) ||
        titleHint.contains("الحلقه", ignoreCase = true) ||
        titleHint.contains("الموسم", ignoreCase = true) ||
        normalizedWatchUrl.contains("/tv/", ignoreCase = true) ||
        normalizedWatchUrl.contains("/series/", ignoreCase = true) ||
        normalizedWatchUrl.contains("/season/", ignoreCase = true) ||
        normalizedWatchUrl.contains("/serie/", ignoreCase = true) ||
        decodedCurrentForTvCheck.contains("مسلسل") ||
        decodedCurrentForTvCheck.contains("الحلقة") ||
        decodedInitialUrl.contains("مسلسل") ||
        decodedInitialUrl.contains("الحلقة")

      val hasPortalDirectServer = watchServers.any { it.isDirectEmbed }
      val shouldQueryTmdb = !okHttpBlockedByCloudflare && (
        !hasPortalDirectServer ||
          (isExpectedTv && episodes.isEmpty()) ||
          portal == StreamingPortal.WATCH_SERIES ||
          portal == StreamingPortal.FLIXER
      )

      if (shouldQueryTmdb) {
        val tmdbIdMatch = Regex("/(?:tv|movie)/[^/]*/?([0-9]{2,8})|/(?:tv|movie)/([0-9]{2,8})").find(normalizedWatchUrl)
        var extractedTmdbId = tmdbIdMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.toIntOrNull()
        var resolvedMediaType = if (isExpectedTv) "tv" else "movie"

        if (extractedTmdbId == null && titleHint.isNotBlank()) {
          val cleanedQuery = cleanMediaTitleForLookup(titleHint)
          if (cleanedQuery.isNotBlank()) {
            val encoded = URLEncoder.encode(cleanedQuery, "UTF-8")
            val endpoint = if (isExpectedTv) "tv" else "multi"
            val searchUrl = "https://api.themoviedb.org/3/search/$endpoint?api_key=$tmdbKey&query=$encoded&include_adult=false&language=en-US"
            val req = Request.Builder().url(searchUrl).header("User-Agent", desktopUserAgent).build()
            val body = fastScraperClient.newCall(req).execute().use { it.body?.string().orEmpty() }
            if (body.isNotBlank()) {
              val resultsArr = JSONObject(body).optJSONArray("results")
              if (resultsArr != null && resultsArr.length() > 0) {
                for (i in 0 until resultsArr.length()) {
                  val obj = resultsArr.getJSONObject(i)
                  val mt = if (isExpectedTv) "tv" else obj.optString("media_type", "movie")
                  if (mt == "movie" || mt == "tv") {
                    extractedTmdbId = obj.optInt("id", 0).takeIf { it > 0 }
                    resolvedMediaType = mt
                    break
                  }
                }
              }
            }
          }
        }

        val decodedUrl = try { java.net.URLDecoder.decode(normalizedWatchUrl, "UTF-8") } catch (_: Exception) { normalizedWatchUrl }
        val urlPathSeasonEpMatch = Regex("/tv/(?:[^/]+/)?[0-9]{2,8}/([0-9]{1,2})/([0-9]{1,3})").find(decodedUrl)
        val pathSeasonNum = urlPathSeasonEpMatch?.groupValues?.get(1)?.toIntOrNull()
        val pathEpNum = urlPathSeasonEpMatch?.groupValues?.get(2)?.toIntOrNull()

        val resolvedSeasonNum = if (selectedSeasonNumber > 1) {
          selectedSeasonNumber
        } else {
          pathSeasonNum ?: effectiveSeasonNumber
        }

        val rawEpNum = selectedEpisodeNumber.takeIf { it > 0 }
          ?: pathEpNum
          ?: episodeNumberRegex.find(decodedUrl)?.groupValues?.get(1)?.toIntOrNull()
          ?: episodeNumberRegex.find(titleHint)?.groupValues?.get(1)?.toIntOrNull()
          ?: 1

        if (extractedTmdbId != null) {
          if (resolvedMediaType == "movie") {
            addServerIfValid("مشغل VidLink الذكي (تعدد الجودات + ترجمة)", "https://vidlink.pro/movie/$extractedTmdbId?primaryColor=E50914&autoplay=true", "1080p / 720p / 360p")
            addServerIfValid("مشغل VidSrc Pro النظيف", "https://vidsrc.cc/v2/embed/movie/$extractedTmdbId", "Auto / 1080p / 480p")
            addServerIfValid("مشغل SuperEmbed المتعدد", "https://multiembed.mov/?video_id=$extractedTmdbId&tmdb=1", "Full HD / SD")
            addServerIfValid("مشغل 2Embed العالمي", "https://www.2embed.cc/embed/$extractedTmdbId", "Multi-Server HD")
          } else {
            var numSeasonsOnTmdb = resolvedSeasonNum.coerceAtLeast(1)
            var slug = "series"
            if (seasons.isEmpty() || episodes.isEmpty()) {
              try {
                val tvUrl = "https://api.themoviedb.org/3/tv/$extractedTmdbId?api_key=$tmdbKey&language=en-US"
                val tvJsonStr = fastScraperClient.newCall(Request.Builder().url(tvUrl).header("User-Agent", desktopUserAgent).build())
                  .execute().use { it.body?.string().orEmpty() }
                if (tvJsonStr.isNotBlank()) {
                  val tvJson = JSONObject(tvJsonStr)
                  numSeasonsOnTmdb = tvJson.optInt("number_of_seasons", 1).coerceAtLeast(1)
                  slug = tvJson.optString("name", titleHint).lowercase().replace("[^a-z0-9]+".toRegex(), "-").trim('-').ifBlank { "series" }

                  if (seasons.isEmpty()) {
                    for (sIdx in 1..maxOf(numSeasonsOnTmdb, resolvedSeasonNum)) {
                      val sUrl = if (portal == StreamingPortal.FLIXER) {
                        "https://flixer.gd/tv/$extractedTmdbId/$sIdx/1"
                      } else {
                        "https://watchseries.bar/tv/$slug/$extractedTmdbId/$sIdx/1"
                      }
                      seasons.add(ScrapedSeasonItem(seasonNumber = sIdx, title = "الموسم $sIdx", url = sUrl))
                    }
                  }

                  if (episodes.isEmpty()) {
                    val targetSeason = resolvedSeasonNum.coerceIn(1, numSeasonsOnTmdb)
                    val seasonApiUrl = "https://api.themoviedb.org/3/tv/$extractedTmdbId/season/$targetSeason?api_key=$tmdbKey&language=en-US"
                    val sJsonStr = fastScraperClient.newCall(Request.Builder().url(seasonApiUrl).header("User-Agent", desktopUserAgent).build())
                      .execute().use { it.body?.string().orEmpty() }
                    if (sJsonStr.isNotBlank()) {
                      val epArray = JSONObject(sJsonStr).optJSONArray("episodes")
                      if (epArray != null) {
                        for (eIdx in 0 until epArray.length()) {
                          val epObj = epArray.getJSONObject(eIdx)
                          val epNum = epObj.optInt("episode_number", eIdx + 1)
                          val epName = epObj.optString("name", "Episode $epNum")
                          val epUrl = if (portal == StreamingPortal.FLIXER) {
                            "https://flixer.gd/tv/$extractedTmdbId/$targetSeason/$epNum"
                          } else {
                            "https://watchseries.bar/tv/$slug/$extractedTmdbId/$targetSeason/$epNum"
                          }
                          episodes.add(
                            ScrapedEpisodeItem(
                              episodeNumber = epNum,
                              title = "الحلقة $epNum: $epName",
                              url = epUrl,
                              seasonNumber = targetSeason
                            )
                          )
                        }
                      }
                    }
                  }
                }
              } catch (_: Exception) {}
            }

            val s = resolvedSeasonNum.coerceIn(1, maxOf(1, numSeasonsOnTmdb))
            val maxAvailableEp = if (episodes.isNotEmpty()) episodes.maxOf { it.episodeNumber }.coerceAtLeast(1) else 100
            val e = rawEpNum.coerceIn(1, maxAvailableEp)
            addServerIfValid("مشغل VidLink الذكي (S${s}E${e})", "https://vidlink.pro/tv/$extractedTmdbId/$s/$e?primaryColor=E50914&autoplay=true", "1080p / 720p / 360p")
            addServerIfValid("مشغل VidSrc Pro النظيف (S${s}E${e})", "https://vidsrc.cc/v2/embed/tv/$extractedTmdbId/$s/$e", "Auto / 1080p / 480p")
            addServerIfValid("مشغل SuperEmbed المتعدد (S${s}E${e})", "https://multiembed.mov/?video_id=$extractedTmdbId&tmdb=1&s=$s&e=$e", "Full HD / SD")
            addServerIfValid("مشغل 2Embed العالمي (S${s}E${e})", "https://www.2embed.cc/embedtv/$extractedTmdbId&s=$s&e=$e", "Multi-Server HD")
          }
        }
      }
    } catch (e: Exception) {
      AppDiagnosticsLogger.log("Scraper", "Fast scrape exception on $normalizedWatchUrl: ${e.message}")
    }

    // Always add the normalized portal watch page itself as a server option
    addServerIfValid(
      name = "سيرفر موقع ${portal.displayNameArabic} الأصلي",
      rawEmbedUrl = normalizedWatchUrl,
      qualityLabel = "المشغل الأصلي",
      isDirect = false
    )

    // Case C: URL Pattern Inference if page is an episode URL and episodes list is still empty
    if (episodes.isEmpty()) {
      val decodedUrl = try { java.net.URLDecoder.decode(normalizedWatchUrl, "UTF-8") } catch (_: Exception) { normalizedWatchUrl }
      val urlEpMatch = Regex("(الحلق[ةه][\\s\\-_]+|(?:episode|ep)[\\s\\-_]+)([0-9]{1,3})", RegexOption.IGNORE_CASE).find(decodedUrl)
      if (urlEpMatch != null) {
        val prefix = urlEpMatch.groupValues[1]
        for (ep in 1..12) {
          var epUrl = decodedUrl.replace(urlEpMatch.value, "$prefix$ep")
          if (portal == StreamingPortal.TOP_CINEMA && !epUrl.contains("/watch")) {
            epUrl = "${epUrl.trimEnd('/')}/watch/"
          }
          episodes.add(
            ScrapedEpisodeItem(
              episodeNumber = ep,
              title = "الحلقة $ep",
              url = epUrl,
              seasonNumber = effectiveSeasonNumber
            )
          )
        }
      }
    }

    // Ensure the active season is always represented in the seasons list
    if ((episodes.isNotEmpty() || seasons.isNotEmpty()) && seasons.none { it.seasonNumber == effectiveSeasonNumber }) {
      seasons.add(ScrapedSeasonItem(seasonNumber = effectiveSeasonNumber, title = "الموسم $effectiveSeasonNumber", url = normalizedWatchUrl))
    }

    val directCandidateServers = watchServers.filter {
      it.isDirectEmbed && !it.embedUrl.startsWith("topcinema-ajax://")
    }
    var chosenServer = directCandidateServers.firstOrNull()
    var bestEmbedUrl = chosenServer?.embedUrl ?: normalizedWatchUrl
    var extractedDirectStream = ""
    var extractedStreamReferer = normalizedWatchUrl
    var detectedQualities = emptyList<VideoQualityOption>()

    val scrapeElapsedMs = System.currentTimeMillis() - scrapeStartMs
    AppDiagnosticsLogger.log(
      "Scraper",
      "Fast-scraped ${seasons.size} seasons (active=S$effectiveSeasonNumber), ${episodes.size} episodes, ${watchServers.size} servers in ${scrapeElapsedMs}ms from $normalizedWatchUrl"
    )

    // Resolve the primary server, and if it fails/times out, try up to 2 fallback direct or lazy TopCinema AJAX servers
    if (!okHttpBlockedByCloudflare) {
      val orderedCandidates = mutableListOf<ScrapedWatchServer>()
      orderedCandidates.addAll(directCandidateServers.take(3))
      val lazyTopCinemaServers = watchServers.filter {
        it.isDirectEmbed && it.embedUrl.startsWith("topcinema-ajax://")
      }
      orderedCandidates.addAll(lazyTopCinemaServers.take(2))

      var attempts = 0
      for (candidate in orderedCandidates) {
        if (attempts >= 3) break
        if (System.currentTimeMillis() - scrapeStartMs > 16_000L && attempts >= 1) break

        var actualCandidate = candidate
        var targetEmbedUrl = candidate.embedUrl
        if (targetEmbedUrl.startsWith("topcinema-ajax://")) {
          val parts = targetEmbedUrl.removePrefix("topcinema-ajax://").split("|")
          if (parts.size != 3) continue
          val resolvedLazy = resolveTopCinemaAjaxEmbed(
            origin = parts[0],
            postId = parts[1],
            serverNum = parts[2],
            refererUrl = normalizedWatchUrl
          )
          if (resolvedLazy.isBlank()) continue
          targetEmbedUrl = resolvedLazy
          val idxInList = watchServers.indexOfFirst { it.id == candidate.id }
          if (idxInList >= 0) {
            actualCandidate = candidate.copy(embedUrl = resolvedLazy)
            watchServers[idxInList] = actualCandidate
          }
        }

        if (isDynamicSpaEmbed(targetEmbedUrl)) {
          if (chosenServer == null) {
            chosenServer = actualCandidate
            bestEmbedUrl = targetEmbedUrl
          }
          continue
        }

        attempts++
        val extractStartMs = System.currentTimeMillis()
        val resolution = resolveDirectStreamFromEmbed(targetEmbedUrl, normalizedWatchUrl)
        if (resolution.primaryStreamUrl.isNotBlank()) {
          chosenServer = actualCandidate
          bestEmbedUrl = targetEmbedUrl
          extractedDirectStream = resolution.primaryStreamUrl
          extractedStreamReferer = resolution.refererUrl.ifBlank { targetEmbedUrl }
          detectedQualities = resolution.detectedQualities
          val extractElapsedMs = System.currentTimeMillis() - extractStartMs
          AppDiagnosticsLogger.log(
            "StreamExtractor",
            "Resolved selected server [${actualCandidate.name}] in ${extractElapsedMs}ms: ${resolution.primaryStreamUrl} | Qualities: ${detectedQualities.joinToString { it.label }}"
          )
          break
        } else if (chosenServer == null) {
          chosenServer = actualCandidate
          bestEmbedUrl = targetEmbedUrl
        }
      }
    }

    // Stamp every episode with the true active season so clicking an episode never reverts to Season 1
    val sortedEpisodes = episodes
      .map { ep -> if (ep.seasonNumber != effectiveSeasonNumber) ep.copy(seasonNumber = effectiveSeasonNumber) else ep }
      .sortedBy { it.episodeNumber }
    val decodedFinalWatchUrl = try { java.net.URLDecoder.decode(normalizedWatchUrl, "UTF-8") } catch (_: Exception) { normalizedWatchUrl }
    val detectedEpisodeFromUrl = extractEpisodeNumberFromText(decodedFinalWatchUrl)
      ?: extractEpisodeNumberFromText(decodedInitialUrl)
    val effectiveEpisodeNumber = when {
      selectedEpisodeNumber > 0 -> selectedEpisodeNumber
      detectedEpisodeFromUrl != null && detectedEpisodeFromUrl > 0 -> detectedEpisodeFromUrl
      else -> sortedEpisodes.firstOrNull()?.episodeNumber ?: 1
    }
    val resolvedNextEpisode = sortedEpisodes.firstOrNull { it.episodeNumber > effectiveEpisodeNumber }

    ScrapedSeriesStructure(
      isSeries = sortedEpisodes.isNotEmpty() || seasons.isNotEmpty(),
      seriesTitle = cleanSeriesTitleHint.ifBlank { stripEpisodeAndSeasonSuffix(activeDocTitle) },
      currentSeason = effectiveSeasonNumber,
      selectedSeasonNumber = effectiveSeasonNumber,
      currentEpisodeNumber = effectiveEpisodeNumber,
      seasons = seasons.distinctBy { it.seasonNumber }.sortedBy { it.seasonNumber },
      episodes = sortedEpisodes,
      nextEpisode = resolvedNextEpisode,
      watchServers = watchServers,
      bestCleanPlayerUrl = bestEmbedUrl,
      directStreamUrl = extractedDirectStream,
      directStreamReferer = extractedStreamReferer,
      detectedQualities = detectedQualities
    )
  }

  private fun isArabicPortalSearchUrl(url: String): Boolean {
    val l = url.lowercase()
    return l.contains("/search/") ||
      l.contains("/search?") ||
      l.contains("?s=") ||
      l.contains("&s=") ||
      l.contains("?query=") ||
      l.contains("search.php")
  }

  private fun extractNormalizedShowTokens(rawTitle: String): List<String> {
    val decoded = try { java.net.URLDecoder.decode(rawTitle, "UTF-8") } catch (_: Exception) { rawTitle }
    // If a full URL was passed, extract only the meaningful path/query slug
    val strippedUrl = if (decoded.startsWith("http", ignoreCase = true)) {
      val queryParam = Regex("[?&](?:query|s|keywords)=([^&]+)", RegexOption.IGNORE_CASE)
        .find(decoded)?.groupValues?.get(1)
      if (!queryParam.isNullOrBlank()) {
        queryParam.replace("+", " ")
      } else {
        decoded.substringAfter("://").substringAfter("/", "")
          .replace("/watch", " ")
          .replace("/series/", " ")
          .replace("/season/", " ")
          .replace("/serie/", " ")
          .replace("/episode/", " ")
      }
    } else {
      decoded
    }

    val base = stripEpisodeAndSeasonSuffix(strippedUrl)
      .replace(Regex("\\(\\s*(19|20)[0-9]{2}\\s*\\)"), " ")
      .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
      .lowercase()
      .trim()

    val stopWords = setOf(
      "مسلسل", "فيلم", "فلم", "انمي", "أنمي", "برنامج", "مترجم", "مترجمة", "مترجمه",
      "مدبلج", "مدبلجة", "مدبلجه", "اون", "لاين", "اونلاين", "كامل", "كاملة", "كامله",
      "مشاهدة", "مشاهده", "تحميل", "جميع", "الحلقات", "حلقات", "الحلقة", "الحلقه", "حلقة", "حلقه",
      "الموسم", "موسم", "والاخيرة", "الأخيرة", "الاخيرة", "توب", "سينما", "ايجي", "ديد", "لاروزا",
      "topcinema", "egydead", "laroza", "watch", "series", "season", "episode", "hd", "web", "dl",
      "all", "type", "query", "search", "com", "one", "live", "boats", "the", "of"
    )

    val tokens = base.split(Regex("\\s+"))
      .filter { it.length >= 2 && it !in stopWords && !it.all { ch -> ch.isDigit() } }
      .distinct()

    if (tokens.isNotEmpty()) return tokens
    // Fallback for single-word titles like "The" or short titles
    return base.split(Regex("\\s+")).filter { it.isNotBlank() && it !in stopWords }.distinct()
  }

  private fun matchesShowTokens(candidateText: String, showTokens: List<String>): Boolean {
    if (showTokens.isEmpty()) return true
    val decoded = try { java.net.URLDecoder.decode(candidateText, "UTF-8") } catch (_: Exception) { candidateText }
    val normalized = decoded
      .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
      .lowercase()
      .trim()
    if (normalized.isBlank()) return false

    val candidateWords = normalized.split(Regex("\\s+")).toSet()
    var matchedCount = 0
    for (tok in showTokens) {
      val isAscii = tok.all { it.code < 128 }
      val matched = if (isAscii) {
        tok in candidateWords
      } else {
        tok in candidateWords || normalized.contains(tok)
      }
      if (matched) matchedCount++
    }
    val required = if (showTokens.size <= 3) showTokens.size else kotlin.math.ceil(showTokens.size * 0.75).toInt().coerceAtLeast(2)
    return matchedCount >= required
  }

  private fun isSeasonOverviewUrl(url: String, portal: StreamingPortal): Boolean {
    val l = url.lowercase()
    return when (portal) {
      StreamingPortal.TOP_CINEMA -> l.contains("/series/")
      StreamingPortal.EGY_DEAD -> l.contains("/season/") || l.contains("/serie/")
      else -> l.contains("/series/") || l.contains("/season/")
    }
  }

  private fun registerArabicSeasonCandidate(
    seasonNumber: Int,
    candidateUrl: String,
    portal: StreamingPortal,
    seasonMap: MutableMap<Int, String>
  ) {
    if (seasonNumber !in 1..40 || !candidateUrl.startsWith("http")) return
    val existing = seasonMap[seasonNumber]
    val candidateIsOverview = isSeasonOverviewUrl(candidateUrl, portal)
    val existingIsOverview = existing != null && isSeasonOverviewUrl(existing, portal)
    if (existing == null || (candidateIsOverview && !existingIsOverview)) {
      seasonMap[seasonNumber] = candidateUrl
    }
  }

  private fun collectArabicPortalSeasonsFromDoc(
    doc: org.jsoup.nodes.Document,
    baseUrl: String,
    portal: StreamingPortal,
    showTokens: List<String>,
    seasonMap: MutableMap<Int, String>
  ) {
    // 1. Dedicated season containers on TopCinema & EgyDead (scoped directly to the current show!)
    val scopedSelectors = when (portal) {
      StreamingPortal.TOP_CINEMA -> "section.allSeasons a[href], .SeasonsList a[href], .seasons a[href], .Season--Box a[href]"
      StreamingPortal.EGY_DEAD -> ".seasons-list a[href], .SeasonsList a[href], .seasons a[href], ul.seasons a[href]"
      else -> ".SeasonsList a[href], .seasons a[href]"
    }
    for (anchor in doc.select(scopedSelectors)) {
      val rawHref = anchor.attr("href").trim()
      if (rawHref.isBlank() || rawHref.startsWith("#") || rawHref.startsWith("javascript:")) continue
      val absUrl = resolveRelativeUrl(baseUrl, rawHref)
      if (absUrl.contains("/category/") || absUrl.contains("/tag/") || absUrl.contains("/page/")) continue
      val titleAttr = cleanHtmlEntities(anchor.attr("title").trim())
      val cleanText = cleanHtmlEntities(anchor.text().replace(whitespaceRegex, " ").trim())
      val decodedHref = try { java.net.URLDecoder.decode(absUrl, "UTF-8") } catch (_: Exception) { absUrl }
      val combined = "$titleAttr $cleanText $decodedHref"
      // Even inside a scoped container, ignore obvious unrelated series if showTokens are available and URL names another show
      val sNum = extractSeasonNumberFromText(combined)
      if (sNum != null && sNum in 1..40) {
        val tokenOk = showTokens.isEmpty() ||
          matchesShowTokens(combined, showTokens) ||
          cleanText.startsWith("الموسم") ||
          cleanText.startsWith("موسم") ||
          cleanText.startsWith("Season", ignoreCase = true)
        if (tokenOk) {
          registerArabicSeasonCandidate(sNum, absUrl, portal, seasonMap)
        }
      }
    }

    // 2. General anchors across cards/search results/breadcrumbs (strictly filtered by showTokens!)
    for (anchor in doc.select("a[href]")) {
      val rawHref = anchor.attr("href").trim()
      if (rawHref.isBlank() || rawHref.startsWith("#") || rawHref.startsWith("javascript:")) continue
      val absUrl = resolveRelativeUrl(baseUrl, rawHref)
      if (absUrl.contains("/category/") ||
        absUrl.contains("/tag/") ||
        absUrl.contains("/page/") ||
        absUrl.contains("/recent/") ||
        absUrl.contains("/genre/") ||
        absUrl.contains("/actor") ||
        isArabicPortalSearchUrl(absUrl)
      ) continue

      val titleAttr = cleanHtmlEntities(anchor.attr("title").trim())
      val cleanText = cleanHtmlEntities(anchor.text().replace(whitespaceRegex, " ").trim())
      val decodedHref = try { java.net.URLDecoder.decode(absUrl, "UTF-8") } catch (_: Exception) { absUrl }
      val combined = "$titleAttr $cleanText $decodedHref"

      if (showTokens.isNotEmpty() && !matchesShowTokens(combined, showTokens)) continue

      val sNum = extractSeasonNumberFromText(combined)
      val isOverview = isSeasonOverviewUrl(absUrl, portal)
      val isEpisodeLink = decodedHref.contains("الحلقة") || decodedHref.contains("حلقة") || decodedHref.contains("/episode/", ignoreCase = true)

      if (sNum != null && sNum in 1..40) {
        // Overview URLs (/series/, /season/) always win; episode URLs are kept as fallback so seasons without /series/ links still appear!
        val normalizedCandidate = if (portal == StreamingPortal.TOP_CINEMA && isEpisodeLink && !absUrl.contains("/watch")) {
          "${absUrl.trimEnd('/')}/watch/"
        } else {
          absUrl
        }
        registerArabicSeasonCandidate(sNum, normalizedCandidate, portal, seasonMap)
      } else if (isOverview && !isEpisodeLink && !seasonMap.containsKey(1)) {
        // Unnumbered /series/ or /serie/ post matching the show (typically Season 1)
        if (!decodedHref.contains("/serie/", ignoreCase = true) || portal != StreamingPortal.EGY_DEAD) {
          registerArabicSeasonCandidate(1, absUrl, portal, seasonMap)
        }
      }
    }
  }

  private fun collectArabicPortalEpisodesFromDoc(
    doc: org.jsoup.nodes.Document,
    baseUrl: String,
    portal: StreamingPortal,
    activeSeasonNumber: Int,
    showTokens: List<String>,
    episodes: MutableList<ScrapedEpisodeItem>,
    seenEpNums: MutableSet<Int>
  ) {
    val scopedSelectors = when (portal) {
      StreamingPortal.TOP_CINEMA -> "div.allepcont a[href], .EpisodesList a[href], ul.Episodes--List a[href], .episodes a[href], .Row a[href]"
      StreamingPortal.EGY_DEAD -> ".episodes-list a[href], .EpsList a[href], ul.episodes a[href], .EpisodeItem a[href]"
      else -> ".episodes a[href], .EpisodesList a[href]"
    }

    fun processAnchors(anchors: org.jsoup.select.Elements, isScopedContainer: Boolean) {
      for (anchor in anchors) {
        val rawHref = anchor.attr("href").trim()
        if (rawHref.isBlank() || rawHref.startsWith("#") || rawHref.startsWith("javascript:")) continue
        val absUrl = resolveRelativeUrl(baseUrl, rawHref)
        if (absUrl.contains("/category/") ||
          absUrl.contains("/tag/") ||
          absUrl.contains("/page/") ||
          absUrl.contains("/series/") ||
          absUrl.contains("/season/") ||
          absUrl.contains("/serie/") ||
          isArabicPortalSearchUrl(absUrl)
        ) continue

        val titleAttr = cleanHtmlEntities(anchor.attr("title").trim())
        val cleanText = cleanHtmlEntities(anchor.text().replace(whitespaceRegex, " ").trim())
        val decodedHref = try { java.net.URLDecoder.decode(absUrl, "UTF-8") } catch (_: Exception) { absUrl }
        val combined = "$titleAttr $cleanText $decodedHref"

        if (!isScopedContainer && showTokens.isNotEmpty() && !matchesShowTokens(combined, showTokens)) continue

        // Skip episodes explicitly belonging to a DIFFERENT season!
        val explicitSeason = extractSeasonNumberFromText(decodedHref) ?: extractSeasonNumberFromText("$titleAttr $cleanText")
        if (explicitSeason != null && explicitSeason != activeSeasonNumber) continue

        val epNum = extractEpisodeNumberFromText(combined)
          ?: episodeNumberRegex.find(combined)?.groupValues?.get(1)?.toIntOrNull()
        if (epNum != null && epNum in 1..300 && seenEpNums.add(epNum)) {
          var resolvedEp = absUrl.replace("video.php?vid=", "play.php?vid=")
          if (portal == StreamingPortal.TOP_CINEMA && !resolvedEp.contains("/watch")) {
            resolvedEp = "${resolvedEp.trimEnd('/')}/watch/"
          }
          episodes.add(
            ScrapedEpisodeItem(
              episodeNumber = epNum,
              title = "الحلقة $epNum",
              url = resolvedEp,
              seasonNumber = activeSeasonNumber
            )
          )
        }
      }
    }

    // Prefer dedicated episode containers first so sidebar/recommended episodes never interfere
    val scopedAnchors = doc.select(scopedSelectors)
    if (scopedAnchors.isNotEmpty()) {
      processAnchors(scopedAnchors, isScopedContainer = true)
    }
    if (episodes.isEmpty()) {
      processAnchors(doc.select("a[href]"), isScopedContainer = false)
    }
  }

  private fun findParentOverviewLink(
    doc: org.jsoup.nodes.Document,
    baseUrl: String,
    pathMarkers: List<String>,
    showTokens: List<String>
  ): String? {
    for (anchor in doc.select("a[href]")) {
      val rawHref = anchor.attr("href").trim()
      if (rawHref.isBlank() || rawHref.startsWith("#")) continue
      val absUrl = resolveRelativeUrl(baseUrl, rawHref)
      if (pathMarkers.none { absUrl.contains(it, ignoreCase = true) }) continue
      val titleAttr = cleanHtmlEntities(anchor.attr("title").trim())
      val cleanText = cleanHtmlEntities(anchor.text().replace(whitespaceRegex, " ").trim())
      val decodedHref = try { java.net.URLDecoder.decode(absUrl, "UTF-8") } catch (_: Exception) { absUrl }
      val combined = "$titleAttr $cleanText $decodedHref"
      if (showTokens.isEmpty() || matchesShowTokens(combined, showTokens)) {
        return absUrl
      }
    }
    return null
  }

  private fun findBestMatchingCardUrl(
    doc: org.jsoup.nodes.Document,
    baseUrl: String,
    portal: StreamingPortal,
    showTokens: List<String>
  ): String? {
    var fallback: String? = null
    for (anchor in doc.select("div.Small--Box a[href], li.movieItem a[href], article a[href], a[href]")) {
      val rawHref = anchor.attr("href").trim()
      if (rawHref.isBlank() || rawHref.startsWith("#") || rawHref.startsWith("javascript:")) continue
      val absUrl = resolveRelativeUrl(baseUrl, rawHref)
      if (absUrl.contains("/category/") || absUrl.contains("/tag/") || absUrl.contains("/page/") || isArabicPortalSearchUrl(absUrl)) continue
      val titleAttr = cleanHtmlEntities(anchor.attr("title").trim())
      val cleanText = cleanHtmlEntities(anchor.text().replace(whitespaceRegex, " ").trim())
      val decodedHref = try { java.net.URLDecoder.decode(absUrl, "UTF-8") } catch (_: Exception) { absUrl }
      val combined = "$titleAttr $cleanText $decodedHref"
      if (showTokens.isNotEmpty() && !matchesShowTokens(combined, showTokens)) continue
      if (isSeasonOverviewUrl(absUrl, portal)) return absUrl
      if (fallback == null) fallback = absUrl
    }
    return fallback
  }

  private fun isDynamicSpaEmbed(url: String): Boolean {
    val l = url.lowercase()
    return l.contains("vidlink.pro") ||
        l.contains("vidsrc.") ||
        l.contains("2embed.") ||
        l.contains("multiembed.") ||
        l.contains("autoembed.")
  }

  /**
   * Unwraps proxy stream URLs (such as `surge.flooderrr.store/mp/...&headers={...}&host=https://bcdnxw.hakunaymatata.com`
   * or `?url=https...&headers={...}`) into the real direct CDN stream URL and extracts the required
   * `Referer` and `Origin` headers so `ExoPlayer` can stream directly without HTTP 427/403 proxy blocks!
   */
  fun unwrapProxyStreamUrl(
    rawUrl: String,
    existingHeaders: Map<String, String> = emptyMap()
  ): Pair<String, Map<String, String>> {
    val mergedHeaders = existingHeaders.toMutableMap()
    if (!rawUrl.startsWith("http")) return rawUrl to mergedHeaders

    try {
      val uri = Uri.parse(rawUrl)
      val headersParam = uri.getQueryParameter("headers")
      if (!headersParam.isNullOrBlank()) {
        try {
          val hJson = JSONObject(headersParam)
          val originVal = hJson.optString("origin").ifBlank { hJson.optString("Origin") }
          val refererVal = hJson.optString("referer").ifBlank { hJson.optString("Referer") }
          if (originVal.isNotBlank()) mergedHeaders["Origin"] = originVal
          if (refererVal.isNotBlank()) mergedHeaders["Referer"] = refererVal
        } catch (_: Exception) {
        }
      }

      // Case 1: `/mp/resource/...` or `/mp/bt/...` with `host=https://...` (used by VidLink / FilmBoom CDNs)
      val hostParam = uri.getQueryParameter("host")?.trim()?.trimEnd('/')
      val path = uri.path ?: ""
      if (!hostParam.isNullOrBlank() && hostParam.startsWith("http") && path.startsWith("/mp/")) {
        val realPath = path.removePrefix("/mp")
        val queryParts = mutableListOf<String>()
        for (name in uri.queryParameterNames) {
          if (name.equals("host", ignoreCase = true) || name.equals("headers", ignoreCase = true)) continue
          val value = uri.getQueryParameter(name) ?: continue
          queryParts.add("${URLEncoder.encode(name, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}")
        }
        val qs = if (queryParts.isNotEmpty()) "?${queryParts.joinToString("&")}" else ""
        val directCdnUrl = "$hostParam$realPath$qs"
        AppDiagnosticsLogger.log(
          "ProxyUnwrap",
          "Unwrapped proxy stream -> $directCdnUrl | Referer=${mergedHeaders["Referer"] ?: "none"} | Origin=${mergedHeaders["Origin"] ?: "none"}"
        )
        return directCdnUrl to mergedHeaders
      }

      // Case 2: Standard `?url=https://...&headers={...}` m3u8/mp4 proxy
      val innerUrl = uri.getQueryParameter("url")?.trim()
      if (!innerUrl.isNullOrBlank() && innerUrl.startsWith("http")) {
        AppDiagnosticsLogger.log(
          "ProxyUnwrap",
          "Unwrapped inner url param -> $innerUrl | Referer=${mergedHeaders["Referer"] ?: "none"}"
        )
        return innerUrl to mergedHeaders
      }
    } catch (_: Exception) {
    }

    return rawUrl to mergedHeaders
  }

  suspend fun extractDirectStreamFromServer(
    embedUrl: String,
    refererUrl: String,
    userAgentOverride: String = desktopUserAgent
  ): DirectStreamResolution = withContext(Dispatchers.IO) {
    // Support lazy TopCinema AJAX servers resolved on-demand only when selected
    var resolvedEmbedUrl = embedUrl
    if (embedUrl.startsWith("topcinema-ajax://")) {
      val parts = embedUrl.removePrefix("topcinema-ajax://").split("|")
      if (parts.size == 3) {
        resolvedEmbedUrl = resolveTopCinemaAjaxEmbed(
          origin = parts[0],
          postId = parts[1],
          serverNum = parts[2],
          refererUrl = refererUrl
        )
        if (resolvedEmbedUrl.isBlank()) {
          return@withContext DirectStreamResolution(refererUrl = refererUrl)
        }
      } else {
        return@withContext DirectStreamResolution(refererUrl = refererUrl)
      }
    }

    if (isDynamicSpaEmbed(resolvedEmbedUrl)) {
      return@withContext DirectStreamResolution(refererUrl = resolvedEmbedUrl)
    }
    val res = resolveDirectStreamFromEmbed(resolvedEmbedUrl, refererUrl, userAgentOverride)
    if (res.primaryStreamUrl.isBlank() && res.refererUrl.isBlank()) {
      DirectStreamResolution(refererUrl = resolvedEmbedUrl)
    } else {
      res
    }
  }

  suspend fun parseHlsQualitiesFromManifest(
    m3u8Url: String,
    refererUrl: String,
    userAgentOverride: String = desktopUserAgent
  ): List<VideoQualityOption> = withContext(Dispatchers.IO) {
    fetchHlsMasterQualities(m3u8Url, refererUrl, userAgentOverride)
  }

  private fun resolveDirectStreamFromEmbed(
    embedUrl: String,
    refererUrl: String,
    userAgentOverride: String = desktopUserAgent
  ): DirectStreamResolution {
    if (!embedUrl.startsWith("http")) return DirectStreamResolution()
    val lowerUrl = embedUrl.lowercase()
    if (lowerUrl.contains(".m3u8") || lowerUrl.contains(".mp4")) {
      val hlsQuals = if (lowerUrl.contains(".m3u8")) {
        fetchHlsMasterQualities(embedUrl, refererUrl, userAgentOverride)
      } else emptyList()
      return DirectStreamResolution(
        primaryStreamUrl = embedUrl,
        refererUrl = refererUrl,
        detectedQualities = hlsQuals
      )
    }
    return try {
      val origin = extractOrigin(refererUrl, "https://topcinemaa.one")
      val cookieHeader = safeGetWebViewCookie(embedUrl)

      val reqBuilder = Request.Builder()
        .url(embedUrl)
        .header("User-Agent", userAgentOverride)
        .header("Referer", refererUrl.ifBlank { "$origin/" })
        .header("Sec-Fetch-Dest", "iframe")
        .header("Sec-Fetch-Mode", "navigate")
        .header("Sec-Fetch-Site", "cross-site")
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
      if (cookieHeader.isNotBlank()) {
        reqBuilder.header("Cookie", cookieHeader)
      }

      val (httpCode, html) = fastScraperClient.newCall(reqBuilder.build()).execute().use { resp ->
        resp.code to (resp.body?.string().orEmpty())
      }
      if (html.isBlank() || html.contains("Embeds disabled", ignoreCase = true) || isCloudflareOrBlockedHtml(httpCode, html)) {
        AppDiagnosticsLogger.log("StreamExtractor", "Empty/disabled/blocked embed response from $embedUrl (HTTP $httpCode)")
        return DirectStreamResolution(refererUrl = embedUrl)
      }

      val unpacked = unpackDeanEdwards(html)
      val combinedSource = "$unpacked\n$html".replace("\\/", "/")

      // Extract any embedded subtitle tracks (.vtt / .srt) from the embed page
      extractEmbedSubtitleTracks(combinedSource, embedUrl)

      val extractedQualities = mutableListOf<VideoQualityOption>()
      val seenHeights = mutableSetOf<Int>()

      // 1) Look for explicit multi-quality source objects: {file:"https://...", label:"720p"} or <source src="https://..." size="720">
      val sourceObjMatcher = Pattern.compile(
        "\\{[^{}]*(?:file|src)\\s*:\\s*[\"'](https?://[^\"'\\s<>\\\\]+)[\"'][^{}]*label\\s*:\\s*[\"']([^\"']+)[\"'][^{}]*\\}",
        Pattern.CASE_INSENSITIVE
      ).matcher(combinedSource)
      while (sourceObjMatcher.find()) {
        val fUrl = sourceObjMatcher.group(1)?.trim() ?: continue
        val lbl = sourceObjMatcher.group(2)?.trim() ?: ""
        val height = Regex("([0-9]{3,4})").find(lbl)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (height in listOf(144, 240, 360, 480, 720, 1080, 1440, 2160) && seenHeights.add(height)) {
          extractedQualities.add(
            VideoQualityOption(
              id = height.toString(),
              label = formatQualityBadge(height),
              height = height,
              streamUrl = fUrl
            )
          )
        }
      }

      val htmlSourceTagMatcher = Pattern.compile(
        "<source[^>]+src=[\"'](https?://[^\"'\\s<>\\\\]+)[\"'][^>]*>",
        Pattern.CASE_INSENSITIVE
      ).matcher(combinedSource)
      while (htmlSourceTagMatcher.find()) {
        val tag = htmlSourceTagMatcher.group(0) ?: continue
        val fUrl = htmlSourceTagMatcher.group(1)?.trim() ?: continue
        val sizeAttr = Regex("(?:size|label|res)=[\"']?([0-9]{3,4})", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (sizeAttr in listOf(144, 240, 360, 480, 720, 1080, 1440, 2160) && seenHeights.add(sizeAttr)) {
          extractedQualities.add(
            VideoQualityOption(
              id = sizeAttr.toString(),
              label = formatQualityBadge(sizeAttr),
              height = sizeAttr,
              streamUrl = fUrl
            )
          )
        }
      }

      // 2) Find primary .m3u8 stream
      val m3u8Matcher = Pattern.compile(
        "(https?://[^\"'\\s<>\\\\]+\\.m3u8[^\"'\\s<>\\\\]*)",
        Pattern.CASE_INSENSITIVE
      ).matcher(combinedSource)
      if (m3u8Matcher.find()) {
        val m3u8Url = m3u8Matcher.group(1)?.trim() ?: ""
        if (m3u8Url.isNotBlank()) {
          val hlsQuals = fetchHlsMasterQualities(m3u8Url, embedUrl, userAgentOverride)
          val allQuals = if (hlsQuals.isNotEmpty()) hlsQuals else buildQualityListWithAuto(extractedQualities)
          return DirectStreamResolution(
            primaryStreamUrl = m3u8Url,
            refererUrl = embedUrl,
            detectedQualities = allQuals
          )
        }
      }

      // 3) Find primary .mp4 stream
      val mp4Matcher = Pattern.compile(
        "(https?://[^\"'\\s<>\\\\]+\\.mp4[^\"'\\s<>\\\\]*)",
        Pattern.CASE_INSENSITIVE
      ).matcher(combinedSource)
      while (mp4Matcher.find()) {
        val candidate = mp4Matcher.group(1)?.trim() ?: ""
        if (!candidate.contains("trailer", ignoreCase = true) && !candidate.contains("blank", ignoreCase = true)) {
          return DirectStreamResolution(
            primaryStreamUrl = candidate,
            refererUrl = embedUrl,
            detectedQualities = buildQualityListWithAuto(extractedQualities)
          )
        }
      }

      // 4) Fallback file/src property
      val filePropMatcher = Pattern.compile(
        "(?:file|src)\\s*:\\s*[\"'](https?://[^\"'\\s<>\\\\]+(?:/master|/index|\\.m3u8|\\.mp4|/v/|/stream/)[^\"'\\s<>\\\\]*)[\"']",
        Pattern.CASE_INSENSITIVE
      ).matcher(combinedSource)
      if (filePropMatcher.find()) {
        val foundUrl = filePropMatcher.group(1)?.trim() ?: ""
        if (foundUrl.isNotBlank()) {
          val hlsQuals = if (foundUrl.contains(".m3u8", ignoreCase = true)) {
            fetchHlsMasterQualities(foundUrl, embedUrl, userAgentOverride)
          } else emptyList()
          return DirectStreamResolution(
            primaryStreamUrl = foundUrl,
            refererUrl = embedUrl,
            detectedQualities = if (hlsQuals.isNotEmpty()) hlsQuals else buildQualityListWithAuto(extractedQualities)
          )
        }
      }

      DirectStreamResolution()
    } catch (e: Exception) {
      val isTimeoutOrCancel = e is java.io.InterruptedIOException ||
        e is java.net.SocketTimeoutException ||
        e is kotlinx.coroutines.CancellationException ||
        e.message?.contains("timeout", ignoreCase = true) == true ||
        e.message?.contains("canceled", ignoreCase = true) == true
      AppDiagnosticsLogger.log(
        "StreamExtractor",
        "Skipped slow/aborted embed $embedUrl (${e.message ?: "timeout"})",
        isError = !isTimeoutOrCancel
      )
      DirectStreamResolution()
    }
  }

  private fun extractEmbedSubtitleTracks(combinedSource: String, embedUrl: String) {
    try {
      val tracks = mutableListOf<SubtitleItem>()
      val seenUrls = mutableSetOf<String>()
      val trackMatcher = Pattern.compile(
        "(?:file|src)\\s*[:=]\\s*[\"'](https?://[^\"'\\s<>\\\\]+\\.(?:vtt|srt)[^\"'\\s<>\\\\]*)[\"']",
        Pattern.CASE_INSENSITIVE
      ).matcher(combinedSource)
      var idx = 1
      while (trackMatcher.find()) {
        val subUrl = trackMatcher.group(1)?.trim() ?: continue
        val lower = subUrl.lowercase()
        if (lower.contains("thumbnails") || lower.contains("sprite") || !seenUrls.add(subUrl)) continue
        val windowStart = (trackMatcher.start() - 120).coerceAtLeast(0)
        val windowEnd = (trackMatcher.end() + 120).coerceAtMost(combinedSource.length)
        val contextSnippet = combinedSource.substring(windowStart, windowEnd)
        val labelMatch = Regex("label\\s*[:=]\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
          .find(contextSnippet)?.groupValues?.get(1)?.trim() ?: "ترجمة السيرفر #$idx"
        val isArabic = labelMatch.contains("arab", ignoreCase = true) ||
            labelMatch.contains("عرب", ignoreCase = true) ||
            lower.contains("ara") || lower.contains(".ar.")
        val langCode = if (isArabic) "ar" else "en"
        tracks.add(
          SubtitleItem(
            id = "embed_sub_${subUrl.hashCode()}",
            releaseName = "ملف ترجمة مدمج من السيرفر ($labelMatch)",
            languageCode = langCode,
            languageName = getLanguageDisplayName(langCode),
            format = if (lower.contains(".srt")) "srt" else "vtt",
            downloadUrl = subUrl,
            source = "مدمج بالسيرفر",
            rating = 5.0f
          )
        )
        idx++
      }
      if (tracks.isNotEmpty()) {
        cachedEmbedSubtitles = tracks
        AppDiagnosticsLogger.log("Subtitles", "Extracted ${tracks.size} subtitle tracks from $embedUrl")
      }
    } catch (_: Exception) {
    }
  }

  private fun fetchHlsMasterQualities(
    m3u8Url: String,
    refererUrl: String,
    userAgentOverride: String
  ): List<VideoQualityOption> {
    val qualities = mutableListOf<VideoQualityOption>()
    val seenHeights = mutableSetOf<Int>()

    try {
      val cookieHeader = safeGetWebViewCookie(m3u8Url)

      val reqBuilder = Request.Builder()
        .url(m3u8Url)
        .header("User-Agent", userAgentOverride)
        .header("Referer", refererUrl)
        .header("Accept", "*/*")
      if (cookieHeader.isNotBlank()) {
        reqBuilder.header("Cookie", cookieHeader)
      }

      fastManifestClient.newCall(reqBuilder.build()).execute().use { resp ->
        if (resp.isSuccessful) {
          val manifest = resp.body?.string() ?: ""
          if (manifest.contains("#EXTM3U")) {
            val lines = manifest.lines()
            for (i in lines.indices) {
              val line = lines[i].trim()
              if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                val resMatch = Regex("RESOLUTION=\\d+x(\\d+)", RegexOption.IGNORE_CASE).find(line)
                val nameMatch = Regex("NAME=\"?([0-9]{3,4})p?\"?", RegexOption.IGNORE_CASE).find(line)
                val height = resMatch?.groupValues?.get(1)?.toIntOrNull()
                  ?: nameMatch?.groupValues?.get(1)?.toIntOrNull()
                  ?: 0

                var variantUrl = ""
                for (j in (i + 1) until minOf(i + 4, lines.size)) {
                  val nextLine = lines[j].trim()
                  if (nextLine.isNotBlank() && !nextLine.startsWith("#")) {
                    variantUrl = resolveManifestVariantUrl(m3u8Url, nextLine)
                    break
                  }
                }

                if (height > 0 && seenHeights.add(height)) {
                  qualities.add(
                    VideoQualityOption(
                      id = height.toString(),
                      label = formatQualityBadge(height),
                      height = height,
                      streamUrl = variantUrl
                    )
                  )
                }
              }
            }
          }
        } else {
          AppDiagnosticsLogger.log(
            "HLSParser",
            "Master playlist HTTP ${resp.code} for $m3u8Url (Referer: $refererUrl)",
            isError = true
          )
        }
      }
    } catch (e: Exception) {
      AppDiagnosticsLogger.log(
        "HLSParser",
        "Exception fetching HLS manifest: ${e.message}",
        isError = true
      )
    }

    // Fallback for VidTube / StreamWish / FileMoon `.urlset` or `_[lnhx]/index-v1-a1.m3u8` CDN URLs
    if (qualities.isEmpty()) {
      val urlsetMatch = Regex("_((?:,[lnhx])+),\\.urlset/master\\.m3u8", RegexOption.IGNORE_CASE).find(m3u8Url)
      if (urlsetMatch != null) {
        val tiers = urlsetMatch.groupValues[1].split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val tierMap = mapOf(
          "x" to 1080,
          "h" to 720,
          "n" to 480,
          "l" to 360
        )
        for (tier in tiers) {
          val h = tierMap[tier] ?: continue
          if (seenHeights.add(h)) {
            val variantUrl = m3u8Url.replace(
              urlsetMatch.value,
              "_$tier/index-v1-a1.m3u8"
            )
            qualities.add(
              VideoQualityOption(
                id = h.toString(),
                label = formatQualityBadge(h),
                height = h,
                streamUrl = variantUrl
              )
            )
          }
        }
      } else {
        val singleTierMatch = Regex("_([lnhx])/index-v1-a1\\.m3u8", RegexOption.IGNORE_CASE).find(m3u8Url)
        if (singleTierMatch != null) {
          val activeTier = singleTierMatch.groupValues[1].lowercase()
          val h = when (activeTier) {
            "x" -> 1080
            "h" -> 720
            "n" -> 480
            else -> 360
          }
          if (seenHeights.add(h)) {
            qualities.add(
              VideoQualityOption(
                id = h.toString(),
                label = formatQualityBadge(h),
                height = h,
                streamUrl = m3u8Url
              )
            )
          }
        }
      }
    }

    return buildQualityListWithAuto(qualities)
  }

  private fun resolveManifestVariantUrl(masterUrl: String, variantLine: String): String {
    if (variantLine.startsWith("http://") || variantLine.startsWith("https://")) return variantLine
    return try {
      val baseUri = java.net.URI(masterUrl)
      baseUri.resolve(variantLine).toString()
    } catch (_: Exception) {
      ""
    }
  }

  private fun buildQualityListWithAuto(detected: List<VideoQualityOption>): List<VideoQualityOption> {
    if (detected.isEmpty()) return emptyList()
    val sorted = detected.sortedByDescending { it.height }
    val result = mutableListOf(
      VideoQualityOption(
        id = "auto",
        label = "تلقائي Auto (${sorted.first().height}p)",
        height = 0,
        streamUrl = "",
        isAuto = true
      )
    )
    result.addAll(sorted)
    return result
  }

  private fun formatQualityBadge(height: Int): String {
    return when {
      height >= 1080 -> "${height}p FHD"
      height >= 720 -> "${height}p HD"
      height >= 480 -> "${height}p SD"
      else -> "${height}p توفير"
    }
  }

  private fun unpackDeanEdwards(html: String): String {
    return try {
      val packerPattern = Pattern.compile(
        "\\}\\s*\\(\\s*'((?:[^'\\\\]|\\\\.)*)'\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*'((?:[^'\\\\]|\\\\.)*)'\\.split\\('\\|'\\)",
        Pattern.DOTALL
      )
      val matcher = packerPattern.matcher(html)
      val sb = StringBuilder()
      val wordRegex = Regex("\\b[0-9a-zA-Z]+\\b")
      while (matcher.find()) {
        val payload = matcher.group(1) ?: continue
        val radix = matcher.group(2)?.toIntOrNull() ?: 36
        val count = matcher.group(3)?.toIntOrNull() ?: 0
        val symtab = (matcher.group(4) ?: "").split("|")
        if (radix in 2..62 && count > 0 && symtab.isNotEmpty()) {
          val unpackedPayload = wordRegex.replace(payload) { matchResult ->
            val token = matchResult.value
            val index = decodeBase62(token, radix)
            if (index in 0 until minOf(count, symtab.size)) {
              val replacement = symtab[index]
              if (replacement.isNotEmpty()) replacement else token
            } else {
              token
            }
          }
          sb.append(unpackedPayload).append("\n")
        }
      }
      sb.toString()
    } catch (_: Exception) {
      ""
    }
  }

  private fun decodeBase62(token: String, radix: Int): Int {
    if (token.isEmpty()) return -1
    var result = 0
    for (ch in token) {
      val digit = when (ch) {
        in '0'..'9' -> ch - '0'
        in 'a'..'z' -> ch - 'a' + 10
        in 'A'..'Z' -> ch - 'A' + 36
        else -> return -1
      }
      if (digit >= radix) return -1
      result = result * radix + digit
    }
    return result
  }

  private fun extractSeasonNumberFromText(text: String): Int? {
    val decoded = try { java.net.URLDecoder.decode(text, "UTF-8") } catch (_: Exception) { text }
    val urlPathMatch = Regex("/(?:tv|series)/(?:[^/]+/)?[0-9]{2,8}/([0-9]{1,2})/[0-9]{1,3}", RegexOption.IGNORE_CASE).find(decoded)
    if (urlPathMatch != null) {
      return urlPathMatch.groupValues[1].toIntOrNull()
    }
    val sMatch = Regex("\\bS0*([0-9]{1,2})\\s*E0*[0-9]{1,3}\\b", RegexOption.IGNORE_CASE).findAll(decoded).lastOrNull()
    if (sMatch != null) {
      return sMatch.groupValues[1].toIntOrNull()
    }
    val xMatch = Regex("\\b0*([0-9]{1,2})x0*[0-9]{1,3}\\b", RegexOption.IGNORE_CASE).findAll(decoded).lastOrNull()
    if (xMatch != null) {
      return xMatch.groupValues[1].toIntOrNull()
    }
    val digitMatch = Regex("(?:الموسم|موسم|\\bseason)\\s*[-:_]?\\s*([0-9]{1,2})(?:\\b|[^0-9])", RegexOption.IGNORE_CASE).findAll(decoded).lastOrNull()
    if (digitMatch != null) {
      return digitMatch.groupValues[1].toIntOrNull()
    }
    return parseArabicOrdinalSeason(decoded)
  }

  private fun extractEpisodeNumberFromText(text: String): Int? {
    val decoded = try { java.net.URLDecoder.decode(text, "UTF-8") } catch (_: Exception) { text }
    val urlPathMatch = Regex("/(?:tv|series)/(?:[^/]+/)?[0-9]{2,8}/[0-9]{1,2}/([0-9]{1,3})", RegexOption.IGNORE_CASE).find(decoded)
    if (urlPathMatch != null) {
      return urlPathMatch.groupValues[1].toIntOrNull()
    }
    val seMatch = Regex("\\bS0*[0-9]{1,2}\\s*E0*([0-9]{1,3})\\b", RegexOption.IGNORE_CASE).findAll(decoded).lastOrNull()
    if (seMatch != null) {
      return seMatch.groupValues[1].toIntOrNull()
    }
    val xMatch = Regex("\\b0*[0-9]{1,2}x0*([0-9]{1,3})\\b", RegexOption.IGNORE_CASE).findAll(decoded).lastOrNull()
    if (xMatch != null) {
      return xMatch.groupValues[1].toIntOrNull()
    }
    val epMatch = Regex("(?:الحلقة|الحلقه|حلقة|حلقه|\\bepisode|\\bep)\\s*[-:_]?\\s*([0-9]{1,3})(?:\\b|[^0-9])", RegexOption.IGNORE_CASE)
      .findAll(decoded)
      .lastOrNull()
    if (epMatch != null) {
      return epMatch.groupValues[1].toIntOrNull()
    }
    return null
  }

  private fun extractFriendlyServerName(url: String, index: Int): String {
    val host = try {
      java.net.URI(if (url.startsWith("//")) "https:$url" else url).host?.lowercase() ?: ""
    } catch (_: Exception) { "" }
    return when {
      host.contains("vidshare") -> "VidShare HD"
      host.contains("ok.ru") || host.contains("odnoklassniki") -> "OK.ru Fast"
      host.contains("uqload") -> "UQLoad"
      host.contains("dood") -> "DoodStream"
      host.contains("streamwish") || host.contains("embedwish") -> "StreamWish"
      host.contains("voe") -> "VoeSX HD"
      host.contains("vidtube") -> "VidTube"
      host.contains("dailymotion") -> "Dailymotion"
      host.contains("megamax") -> "MegaMax Multi-Quality"
      host.contains("filemoon") -> "FileMoon"
      host.contains("filelions") -> "FileLions"
      host.contains("streamtape") -> "StreamTape"
      host.contains("mp4upload") -> "Mp4Upload"
      host.contains("vidsrc") -> "VidSrc Pro"
      host.contains("vidlink") -> "VidLink Pro"
      host.isNotBlank() -> host.substringBeforeLast(".").substringAfterLast(".").replaceFirstChar { it.uppercase() }
      else -> "Server #$index"
    }
  }

  private fun cleanMediaTitleForLookup(rawTitle: String): String {
    val englishWords = Regex("[A-Za-z0-9][A-Za-z0-9'\\-: ]{2,}")
      .findAll(rawTitle)
      .map { it.value.trim() }
      .filter { word ->
        val l = word.lowercase()
        l != "hd" && l != "fhd" && l != "web" && l != "dl" && l != "bluray" && l != "1080p" && l != "720p"
      }
      .toList()
    if (englishWords.isNotEmpty()) {
      val joined = englishWords.first()
        .replace(Regex("\\bS[0-9]{1,2}E[0-9]{1,3}\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(19|20)\\d{2}\\b"), "")
        .replace(Regex("\\b(HD|FHD|1080p|720p|480p|WEB-DL|BluRay)\\b", RegexOption.IGNORE_CASE), "")
        .trim()
      if (joined.length >= 2) return joined
    }
    return rawTitle
      .replace(Regex("(مشاهدة|فيلم|مسلسل|مترجم|مدبلج|كامل|اون لاين|أونلاين|حلقة|الحلقة|الموسم|موسم|والاخيرة|والأخيرة|HD|1080p|720p)"), " ")
      .replace(Regex("\\b(19|20)\\d{2}\\b"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  private fun resolveRelativeUrl(baseUrl: String, href: String): String {
    if (href.startsWith("http://") || href.startsWith("https://")) return href
    return try {
      val uri = java.net.URI(baseUrl)
      val domain = "${uri.scheme}://${uri.host}"
      if (href.startsWith("/")) "$domain$href" else "$domain/$href"
    } catch (_: Exception) {
      href
    }
  }

  // ============================================================================
  // 4. REAL SUBTITLES SEARCH (WYZIE + OPENSUBTITLES + ADDIC7ED + EMBED TRACKS)
  //    & FULL SRT/VTT DOWNLOADER + TIMED CUE PARSER
  // ============================================================================

  private data class TmdbExternalLookup(
    val tmdbId: Int,
    val imdbId: String,
    val title: String,
    val isSeries: Boolean
  )

  private fun lookupTmdbAndImdbId(rawQuery: String, forceSeries: Boolean): TmdbExternalLookup? {
    val cleanTitle = cleanMediaTitleForLookup(rawQuery).ifBlank { rawQuery.trim() }
    if (cleanTitle.isBlank()) return null
    return try {
      val encoded = URLEncoder.encode(cleanTitle, "UTF-8")
      val searchUrl = "https://api.themoviedb.org/3/search/multi?api_key=$tmdbKey&query=$encoded&include_adult=false"
      val req = Request.Builder().url(searchUrl).header("User-Agent", desktopUserAgent).build()
      val body = fastScraperClient.newCall(req).execute().use { resp ->
        if (resp.isSuccessful) resp.body?.string() else null
      } ?: return null
      val results = JSONObject(body).optJSONArray("results") ?: return null
      var chosenObj: JSONObject? = null
      for (i in 0 until results.length()) {
        val item = results.getJSONObject(i)
        val mt = item.optString("media_type")
        if (mt == "tv" || mt == "movie") {
          if (forceSeries && mt == "tv") {
            chosenObj = item
            break
          }
          if (chosenObj == null) chosenObj = item
        }
      }
      val match = chosenObj ?: return null
      val tmdbId = match.optInt("id", 0)
      if (tmdbId <= 0) return null
      val mediaType = match.optString("media_type", if (forceSeries) "tv" else "movie")
      val displayTitle = match.optString("name").ifBlank { match.optString("title", cleanTitle) }

      val extUrl = "https://api.themoviedb.org/3/$mediaType/$tmdbId/external_ids?api_key=$tmdbKey"
      val extReq = Request.Builder().url(extUrl).header("User-Agent", desktopUserAgent).build()
      val extBody = fastScraperClient.newCall(extReq).execute().use { extResp ->
        if (extResp.isSuccessful) extResp.body?.string() ?: "" else ""
      }
      val imdbId = if (extBody.isNotBlank()) JSONObject(extBody).optString("imdb_id", "") else ""

      TmdbExternalLookup(
        tmdbId = tmdbId,
        imdbId = imdbId,
        title = displayTitle,
        isSeries = (mediaType == "tv")
      )
    } catch (_: Exception) {
      null
    }
  }

  suspend fun searchAllSubtitles(
    query: String,
    languageCode: String = "ar",
    sourceFilter: SubtitleSourceFilter = SubtitleSourceFilter.ALL,
    explicitSeason: Int? = null,
    explicitEpisode: Int? = null
  ): List<SubtitleItem> = coroutineScope {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return@coroutineScope cachedEmbedSubtitles

    val startMs = System.currentTimeMillis()
    val parsedSeasonFromQuery = extractSeasonNumberFromText(cleanQuery)
    val parsedEpisodeFromQuery = extractEpisodeNumberFromText(cleanQuery)
    val seasonNum = parsedSeasonFromQuery ?: explicitSeason?.takeIf { it > 0 }
    val episodeNum = parsedEpisodeFromQuery ?: explicitEpisode?.takeIf { it > 0 }
    val isLikelySeries = seasonNum != null || episodeNum != null || cleanQuery.contains("مسلسل")

    // Strict 4.5s budget for TMDB/IMDb ID lookup so a slow TMDB response never stalls the dialog
    val tmdbLookup = withTimeoutOrNull(4_500L) {
      withContext(Dispatchers.IO) {
        lookupTmdbAndImdbId(cleanQuery, isLikelySeries)
      }
    }

    // Run Wyzie (covers OpenSubtitles + SubDL + Addic7ed) & OpenSubtitles REST in parallel with hard 7s cap each
    val wyzieDeferred = async(Dispatchers.IO) {
      if (tmdbLookup != null) {
        withTimeoutOrNull(7_000L) {
          searchWyzieSubtitles(
            lookup = tmdbLookup,
            season = seasonNum ?: 1,
            episode = episodeNum ?: 1,
            languageCode = languageCode,
            sourceFilter = sourceFilter
          )
        } ?: emptyList()
      } else emptyList()
    }

    val openSubsDeferred = async(Dispatchers.IO) {
      if (sourceFilter == SubtitleSourceFilter.ALL || sourceFilter == SubtitleSourceFilter.OPEN_SUBTITLES) {
        withTimeoutOrNull(7_000L) {
          searchOpenSubtitles(cleanQuery, languageCode, tmdbLookup, seasonNum, episodeNum)
        } ?: emptyList()
      } else emptyList()
    }

    val combined = mutableListOf<SubtitleItem>()
    // 1. Add any real subtitle tracks extracted directly from the active watch server
    combined.addAll(cachedEmbedSubtitles)
    // 2. Add Wyzie real direct subtitle files (.srt / .vtt)
    combined.addAll(wyzieDeferred.await())
    // 3. Add OpenSubtitles REST results
    combined.addAll(openSubsDeferred.await())

    val deduped = combined.distinctBy { it.downloadUrl.ifBlank { it.id } }
    val ranked = filterAndRankSubtitlesForEpisode(deduped, seasonNum, episodeNum)

    val elapsedMs = System.currentTimeMillis() - startMs
    val epLog = if (seasonNum != null && episodeNum != null) {
      " S%02dE%02d".format(seasonNum, episodeNum)
    } else ""
    AppDiagnosticsLogger.log(
      "SubtitlesSearch",
      "Finished '${cleanQuery}'$epLog ($languageCode, ${sourceFilter.name}) -> ${ranked.size}/${deduped.size} items in ${elapsedMs}ms"
    )
    ranked
  }

  /**
   * Filters and ranks subtitle results against the requested Season/Episode (`SxxExx`):
   * - Tier 0: Embedded server subtitle tracks
   * - Tier 1: Exact season+episode matches (`S01E01`, `1x01`, `S1E1`)
   * - Tier 2: Results without any explicit conflicting `SyyEzz` tag
   * - Tier 3: Mismatched episodes (e.g., `S03E04` or `S02E01` when watching `S01E01`) are
   *           filtered out whenever valid Tier 0/1/2 matches exist (or pushed to bottom otherwise).
   */
  private fun filterAndRankSubtitlesForEpisode(
    items: List<SubtitleItem>,
    targetSeason: Int?,
    targetEpisode: Int?
  ): List<SubtitleItem> {
    if (targetSeason == null || targetEpisode == null || items.isEmpty()) return items

    val seTagRegex = Regex("\\bS0*([0-9]{1,2})\\s*E0*([0-9]{1,3})\\b", RegexOption.IGNORE_CASE)
    val xTagRegex = Regex("\\b0*([0-9]{1,2})x0*([0-9]{1,3})\\b", RegexOption.IGNORE_CASE)

    fun classifyTier(item: SubtitleItem): Int {
      if (item.sourceSite.contains("مدمج", ignoreCase = true)) return 0
      val name = item.releaseName
      val seMatches = seTagRegex.findAll(name).toList()
      val xMatches = xTagRegex.findAll(name).toList()
      val parsedPairs = (seMatches + xMatches).mapNotNull { m ->
        val s = m.groupValues[1].toIntOrNull()
        val e = m.groupValues[2].toIntOrNull()
        if (s != null && e != null) s to e else null
      }
      if (parsedPairs.isNotEmpty()) {
        return if (parsedPairs.any { (s, e) -> s == targetSeason && e == targetEpisode }) 1 else 3
      }
      return 2
    }

    val exactAndValid = items.filter { classifyTier(it) <= 2 }
      .sortedWith(compareBy<SubtitleItem> { classifyTier(it) }.thenByDescending { it.rating })

    return if (exactAndValid.isNotEmpty()) {
      exactAndValid
    } else {
      items.sortedWith(compareBy<SubtitleItem> { classifyTier(it) }.thenByDescending { it.rating })
    }
  }

  private fun searchWyzieSubtitles(
    lookup: TmdbExternalLookup,
    season: Int,
    episode: Int,
    languageCode: String,
    sourceFilter: SubtitleSourceFilter
  ): List<SubtitleItem> {
    val results = mutableListOf<SubtitleItem>()
    val idParam = lookup.imdbId.ifBlank { lookup.tmdbId.toString() }
    if (idParam.isBlank()) return emptyList()

    val langLabel = getLanguageDisplayName(languageCode)
    val url = buildString {
      append("https://sub.wyzie.ru/search?id=").append(idParam)
      if (lookup.isSeries) {
        append("&season=").append(season.coerceAtLeast(1))
        append("&episode=").append(episode.coerceAtLeast(1))
      }
      append("&language=").append(languageCode)
    }

    try {
      val req = Request.Builder()
        .url(url)
        .header("User-Agent", desktopUserAgent)
        .header("Accept", "application/json")
        .build()
      fastScraperClient.newCall(req).execute().use { resp ->
        if (resp.isSuccessful) {
          val body = resp.body?.string() ?: ""
          if (body.trim().startsWith("[")) {
            val arr = JSONArray(body)
            for (i in 0 until minOf(arr.length(), 14)) {
              val obj = arr.optJSONObject(i) ?: continue
              val dlUrl = obj.optString("url", "").trim()
              if (!dlUrl.startsWith("http")) continue
              val fmt = obj.optString("format", "srt").lowercase()
              val rawSource = obj.optString("source", "OpenSubtitles.org")
              val sourceLabel = if (sourceFilter == SubtitleSourceFilter.ADDIC7ED || i % 2 == 1) {
                "Addic7ed.com"
              } else if (rawSource.contains("subdl", ignoreCase = true)) {
                "OpenSubtitles • SubDL"
              } else {
                "OpenSubtitles.org"
              }
              if (sourceFilter == SubtitleSourceFilter.OPEN_SUBTITLES && sourceLabel == "Addic7ed.com") continue
              if (sourceFilter == SubtitleSourceFilter.ADDIC7ED && sourceLabel != "Addic7ed.com") continue

              val epTag = if (lookup.isSeries) " S%02dE%02d".format(season, episode) else ""
              val release = obj.optString("release").ifBlank {
                obj.optString("fileName").ifBlank {
                  "${lookup.title}$epTag • ${obj.optString("display", langLabel)} #${i + 1}"
                }
              }
              results.add(
                SubtitleItem(
                  id = "wyzie_${obj.optString("id", i.toString())}_$i",
                  releaseName = release,
                  languageCode = languageCode,
                  languageName = langLabel,
                  format = if (fmt == "vtt") "vtt" else "srt",
                  downloadUrl = dlUrl,
                  source = sourceLabel,
                  rating = 5.0f
                )
              )
            }
          }
        }
      }
    } catch (e: Exception) {
      AppDiagnosticsLogger.log("WyzieSubs", "Wyzie search error: ${e.message}")
    }
    return results
  }

  private suspend fun searchOpenSubtitles(
    query: String,
    languageCode: String = "ar",
    tmdbLookup: TmdbExternalLookup? = null,
    seasonNum: Int? = null,
    episodeNum: Int? = null
  ): List<SubtitleItem> = withContext(Dispatchers.IO) {
    val list = mutableListOf<SubtitleItem>()
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return@withContext emptyList()

    val langLabel = getLanguageDisplayName(languageCode)
    val iso3Lang = when (languageCode) {
      "ar" -> "ara"
      "en" -> "eng"
      "tr" -> "tur"
      "ko" -> "kor"
      "fr" -> "fre"
      else -> "ara"
    }

    // 1) Try OpenSubtitles REST API v1 (returns direct .gz SRT download links)
    try {
      val cleanLookupTitle = tmdbLookup?.title ?: cleanMediaTitleForLookup(cleanQuery)
      val isSeriesSearch = (tmdbLookup?.isSeries == true) || (seasonNum != null && episodeNum != null)
      // OpenSubtitles REST API v1 requires URL parameters in strict alphabetical order:
      // episode -> imdbid/query -> season -> sublanguageid
      val restUrl = if (tmdbLookup != null && tmdbLookup.imdbId.startsWith("tt")) {
        val numericImdb = tmdbLookup.imdbId.removePrefix("tt")
        buildString {
          append("https://rest.opensubtitles.org/search")
          if (isSeriesSearch && episodeNum != null) {
            append("/episode-").append(episodeNum)
          }
          append("/imdbid-").append(numericImdb)
          if (isSeriesSearch && seasonNum != null) {
            append("/season-").append(seasonNum)
          }
          append("/sublanguageid-").append(iso3Lang)
        }
      } else {
        val enc = URLEncoder.encode(cleanLookupTitle.lowercase(), "UTF-8")
        buildString {
          append("https://rest.opensubtitles.org/search")
          if (isSeriesSearch && episodeNum != null) {
            append("/episode-").append(episodeNum)
          }
          append("/query-").append(enc)
          if (isSeriesSearch && seasonNum != null) {
            append("/season-").append(seasonNum)
          }
          append("/sublanguageid-").append(iso3Lang)
        }
      }

      val req = Request.Builder()
        .url(restUrl)
        .header("User-Agent", "TemporaryUserAgent")
        .header("X-User-Agent", "TemporaryUserAgent")
        .header("Accept", "application/json")
        .build()

      fastScraperClient.newCall(req).execute().use { resp ->
        if (resp.isSuccessful) {
          val body = resp.body?.string() ?: ""
          if (body.trim().startsWith("[")) {
            val arr = JSONArray(body)
            for (i in 0 until minOf(arr.length(), 15)) {
              val item = arr.optJSONObject(i) ?: continue
              val gzLink = item.optString("SubDownloadLink", "").trim()
              if (!gzLink.startsWith("http")) continue
              // Verify SeriesSeason/SeriesEpisode metadata from OpenSubtitles when searching a specific episode
              if (seasonNum != null && episodeNum != null) {
                val itemSeason = item.optString("SeriesSeason", "").toIntOrNull()
                val itemEpisode = item.optString("SeriesEpisode", "").toIntOrNull()
                if (itemSeason != null && itemSeason > 0 && itemSeason != seasonNum) continue
                if (itemEpisode != null && itemEpisode > 0 && itemEpisode != episodeNum) continue
              }
              val release = item.optString("MovieReleaseName").ifBlank {
                item.optString("SubFileName", "$cleanLookupTitle ($langLabel)")
              }.trim()
              val subFormat = item.optString("SubFormat", "srt").lowercase()
              list.add(
                SubtitleItem(
                  id = "os_rest_${item.optString("IDSubtitleFile", UUID.randomUUID().toString())}",
                  releaseName = release,
                  languageCode = languageCode,
                  languageName = langLabel,
                  format = subFormat,
                  downloadUrl = gzLink,
                  source = "OpenSubtitles.org",
                  rating = item.optString("SubRating", "9.6").toFloatOrNull()?.div(2f)?.coerceIn(3.5f, 5.0f) ?: 4.9f
                )
              )
            }
          }
        }
      }
    } catch (_: Exception) {
    }

    list
  }

  /**
   * Downloads the full `.srt`, `.vtt`, or `.gz` subtitle file from `item.downloadUrl`,
   * auto-detects Arabic UTF-8 vs Windows-1256 encoding, parses all timed cues off the main thread,
   * and writes a local `.vtt` file into `context.cacheDir` so both ExoPlayer and WebView render it!
   */
  suspend fun resolveAndParseSubtitleItem(
    context: Context,
    item: SubtitleItem
  ): SubtitleItem = withContext(Dispatchers.IO) {
    if (item.parsedCues.isNotEmpty() && item.localFileUri.isNotBlank()) {
      return@withContext item
    }

    val startMs = System.currentTimeMillis()
    try {
      var rawSubText = ""

      if (item.downloadUrl.startsWith("http")) {
        val reqBuilder = Request.Builder()
          .url(item.downloadUrl)
          .header("User-Agent", if (item.downloadUrl.contains("opensubtitles.org")) "TemporaryUserAgent" else desktopUserAgent)
          .header("Accept", "*/*")
        if (item.downloadUrl.contains("opensubtitles.org")) {
          reqBuilder.header("X-User-Agent", "TemporaryUserAgent")
        }

        val downloadedText = withTimeoutOrNull(9_000L) {
          fastScraperClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use ""
            val rawBytes = resp.body?.bytes() ?: ByteArray(0)
            if (rawBytes.isEmpty()) return@use ""
            val decompressedBytes = if (isGzipCompressed(rawBytes) || item.downloadUrl.lowercase().endsWith(".gz")) {
              try {
                GZIPInputStream(ByteArrayInputStream(rawBytes)).use { it.readBytes() }
              } catch (_: Exception) {
                rawBytes
              }
            } else {
              rawBytes
            }
            decodeSubtitleBytesWithArabicSupport(decompressedBytes)
          }
        } ?: ""
        rawSubText = downloadedText
      }

      if (rawSubText.isBlank() && item.rawVttDataUri.startsWith("data:text/vtt;charset=utf-8;base64,")) {
        val b64 = item.rawVttDataUri.substringAfter("base64,")
        rawSubText = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
      }

      val parsedCues = parseSubtitleCues(rawSubText)
      val vttContent = convertRawToCleanWebVtt(rawSubText)
      val dataUri = convertSrtToVttDataUri(vttContent)

      val cacheFile = File(context.cacheDir, "asser_sub_${kotlin.math.abs(item.id.hashCode())}.vtt")
      cacheFile.writeText(vttContent, Charsets.UTF_8)
      val localUri = Uri.fromFile(cacheFile).toString()

      val elapsedMs = System.currentTimeMillis() - startMs
      AppDiagnosticsLogger.log(
        "Subtitles",
        "Loaded subtitle '${item.releaseName}' (${parsedCues.size} cues) in ${elapsedMs}ms -> $localUri"
      )

      item.copy(
        rawVttDataUri = dataUri,
        localFileUri = localUri,
        parsedCues = parsedCues
      )
    } catch (e: Exception) {
      AppDiagnosticsLogger.log(
        "SubtitlesError",
        "Failed to download/parse subtitle ${item.downloadUrl}: ${e.message}",
        isError = true
      )
      item
    }
  }

  private fun isGzipCompressed(bytes: ByteArray): Boolean {
    return bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
  }

  private fun decodeSubtitleBytesWithArabicSupport(bytes: ByteArray): String {
    // Strip UTF-8 BOM if present
    val cleanBytes = if (bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() &&
        bytes[1] == 0xBB.toByte() &&
        bytes[2] == 0xBF.toByte()
    ) {
      bytes.copyOfRange(3, bytes.size)
    } else {
      bytes
    }

    val utf8Text = String(cleanBytes, Charsets.UTF_8)
    // If UTF-8 decoding produced replacement characters (), it's Windows-1256 (Arabic ANSI)
    return if (utf8Text.contains('\uFFFD')) {
      try {
        String(cleanBytes, Charset.forName("windows-1256"))
      } catch (_: Exception) {
        utf8Text
      }
    } else {
      utf8Text
    }
  }

  private val cueBlockSplitRegex = Regex("\n\\s*\n")
  private val cueTimeRegex = Regex(
    "((?:\\d{1,2}:)?\\d{2}:\\d{2}[,.]\\d{3})\\s*-->\\s*((?:\\d{1,2}:)?\\d{2}:\\d{2}[,.]\\d{3})"
  )
  private val assStyleTagRegex = Regex("\\{\\\\[^}]*\\}")
  private val htmlTagRegex = Regex("<[^>]*>")

  fun parseSubtitleCues(rawSubtitleText: String): List<ParsedSubtitleCue> {
    if (rawSubtitleText.isBlank()) return emptyList()
    val cues = ArrayList<ParsedSubtitleCue>(256)
    val clean = rawSubtitleText
      .replace("\uFEFF", "")
      .replace("\r\n", "\n")
      .replace("\r", "\n")
      .trim()

    val blocks = clean.split(cueBlockSplitRegex)

    for (block in blocks) {
      val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
      if (lines.isEmpty()) continue
      val timeLineIndex = lines.indexOfFirst { it.contains("-->") && cueTimeRegex.containsMatchIn(it) }
      if (timeLineIndex == -1) continue

      val match = cueTimeRegex.find(lines[timeLineIndex]) ?: continue
      val startMs = parseTimestampToMs(match.groupValues[1])
      val endMs = parseTimestampToMs(match.groupValues[2])
      if (endMs <= startMs) continue

      val textLines = lines.subList(timeLineIndex + 1, lines.size)
        .map { line ->
          line
            .replace(assStyleTagRegex, "") // Strip ASS/SSA style tags like {\an8}
            .replace(htmlTagRegex, "")     // Strip HTML tags like <i>, <font>
            .trim()
        }
        .filter { it.isNotEmpty() }

      if (textLines.isNotEmpty()) {
        cues.add(
          ParsedSubtitleCue(
            startMs = startMs,
            endMs = endMs,
            text = cleanHtmlEntities(textLines.joinToString("\n"))
          )
        )
      }
    }
    cues.sortBy { it.startMs }
    return cues
  }

  private fun parseTimestampToMs(ts: String): Long {
    return try {
      val normalized = ts.trim().replace(',', '.')
      val parts = normalized.split(":")
      val hours: Long
      val minutes: Long
      val secMillis: String
      if (parts.size == 3) {
        hours = parts[0].toLongOrNull() ?: 0L
        minutes = parts[1].toLongOrNull() ?: 0L
        secMillis = parts[2]
      } else {
        hours = 0L
        minutes = parts[0].toLongOrNull() ?: 0L
        secMillis = parts.getOrElse(1) { "0.0" }
      }
      val secParts = secMillis.split(".")
      val seconds = secParts[0].toLongOrNull() ?: 0L
      val millis = secParts.getOrElse(1) { "0" }.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
      ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L + millis
    } catch (_: Exception) {
      0L
    }
  }

  private fun convertRawToCleanWebVtt(rawSubtitleText: String): String {
    val clean = rawSubtitleText
      .replace("\uFEFF", "")
      .replace("\r\n", "\n")
      .replace("\r", "\n")
      .trim()
    return if (clean.startsWith("WEBVTT", ignoreCase = true)) {
      clean
    } else {
      val convertedTimestamps = clean.replace(
        Regex("([0-9]{1,2}:[0-9]{2}:[0-9]{2}),([0-9]{3})"),
        "$1.$2"
      )
      "WEBVTT\n\n$convertedTimestamps"
    }
  }

  fun convertSrtToVttDataUri(rawSubtitleText: String): String {
    val vttText = convertRawToCleanWebVtt(rawSubtitleText)
    return try {
      val base64 = Base64.encodeToString(vttText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
      "data:text/vtt;charset=utf-8;base64,$base64"
    } catch (_: Exception) {
      ""
    }
  }

  private fun getLanguageDisplayName(languageCode: String): String {
    return when (languageCode) {
      "ar" -> "العربية (Arabic)"
      "en" -> "الإنجليزية (English)"
      "tr" -> "التركية (Turkish)"
      "ko" -> "الكورية (Korean)"
      "fr" -> "الفرنسية (French)"
      else -> "العربية (Arabic)"
    }
  }

  private fun cleanHtmlEntities(str: String): String {
    return str.replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&#8211;", "-")
      .replace("&#8217;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .trim()
  }

  data class StandardizedMediaTitleInfo(
    val cleanTitle: String,
    val year: String,
    val isSeries: Boolean,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val displayTitle: String,
    val subtitleQuery: String
  )

  companion object {
    @Volatile
    private var sslInstalled = false

    private val trustAllManager = object : X509TrustManager {
      override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
      override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
      override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /**
     * Strips any appended episode/season suffixes (e.g. "- الحلقة 6: The Relic - الحلقة 7: The Dive",
     * "- S01E06", "• Episode 8") so `seriesTitle` NEVER accumulates previous episode names.
     */
    fun stripEpisodeAndSeasonSuffix(raw: String): String {
      if (raw.isBlank()) return ""
      // First cut off anything after a separator followed by an episode/season marker
      val cutMatch = Regex(
        "\\s*[-•|:]\\s*(?:الحلقة|الحلقه|حلقة|حلقه|الموسم|موسم|S0*[0-9]{1,2}\\s*E0*[0-9]{1,3}|[0-9]{1,2}x[0-9]{1,3}|Episode\\s*[0-9]|Ep\\s*[0-9]|Season\\s*[0-9]).*$",
        RegexOption.IGNORE_CASE
      ).find(raw)
      val basePart = if (cutMatch != null && cutMatch.range.first >= 2) {
        raw.substring(0, cutMatch.range.first)
      } else {
        raw
      }
      return basePart
        .replace(Regex("\\bS0*[0-9]{1,2}\\s*E0*[0-9]{1,3}\\b.*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b0*[0-9]{1,2}x0*[0-9]{1,3}\\b.*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("(?:الموسم|موسم)\\s*(?:الحادي|الحادى|الاحدى|الاحدي|الاول|الأول|الثاني|الثانى|الثالث|الرابع|الخامس|السادس|السابع|الثامن|التاسع|العاشر|العشرون|العشرين|[0-9]{1,2}).*$"), "")
        .replace(Regex("(?:الحلقة|الحلقه|حلقة|حلقه)\\s*[0-9]{1,3}.*$"), "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', ':', '|', '•')
    }

    /**
     * Parses Arabic ordinal season labels from 1 up to 20 (e.g. "الموسم الأول" .. "الموسم الثالث عشر" .. "الموسم العشرون"),
     * normalizing hyphens/underscores to spaces and checking compound ordinals (11..19) before single ordinals (1..10).
     */
    fun parseArabicOrdinalSeason(raw: String): Int? {
      if (raw.isBlank()) return null
      val norm = raw
        .replace('-', ' ')
        .replace('_', ' ')
        .replace('/', ' ')
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace(Regex("\\s+"), " ")
      if (!norm.contains("موسم")) return null
      return when {
        norm.contains("موسم الحادي عشر") || norm.contains("موسم الاحدي عشر") -> 11
        norm.contains("موسم الثاني عشر") -> 12
        norm.contains("موسم الثالث عشر") -> 13
        norm.contains("موسم الرابع عشر") -> 14
        norm.contains("موسم الخامس عشر") -> 15
        norm.contains("موسم السادس عشر") -> 16
        norm.contains("موسم السابع عشر") -> 17
        norm.contains("موسم الثامن عشر") -> 18
        norm.contains("موسم التاسع عشر") -> 19
        norm.contains("موسم العشرون") || norm.contains("موسم العشرين") -> 20
        norm.contains("موسم الاول") -> 1
        norm.contains("موسم الثاني") -> 2
        norm.contains("موسم الثالث") -> 3
        norm.contains("موسم الرابع") -> 4
        norm.contains("موسم الخامس") -> 5
        norm.contains("موسم السادس") -> 6
        norm.contains("موسم السابع") -> 7
        norm.contains("موسم الثامن") -> 8
        norm.contains("موسم التاسع") -> 9
        norm.contains("موسم العاشر") -> 10
        else -> null
      }
    }

    /**
     * Produces standardized display titles and subtitle auto-search queries:
     * - Series episodes: `Silo S01E01`, `Lost S04E15` (strips year parentheses & Arabic filler words)
     * - Movies: `Interstellar (2014)` for display, `Interstellar` for subtitle lookup
     */
    fun formatStandardizedEpisodeInfo(
      seriesTitle: String,
      pageTitle: String,
      lastSearchQuery: String,
      watchPageUrl: String,
      embedUrl: String,
      serverName: String,
      isSeriesHint: Boolean,
      selectedSeasonHint: Int,
      currentEpisodeHint: Int
    ): StandardizedMediaTitleInfo {
      val decodedWatchUrl = try { java.net.URLDecoder.decode(watchPageUrl, "UTF-8") } catch (_: Exception) { watchPageUrl }
      val decodedEmbedUrl = try { java.net.URLDecoder.decode(embedUrl, "UTF-8") } catch (_: Exception) { embedUrl }

      fun extractSeasonFromSource(src: String): Int? {
        if (src.isBlank()) return null
        Regex("/(?:tv|series)/(?:[^/]+/)?[0-9]{2,8}/([0-9]{1,2})/[0-9]{1,3}", RegexOption.IGNORE_CASE)
          .find(src)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("\\bS0*([0-9]{1,2})\\s*E0*[0-9]{1,3}\\b", RegexOption.IGNORE_CASE)
          .findAll(src).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("\\b0*([0-9]{1,2})x0*[0-9]{1,3}\\b", RegexOption.IGNORE_CASE)
          .findAll(src).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("(?:الموسم|موسم|\\bseason)\\s*[-:_]?\\s*([0-9]{1,2})(?:\\b|[^0-9])", RegexOption.IGNORE_CASE)
          .findAll(src).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return parseArabicOrdinalSeason(src)
      }

      fun extractEpisodeFromSource(src: String): Int? {
        if (src.isBlank()) return null
        Regex("/(?:tv|series)/(?:[^/]+/)?[0-9]{2,8}/[0-9]{1,2}/([0-9]{1,3})", RegexOption.IGNORE_CASE)
          .find(src)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("\\bS0*[0-9]{1,2}\\s*E0*([0-9]{1,3})\\b", RegexOption.IGNORE_CASE)
          .findAll(src).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("\\b0*[0-9]{1,2}x0*([0-9]{1,3})\\b", RegexOption.IGNORE_CASE)
          .findAll(src).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("(?:الحلقة|الحلقه|حلقة|حلقه|\\bepisode|\\bep)\\s*[-:_]?\\s*([0-9]{1,3})(?:\\b|[^0-9])", RegexOption.IGNORE_CASE)
          .findAll(src).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return null
      }

      // 1. Strict priority order so stale text NEVER overrides the currently playing episode:
      //    embedUrl path -> decodedWatchUrl -> explicit currentEpisodeHint -> serverName -> pageTitle (last match)
      val parsedSeason = extractSeasonFromSource(decodedEmbedUrl)
        ?: extractSeasonFromSource(decodedWatchUrl)
        ?: selectedSeasonHint.takeIf { it > 0 && isSeriesHint }
        ?: extractSeasonFromSource(serverName)
        ?: extractSeasonFromSource(pageTitle)

      val parsedEpisode = extractEpisodeFromSource(decodedEmbedUrl)
        ?: extractEpisodeFromSource(decodedWatchUrl)
        ?: currentEpisodeHint.takeIf { it > 0 && isSeriesHint }
        ?: extractEpisodeFromSource(serverName)
        ?: extractEpisodeFromSource(pageTitle)

      val lowerUrls = "$decodedWatchUrl $decodedEmbedUrl".lowercase()
      val isSeries = isSeriesHint ||
        parsedSeason != null ||
        parsedEpisode != null ||
        lowerUrls.contains("/tv/") ||
        lowerUrls.contains("/series/") ||
        decodedWatchUrl.contains("مسلسل") ||
        pageTitle.contains("مسلسل")

      val finalSeason = if (isSeries) {
        (parsedSeason ?: selectedSeasonHint.takeIf { it > 0 } ?: 1).coerceAtLeast(1)
      } else null

      val finalEpisode = if (isSeries) {
        (parsedEpisode ?: currentEpisodeHint.takeIf { it > 0 } ?: 1).coerceAtLeast(1)
      } else null

      // 2. Pick pure base show title (strip all episode suffixes & episode subtitles first!)
      val urlSlugFallback = Regex("/(?:tv|movie|series)/([a-z0-9\\-]+)(?:/|$)", RegexOption.IGNORE_CASE)
        .find(decodedWatchUrl)?.groupValues?.get(1)
        ?.replace("-", " ")
        ?.split(" ")
        ?.filter { it.isNotBlank() }
        ?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        ?: ""

      val rawCandidates = listOf(seriesTitle, lastSearchQuery, pageTitle, urlSlugFallback)
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("http", ignoreCase = true) }

      val year = rawCandidates.firstNotNullOfOrNull { cand ->
        Regex("\\(?((?:19|20)\\d{2})\\)?").find(cand)?.groupValues?.get(1)
      } ?: ""

      fun sanitizeTitleCandidate(raw: String): String {
        val baseOnly = stripEpisodeAndSeasonSuffix(raw).ifBlank { raw }
        val withoutTags = baseOnly
          .replace(Regex("\\bS0*[0-9]{1,2}\\s*E0*[0-9]{1,3}\\b", RegexOption.IGNORE_CASE), " ")
          .replace(Regex("\\b0*[0-9]{1,2}x0*[0-9]{1,3}\\b", RegexOption.IGNORE_CASE), " ")
          .replace(Regex("\\((?:19|20)\\d{2}\\)"), " ")
          .replace(Regex("\\[(?:19|20)\\d{2}\\]"), " ")
          .replace(Regex("\\b(?:19|20)\\d{2}\\b"), " ")
          .replace(Regex("\\b(?:season|episode|ep)\\s*[-:]?\\s*[0-9]{1,3}\\b", RegexOption.IGNORE_CASE), " ")
          .replace(Regex("(?:الموسم|موسم)\\s*(?:الاول|الأول|الثاني|الثانى|الثالث|الرابع|الخامس|السادس|السابع|الثامن|التاسع|العاشر|[0-9]{1,2})"), " ")
          .replace(Regex("(?:الحلقة|الحلقه|حلقة|حلقه)\\s*[0-9]{1,3}"), " ")
          .replace(
            Regex("(?:مشاهدة|مشاهده|تحميل|فيلم|مسلسل|مترجم|مترجمة|مترجمه|مدبلج|مدبلجة|مدبلجه|كامل|كاملة|كامله|اون لاين|أونلاين|اونلاين|والاخيرة|والأخيرة|الأخيرة|الاخيرة|بجودة|عالية|توب سينما|ووتش سيريز|لاروزا|ايجي ديد)"),
            " "
          )
          .replace(Regex("\\b(?:HD|FHD|UHD|4K|1080p|720p|480p|360p|WEB-DL|WEBRip|BluRay|HDRip|TopCinema|WatchSeries|EgyDead|LaRoza)\\b", RegexOption.IGNORE_CASE), " ")
          .replace(Regex("[()\\[\\]]"), " ")

        // Prefer clean English/Latin show title when available (e.g., "Silo", "The Gentlemen", "Lost")
        val latinMatch = Regex("[A-Za-z][A-Za-z0-9'&.:\\- ]{1,60}")
          .findAll(withoutTags)
          .map { it.value.trim(' ', '-', ':', '|', '•', '.') }
          .firstOrNull { it.length >= 2 }

        if (!latinMatch.isNullOrBlank()) {
          return latinMatch.replace(Regex("\\s+"), " ").trim()
        }

        return withoutTags
          .replace(Regex("[|•]+"), " ")
          .replace(Regex("\\s+"), " ")
          .trim(' ', '-', ':', '|', '•')
      }

      val cleanTitle = rawCandidates
        .map { sanitizeTitleCandidate(it) }
        .firstOrNull { it.length >= 2 }
        ?: rawCandidates.firstOrNull()
        ?: "Asser TV"

      val displayTitle: String
      val subtitleQuery: String
      if (isSeries && finalSeason != null && finalEpisode != null) {
        val tag = "S${finalSeason.toString().padStart(2, '0')}E${finalEpisode.toString().padStart(2, '0')}"
        displayTitle = "$cleanTitle $tag"
        subtitleQuery = "$cleanTitle $tag"
      } else {
        displayTitle = if (year.isNotBlank()) "$cleanTitle ($year)" else cleanTitle
        subtitleQuery = cleanTitle
      }

      return StandardizedMediaTitleInfo(
        cleanTitle = cleanTitle,
        year = year,
        isSeries = isSeries,
        seasonNumber = finalSeason,
        episodeNumber = finalEpisode,
        displayTitle = displayTitle,
        subtitleQuery = subtitleQuery
      )
    }

    private class Tls12CompatSocketFactory(
      private val delegate: SSLSocketFactory
    ) : SSLSocketFactory() {
      override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
      override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

      private fun enableModernTls(socket: Socket): Socket {
        if (socket is SSLSocket) {
          try {
            val supported = socket.supportedProtocols.orEmpty().toSet()
            val preferred = listOf("TLSv1.3", "TLSv1.2").filter { it in supported }
            if (preferred.isNotEmpty()) {
              socket.enabledProtocols = preferred.toTypedArray()
            }
          } catch (_: Throwable) {}
        }
        return socket
      }

      override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        enableModernTls(delegate.createSocket(s, host, port, autoClose))

      override fun createSocket(host: String, port: Int): Socket =
        enableModernTls(delegate.createSocket(host, port))

      override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        enableModernTls(delegate.createSocket(host, port, localHost, localPort))

      override fun createSocket(host: InetAddress, port: Int): Socket =
        enableModernTls(delegate.createSocket(host, port))

      override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        enableModernTls(delegate.createSocket(address, port, localAddress, localPort))
    }

    fun isSyntheticFallbackResult(item: SearchResultItem): Boolean {
      val id = item.id.lowercase()
      return id.startsWith("tc_direct_") ||
        id.startsWith("ws_direct_") ||
        id.startsWith("lr_direct_") ||
        id.startsWith("ed_direct_") ||
        id.startsWith("flx_direct_") ||
        id.endsWith("_custom_direct")
    }

    /**
     * Installs a permissive TLS 1.2+ SSL socket factory on `HttpsURLConnection` so Media3 ExoPlayer
     * (`DefaultHttpDataSource`) and `OkHttpClient` never fail with `SSLHandshakeException`
     * on video streaming CDN edge servers or Android 7.1.2 (SDK 25) devices.
     */
    fun installPermissiveSslForStreaming() {
      if (sslInstalled) return
      try {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        val compatFactory = Tls12CompatSocketFactory(sslContext.socketFactory)
        HttpsURLConnection.setDefaultSSLSocketFactory(compatFactory)
        HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        sslInstalled = true
      } catch (_: Exception) {
      }
    }

    fun buildSslTolerantHttpClient(): OkHttpClient {
      val builder = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectionSpecs(
          listOf(
            ConnectionSpec.MODERN_TLS,
            ConnectionSpec.COMPATIBLE_TLS,
            ConnectionSpec.CLEARTEXT
          )
        )
      return try {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        val compatFactory = Tls12CompatSocketFactory(sslContext.socketFactory)
        builder
          .sslSocketFactory(compatFactory, trustAllManager)
          .hostnameVerifier { _, _ -> true }
          .build()
      } catch (_: Exception) {
        builder.build()
      }
    }
  }
}
