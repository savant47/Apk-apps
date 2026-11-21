package com.example.model

import java.net.URLEncoder

enum class StreamingPortal(
  val id: String,
  var displayNameArabic: String,
  var displayNameEnglish: String,
  var homeUrl: String,
  var searchBaseUrl: String,
  val description: String,
  var isEnabled: Boolean = true
) {
  TOP_CINEMA(
    id = "topcinema",
    displayNameArabic = "توب سينما",
    displayNameEnglish = "TopCinema",
    homeUrl = "https://topcinema.io/home1/?",
    searchBaseUrl = "https://topcinema.io/search/?query=",
    description = "أفلام، مسلسلات، عروض، وأنيمي بجودة عالية وسيرفرات سريعة"
  ),
  WATCH_SERIES(
    id = "watchseries",
    displayNameArabic = "ووتش سيريز",
    displayNameEnglish = "WatchSeries",
    homeUrl = "https://watchseries.bar/home",
    searchBaseUrl = "https://watchseries.bar/search",
    description = "أحدث المسلسلات التلفزيونية العالمية والحلقات كاملة بجميع المواسم"
  ),
  LAROZA(
    id = "laroza",
    displayNameArabic = "لاروزا",
    displayNameEnglish = "LaRoza",
    homeUrl = "https://llaroza.boats/home.24",
    searchBaseUrl = "https://llaroza.boats/search.php?keywords=",
    description = "أفلام ومسلسلات عربية وأجنبية مترجمة وحصرية بجودة عالية وسيرفرات سريعة جداً"
  ),
  EGY_DEAD(
    id = "egydead",
    displayNameArabic = "إيجي ديد",
    displayNameEnglish = "EgyDead",
    homeUrl = "https://tv10.egydead.live/",
    searchBaseUrl = "https://tv10.egydead.live/?s=",
    description = "سيرفر إيجي ديد السريع للأفلام والمسلسلات العربية والأجنبية المترجمة",
    isEnabled = true
  ),
  FLIXER(
    id = "flixer",
    displayNameArabic = "فليكسر",
    displayNameEnglish = "Flixer",
    homeUrl = "https://flixer.gd/",
    searchBaseUrl = "https://flixer.gd/search/",
    description = "مكتبة فليكسر الشاملة لأحدث الأفلام والمسلسلات العالمية المترجمة بجودة ممتازة",
    isEnabled = true
  ),
  PORTAL_5(
    id = "portal5",
    displayNameArabic = "مصدر مخصص 2",
    displayNameEnglish = "Custom Portal 2",
    homeUrl = "",
    searchBaseUrl = "",
    description = "مصدر مخصص إضافي يمكنك برمجته وتعديله بالكامل من الإعدادات",
    isEnabled = false
  );

  fun buildSearchUrl(query: String): String {
    val cleanQuery = query.trim()
    val normalized = cleanQuery.replace("([a-z])([A-Z])".toRegex(), "$1 $2").trim()
    val finalQuery = if (!normalized.contains(" ") && normalized.length >= 6 && normalized.startsWith("mob", ignoreCase = true)) {
      normalized.substring(0, 3) + " " + normalized.substring(3)
    } else {
      normalized
    }
    val encoded = try {
      URLEncoder.encode(finalQuery, "UTF-8")
    } catch (_: Exception) {
      finalQuery.replace(" ", "+")
    }
    return when (this) {
      TOP_CINEMA -> {
        val base = homeUrl.substringBefore("/home")
        "$base/search/?query=$encoded&type=all"
      }
      WATCH_SERIES -> {
        val base = homeUrl.substringBefore("/home")
        "$base/search/$encoded?q=$encoded"
      }
      LAROZA -> {
        "${homeUrl.substringBefore("/home")}/search.php?keywords=$encoded"
      }
      EGY_DEAD -> {
        "${homeUrl.trimEnd('/')}/?s=$encoded"
      }
      FLIXER -> {
        "https://flixer.gd/search/$encoded"
      }
      PORTAL_5 -> {
        if (searchBaseUrl.isNotBlank()) {
          if (searchBaseUrl.contains("%s")) searchBaseUrl.replace("%s", encoded) else "$searchBaseUrl$encoded"
        } else {
          "${homeUrl.trimEnd('/')}/search/$encoded"
        }
      }
    }
  }

  companion object {
    fun fromUrl(url: String): StreamingPortal {
      val lower = url.lowercase()
      return if (lower.contains("watchseries")) {
        WATCH_SERIES
      } else if (lower.contains("flixer")) {
        FLIXER
      } else if (lower.contains("laroza") || lower.contains("llaroza") || lower.contains("roza")) {
        LAROZA
      } else if (lower.contains("egydead") || lower.contains("egy-dead")) {
        EGY_DEAD
      } else if (PORTAL_5.isEnabled && PORTAL_5.homeUrl.isNotBlank() && lower.contains(PORTAL_5.homeUrl.substringAfter("://").substringBefore("/").lowercase())) {
        PORTAL_5
      } else {
        TOP_CINEMA
      }
    }

    fun fromId(id: String): StreamingPortal {
      return when (id.lowercase()) {
        "watchseries" -> WATCH_SERIES
        "laroza" -> LAROZA
        "egydead" -> EGY_DEAD
        "flixer" -> FLIXER
        "portal5" -> PORTAL_5
        else -> TOP_CINEMA
      }
    }

    private val embedHostKeywords = listOf(
      "vidlink.", "vidsrc.", "2embed.", "multiembed.", "autoembed.", "superembed.",
      "vidtube.", "streamwish.", "filemoon.", "filelions.", "mixdrop.", "lulustream.",
      "luluvdo.", "dood.", "d000d.", "ds2play.", "streamtape.", "mp4upload.",
      "uqload.", "upstream.", "voe.", "earnvids.", "vidshare.", "vidfast.",
      "videasy.", "vidrock.", "vidup.", "vidcore.", "vidzee.", "rabbitstream.",
      "sbplay.", "embedsito.", "embedwish.", "streamhg.", "streamhide.",
      "megamax.", "firestream.", "updown.", "byse.", "cdn-video.", "hakunaymatata.",
      "flooderrr.", "smashystream.", "apiplayer.", "yapgrid.", "peachify.", "xpass."
    )

    /**
     * Returns true if the URL is a third-party embed iframe, lazy AJAX token, or direct stream
     * that must NEVER be saved into Watch History as a portal watch page or have `/watch/` appended.
     */
    fun isEmbedIframeUrl(url: String): Boolean {
      val trimmed = url.trim()
      if (trimmed.isBlank()) return false
      val lower = trimmed.lowercase()
      if (lower.startsWith("topcinema-ajax://")) return true
      if (lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".mpd")) return true
      val host = try {
        java.net.URI(trimmed).host?.lowercase() ?: ""
      } catch (_: Exception) {
        lower.substringAfter("://").substringBefore("/")
      }
      if (embedHostKeywords.any { host.contains(it) }) return true
      val isKnownPortalHost = values().any { portal -> belongsToPortalDomain(trimmed, portal) }
      if (!isKnownPortalHost && (lower.contains("/embed-") || lower.contains("/embed/") || lower.contains("/v2/embed/"))) {
        return true
      }
      return false
    }

    /**
     * Checks whether the URL host actually belongs to the specified streaming portal.
     */
    fun belongsToPortalDomain(url: String, portal: StreamingPortal): Boolean {
      val trimmed = url.trim()
      if (!trimmed.startsWith("http", ignoreCase = true)) return false
      val host = try {
        java.net.URI(trimmed).host?.lowercase() ?: ""
      } catch (_: Exception) {
        trimmed.lowercase().substringAfter("://").substringBefore("/")
      }
      if (host.isBlank()) return false
      return when (portal) {
        TOP_CINEMA -> host.contains("topcinema")
        WATCH_SERIES -> host.contains("watchseries")
        LAROZA -> host.contains("laroza") || host.contains("llaroza") || host.contains("larooza")
        EGY_DEAD -> host.contains("egydead") || host.contains("egy-dead")
        FLIXER -> host.contains("flixer")
        PORTAL_5 -> {
          val customHost = portal.homeUrl.substringAfter("://").substringBefore("/").lowercase()
          portal.isEnabled && customHost.isNotBlank() && host.contains(customHost)
        }
      }
    }

    /**
     * Safely normalizes a watch URL without EVER appending `/watch/` to embed iframes
     * or non-TopCinema domains. Also repairs legacy corrupted embed URLs ending with `/watch/`.
     */
    fun sanitizePortalWatchUrl(rawUrl: String, portal: StreamingPortal): String {
      var clean = rawUrl.trim()
        .replace("topcinema.site", "topcinema.io")
        .replace("video.php?vid=", "play.php?vid=")

      if (isEmbedIframeUrl(clean)) {
        // Strip accidental trailing /watch or /watch/ appended by older app versions to embed URLs
        if (clean.endsWith("/watch/", ignoreCase = true)) {
          clean = clean.dropLast("/watch/".length)
        } else if (clean.endsWith("/watch", ignoreCase = true)) {
          clean = clean.dropLast("/watch".length)
        }
        return clean
      }

      if (portal == TOP_CINEMA &&
        belongsToPortalDomain(clean, TOP_CINEMA) &&
        !clean.contains("/watch") &&
        !clean.contains("/search") &&
        !clean.contains("/series/") &&
        !clean.contains("/home") &&
        !clean.contains("/category/")
      ) {
        clean = "${clean.trimEnd('/')}/watch/"
      }
      return clean
    }
  }
}

