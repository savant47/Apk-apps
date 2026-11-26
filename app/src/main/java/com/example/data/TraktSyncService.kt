package com.example.data

import com.example.model.StreamingPortal
import com.example.model.UpNextItem
import com.example.ui.TraktUrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class TraktSyncResult {
  data class Success(
    val items: List<UpNextItem>,
    val username: String,
    val csrfToken: String
  ) : TraktSyncResult()

  data object NotLoggedIn : TraktSyncResult()
  data class Error(val message: String) : TraktSyncResult()
}

data class TraktDomExtractionPayload(
  val username: String,
  val csrfToken: String,
  val pageUrl: String,
  val items: List<UpNextItem>
)

class TraktSyncService {

  private val client: OkHttpClient by lazy {
    SearchAndSubtitlesService.buildSslTolerantHttpClient()
      .newBuilder()
      .followRedirects(true)
      .followSslRedirects(true)
      .connectTimeout(12, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .writeTimeout(12, TimeUnit.SECONDS)
      .build()
  }

  private val desktopUa =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

  /**
   * Parses the JSON string emitted by the Trakt WebView DOM extractor bridge.
   */
  fun parseDomExtractionJson(rawJson: String): TraktDomExtractionPayload {
    return try {
      val root = JSONObject(rawJson)
      val username = root.optString("username", "").trim()
      val csrfToken = root.optString("csrfToken", "").trim()
      val pageUrl = root.optString("pageUrl", "").trim()
      val arr = root.optJSONArray("items") ?: JSONArray()
      val parsed = mutableListOf<UpNextItem>()
      val seenSlugs = mutableSetOf<String>()

      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val slug = obj.optString("slug", "").trim().lowercase()
        val showTitle = obj.optString("showTitle", "").trim()
          .ifBlank { if (slug.isNotBlank()) TraktUrlParser.slugToTitle(slug) else "" }
        val season = obj.optInt("season", 0)
        val episode = obj.optInt("episode", 0)
        if (showTitle.isBlank() || season <= 0 || episode <= 0) continue

        val dedupKey = slug.ifBlank {
          TvPreferencesRepository.buildNormalizedContentKey(showTitle, null, null, isSeries = false)
        }
        if (dedupKey.isNotBlank() && !seenSlugs.add(dedupKey)) continue

        val epTitle = cleanEpisodeTitle(obj.optString("episodeTitle", ""), season, episode)
        val poster = normalizePosterUrl(obj.optString("posterUrl", ""))
        val watched = obj.optInt("watchedCount", 0).coerceAtLeast(0)
        val total = obj.optInt("totalCount", 0).coerceAtLeast(0)
        val epUrl = obj.optString("episodeUrl", "").trim().ifBlank {
          if (slug.isNotBlank()) "https://trakt.tv/shows/$slug/seasons/$season/episodes/$episode" else ""
        }
        val epId = obj.optString("episodeId", "").trim()

        parsed.add(
          UpNextItem(
            id = "trakt_${dedupKey.ifBlank { UUID.randomUUID().toString() }}",
            seriesKey = TvPreferencesRepository.buildNormalizedContentKey(showTitle, null, null, isSeries = false),
            showTitle = showTitle,
            seasonNumber = season,
            episodeNumber = episode,
            episodeTitle = epTitle,
            posterUrl = poster,
            watchedCount = watched,
            totalCount = total,
            traktSlug = slug,
            traktEpisodeUrl = epUrl,
            traktEpisodeId = epId,
            fromTrakt = true,
            preferredPortal = StreamingPortal.WATCH_SERIES,
            updatedAtMs = System.currentTimeMillis() - (i * 10L)
          )
        )
      }

      TraktDomExtractionPayload(
        username = username,
        csrfToken = csrfToken,
        pageUrl = pageUrl,
        items = parsed
      )
    } catch (e: Exception) {
      AppDiagnosticsLogger.log("TraktSync", "DOM JSON parse error: ${e.message}", isError = true)
      TraktDomExtractionPayload("", "", "", emptyList())
    }
  }

