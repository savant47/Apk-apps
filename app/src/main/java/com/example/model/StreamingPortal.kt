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
    searchBaseUrl = "https://watchseries.bar/search/",
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
        "portal5" -> PORTAL_5
        else -> TOP_CINEMA
      }
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
  val timestamp: Long = System.currentTimeMillis()
)

enum class ControlMode {
  DPAD_NAV,
  VIRTUAL_MOUSE
}