data class Bookmark(
  val id: String,
  val title: String,
  val url: String,
  val portal: StreamingPortal,
  val timestamp: Long = System.currentTimeMillis()
)

data class HistoryEntry(
  val id: String,
  val title: String,
  val url: String,
  val portal: StreamingPortal,
  val timestamp: Long = System.currentTimeMillis(),
  val seasonNumber: Int = 0,
  val episodeNumber: Int = 0,
  val isSeries: Boolean = false
)

enum class ControlMode {
  DPAD_NAV,
  VIRTUAL_MOUSE
}

enum class AppMainTab {
  BROWSER,
  ASSER_TV
}

data class QuickWebSite(
  val id: String,
  val titleArabic: String,
  val titleEnglish: String,
  val url: String,
  val accentColorHex: Long
)

object SmartOmniboxResolver {
  fun resolveInputToUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return "https://www.google.com"
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
      return trimmed
    }
    // Check if it looks like a valid domain or URL without spaces
    val domainRegex = "^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(/.*)?$".toRegex()
    if (!trimmed.contains(" ") && domainRegex.matches(trimmed)) {
      return "https://$trimmed"
    }
    return buildGoogleSearchUrl(trimmed)
  }

  fun buildGoogleSearchUrl(query: String): String {
    val clean = query.trim()
    if (clean.isBlank()) return "https://www.google.com"
    val encoded = try {
      URLEncoder.encode(clean, "UTF-8")
    } catch (_: Exception) {
      clean.replace(" ", "+")
    }
    return "https://www.google.com/search?q=$encoded"
  }
}