  /**
   * Fetches the user's real Trakt Progress ("Up Next to Watch") page using their saved WebView session cookie.
   * Runs purely over OkHttp + Jsoup (Zero WebView RAM overhead on Android 9 TV!).
   */
  suspend fun fetchProgressViaCookie(
    cookieHeader: String,
    usernameHint: String = ""
  ): TraktSyncResult = withContext(Dispatchers.IO) {
    if (cookieHeader.isBlank()) {
      return@withContext TraktSyncResult.NotLoggedIn
    }
    try {
      val targetUrls = buildList {
        if (usernameHint.isNotBlank()) {
          add("https://trakt.tv/users/${usernameHint.trim()}/progress")
        }
        add("https://trakt.tv/users/me/progress")
        add("https://trakt.tv/dashboard")
      }

      var lastError = ""
      for (targetUrl in targetUrls) {
        val req = Request.Builder()
          .url(targetUrl)
          .header("Cookie", cookieHeader)
          .header("User-Agent", desktopUa)
          .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
          .header("Accept-Language", "en-US,en;q=0.9")
          .header("Referer", "https://trakt.tv/dashboard")
          .build()

        client.newCall(req).execute().use { resp ->
          val finalUrl = resp.request.url.toString()
          if (finalUrl.contains("/auth/signin", ignoreCase = true) ||
            finalUrl.contains("/login", ignoreCase = true) ||
            resp.code == 401 || resp.code == 403
          ) {
            return@withContext TraktSyncResult.NotLoggedIn
          }
          if (!resp.isSuccessful) {
            lastError = "HTTP ${resp.code}"
            return@use
          }
          val html = resp.body?.string().orEmpty()
          if (html.isBlank()) return@use

          val doc = Jsoup.parse(html, finalUrl)
          val csrfToken = doc.selectFirst("meta[name=csrf-token]")?.attr("content")?.trim().orEmpty()
          val username = extractUsernameFromDoc(doc, finalUrl).ifBlank { usernameHint }
          val items = parseProgressFromDocument(doc)
          if (items.isNotEmpty() || finalUrl.contains("/progress", ignoreCase = true)) {
            AppDiagnosticsLogger.log(
              "TraktSync",
              "Synced ${items.size} shows from $finalUrl (user=$username)"
            )
            return@withContext TraktSyncResult.Success(
              items = items,
              username = username,
              csrfToken = csrfToken
            )
          }
        }
      }
      TraktSyncResult.Error(lastError.ifBlank { "لم يتم العثور على حلقات في صفحة Progress" })
    } catch (e: Exception) {
      AppDiagnosticsLogger.log("TraktSync", "Cookie sync failed: ${e.message}", isError = true)
      TraktSyncResult.Error(e.message ?: "خطأ في الاتصال بـ Trakt")
    }
  }

