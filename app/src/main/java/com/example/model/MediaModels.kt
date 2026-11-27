package com.example.model

data class SearchResultItem(
  val id: String,
  val title: String,
  val url: String,
  val portal: StreamingPortal,
  val posterUrl: String = "",
  val type: String = "فيلم / مسلسل",
  val year: String = "",
  val quality: String = "1080p Web-DL",
  val description: String = "",
  val rating: String = "8.2"
)

data class CatalogMediaItem(
  val id: String,
  val title: String,
  val searchTitle: String = title,
  val posterUrl: String,
  val backdropUrl: String = posterUrl,
  val rating: String = "8.4",
  val year: String = "2025",
  val genre: String = "Drama • Action",
  val overview: String = "",
  val mediaType: String = "tv", // "movie" or "tv"
  val rank: Int? = null
)

data class VideoQualityOption(
  val id: String,
  val label: String,
  val height: Int = 0,
  val streamUrl: String = "",
  val isAuto: Boolean = false
)

data class DirectStreamResolution(
  val primaryStreamUrl: String = "",
  val refererUrl: String = "",
  val detectedQualities: List<VideoQualityOption> = emptyList()
)

data class ScrapedWatchServer(
  val id: String,
  val name: String,
  val embedUrl: String,
  val qualityLabel: String = "Auto HD",
  val isDirectEmbed: Boolean = true,
  val directStreamUrl: String = ""
)

data class ScrapedSeasonItem(
  val seasonNumber: Int,
  val title: String,
  val url: String
)

data class ScrapedEpisodeItem(
  val episodeNumber: Int,
  val title: String,
  val url: String,
  val seasonNumber: Int = 1
)

data class ScrapedSeriesStructure(
  val isSeries: Boolean = false,
  val seriesTitle: String = "",
  val currentSeason: Int = 1,
  val selectedSeasonNumber: Int = currentSeason,
  val currentEpisodeNumber: Int = 1,
  val seasons: List<ScrapedSeasonItem> = emptyList(),
  val episodes: List<ScrapedEpisodeItem> = emptyList(),
  val nextEpisode: ScrapedEpisodeItem? = null,
  val watchServers: List<ScrapedWatchServer> = emptyList(),
  val bestCleanPlayerUrl: String = "",
  val directStreamUrl: String = "",
  val directStreamReferer: String = "",
  val detectedQualities: List<VideoQualityOption> = emptyList()
)

enum class SubtitleSourceFilter(val titleArabic: String, val id: String) {
  ALL("الكل (OpenSubtitles + Addic7ed)", "all"),
  OPEN_SUBTITLES("OpenSubtitles.org", "opensubtitles"),
  ADDIC7ED("Addic7ed.com", "addic7ed");

  val labelAr: String
    get() = titleArabic
}

data class ParsedSubtitleCue(
  val startMs: Long,
  val endMs: Long,
  val text: String
)

enum class SubtitleEdgeStyle(val id: String, val labelAr: String) {
  DROP_SHADOW("DROP_SHADOW", "ظل سينمائي"),
  OUTLINE("OUTLINE", "إطار أسود واضح"),
  NONE("NONE", "بدون ظل");

  companion object {
    fun fromId(raw: String?): SubtitleEdgeStyle {
      return values().firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: DROP_SHADOW
    }
  }
}

data class SubtitleStyleConfig(
  val textColorArgb: Long = 0xFFFFD700L,       // Default: Cinema Gold/Yellow (#FFD700)
  val backgroundColorArgb: Long = 0xBF000000L, // Default: 75% Black (#BF000000)
  val fontSizeSp: Float = 20f,                 // Default: Large readable 20sp
  val bottomOffsetDp: Int = 28,                // Distance from bottom of player
  val edgeStyle: SubtitleEdgeStyle = SubtitleEdgeStyle.DROP_SHADOW,
  val syncOffsetMs: Long = 0L                  // Subtitle timing offset in milliseconds
)

data class SubtitleItem(
  val id: String,
  val releaseName: String,
  val languageCode: String,
  val languageName: String,
  val format: String = "vtt",
  val downloadUrl: String,
  val source: String = "OpenSubtitles.org",
  val rating: Float = 5.0f,
  val rawVttDataUri: String = "",
  val localFileUri: String = "",
  val parsedCues: List<ParsedSubtitleCue> = emptyList()
) {
  val sourceSite: String
    get() = source
}

data class UpNextItem(
  val id: String,
  val seriesKey: String,
  val showTitle: String,
  val seasonNumber: Int,
  val episodeNumber: Int,
  val episodeTitle: String = "",
  val firstAiredIso: String = "",
  val lastWatchedAtIso: String = "",
  val lastWatchedAtMs: Long = 0L,
  val posterUrl: String = "",
  val watchedCount: Int = 0,
  val totalCount: Int = 0,
  val traktSlug: String = "",
  val traktShowId: Long = 0L,
  val imdbId: String = "",
  val traktEpisodeUrl: String = "",
  val traktEpisodeId: String = "",
  val fromTrakt: Boolean = true,
  val preferredPortal: StreamingPortal = StreamingPortal.WATCH_SERIES,
  val watchUrl: String = "",
  val updatedAtMs: Long = System.currentTimeMillis()
)

data class TraktCalendarItem(
  val id: String,
  val showTitle: String,
  val seasonNumber: Int,
  val episodeNumber: Int,
  val episodeTitle: String = "",
  val firstAiredIso: String = "",
  val posterUrl: String = "",
  val traktShowId: Long = 0L,
  val traktEpisodeId: String = "",
  val imdbId: String = ""
)

data class TraktWatchlistItem(
  val id: String,
  val title: String,
  val mediaType: String = "show", // "show" or "movie"
  val year: String = "",
  val posterUrl: String = "",
  val traktId: Long = 0L,
  val imdbId: String = ""
)

data class TraktParsedLink(
  val slug: String,
  val showTitle: String,
  val seasonNumber: Int?,
  val episodeNumber: Int?,
  val isMovie: Boolean,
  val originalUrl: String
)



