package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.Bookmark
import com.example.model.ControlMode
import com.example.model.HistoryEntry
import com.example.model.MediaCategory
import com.example.model.MediaItem
import com.example.model.StreamingPortal
import com.example.model.WatchProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class TvPreferencesRepository(context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences("cinema_tv_prefs", Context.MODE_PRIVATE)

  private val _bookmarksFlow = MutableStateFlow<List<Bookmark>>(emptyList())
  val bookmarksFlow: StateFlow<List<Bookmark>> = _bookmarksFlow.asStateFlow()

  private val _historyFlow = MutableStateFlow<List<HistoryEntry>>(emptyList())
  val historyFlow: StateFlow<List<HistoryEntry>> = _historyFlow.asStateFlow()

  private val _watchProgressFlow = MutableStateFlow<List<WatchProgress>>(emptyList())
  val watchProgressFlow: StateFlow<List<WatchProgress>> = _watchProgressFlow.asStateFlow()

  init {
    loadBookmarks()
    loadHistory()
    loadWatchProgress()
  }

  fun getActivePortal(): StreamingPortal {
    val portalId = prefs.getString(KEY_ACTIVE_PORTAL, StreamingPortal.TOP_CINEMA.id)
    return if (portalId == StreamingPortal.WATCH_SERIES.id) {
      StreamingPortal.WATCH_SERIES
    } else {
      StreamingPortal.TOP_CINEMA
    }
  }

  fun setActivePortal(portal: StreamingPortal) {
    prefs.edit().putString(KEY_ACTIVE_PORTAL, portal.id).apply()
  }

  fun getControlMode(): ControlMode {
    val mode = prefs.getString(KEY_CONTROL_MODE, ControlMode.DPAD_NAV.name)
    return try {
      ControlMode.valueOf(mode ?: ControlMode.DPAD_NAV.name)
    } catch (_: Exception) {
      ControlMode.DPAD_NAV
    }
  }

  fun setControlMode(mode: ControlMode) {
    prefs.edit().putString(KEY_CONTROL_MODE, mode.name).apply()
  }

  fun isAdBlockEnabled(): Boolean {
    return prefs.getBoolean(KEY_AD_BLOCK, true)
  }

  fun setAdBlockEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_AD_BLOCK, enabled).apply()
  }

  fun addBookmark(title: String, url: String, portal: StreamingPortal): Boolean {
    val current = _bookmarksFlow.value.toMutableList()
    if (current.any { it.url == url }) return false

    val cleanTitle = title.ifBlank { portal.displayNameArabic }
    val newBookmark = Bookmark(
      id = UUID.randomUUID().toString(),
      title = cleanTitle,
      url = url,
      portal = portal
    )
    current.add(0, newBookmark)
    saveBookmarks(current)
    return true
  }

  fun removeBookmark(id: String) {
    val current = _bookmarksFlow.value.filterNot { it.id == id }
    saveBookmarks(current)
  }

  fun isBookmarked(url: String): Boolean {
    return _bookmarksFlow.value.any { it.url == url }
  }

  fun getBookmarks(): List<Bookmark> {
    return _bookmarksFlow.value
  }

  fun addHistory(title: String, url: String, portal: StreamingPortal) {
    if (url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
    val current = _historyFlow.value.filterNot { it.url == url }.toMutableList()
    val cleanTitle = title.ifBlank { url }
    current.add(0, HistoryEntry(
      id = UUID.randomUUID().toString(),
      title = cleanTitle,
      url = url,
      portal = portal
    ))
    // Keep last 30 entries
    val trimmed = current.take(30)
    saveHistory(trimmed)
  }

  fun clearHistory() {
    saveHistory(emptyList())
  }

  fun saveWatchProgress(mediaItem: MediaItem, positionMs: Long, durationMs: Long) {
    if (positionMs <= 1000L && durationMs <= 0L) return
    val current = _watchProgressFlow.value.filterNot { it.mediaId == mediaItem.id }.toMutableList()
    val progress = WatchProgress(
      mediaId = mediaItem.id,
      title = mediaItem.title,
      arabicTitle = mediaItem.arabicTitle,
      posterUrl = mediaItem.posterUrl,
      backdropUrl = mediaItem.backdropUrl,
      portal = mediaItem.portal,
      category = mediaItem.category,
      positionMs = positionMs,
      durationMs = if (durationMs > 0L) durationMs else 120_000L, // fallback duration
      lastWatchedTimestamp = System.currentTimeMillis(),
      portalUrl = mediaItem.portalUrl,
      defaultStreamUrl = mediaItem.defaultStreamUrl,
      qualityBadge = mediaItem.qualityBadge,
      rating = mediaItem.rating
    )
    current.add(0, progress)
    // Keep last 25 recently watched items
    val trimmed = current.take(25)
    saveWatchProgressList(trimmed)
  }

  fun getWatchProgress(mediaId: String): WatchProgress? {
    return _watchProgressFlow.value.find { it.mediaId == mediaId }
  }

  fun removeWatchProgress(mediaId: String) {
    val current = _watchProgressFlow.value.filterNot { it.mediaId == mediaId }
    saveWatchProgressList(current)
  }

  fun clearWatchHistory() {
    saveWatchProgressList(emptyList())
  }

  private fun loadWatchProgress() {
    val raw = prefs.getString(KEY_WATCH_PROGRESS_JSON, null)
    if (raw == null) {
      // Seed with initial realistic viewing history
      val initialRecents = listOf(
        WatchProgress(
          mediaId = "tc_dune2",
          title = "Dune: Part Two",
          arabicTitle = "كثيب: الجزء الثاني",
          posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&q=80",
          backdropUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1280&q=80",
          portal = StreamingPortal.TOP_CINEMA,
          category = MediaCategory.MOVIE,
          positionMs = 45 * 60 * 1000L, // 45 minutes in
          durationMs = (2 * 3600 + 46 * 60) * 1000L, // 2h 46m
          portalUrl = "https://topcinema.io/home1/",
          defaultStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
          qualityBadge = "4K UHD",
          rating = 8.6
        ),
        WatchProgress(
          mediaId = "ws_hotd",
          title = "House of the Dragon",
          arabicTitle = "آل التنين (الموسم 2)",
          posterUrl = "https://images.unsplash.com/photo-1514539079130-25950c84af65?w=500&q=80",
          backdropUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1280&q=80",
          portal = StreamingPortal.WATCH_SERIES,
          category = MediaCategory.SERIES,
          positionMs = 28 * 60 * 1000L, // 28 minutes in
          durationMs = 62 * 60 * 1000L, // 62m
          portalUrl = "https://watchseries.bar",
          defaultStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
          qualityBadge = "1080p FHD",
          rating = 8.5
        ),
        WatchProgress(
          mediaId = "ws_shogun",
          title = "Shōgun",
          arabicTitle = "شوغون (الحلقة 4)",
          posterUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80",
          backdropUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1280&q=80",
          portal = StreamingPortal.WATCH_SERIES,
          category = MediaCategory.SERIES,
          positionMs = 15 * 60 * 1000L, // 15 minutes in
          durationMs = 58 * 60 * 1000L,
          portalUrl = "https://watchseries.bar",
          defaultStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
          qualityBadge = "4K UHD",
          rating = 8.8
        )
      )
      saveWatchProgressList(initialRecents)
      return
    }

    val list = mutableListOf<WatchProgress>()
    try {
      val jsonArray = JSONArray(raw)
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        list.add(
          WatchProgress(
            mediaId = obj.optString("mediaId", ""),
            title = obj.optString("title", ""),
            arabicTitle = obj.optString("arabicTitle", ""),
            posterUrl = obj.optString("posterUrl", ""),
            backdropUrl = obj.optString("backdropUrl", ""),
            portal = StreamingPortal.valueOf(
              obj.optString("portal", StreamingPortal.TOP_CINEMA.name)
            ),
            category = MediaCategory.valueOf(
              obj.optString("category", MediaCategory.MOVIE.name)
            ),
            positionMs = obj.optLong("positionMs", 0L),
            durationMs = obj.optLong("durationMs", 0L),
            lastWatchedTimestamp = obj.optLong("lastWatchedTimestamp", System.currentTimeMillis()),
            portalUrl = obj.optString("portalUrl", ""),
            defaultStreamUrl = obj.optString("defaultStreamUrl", ""),
            qualityBadge = obj.optString("qualityBadge", "1080p"),
            rating = obj.optDouble("rating", 8.5)
          )
        )
      }
    } catch (_: Exception) {}
    _watchProgressFlow.value = list
  }

  private fun saveWatchProgressList(list: List<WatchProgress>) {
    _watchProgressFlow.value = list
    val jsonArray = JSONArray()
    for (item in list) {
      val obj = JSONObject()
      obj.put("mediaId", item.mediaId)
      obj.put("title", item.title)
      obj.put("arabicTitle", item.arabicTitle)
      obj.put("posterUrl", item.posterUrl)
      obj.put("backdropUrl", item.backdropUrl)
      obj.put("portal", item.portal.name)
      obj.put("category", item.category.name)
      obj.put("positionMs", item.positionMs)
      obj.put("durationMs", item.durationMs)
      obj.put("lastWatchedTimestamp", item.lastWatchedTimestamp)
      obj.put("portalUrl", item.portalUrl)
      obj.put("defaultStreamUrl", item.defaultStreamUrl)
      obj.put("qualityBadge", item.qualityBadge)
      obj.put("rating", item.rating)
      jsonArray.put(obj)
    }
    prefs.edit().putString(KEY_WATCH_PROGRESS_JSON, jsonArray.toString()).apply()
  }

  private fun loadBookmarks() {
    val raw = prefs.getString(KEY_BOOKMARKS_JSON, "[]") ?: "[]"
    val list = mutableListOf<Bookmark>()
    try {
      val jsonArray = JSONArray(raw)
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        list.add(
          Bookmark(
            id = obj.optString("id", UUID.randomUUID().toString()),
            title = obj.optString("title", "عنصر محفوظ"),
            url = obj.optString("url", ""),
            portal = StreamingPortal.valueOf(
              obj.optString("portal", StreamingPortal.TOP_CINEMA.name)
            ),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
          )
        )
      }
    } catch (_: Exception) {}
    _bookmarksFlow.value = list
  }

  private fun saveBookmarks(list: List<Bookmark>) {
    _bookmarksFlow.value = list
    val jsonArray = JSONArray()
    for (item in list) {
      val obj = JSONObject()
      obj.put("id", item.id)
      obj.put("title", item.title)
      obj.put("url", item.url)
      obj.put("portal", item.portal.name)
      obj.put("timestamp", item.timestamp)
      jsonArray.put(obj)
    }
    prefs.edit().putString(KEY_BOOKMARKS_JSON, jsonArray.toString()).apply()
  }

  private fun loadHistory() {
    val raw = prefs.getString(KEY_HISTORY_JSON, "[]") ?: "[]"
    val list = mutableListOf<HistoryEntry>()
    try {
      val jsonArray = JSONArray(raw)
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        list.add(
          HistoryEntry(
            id = obj.optString("id", UUID.randomUUID().toString()),
            title = obj.optString("title", "صفحة سابقة"),
            url = obj.optString("url", ""),
            portal = StreamingPortal.valueOf(
              obj.optString("portal", StreamingPortal.TOP_CINEMA.name)
            ),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
          )
        )
      }
    } catch (_: Exception) {}
    _historyFlow.value = list
  }

  private fun saveHistory(list: List<HistoryEntry>) {
    _historyFlow.value = list
    val jsonArray = JSONArray()
    for (item in list) {
      val obj = JSONObject()
      obj.put("id", item.id)
      obj.put("title", item.title)
      obj.put("url", item.url)
      obj.put("portal", item.portal.name)
      obj.put("timestamp", item.timestamp)
      jsonArray.put(obj)
    }
    prefs.edit().putString(KEY_HISTORY_JSON, jsonArray.toString()).apply()
  }

  companion object {
    private const val KEY_ACTIVE_PORTAL = "active_portal"
    private const val KEY_CONTROL_MODE = "control_mode"
    private const val KEY_AD_BLOCK = "ad_block_enabled"
    private const val KEY_BOOKMARKS_JSON = "bookmarks_json"
    private const val KEY_HISTORY_JSON = "history_json"
    private const val KEY_WATCH_PROGRESS_JSON = "watch_progress_json"
  }
}
