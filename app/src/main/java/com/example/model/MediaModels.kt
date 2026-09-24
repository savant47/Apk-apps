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
  val description: String = ""
)

data class SubtitleItem(
  val id: String,
  val releaseName: String,
  val languageCode: String,
  val languageName: String,
  val format: String = "vtt",
  val downloadUrl: String,
  val source: String = "OpenSubtitles.org",
  val rating: Float = 5.0f
)