  /**
   * Marks an episode as Watched directly on the user's Trakt account using their session cookie + CSRF token.
   */
  suspend fun markEpisodeWatchedOnTrakt(
    item: UpNextItem,
    cookieHeader: String,
    csrfTokenHint: String = "",
    usernameHint: String = ""
  ): Boolean = withContext(Dispatchers.IO) {
    if (cookieHeader.isBlank()) return@withContext false
    val slug = item.traktSlug.ifBlank {
      TraktUrlParser.parse(item.traktEpisodeUrl)?.slug.orEmpty()
    }.ifBlank {
      item.showTitle.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    }
    if (slug.isBlank()) return@withContext false

    val epUrl = item.traktEpisodeUrl.ifBlank {
      "https://trakt.tv/shows/$slug/seasons/${item.seasonNumber}/episodes/${item.episodeNumber}"
    }

    try {
      // 1. Fetch fresh CSRF token & real watch action path/episode ID from the episode page
      var csrfToken = csrfTokenHint
      var episodeId = item.traktEpisodeId
      var discoveredWatchPath = ""

      val pageReq = Request.Builder()
        .url(epUrl)
        .header("Cookie", cookieHeader)
        .header("User-Agent", desktopUa)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .build()

      client.newCall(pageReq).execute().use { resp ->
        val finalUrl = resp.request.url.toString()
        if (finalUrl.contains("/auth/signin", ignoreCase = true) || resp.code == 401) {
          return@withContext false
        }
        val html = resp.body?.string().orEmpty()
        if (html.isNotBlank()) {
          val doc = Jsoup.parse(html, finalUrl)
          val metaCsrf = doc.selectFirst("meta[name=csrf-token]")?.attr("content")?.trim().orEmpty()
          if (metaCsrf.isNotBlank()) csrfToken = metaCsrf

          val idEl = doc.selectFirst("[data-episode-id], [data-type=episode][data-id], .watch[data-id]")
          val foundId = idEl?.attr("data-episode-id")?.ifBlank { idEl.attr("data-id") }.orEmpty().trim()
          if (foundId.isNotBlank()) episodeId = foundId

          val watchBtn = doc.selectFirst("a.watch[href], form[action*='/watch']")
          val rawAction = watchBtn?.attr("href")?.ifBlank { watchBtn.attr("action") }.orEmpty().trim()
          if (rawAction.isNotBlank() && rawAction != "#") {
            discoveredWatchPath = if (rawAction.startsWith("http")) rawAction else "https://trakt.tv$rawAction"
          }
        }
      }

      if (csrfToken.isBlank() && usernameHint.isNotBlank()) {
        val progReq = Request.Builder()
          .url("https://trakt.tv/users/$usernameHint/progress")
          .header("Cookie", cookieHeader)
          .header("User-Agent", desktopUa)
          .build()
        client.newCall(progReq).execute().use { r ->
          val html = r.body?.string().orEmpty()
          if (html.isNotBlank()) {
            csrfToken = Jsoup.parse(html).selectFirst("meta[name=csrf-token]")?.attr("content")?.trim().orEmpty()
          }
        }
      }

      val candidateEndpoints = buildList {
        if (discoveredWatchPath.isNotBlank()) add(discoveredWatchPath)
        add("https://trakt.tv/shows/$slug/seasons/${item.seasonNumber}/episodes/${item.episodeNumber}/watch")
        if (episodeId.isNotBlank()) {
          add("https://trakt.tv/episodes/$episodeId/watch")
        }
      }.distinct()

      val jsonBody = """{"watched_at":"now"}""".toRequestBody("application/json; charset=utf-8".toMediaType())

      for (endpoint in candidateEndpoints) {
        val postReq = Request.Builder()
          .url(endpoint)
          .post(jsonBody)
          .header("Cookie", cookieHeader)
          .header("User-Agent", desktopUa)
          .header("Accept", "application/json, text/javascript, */*; q=0.01")
          .header("X-Requested-With", "XMLHttpRequest")
          .header("Origin", "https://trakt.tv")
          .header("Referer", epUrl)
          .apply {
            if (csrfToken.isNotBlank()) {
              header("X-CSRF-Token", csrfToken)
            }
          }
          .build()

        client.newCall(postReq).execute().use { resp ->
          AppDiagnosticsLogger.log(
            "TraktSync",
            "POST $endpoint -> HTTP ${resp.code}"
          )
          if (resp.isSuccessful || resp.code == 201 || resp.code == 204) {
            return@withContext true
          }
        }
      }
      false
    } catch (e: Exception) {
      AppDiagnosticsLogger.log("TraktSync", "markWatched error: ${e.message}", isError = true)
      false
    }
  }

  private fun extractUsernameFromDoc(doc: Document, finalUrl: String): String {
    val urlMatch = Regex("""/users/([^/?#]+)/""", RegexOption.IGNORE_CASE).find(finalUrl)
    val fromUrl = urlMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
    if (fromUrl.isNotBlank() && !fromUrl.equals("me", ignoreCase = true)) {
      return fromUrl
    }
    val userLinks = doc.select("a[href^='/users/']")
    for (link in userLinks) {
      val href = link.attr("href")
      val m = Regex("""^/users/([^/?#]+)""").find(href)
      val candidate = m?.groupValues?.getOrNull(1)?.trim().orEmpty()
      if (candidate.isNotBlank() && !candidate.equals("me", ignoreCase = true) && !candidate.equals("settings", ignoreCase = true)) {
        return candidate
      }
    }
    return ""
  }

