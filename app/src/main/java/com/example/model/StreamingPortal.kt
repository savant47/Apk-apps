package com.example.model

enum class StreamingPortal(
  val id: String,
  val displayNameArabic: String,
  val displayNameEnglish: String,
  val homeUrl: String,
  val description: String
) {
  TOP_CINEMA(
    id = "topcinema",
    displayNameArabic = "توب سينما",
    displayNameEnglish = "TopCinema",
    homeUrl = "https://topcinema.io/home1/",
    description = "أفلام، مسلسلات، عروض، وأنيمي بجودة عالية وسيرفرات سريعة"
  ),
  WATCH_SERIES(
    id = "watchseries",
    displayNameArabic = "ووتش سيريز",
    displayNameEnglish = "WatchSeries",
    homeUrl = "https://watchseries.bar",
    description = "أحدث المسلسلات التلفزيونية العالمية والحلقات مترجمة"
  );

  companion object {
    fun fromUrl(url: String): StreamingPortal {
      return if (url.contains("watchseries", ignoreCase = true)) {
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
