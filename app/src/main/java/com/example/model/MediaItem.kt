package com.example.model

enum class MediaCategory(val arabicLabel: String) {
  MOVIE("أفلام"),
  SERIES("مسلسلات"),
  ANIME("أنمي")
}

data class StreamServer(
  val id: String,
  val name: String,
  val quality: String,
  val isFast: Boolean = true,
  val streamUrl: String
)

data class MediaItem(
  val id: String,
  val title: String,
  val arabicTitle: String,
  val portal: StreamingPortal,
  val category: MediaCategory,
  val genres: List<String>,
  val year: Int,
  val rating: Double,
  val qualityBadge: String,
  val duration: String,
  val description: String,
  val posterUrl: String,
  val backdropUrl: String,
  val portalUrl: String,
  val defaultStreamUrl: String,
  val audioTracks: List<String> = listOf("الإنجليزية (الصوت الأصلي)", "العربية (دبلجة VIP)"),
  val subtitleTracks: List<String> = listOf("العربية (مترجم)", "English (CC)", "معطل"),
  val availableQualities: List<String> = listOf("1080p Full HD", "720p HD", "480p SD", "تلقائي Auto"),
  val servers: List<StreamServer> = listOf(
    StreamServer("srv_vip", "سيرفر VIP فائق السرعة", "1080p", true, defaultStreamUrl),
    StreamServer("srv_fast", "سيرفر سريع (متعدد الجودات)", "720p", true, defaultStreamUrl),
    StreamServer("srv_backup", "سيرفر احتياطي للإنترنت الضعيف", "480p", false, defaultStreamUrl)
  )
)