  private fun parseProgressFromDocument(doc: Document): List<UpNextItem> {
    val results = mutableListOf<UpNextItem>()
    val seenSlugs = mutableSetOf<String>()

    // Strategy 1: Standard Trakt Progress & Dashboard Up Next cards
    val epLinks = doc.select("a[href*='/shows/'][href*='/seasons/'][href*='/episodes/']")
    for ((index, epLink) in epLinks.withIndex()) {
      val href = epLink.absUrl("href").ifBlank { "https://trakt.tv" + epLink.attr("href") }
      val parsed = TraktUrlParser.parse(href) ?: continue
      val season = parsed.seasonNumber ?: continue
      val episode = parsed.episodeNumber ?: continue
      val slug = parsed.slug.lowercase()
      if (slug.isBlank() || !seenSlugs.add(slug)) continue

      val card = findEnclosingCard(epLink)

      val showTitle = extractShowTitleFromCard(card, slug)
        .ifBlank { parsed.showTitle }
      val rawEpTitle = extractEpisodeTitleFromCard(card, epLink)
      val epTitle = cleanEpisodeTitle(rawEpTitle, season, episode)
      val posterUrl = extractPosterFromCard(card)
      val (watched, total) = extractProgressCountsFromCard(card)
      val episodeId = card?.selectFirst("[data-episode-id]")?.attr("data-episode-id")
        ?.ifBlank { card.attr("data-episode-id") }
        .orEmpty()

      results.add(
        UpNextItem(
          id = "trakt_$slug",
          seriesKey = TvPreferencesRepository.buildNormalizedContentKey(showTitle, null, null, isSeries = false),
          showTitle = showTitle,
          seasonNumber = season,
          episodeNumber = episode,
          episodeTitle = epTitle,
          posterUrl = posterUrl,
          watchedCount = watched,
          totalCount = total,
          traktSlug = slug,
          traktEpisodeUrl = href,
          traktEpisodeId = episodeId,
          fromTrakt = true,
          preferredPortal = StreamingPortal.WATCH_SERIES,
          updatedAtMs = System.currentTimeMillis() - (index * 10L)
        )
      )
    }

    return results
  }

  private fun findEnclosingCard(el: Element): Element? {
    var curr: Element? = el.parent()
    var best: Element? = el.parent()
    var steps = 0
    while (curr != null && steps < 6) {
      val cls = curr.className().lowercase()
      val tag = curr.tagName().lowercase()
      if (cls.contains("grid-item") ||
        cls.contains("progress-wrapper") ||
        cls.contains("fanart") ||
        cls.contains("up-next") ||
        curr.hasAttr("data-show-id") ||
        curr.hasAttr("data-episode-id") ||
        tag == "article" ||
        tag == "li"
      ) {
        return curr
      }
      if (curr.select("img").isNotEmpty()) {
        best = curr
      }
      curr = curr.parent()
      steps++
    }
    return best
  }

  private fun extractShowTitleFromCard(card: Element?, slug: String): String {
    if (card == null) return ""
    val selectors = listOf(
      "h3 a",
      "h3",
      ".titles h3",
      "a[href='/shows/$slug']",
      "a[href$='/shows/$slug']",
      ".show-title"
    )
    for (sel in selectors) {
      val text = card.selectFirst(sel)?.text()?.trim().orEmpty()
      if (text.isNotBlank() && !text.matches(Regex("""^\d+x\d+.*""", RegexOption.IGNORE_CASE))) {
        return text
      }
    }
    val imgAlt = card.selectFirst("img[alt]")?.attr("alt")?.trim().orEmpty()
    if (imgAlt.isNotBlank() && imgAlt.length < 80) return imgAlt
    return ""
  }

  private fun extractEpisodeTitleFromCard(card: Element?, epLink: Element): String {
    val linkText = epLink.text().trim()
    if (linkText.isNotBlank() && !linkText.equals("watch", ignoreCase = true)) {
      return linkText
    }
    if (card == null) return ""
    val selectors = listOf(
      ".main-title",
      "h4 a",
      ".titles h4",
      ".episode-title",
      ".titles h5"
    )
    for (sel in selectors) {
      val text = card.selectFirst(sel)?.text()?.trim().orEmpty()
      if (text.isNotBlank()) return text
    }
    return ""
  }

  private fun extractPosterFromCard(card: Element?): String {
    if (card == null) return ""
    val imgs = card.select("img")
    for (img in imgs) {
      val candidates = listOf(
        img.attr("data-original"),
        img.attr("data-src"),
        img.absUrl("src"),
        img.attr("src")
      )
      for (c in candidates) {
        val norm = normalizePosterUrl(c)
        if (norm.isNotBlank()) return norm
      }
    }
    return ""
  }

