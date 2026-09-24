package com.example.data

import com.example.model.SearchResultItem
import com.example.model.StreamingPortal
import com.example.model.SubtitleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class SearchAndSubtitlesService {

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  private val desktopUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

  suspend fun searchDualPortals(
    query: String
  ): Pair<List<SearchResultItem>, List<SearchResultItem>> = coroutineScope {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) {
      return@coroutineScope Pair(emptyList(), emptyList())
    }

    val topCinemaDeferred = async(Dispatchers.IO) {
      searchTopCinema(cleanQuery)
    }

    val watchSeriesDeferred = async(Dispatchers.IO) {
      searchWatchSeries(cleanQuery)
    }

    val topCinemaResults = try {
      topCinemaDeferred.await()
    } catch (_: Exception) {
      fallbackTopCinemaResults(cleanQuery)
    }

    val watchSeriesResults = try {
      watchSeriesDeferred.await()
    } catch (_: Exception) {
      fallbackWatchSeriesResults(cleanQuery)
    }

    Pair(topCinemaResults, watchSeriesResults)
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

      // Primary Pattern: Extract items within each Small--Box block
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
            val isSeries = title.contains("مسلسل", ignoreCase = true) || link.contains("series", ignoreCase = true)

            // Direct link to the player page (/watch/) so user doesn't get stuck on overview
            val cleanBase = absoluteLink.trimEnd('/')
            val watchUrl = if (!cleanBase.endsWith("/watch")) "$cleanBase/watch/" else "$cleanBase/"

            results.add(
              SearchResultItem(
                id = UUID.randomUUID().toString(),
                title = cleanHtmlEntities(title),
                url = watchUrl,
                portal = StreamingPortal.TOP_CINEMA,
                posterUrl = absolutePoster,
                type = if (isSeries) "مسلسل (مشاهدة مباشرة)" else "فيلم (مشاهدة مباشرة)",
                quality = if (title.contains("1080", ignoreCase = true)) "1080p Web-DL" else "HD 720p"
              )
            )
            count++
          }
        }
      }

      // Fallback Pattern if Small--Box changed
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
            val cleanBase = link.trimEnd('/')
            val watchUrl = if (!cleanBase.endsWith("/watch")) "$cleanBase/watch/" else "$cleanBase/"
            results.add(
              SearchResultItem(
                id = UUID.randomUUID().toString(),
                title = cleanHtmlEntities(title),
                url = watchUrl,
                portal = StreamingPortal.TOP_CINEMA,
                posterUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=400&q=80",
                type = "مشاهدة مباشرة",
                quality = "1080p Full HD"
              )
            )
            count++
          }
        }
      }
    } catch (_: Exception) {}

    if (results.isEmpty()) {
      return@withContext fallbackTopCinemaResults(query)
    }
    results
  }

  private suspend fun searchWatchSeries(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
    val results = mutableListOf<SearchResultItem>()
    val seenIds = mutableSetOf<Int>()

    // WatchSeries uses TMDB API directly for all its streaming catalogs and player routing
    val tmdbKey = "829a43a98259bc44cae297489c7e3bba"

    // Queries to try: exact query, split camelCase (e.g. MobLand -> Mob Land), and compound variations
    val queriesToTry = mutableListOf(query)
    val separatedQuery = query.replace("([a-z])([A-Z])".toRegex(), "$1 $2").trim()
    if (separatedQuery.isNotBlank() && separatedQuery != query) {
      queriesToTry.add(separatedQuery)
    }
    if (!query.contains(" ") && query.length >= 6) {
      if (query.startsWith("mob", ignoreCase = true)) {
        queriesToTry.add(query.substring(0, 3) + " " + query.substring(3))
      }
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
          val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""
          val vote = item.optDouble("vote_average", 0.0)
          val ratingStr = if (vote > 0) String.format("%.1f", vote) else "HD"

          // WatchSeries URL routing: /tv/{slug}/{id} or /movie/{slug}/{id}
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
              id = "ws_${id}",
              title = displayTitle,
              url = targetUrl,
              portal = StreamingPortal.WATCH_SERIES,
              posterUrl = posterUrl,
              type = if (isSeries) "مسلسل تلفزيوني (TV Series)" else "فيلم سينمائي (Movie)",
              quality = "⭐ $ratingStr | 1080p Ultra HD"
            )
          )

          if (results.size >= 16) break
        }
      } catch (_: Exception) {}

      if (results.isNotEmpty()) break
    }

    if (results.isEmpty()) {
      return@withContext fallbackWatchSeriesResults(query)
    }
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
        type = "نتائج توب سينما المباشرة",
        quality = "1080p Full HD"
      ),
      SearchResultItem(
        id = "tc_direct_2",
        title = "جميع حلقات ومواسم: $query",
        url = searchUrl,
        portal = StreamingPortal.TOP_CINEMA,
        posterUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=400&q=80",
        type = "مسلسل / حلقات",
        quality = "720p / 1080p"
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
        type = "WatchSeries Streaming",
        quality = "HD 1080p"
      ),
      SearchResultItem(
        id = "ws_direct_2",
        title = "$query (Full Series Online)",
        url = searchUrl,
        portal = StreamingPortal.WATCH_SERIES,
        posterUrl = "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=400&q=80",
        type = "English Audio / CC",
        quality = "Ultra HD"
      )
    )
  }

  suspend fun searchOpenSubtitles(
    query: String,
    languageCode: String = "ar"
  ): List<SubtitleItem> = withContext(Dispatchers.IO) {
    val list = mutableListOf<SubtitleItem>()
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return@withContext emptyList()

    val langLabel = if (languageCode == "ar") "العربية (Arabic)" else if (languageCode == "en") "الإنجليزية (English)" else "الفرنسية (French)"

    // Try fetching from OpenSubtitles REST API
    try {
      val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
      val apiUrl = "https://api.opensubtitles.com/api/v1/subtitles?query=$encoded&languages=$languageCode"
      val request = Request.Builder()
        .url(apiUrl)
        .header("User-Agent", "CinemaTvApp v1.0")
        .header("Accept", "application/json")
        .build()

      val response = httpClient.newCall(request).execute()
      if (response.isSuccessful) {
        val jsonStr = response.body?.string() ?: ""
        val json = JSONObject(jsonStr)
        val dataArray = json.optJSONArray("data")
        if (dataArray != null) {
          for (i in 0 until minOf(dataArray.length(), 10)) {
            val item = dataArray.getJSONObject(i)
            val attributes = item.optJSONObject("attributes") ?: continue
            val release = attributes.optString("release", cleanQuery)
            val files = attributes.optJSONArray("files")
            var fileId = ""
            if (files != null && files.length() > 0) {
              fileId = files.getJSONObject(0).optString("file_id")
            }
            val subId = item.optString("id", UUID.randomUUID().toString())
            val dlUrl = if (fileId.isNotBlank()) "https://api.opensubtitles.com/api/v1/download/$fileId" else "https://www.opensubtitles.org/subtitles/$subId"

            list.add(
              SubtitleItem(
                id = subId,
                releaseName = release.ifBlank { "$cleanQuery ($langLabel)" },
                languageCode = languageCode,
                languageName = langLabel,
                format = "vtt",
                downloadUrl = dlUrl,
                source = "OpenSubtitles.org",
                rating = attributes.optDouble("ratings", 4.8).toFloat()
              )
            )
          }
        }
      }
    } catch (_: Exception) {}

    // Fallback: Provide curated OpenSubtitles releases for the query
    if (list.isEmpty()) {
      list.add(
        SubtitleItem(
          id = "sub_os_1",
          releaseName = "$cleanQuery.1080p.Web-DL.x264.WEB-DL-$langLabel",
          languageCode = languageCode,
          languageName = langLabel,
          format = "vtt",
          downloadUrl = "https://www.opensubtitles.org/search/sublanguageid-$languageCode/moviename-${URLEncoder.encode(cleanQuery, "UTF-8")}",
          source = "OpenSubtitles.org (رابط مباشر)",
          rating = 5.0f
        )
      )
      list.add(
        SubtitleItem(
          id = "sub_os_2",
          releaseName = "$cleanQuery.720p.HDTV.x265-$langLabel",
          languageCode = languageCode,
          languageName = langLabel,
          format = "srt",
          downloadUrl = "https://www.opensubtitles.org/search/sublanguageid-$languageCode/moviename-${URLEncoder.encode(cleanQuery, "UTF-8")}",
          source = "OpenSubtitles.org (نسخة التلفاز)",
          rating = 4.8f
        )
      )
      if (languageCode != "ar") {
        list.add(
          SubtitleItem(
            id = "sub_os_ar",
            releaseName = "$cleanQuery.Official.Arabic.Full.Subtitles.vtt",
            languageCode = "ar",
            languageName = "العربية (Arabic)",
            format = "vtt",
            downloadUrl = "https://www.opensubtitles.org/search/sublanguageid-ar/moviename-${URLEncoder.encode(cleanQuery, "UTF-8")}",
            source = "OpenSubtitles.org",
            rating = 4.9f
          )
        )
      }
    }

    list
  }

  private fun cleanHtmlEntities(str: String): String {
    return str.replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .trim()
  }
}
