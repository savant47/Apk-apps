package com.example.model

data class WatchProgress(
  val mediaId: String,
  val title: String,
  val arabicTitle: String,
  val posterUrl: String,
  val backdropUrl: String,
  val portal: StreamingPortal,
  val category: MediaCategory,
  val positionMs: Long,
  val durationMs: Long,
  val lastWatchedTimestamp: Long = System.currentTimeMillis(),
  val portalUrl: String = "",
  val defaultStreamUrl: String = "",
  val qualityBadge: String = "1080p FHD",
  val rating: Double = 8.5
) {
  val progressFraction: Float
    get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

  val progressPercent: Int
    get() = (progressFraction * 100).toInt()

  val remainingMs: Long
    get() = (durationMs - positionMs).coerceAtLeast(0L)

  val isFinished: Boolean
    get() = durationMs > 0 && (positionMs >= durationMs - 15000L)
}