  private fun extractProgressCountsFromCard(card: Element?): Pair<Int, Int> {
    if (card == null) return 0 to 0
    val dataWatched = card.attr("data-watched").toIntOrNull()
    val dataTotal = card.attr("data-total").toIntOrNull()
    if (dataWatched != null && dataTotal != null && dataTotal > 0) {
      return dataWatched to dataTotal
    }

    val text = card.text()
    // Matches "25/145", "25 / 145", "25 of 145"
    Regex("""(\d{1,4})\s*(?:/|of)\s*(\d{1,4})""", RegexOption.IGNORE_CASE).find(text)?.let { m ->
      val w = m.groupValues[1].toIntOrNull() ?: 0
      val t = m.groupValues[2].toIntOrNull() ?: 0
      if (t in 1..9999 && w <= t) {
        return w to t
      }
    }
    // Matches "25 watched" + "120 left"
    val wMatch = Regex("""(\d{1,4})\s+watched""", RegexOption.IGNORE_CASE).find(text)
    val lMatch = Regex("""(\d{1,4})\s+(?:left|remaining|episodes?\s+left)""", RegexOption.IGNORE_CASE).find(text)
    if (wMatch != null && lMatch != null) {
      val w = wMatch.groupValues[1].toIntOrNull() ?: 0
      val l = lMatch.groupValues[1].toIntOrNull() ?: 0
      if (w + l > 0) return w to (w + l)
    }

    // Percentage fallback from progress bar width (e.g., "width: 65%")
    val pctEl = card.selectFirst(".progress-bar, [aria-valuenow]")
    val pct = pctEl?.attr("aria-valuenow")?.toIntOrNull()
      ?: Regex("""width\s*:\s*(\d{1,3})%""").find(pctEl?.attr("style").orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (pct != null && pct in 0..100) {
      return pct to 100
    }
    return 0 to 0
  }

  private fun cleanEpisodeTitle(raw: String, season: Int, episode: Int): String {
    return raw
      .replace(Regex("""^\s*(?:S?0?$season\s*[xXeE.]\s*0?$episode|S\d{1,2}\s*\.?\s*E\d{1,3}|\d{1,2}x\d{1,3})\s*[-–:•]?\s*""", RegexOption.IGNORE_CASE), "")
      .replace(Regex("""\s+"""), " ")
      .trim()
  }

  private fun normalizePosterUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    val lower = trimmed.lowercase()
    if (lower.startsWith("data:") ||
      lower.contains("placeholder") ||
      lower.contains("blank.gif") ||
      lower.contains("avatar") ||
      lower.contains("gravatar")
    ) {
      return ""
    }
    return when {
      trimmed.startsWith("//") -> "https:$trimmed"
      trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
      trimmed.startsWith("/") -> "https://trakt.tv$trimmed"
      else -> ""
    }
  }

