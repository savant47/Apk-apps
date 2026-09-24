package com.example.model

import java.net.URLEncoder

enum class StreamingPortal(
  val id: String,
  val displayNameArabic: String,
  val displayNameEnglish: String,
  val homeUrl: String,
  val searchBaseUrl: String,
  val description: String
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
      TOP_CINEMA -> "https://topcinema.io/search/?query=$encoded&type=all"
      WATCH_SERIES -> "https://watchseries.bar/search/$encoded?q=$encoded"
    }
  }

  companion object {
    fun fromUrl(url: String): StreamingPortal {
      return if (url.contains("watchseries", ignoreCase = true)) {
        WATCH_SERIES
      } else {
        TOP_CINEMA
      }
    }

    fun fromId(id: String): StreamingPortal {
      return if (id.equals(WATCH_SERIES.id, ignoreCase = true) || id.equals(WATCH_SERIES.name, ignoreCase = true)) {
        WATCH_SERIES
      } else {
        TOP_CINEMA
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
