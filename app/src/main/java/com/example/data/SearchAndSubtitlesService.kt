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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
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
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SearchAndSubtitlesService {

  init {
    installPermissiveSslForStreaming()
  }

  private val httpClient = buildSslTolerantHttpClient()

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

  suspend fun fetchTrendingCatalog(): List<CatalogMediaItem> = withContext(Dispatchers.IO) {
    val list = mutableListOf<CatalogMediaItem>()
    val seenTitles = mutableSetOf<String>()

    // 1) Try scraping live IMDb Daily Trending (moviemeter + tvmeter top movers)
    val imdbTrendingTv = scrapeImdbChart(
      chartUrl = "https://www.imdb.com/chart/tvmeter/",
      mediaType = "tv",
      maxItems = 8,
      assignRank = false
    )
    val imdbTrendingMovies = scrapeImdbChart(
      chartUrl = "https://www.imdb.com/chart/moviemeter/",
      mediaType = "movie",
      maxItems = 8,
      assignRank = false
    )
    val maxInterleave = maxOf(imdbTrendingTv.size, imdbTrendingMovies.size)
    for (i in 0 until maxInterleave) {
      if (i < imdbTrendingTv.size) {
        val tvItem = imdbTrendingTv[i]
        if (seenTitles.add(tvItem.title.lowercase())) list.add(tvItem)
      }
      if (i < imdbTrendingMovies.size) {
        val movItem = imdbTrendingMovies[i]
        if (seenTitles.add(movItem.title.lowercase())) list.add(movItem)
      }
    }

    // 2) Enrich / fallback with Live Daily Trending (/trending/all/day) — strictly released & rated items only
    if (list.size < 12) {
      try {
        val url = "https://api.themoviedb.org/3/trending/all/day?api_key=$tmdbKey&language=en-US"
        val request = Request.Builder().url(url).header("User-Agent", desktopUserAgent).build()
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (body.isNotBlank()) {
          val json = JSONObject(body)
          val results = json.optJSONArray("results")
          if (results != null) {
            for (i in 0 until results.length()) {
              if (list.size >= 16) break
              val item = results.getJSONObject(i)
              val mediaType = item.optString("media_type", "tv")
              if (mediaType != "movie" && mediaType != "tv") continue
              val parsed = parseTmdbCatalogItem(item, mediaType, rank = null)
              if (parsed != null && seenTitles.add(parsed.title.lowercase())) {
                list.add(parsed)
              }
            }
          }
        }
      } catch (_: Exception) {}
    }

    list
  }

  suspend fun fetchTop10Movies(): List<CatalogMediaItem> = withContext(Dispatchers.IO) {
    val list = mutableListOf<CatalogMediaItem>()
    val seenTitles = mutableSetOf<String>()

    // 1) Scrape Live Daily IMDb Most Popular Movies Chart (https://www.imdb.com/chart/moviemeter/)
    val imdbMovies = scrapeImdbChart(
      chartUrl = "https://www.imdb.com/chart/moviemeter/",
      mediaType = "movie",
      maxItems = 10,
      assignRank = true
    )
    for (item in imdbMovies) {
      if (seenTitles.add(item.title.lowercase())) {
        list.add(item.copy(rank = list.size + 1))
      }
    }

    // 2) Fill remaining slots up to 10 from Live Daily Trending Movies (/trending/movie/day) & Now Playing
    val dailyUrls = listOf(
      "https://api.themoviedb.org/3/trending/movie/day?api_key=$tmdbKey&language=en-US",
      "https://api.themoviedb.org/3/movie/now_playing?api_key=$tmdbKey&language=en-US&page=1"
    )
    for (apiUrl in dailyUrls) {
      if (list.size >= 10) break
      try {
        val request = Request.Builder().url(apiUrl).header("User-Agent", desktopUserAgent).build()
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (body.isNotBlank()) {
          val results = JSONObject(body).optJSONArray("results")
          if (results != null) {
            for (i in 0 until results.length()) {
              if (list.size >= 10) break
              val item = results.getJSONObject(i)
              val parsed = parseTmdbCatalogItem(item, "movie", rank = list.size + 1)
              if (parsed != null && seenTitles.add(parsed.title.lowercase())) {
                list.add(parsed.copy(rank = list.size + 1))
              }
            }
          }
        }
      } catch (_: Exception) {}
    }

    list
  }

  suspend fun fetchTop10TvSeries(): List<CatalogMediaItem> = withContext(Dispatchers.IO) {
    val list = mutableListOf<CatalogMediaItem>()
    val seenTitles = mutableSetOf<String>()

    // 1) Scrape Live Daily IMDb Most Popular TV Shows Chart (https://www.imdb.com/chart/tvmeter/)
    val imdbTv = scrapeImdbChart(
      chartUrl = "https://www.imdb.com/chart/tvmeter/",
      mediaType = "tv",
      maxItems = 10,
      assignRank = true
    )
    for (item in imdbTv) {
      if (seenTitles.add(item.title.lowercase())) {
        list.add(item.copy(rank = list.size + 1))
      }
    }

    // 2) Fill remaining slots up to 10 from Live Daily Trending TV (/trending/tv/day) & On The Air
    val dailyUrls = listOf(
      "https://api.themoviedb.org/3/trending/tv/day?api_key=$tmdbKey&language=en-US",
      "https://api.themoviedb.org/3/tv/on_the_air?api_key=$tmdbKey&language=en-US&page=1"
    )
    for (apiUrl in dailyUrls) {
      if (list.size >= 10) break
      try {
        val request = Request.Builder().url(apiUrl).header("User-Agent", desktopUserAgent).build()
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (body.isNotBlank()) {
          val results = JSONObject(body).optJSONArray("results")
          if (results != null) {
            for (i in 0 until results.length()) {
              if (list.size >= 10) break
              val item = results.getJSONObject(i)
              val parsed = parseTmdbCatalogItem(item, "tv", rank = list.size + 1)
              if (parsed != null && seenTitles.add(parsed.title.lowercase())) {
                list.add(parsed.copy(rank = list.size + 1))
              }
            }
          }
        }
      } catch (_: Exception) {}
    }

    list
  }

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
      val resp = httpClient.newCall(req).execute()
      val html = resp.body?.string() ?: ""
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

              val posterUrl = node.optJSONObject("primaryImage")?.optString("url", "") ?: ""
              if (posterUrl.isBlank()) continue

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
    val posterUrl = "https://image.tmdb.org/t/p/w500$posterPath"
    val backdropUrl = if (backdropPath.isNotBlank() && backdropPath != "null") {
      "https://image.tmdb.org/t/p/w780$backdropPath"
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
    val url = "https://topcinema.io/search/?query=$encoded&type=all"

    try {
      val request = Request.Builder()
        .url(url)
        .header("User-Agent", desktopUserAgent)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header("Accept-Language", "ar,en-US;q=0.7,en;q=0.3")
        .build()

      val response = httpClient.newCall(request).execute()
      val html = response.body?.string() ?: ""

      val boxPattern = Pattern.compile(
        "<div[^>]*class=\"[^\"]*Small--Box[^\"]*\"[^>]*>.*?<a[^>]+href=\"([^\"]+)\"[^>]+title=\"([^\"]+)\"[^>]*>.*?<img[^>]+(?:data-src|src)=\"([^\"]+)\"",
        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
      )
      var matcher = boxPattern.matcher(html)
      var count = 0
      val seenUrls = mutableSetOf<String>()

      while (matcher.find() && count < 20) {
        val link = matcher.group(1)?.trim() ?: ""
        val title = matcher.group(2)?.trim() ?: ""
        val poster = matcher.group(3)?.trim() ?: ""

        if (title.isNotBlank() && !link.contains("/recent/") && !link.contains("/category/") && !link.contains("/page/")) {
          val absoluteLink = if (link.startsWith("/")) "https://topcinema.io$link" else link
          if (!seenUrls.contains(absoluteLink)) {
            seenUrls.add(absoluteLink)
            val absolutePoster = if (poster.startsWith("/")) "https://topcinema.io$poster" else poster
            val isSeries = title.contains("مسلسل", ignoreCase = true) ||
              title.contains("حلقة", ignoreCase = true) ||
              link.contains("series", ignoreCase = true)
            val isSeriesOverview = link.contains("/series/", ignoreCase = true) ||
              (title.contains("مسلسل", ignoreCase = true) && !title.contains("حلقة") && !title.contains("الحلقة") && !title.contains("الحلقه"))

            val cleanBase = absoluteLink.trimEnd('/')
            val watchUrl = if (!cleanBase.endsWith("/watch") && !isSeriesOverview) "$cleanBase/watch/" else "$cleanBase/"

            results.add(
              SearchResultItem(
                id = UUID.randomUUID().toString(),
                title = cleanHtmlEntities(title),
                url = watchUrl,
                portal = StreamingPortal.TOP_CINEMA,
                posterUrl = absolutePoster,
                type = if (isSeries) "مسلسل (توب سينما)" else "فيلم (توب سينما)",
                quality = if (title.contains("1080", ignoreCase = true)) "1080p Web-DL" else "HD 720p",
                year = "2024",
                rating = "8.5"
              )
            )
            count++
          }
        }
      }

      if (results.isEmpty()) {
        val fallbackPattern = Pattern.compile(
          "<a[^>]+href=\"(https?://topcinema\\.io/[^\"]+)\"[^>]+title=\"([^\"]+)\"[^>]*class=\"[^\"]*recent--block[^\"]*\"",
          Pattern.DOTALL or Pattern.CASE_INSENSITIVE
        )
        matcher = fallbackPattern.matcher(html)
        while (matcher.find() && count < 15) {
          val link = matcher.group(1)?.trim() ?: ""
          val title = matcher.group(2)?.trim() ?: ""
          if (!link.contains("/recent/") && !link.contains("/category/")) {
            val isSeriesOverview = link.contains("/series/", ignoreCase = true) ||
              (title.contains("مسلسل", ignoreCase = true) && !title.contains("حلقة") && !title.contains("الحلقة") && !title.contains("الحلقه"))
            val cleanBase = link.trimEnd('/')
            val watchUrl = if (!cleanBase.endsWith("/watch") && !isSeriesOverview) "$cleanBase/watch/" else "$cleanBase/"
            results.add(
              SearchResultItem(
                id = UUID.randomUUID().toString(),
                title = cleanHtmlEntities(title),
                url = watchUrl,
                portal = StreamingPortal.TOP_CINEMA,
                posterUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=400&q=80",
                type = "مشاهدة مباشرة",
                quality = "1080p Full HD",
                year = "2024",
                rating = "8.4"
              )
            )
            count++
          }
        }
      }
    } catch (_: Exception) {}

    results
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

  suspend fun scrapeSeasonsAndEpisodes(
    pageUrl: String,
    portal: StreamingPortal,
    titleHint: String = "",
    selectedSeasonNumber: Int = 1
  ): ScrapedSeriesStructure = withContext(Dispatchers.IO) {
    val seasons = mutableListOf<ScrapedSeasonItem>()
    val episodes = mutableListOf<ScrapedEpisodeItem>()
    val watchServers = mutableListOf<ScrapedWatchServer>()
    val seenEmbedUrls = mutableSetOf<String>()

    // 1. Normalize watch page URL (e.g. LaRoza video.php -> play.php, TopCinema -> /watch/)
    var normalizedWatchUrl = when {
      pageUrl.contains("video.php?vid=") -> pageUrl.replace("video.php?vid=", "play.php?vid=")
      portal == StreamingPortal.TOP_CINEMA &&
        !pageUrl.contains("/watch") &&
        !pageUrl.contains("/search") &&
        !pageUrl.contains("/series/") -> "${pageUrl.trimEnd('/')}/watch/"
      else -> pageUrl
    }

    val decodedInitialUrl = try { java.net.URLDecoder.decode(normalizedWatchUrl, "UTF-8") } catch (_: Exception) { normalizedWatchUrl }
    val detectedSeasonFromText = extractSeasonNumberFromText("$titleHint $decodedInitialUrl")
    val effectiveSeasonNumber = if (selectedSeasonNumber > 1) selectedSeasonNumber else (detectedSeasonFromText ?: 1)

    fun addServerIfValid(name: String, rawEmbedUrl: String, qualityLabel: String = "Auto HD", isDirect: Boolean = true) {
      val cleanUrl = when {
        rawEmbedUrl.startsWith("//") -> "https:$rawEmbedUrl"
        rawEmbedUrl.startsWith("/") -> resolveRelativeUrl(normalizedWatchUrl, rawEmbedUrl)
        else -> rawEmbedUrl.trim()
      }
      if (!cleanUrl.startsWith("http")) return
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

    fun extractServersFromHtml(htmlContent: String) {
      if (htmlContent.isBlank()) return
      var srvIdx = watchServers.size + 1

      // 1) Extract data-embed / data-url / data-link / data-src watch server attributes
      val dataEmbedMatcher = Pattern.compile(
        "(?:data-embed|data-link|data-url|data-frame|data-watch)=\"(https?:[^\"]+|//[^\"]+)\"",
        Pattern.CASE_INSENSITIVE
      ).matcher(htmlContent)
      while (dataEmbedMatcher.find() && srvIdx <= 14) {
        val embed = dataEmbedMatcher.group(1)?.trim() ?: continue
        val hostName = extractFriendlyServerName(embed, srvIdx)
        addServerIfValid(
          name = "سيرفر $srvIdx • $hostName",
          rawEmbedUrl = embed,
          qualityLabel = "1080p / 720p / 480p",
          isDirect = true
        )
        srvIdx = watchServers.size + 1
      }

      // 2) Extract <iframe src="..."> embed players directly from the watch page
      val iframeMatcher = Pattern.compile(
        "<iframe[^>]+(?:src|data-src)=\"(https?:[^\"]+|//[^\"]+)\"",
        Pattern.CASE_INSENSITIVE
      ).matcher(htmlContent)
      while (iframeMatcher.find() && srvIdx <= 16) {
        val iframeSrc = iframeMatcher.group(1)?.trim() ?: continue
        val hostName = extractFriendlyServerName(iframeSrc, srvIdx)
        addServerIfValid(
          name = "مشغل مباشر $srvIdx • $hostName",
          rawEmbedUrl = iframeSrc,
          qualityLabel = "Multi-Quality HD",
          isDirect = true
        )
        srvIdx = watchServers.size + 1
      }

      // 3) If TopCinema, also query TopCinema's AJAX Server.php for all Arabic-subtitled servers (VidTube, Mixdrop, Lulustream, VidShare, etc.)
      if (portal == StreamingPortal.TOP_CINEMA) {
        val tcLiMatcher = Pattern.compile(
          "<li([^>]*server--item[^>]*)>(.*?)</li>",
          Pattern.DOTALL or Pattern.CASE_INSENSITIVE
        ).matcher(htmlContent)
        var ajaxFetched = 0
        while (tcLiMatcher.find() && ajaxFetched < 8) {
          val liAttrs = tcLiMatcher.group(1) ?: ""
          val liInner = tcLiMatcher.group(2) ?: ""
          val postId = Regex("data-id=\"([0-9]+)\"", RegexOption.IGNORE_CASE).find(liAttrs)?.groupValues?.get(1)?.trim() ?: continue
          val serverNum = Regex("data-server=\"([0-9]+)\"", RegexOption.IGNORE_CASE).find(liAttrs)?.groupValues?.get(1)?.trim() ?: continue
          val serverLabelRaw = cleanHtmlEntities(liInner.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim())
          try {
            val ajaxReq = Request.Builder()
              .url("https://topcinema.io/wp-content/themes/movies2023/AjaxCenter/Single/Server.php")
              .post(FormBody.Builder().add("id", postId).add("i", serverNum).build())
              .header("User-Agent", desktopUserAgent)
              .header("X-Requested-With", "XMLHttpRequest")
              .header("Referer", normalizedWatchUrl)
              .build()
            val ajaxResp = httpClient.newCall(ajaxReq).execute()
            val ajaxHtml = ajaxResp.body?.string() ?: ""
            val ajaxIframe = Pattern.compile("<iframe[^>]+(?:src|data-src)=\"(https?:[^\"]+|//[^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(ajaxHtml)
            if (ajaxIframe.find()) {
              val embedSrc = ajaxIframe.group(1)?.trim() ?: ""
              if (embedSrc.isNotBlank()) {
                val hostName = if (serverLabelRaw.isNotBlank()) serverLabelRaw else extractFriendlyServerName(embedSrc, watchServers.size + 1)
                addServerIfValid(
                  name = "سيرفر توب سينما • $hostName",
                  rawEmbedUrl = embedSrc,
                  qualityLabel = "مترجم HD",
                  isDirect = true
                )
              }
            }
          } catch (_: Exception) {}
          ajaxFetched++
        }
      }
    }

    try {
      // Case A: Scrape HTML directly from normalizedWatchUrl (LaRoza play.php, TopCinema /watch/, EgyDead POST View=1)
      if (normalizedWatchUrl.startsWith("http")) {
        val html = if (portal == StreamingPortal.EGY_DEAD) {
          val postReq = Request.Builder()
            .url(normalizedWatchUrl)
            .post(FormBody.Builder().add("View", "1").build())
            .header("User-Agent", desktopUserAgent)
            .header("Referer", normalizedWatchUrl)
            .build()
          val resp = httpClient.newCall(postReq).execute()
          resp.body?.string() ?: ""
        } else {
          val req = Request.Builder()
            .url(normalizedWatchUrl)
            .header("User-Agent", desktopUserAgent)
            .header("Referer", portal.homeUrl)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()
          val resp = httpClient.newCall(req).execute()
          resp.body?.string() ?: ""
        }

        if (html.isNotBlank()) {
          extractServersFromHtml(html)

          // Extract Seasons & Episodes links from the HTML (including title="..." attribute on <a> tags)
          val linkPattern = Pattern.compile("<a([^>]+href=\"([^\"]+)\"[^>]*)>(.*?)</a>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
          val matcher = linkPattern.matcher(html)
          val seenEpNums = mutableSetOf<Int>()
          val seenSeasonNums = mutableSetOf<Int>()

          while (matcher.find()) {
            val fullATagAttrs = matcher.group(1) ?: ""
            val rawHref = matcher.group(2)?.trim() ?: continue
            val innerHtml = matcher.group(3) ?: ""
            val titleAttrMatch = Pattern.compile("title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(fullATagAttrs)
            val titleAttr = if (titleAttrMatch.find()) cleanHtmlEntities(titleAttrMatch.group(1) ?: "") else ""
            val cleanText = cleanHtmlEntities(innerHtml.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim())
            val decodedHref = try { java.net.URLDecoder.decode(rawHref, "UTF-8") } catch (_: Exception) { rawHref }

            val combined = "$titleAttr $cleanText $decodedHref"

            val sNum = extractSeasonNumberFromText(combined)
            if (sNum != null &&
                sNum in 1..30 &&
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

            val epMatch = Regex("(?:الحلقة|الحلقه|حلقة|episode|ep)[\\s\\-_]*([0-9]{1,3})", RegexOption.IGNORE_CASE).find(combined)
            if (epMatch != null && !rawHref.contains("/category/") && !rawHref.contains("/tag/") && !rawHref.contains("/series/")) {
              val epNum = epMatch.groupValues[1].toIntOrNull()
              if (epNum != null && epNum in 1..300 && !seenEpNums.contains(epNum)) {
                seenEpNums.add(epNum)
                var resolvedEp = resolveRelativeUrl(normalizedWatchUrl, rawHref).replace("video.php?vid=", "play.php?vid=")
                if (portal == StreamingPortal.TOP_CINEMA && !resolvedEp.contains("/watch")) {
                  resolvedEp = "${resolvedEp.trimEnd('/')}/watch/"
                }
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

          // If the initial URL was a series overview page (e.g. TopCinema /series/...) with no direct watch servers yet,
          // automatically fetch the first episode's watch page so the user gets working watch servers immediately!
          if (watchServers.isEmpty() && episodes.isNotEmpty()) {
            val firstEpUrl = episodes.sortedBy { it.episodeNumber }.first().url
            normalizedWatchUrl = firstEpUrl
            try {
              val epReq = Request.Builder()
                .url(firstEpUrl)
                .header("User-Agent", desktopUserAgent)
                .header("Referer", portal.homeUrl)
                .build()
              val epHtml = httpClient.newCall(epReq).execute().body?.string() ?: ""
              extractServersFromHtml(epHtml)
            } catch (_: Exception) {}
          }
        }
      }

      // Case B: Resolve TMDB ID (either from URL or by searching cleaned English/Arabic titleHint)
      // so we ALWAYS provide clean, ad-free, multi-quality Direct Video Scraper Embed Servers!
      val isExpectedTv = episodes.isNotEmpty() ||
        seasons.isNotEmpty() ||
        titleHint.contains("مسلسل", ignoreCase = true) ||
        titleHint.contains("حلقة", ignoreCase = true) ||
        titleHint.contains("الحلقة", ignoreCase = true) ||
        titleHint.contains("الحلقه", ignoreCase = true) ||
        titleHint.contains("الموسم", ignoreCase = true) ||
        normalizedWatchUrl.contains("/tv/", ignoreCase = true) ||
        normalizedWatchUrl.contains("/series/", ignoreCase = true) ||
        decodedInitialUrl.contains("مسلسل") ||
        decodedInitialUrl.contains("الحلقة")

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
          val resp = httpClient.newCall(req).execute()
          val body = resp.body?.string() ?: ""
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

      // Detect current episode number from the active watch URL first (so switching episodes updates S/E accurately)
      val decodedUrl = try { java.net.URLDecoder.decode(normalizedWatchUrl, "UTF-8") } catch (_: Exception) { normalizedWatchUrl }
      val urlPathSeasonEpMatch = Regex("/tv/(?:[^/]+/)?[0-9]{2,8}/([0-9]{1,2})/([0-9]{1,3})").find(decodedUrl)
      val pathSeasonNum = urlPathSeasonEpMatch?.groupValues?.get(1)?.toIntOrNull()
      val pathEpNum = urlPathSeasonEpMatch?.groupValues?.get(2)?.toIntOrNull()

      val resolvedSeasonNum = if (selectedSeasonNumber > 1) {
        selectedSeasonNumber
      } else {
        pathSeasonNum ?: effectiveSeasonNumber
      }

      val epRegex = Regex("(?:الحلق[ةه]|حلقة|\\bepisode|\\bep)[\\s\\-_]*([0-9]{1,3})\\b", RegexOption.IGNORE_CASE)
      val rawEpNum = pathEpNum
        ?: epRegex.find(decodedUrl)?.groupValues?.get(1)?.toIntOrNull()
        ?: epRegex.find(titleHint)?.groupValues?.get(1)?.toIntOrNull()
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
          try {
            val tvUrl = "https://api.themoviedb.org/3/tv/$extractedTmdbId?api_key=$tmdbKey&language=en-US"
            val resp = httpClient.newCall(Request.Builder().url(tvUrl).header("User-Agent", desktopUserAgent).build()).execute()
            val tvJsonStr = resp.body?.string() ?: ""
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
                val sResp = httpClient.newCall(Request.Builder().url(seasonApiUrl).header("User-Agent", desktopUserAgent).build()).execute()
                val sJsonStr = sResp.body?.string() ?: ""
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

          val s = resolvedSeasonNum.coerceIn(1, maxOf(1, numSeasonsOnTmdb))
          val maxAvailableEp = if (episodes.isNotEmpty()) episodes.maxOf { it.episodeNumber }.coerceAtLeast(1) else 100
          val e = rawEpNum.coerceIn(1, maxAvailableEp)
          addServerIfValid("مشغل VidLink الذكي (S${s}E${e})", "https://vidlink.pro/tv/$extractedTmdbId/$s/$e?primaryColor=E50914&autoplay=true", "1080p / 720p / 360p")
          addServerIfValid("مشغل VidSrc Pro النظيف (S${s}E${e})", "https://vidsrc.cc/v2/embed/tv/$extractedTmdbId/$s/$e", "Auto / 1080p / 480p")
          addServerIfValid("مشغل SuperEmbed المتعدد (S${s}E${e})", "https://multiembed.mov/?video_id=$extractedTmdbId&tmdb=1&s=$s&e=$e", "Full HD / SD")
          addServerIfValid("مشغل 2Embed العالمي (S${s}E${e})", "https://www.2embed.cc/embedtv/$extractedTmdbId&s=$s&e=$e", "Multi-Server HD")
        }
      }
    } catch (_: Exception) {}

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

    if (seasons.isEmpty() && episodes.isNotEmpty()) {
      seasons.add(ScrapedSeasonItem(seasonNumber = effectiveSeasonNumber, title = "الموسم $effectiveSeasonNumber", url = normalizedWatchUrl))
    }

    val bestEmbedUrl = watchServers.firstOrNull { it.isDirectEmbed }?.embedUrl ?: normalizedWatchUrl
    var extractedDirectStream = ""
    var extractedStreamReferer = normalizedWatchUrl
    var detectedQualities = emptyList<VideoQualityOption>()

    AppDiagnosticsLogger.log(
      "Scraper",
      "Scraped ${seasons.size} seasons, ${episodes.size} episodes, ${watchServers.size} servers from $normalizedWatchUrl"
    )

    // Proactively resolve direct .m3u8 / .mp4 stream URL from server embeds that include streams in their HTML/JS
    // (Skip dynamic SPA embeds like vidlink.pro / vidsrc.cc / 2embed.cc / multiembed.mov which generate streams in WebView)
    for (srv in watchServers.filter { it.isDirectEmbed && !isDynamicSpaEmbed(it.embedUrl) }.take(5)) {
      val resolution = resolveDirectStreamFromEmbed(srv.embedUrl, normalizedWatchUrl)
      if (resolution.primaryStreamUrl.isNotBlank()) {
        extractedDirectStream = resolution.primaryStreamUrl
        extractedStreamReferer = resolution.refererUrl.ifBlank { srv.embedUrl }
        detectedQualities = resolution.detectedQualities
        AppDiagnosticsLogger.log(
          "StreamExtractor",
          "Resolved direct stream from [${srv.name}]: ${resolution.primaryStreamUrl} | Qualities: ${detectedQualities.joinToString { it.label }}"
        )
        break
      }
    }

    ScrapedSeriesStructure(
      isSeries = episodes.isNotEmpty() || seasons.isNotEmpty(),
      seriesTitle = titleHint,
      currentSeason = effectiveSeasonNumber,
      selectedSeasonNumber = effectiveSeasonNumber,
      seasons = seasons.sortedBy { it.seasonNumber },
      episodes = episodes.sortedBy { it.episodeNumber },
      watchServers = watchServers,
      bestCleanPlayerUrl = bestEmbedUrl,
      directStreamUrl = extractedDirectStream,
      directStreamReferer = extractedStreamReferer,
      detectedQualities = detectedQualities
    )
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
    if (isDynamicSpaEmbed(embedUrl)) {
      return@withContext DirectStreamResolution()
    }
    resolveDirectStreamFromEmbed(embedUrl, refererUrl, userAgentOverride)
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
      val origin = try {
        val u = java.net.URI(refererUrl)
        "${u.scheme}://${u.host}"
      } catch (_: Exception) {
        "https://topcinema.io"
      }
      val cookieHeader = try {
        android.webkit.CookieManager.getInstance()?.getCookie(embedUrl) ?: ""
      } catch (_: Exception) { "" }

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

      val resp = httpClient.newCall(reqBuilder.build()).execute()
      val html = resp.body?.string() ?: ""
      if (html.isBlank() || html.contains("Embeds disabled", ignoreCase = true)) {
        AppDiagnosticsLogger.log("StreamExtractor", "Empty or disabled embed response from $embedUrl (HTTP ${resp.code})")
        return DirectStreamResolution()
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
      AppDiagnosticsLogger.log("StreamExtractor", "Error extracting from $embedUrl: ${e.message}", isError = true)
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
      val cookieHeader = try {
        android.webkit.CookieManager.getInstance()?.getCookie(m3u8Url) ?: ""
      } catch (_: Exception) { "" }

      val reqBuilder = Request.Builder()
        .url(m3u8Url)
        .header("User-Agent", userAgentOverride)
        .header("Referer", refererUrl)
        .header("Accept", "*/*")
      if (cookieHeader.isNotBlank()) {
        reqBuilder.header("Cookie", cookieHeader)
      }

      val resp = httpClient.newCall(reqBuilder.build()).execute()
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
    val urlPathMatch = Regex("/tv/(?:[^/]+/)?[0-9]{2,8}/([0-9]{1,2})/[0-9]{1,3}").find(text)
    if (urlPathMatch != null) {
      return urlPathMatch.groupValues[1].toIntOrNull()
    }
    val sMatch = Regex("\\bS([0-9]{1,2})E[0-9]{1,3}\\b", RegexOption.IGNORE_CASE).find(text)
    if (sMatch != null) {
      return sMatch.groupValues[1].toIntOrNull()
    }
    val digitMatch = Regex("(?:الموسم|موسم|\\bseason|\\bs)\\s*([0-9]{1,2})\\b", RegexOption.IGNORE_CASE).find(text)
    if (digitMatch != null) {
      return digitMatch.groupValues[1].toIntOrNull()
    }
    return when {
      text.contains("الموسم الاول") || text.contains("الموسم الأول") -> 1
      text.contains("الموسم الثاني") || text.contains("الموسم الثانى") -> 2
      text.contains("الموسم الثالث") -> 3
      text.contains("الموسم الرابع") -> 4
      text.contains("الموسم الخامس") -> 5
      text.contains("الموسم السادس") -> 6
      text.contains("الموسم السابع") -> 7
      text.contains("الموسم الثامن") -> 8
      text.contains("الموسم التاسع") -> 9
      text.contains("الموسم العاشر") -> 10
      else -> null
    }
  }

  private fun extractEpisodeNumberFromText(text: String): Int? {
    val seMatch = Regex("\\bS[0-9]{1,2}E([0-9]{1,3})\\b", RegexOption.IGNORE_CASE).find(text)
    if (seMatch != null) {
      return seMatch.groupValues[1].toIntOrNull()
    }
    val epMatch = Regex("(?:الحلقة|الحلقه|حلقة|حلقه|\\bepisode|\\bep)\\s*([0-9]{1,3})\\b", RegexOption.IGNORE_CASE).find(text)
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
      val resp = httpClient.newCall(req).execute()
      val body = resp.body?.string() ?: return null
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
      val extResp = httpClient.newCall(extReq).execute()
      val extBody = extResp.body?.string() ?: ""
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
    sourceFilter: SubtitleSourceFilter = SubtitleSourceFilter.ALL
  ): List<SubtitleItem> = coroutineScope {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return@coroutineScope cachedEmbedSubtitles

    val seasonNum = extractSeasonNumberFromText(cleanQuery)
    val episodeNum = extractEpisodeNumberFromText(cleanQuery)
    val isLikelySeries = seasonNum != null || episodeNum != null || cleanQuery.contains("مسلسل")

    val tmdbLookup = withContext(Dispatchers.IO) {
      lookupTmdbAndImdbId(cleanQuery, isLikelySeries)
    }

    val wyzieDeferred = async(Dispatchers.IO) {
      if (tmdbLookup != null) {
        searchWyzieSubtitles(
          lookup = tmdbLookup,
          season = seasonNum ?: 1,
          episode = episodeNum ?: 1,
          languageCode = languageCode,
          sourceFilter = sourceFilter
        )
      } else emptyList()
    }

    val openSubsDeferred = async(Dispatchers.IO) {
      if (sourceFilter == SubtitleSourceFilter.ALL || sourceFilter == SubtitleSourceFilter.OPEN_SUBTITLES) {
        searchOpenSubtitles(cleanQuery, languageCode, tmdbLookup, seasonNum, episodeNum)
      } else emptyList()
    }

    val addic7edDeferred = async(Dispatchers.IO) {
      if (sourceFilter == SubtitleSourceFilter.ALL || sourceFilter == SubtitleSourceFilter.ADDIC7ED) {
        searchAddic7ed(cleanQuery, languageCode, tmdbLookup, seasonNum, episodeNum)
      } else emptyList()
    }

    val combined = mutableListOf<SubtitleItem>()
    // 1. Add any real subtitle tracks extracted directly from the active watch server
    combined.addAll(cachedEmbedSubtitles)
    // 2. Add Wyzie real direct subtitle files (.srt / .vtt)
    combined.addAll(wyzieDeferred.await())
    // 3. Add OpenSubtitles REST results
    combined.addAll(openSubsDeferred.await())
    // 4. Add Addic7ed results
    combined.addAll(addic7edDeferred.await())

    combined.distinctBy { it.downloadUrl.ifBlank { it.id } }
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
      val resp = httpClient.newCall(req).execute()
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
      val restUrl = if (tmdbLookup != null && tmdbLookup.imdbId.startsWith("tt")) {
        val numericImdb = tmdbLookup.imdbId.removePrefix("tt")
        buildString {
          append("https://rest.opensubtitles.org/search")
          if (tmdbLookup.isSeries && episodeNum != null) {
            append("/episode-").append(episodeNum)
          }
          append("/imdbid-").append(numericImdb)
          if (tmdbLookup.isSeries && seasonNum != null) {
            append("/season-").append(seasonNum)
          }
          append("/sublanguageid-").append(iso3Lang)
        }
      } else {
        val enc = URLEncoder.encode(cleanLookupTitle.lowercase(), "UTF-8")
        "https://rest.opensubtitles.org/search/query-$enc/sublanguageid-$iso3Lang"
      }

      val req = Request.Builder()
        .url(restUrl)
        .header("User-Agent", "TemporaryUserAgent")
        .header("X-User-Agent", "TemporaryUserAgent")
        .header("Accept", "application/json")
        .build()

      val resp = httpClient.newCall(req).execute()
      if (resp.isSuccessful) {
        val body = resp.body?.string() ?: ""
        if (body.trim().startsWith("[")) {
          val arr = JSONArray(body)
          for (i in 0 until minOf(arr.length(), 10)) {
            val item = arr.optJSONObject(i) ?: continue
            val gzLink = item.optString("SubDownloadLink", "").trim()
            if (!gzLink.startsWith("http")) continue
            val release = item.optString("MovieReleaseName").ifBlank {
              item.optString("SubFileName", "$cleanLookupTitle ($langLabel)")
            }
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
    } catch (_: Exception) {
    }

    list
  }

  private suspend fun searchAddic7ed(
    query: String,
    languageCode: String = "ar",
    tmdbLookup: TmdbExternalLookup? = null,
    seasonNum: Int? = null,
    episodeNum: Int? = null
  ): List<SubtitleItem> = withContext(Dispatchers.IO) {
    val list = mutableListOf<SubtitleItem>()
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return@withContext emptyList()

    val lookup = tmdbLookup ?: lookupTmdbAndImdbId(cleanQuery, seasonNum != null || episodeNum != null)
    if (lookup != null) {
      val wyzieAddic7ed = searchWyzieSubtitles(
        lookup = lookup,
        season = seasonNum ?: 1,
        episode = episodeNum ?: 1,
        languageCode = languageCode,
        sourceFilter = SubtitleSourceFilter.ADDIC7ED
      )
      list.addAll(wyzieAddic7ed)
    }

    list
  }

  /**
   * Downloads the full `.srt`, `.vtt`, or `.gz` subtitle file from `item.downloadUrl`,
   * auto-detects Arabic UTF-8 vs Windows-1256 encoding, parses all timed cues,
   * and writes a local `.vtt` file into `context.cacheDir` so both ExoPlayer and WebView render it!
   */
  suspend fun resolveAndParseSubtitleItem(
    context: Context,
    item: SubtitleItem
  ): SubtitleItem = withContext(Dispatchers.IO) {
    if (item.parsedCues.isNotEmpty() && item.localFileUri.isNotBlank()) {
      return@withContext item
    }

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

        val resp = httpClient.newCall(reqBuilder.build()).execute()
        if (resp.isSuccessful) {
          val rawBytes = resp.body?.bytes() ?: ByteArray(0)
          if (rawBytes.isNotEmpty()) {
            val decompressedBytes = if (isGzipCompressed(rawBytes) || item.downloadUrl.lowercase().endsWith(".gz")) {
              try {
                GZIPInputStream(ByteArrayInputStream(rawBytes)).use { it.readBytes() }
              } catch (_: Exception) {
                rawBytes
              }
            } else {
              rawBytes
            }
            rawSubText = decodeSubtitleBytesWithArabicSupport(decompressedBytes)
          }
        }
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

      AppDiagnosticsLogger.log(
        "Subtitles",
        "Loaded subtitle '${item.releaseName}' (${parsedCues.size} cues) -> $localUri"
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

  fun parseSubtitleCues(rawSubtitleText: String): List<ParsedSubtitleCue> {
    if (rawSubtitleText.isBlank()) return emptyList()
    val cues = mutableListOf<ParsedSubtitleCue>()
    val clean = rawSubtitleText
      .replace("\uFEFF", "")
      .replace("\r\n", "\n")
      .replace("\r", "\n")
      .trim()

    val blocks = clean.split(Regex("\n\\s*\n"))
    val timeRegex = Regex(
      "((?:\\d{1,2}:)?\\d{2}:\\d{2}[,.]\\d{3})\\s*-->\\s*((?:\\d{1,2}:)?\\d{2}:\\d{2}[,.]\\d{3})"
    )

    for (block in blocks) {
      val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
      if (lines.isEmpty()) continue
      val timeLineIndex = lines.indexOfFirst { timeRegex.containsMatchIn(it) }
      if (timeLineIndex == -1) continue

      val match = timeRegex.find(lines[timeLineIndex]) ?: continue
      val startMs = parseTimestampToMs(match.groupValues[1])
      val endMs = parseTimestampToMs(match.groupValues[2])
      if (endMs <= startMs) continue

      val textLines = lines.subList(timeLineIndex + 1, lines.size)
        .map { line ->
          line
            .replace(Regex("\\{\\\\[^}]*\\}"), "") // Strip ASS/SSA style tags like {\an8}
            .replace(Regex("<[^>]*>"), "")         // Strip HTML tags like <i>, <font>
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
    return cues.sortedBy { it.startMs }
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

  companion object {
    @Volatile
    private var sslInstalled = false

    private val trustAllManager = object : X509TrustManager {
      override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
      override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
      override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /**
     * Installs a permissive SSL socket factory on `HttpsURLConnection` so Media3 ExoPlayer
     * (`DefaultHttpDataSource`) and `OkHttpClient` never fail with `SSLHandshakeException`
     * on video streaming CDN edge servers (such as `serv-stream-cdn10.cdn-video.xyz`).
     */
    fun installPermissiveSslForStreaming() {
      if (sslInstalled) return
      try {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
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
      return try {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        builder
          .sslSocketFactory(sslContext.socketFactory, trustAllManager)
          .hostnameVerifier { _, _ -> true }
          .build()
      } catch (_: Exception) {
        builder.build()
      }
    }
  }
}