  companion object {
    /**
     * Injected into the live logged-in Trakt WebView to extract all Next-to-Watch Progress items
     * straight from the rendered DOM (including dynamically hydrated React/Turbo cards).
     */
    const val TRAKT_DOM_EXTRACTOR_JS = """
      (function() {
        try {
          var csrfMeta = document.querySelector('meta[name="csrf-token"]');
          var csrfToken = csrfMeta ? (csrfMeta.getAttribute('content') || '') : '';
          var username = '';
          var pathMatch = window.location.pathname.match(/^\/users\/([^\/?#]+)/i);
          if (pathMatch && pathMatch[1] && pathMatch[1].toLowerCase() !== 'me') {
            username = pathMatch[1];
          }
          if (!username) {
            var userLinks = document.querySelectorAll('a[href^="/users/"]');
            for (var u = 0; u < userLinks.length; u++) {
              var um = (userLinks[u].getAttribute('href') || '').match(/^\/users\/([^\/?#]+)/i);
              if (um && um[1] && um[1].toLowerCase() !== 'me' && um[1].toLowerCase() !== 'settings') {
                username = um[1];
                break;
              }
            }
          }

          var items = [];
          var seen = {};
          var epLinks = document.querySelectorAll('a[href*="/shows/"][href*="/seasons/"][href*="/episodes/"]');

          for (var i = 0; i < epLinks.length; i++) {
            var a = epLinks[i];
            var href = a.href || a.getAttribute('href') || '';
            var m = href.match(/\/shows\/([^\/?#]+)\/seasons\/(\d+)\/episodes\/(\d+)/i);
            if (!m) continue;
            var slug = m[1].toLowerCase();
            var season = parseInt(m[2], 10);
            var episode = parseInt(m[3], 10);
            if (!slug || !season || !episode || seen[slug]) continue;
            seen[slug] = true;

            // Walk up to find the card wrapper
            var card = a.closest('.grid-item, .progress-wrapper, .fanart, [data-show-id], [data-episode-id], article, li') || a.parentElement;
            if (card && !card.querySelector('img') && card.parentElement) {
              card = card.parentElement;
            }

            // Show title
            var showTitle = '';
            if (card) {
              var titleEl = card.querySelector('h3 a, h3, .titles h3, a[href="/shows/' + slug + '"], .show-title');
              if (titleEl) showTitle = (titleEl.innerText || titleEl.textContent || '').trim();
              if (!showTitle) {
                var altImg = card.querySelector('img[alt]');
                if (altImg) showTitle = (altImg.getAttribute('alt') || '').trim();
              }
            }

            // Episode title
            var episodeTitle = (a.innerText || a.textContent || '').trim();
            if ((!episodeTitle || episodeTitle.toLowerCase() === 'watch') && card) {
              var epEl = card.querySelector('.main-title, h4 a, .titles h4, .episode-title, .titles h5');
              if (epEl) episodeTitle = (epEl.innerText || epEl.textContent || '').trim();
            }

            // Poster URL
            var posterUrl = '';
            if (card) {
              var imgs = card.querySelectorAll('img');
              for (var k = 0; k < imgs.length; k++) {
                var img = imgs[k];
                var cand = img.getAttribute('data-original') || img.getAttribute('data-src') || img.currentSrc || img.src || '';
                if (cand && cand.indexOf('data:') !== 0 && cand.indexOf('placeholder') === -1 && cand.indexOf('blank.gif') === -1 && cand.indexOf('avatar') === -1) {
                  posterUrl = cand;
                  break;
                }
              }
              if (!posterUrl) {
                var bgEl = card.querySelector('[style*="background-image"]');
                if (bgEl) {
                  var bgm = (bgEl.getAttribute('style') || '').match(/url\(['"]?([^'")]+)['"]?\)/i);
                  if (bgm && bgm[1]) posterUrl = bgm[1];
                }
              }
            }

            // Watched / Total progress counts
            var watchedCount = 0;
            var totalCount = 0;
            if (card) {
              var dw = parseInt(card.getAttribute('data-watched') || '', 10);
              var dt = parseInt(card.getAttribute('data-total') || '', 10);
              if (!isNaN(dw) && !isNaN(dt) && dt > 0) {
                watchedCount = dw;
                totalCount = dt;
              } else {
                var txt = (card.innerText || card.textContent || '');
                var frac = txt.match(/(\d{1,4})\s*(?:\/|of)\s*(\d{1,4})/i);
                if (frac) {
                  var w = parseInt(frac[1], 10);
                  var t = parseInt(frac[2], 10);
                  if (t > 0 && w <= t) {
                    watchedCount = w;
                    totalCount = t;
                  }
                } else {
                  var wm = txt.match(/(\d{1,4})\s+watched/i);
                  var lm = txt.match(/(\d{1,4})\s+(?:left|remaining)/i);
                  if (wm && lm) {
                    watchedCount = parseInt(wm[1], 10);
                    totalCount = watchedCount + parseInt(lm[1], 10);
                  } else {
                    var bar = card.querySelector('.progress-bar, [aria-valuenow]');
                    if (bar) {
                      var av = parseInt(bar.getAttribute('aria-valuenow') || '', 10);
                      if (!isNaN(av)) {
                        watchedCount = av;
                        totalCount = 100;
                      } else {
                        var sm = (bar.getAttribute('style') || '').match(/width\s*:\s*(\d{1,3})%/i);
                        if (sm) {
                          watchedCount = parseInt(sm[1], 10);
                          totalCount = 100;
                        }
                      }
                    }
                  }
                }
              }
            }

            var episodeId = '';
            if (card) {
              var idEl = card.querySelector('[data-episode-id]') || card;
              episodeId = idEl.getAttribute('data-episode-id') || '';
            }

            items.push({
              slug: slug,
              showTitle: showTitle,
              season: season,
              episode: episode,
              episodeTitle: episodeTitle,
              posterUrl: posterUrl,
              watchedCount: watchedCount,
              totalCount: totalCount,
              episodeUrl: href.indexOf('http') === 0 ? href : ('https://trakt.tv' + href),
              episodeId: episodeId
            });
          }

          var payload = JSON.stringify({
            username: username,
            csrfToken: csrfToken,
            pageUrl: window.location.href || '',
            items: items
          });
          if (window.AsserTraktBridge && typeof window.AsserTraktBridge.onTraktExtracted === 'function') {
            window.AsserTraktBridge.onTraktExtracted(payload);
          }
          return items.length;
        } catch (e) {
          return -1;
        }
      })();
    """
  }
}
