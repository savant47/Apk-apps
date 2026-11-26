package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.model.Bookmark
import com.example.model.ControlMode
import com.example.model.HistoryEntry
import com.example.model.StreamingPortal
import com.example.model.SubtitleEdgeStyle
import com.example.model.SubtitleStyleConfig
import com.example.model.TraktCalendarItem
import com.example.model.TraktWatchlistItem
import com.example.model.UpNextItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AppDisplayMode(val arabicTitle: String, val subtitle: String) {
  MOBILE("📱 منظور الهاتف المحمول", "واجهة رأسية متناسقة تعمل باللمس ومصممة خصيصاً لشاشات الهواتف المحمولة"),
  TV("📺 منظور الشاشة أو التابلت", "واجهة أفقية عريضة للشاشات والرسيفرات والتابلت مع دعم الريموت والماوس")
}

data class PlaybackProgressRecord(
  val contentKey: String,
  val displayTitle: String,
  val positionMs: Long,
  val durationMs: Long,
  val updatedAtMs: Long = System.currentTimeMillis()
)

class TvPreferencesRepository(context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences("cinema_tv_prefs", Context.MODE_PRIVATE)

  private val _bookmarksFlow = MutableStateFlow<List<Bookmark>>(emptyList())
  val bookmarksFlow: StateFlow<List<Bookmark>> = _bookmarksFlow.asStateFlow()

  private val _historyFlow = MutableStateFlow<List<HistoryEntry>>(emptyList())
  val historyFlow: StateFlow<List<HistoryEntry>> = _historyFlow.asStateFlow()

  private val _displayModeFlow = MutableStateFlow<AppDisplayMode>(AppDisplayMode.MOBILE)
  val displayModeFlow: StateFlow<AppDisplayMode> = _displayModeFlow.asStateFlow()

  private val _subtitleStyleFlow = MutableStateFlow(SubtitleStyleConfig())
  val subtitleStyleFlow: StateFlow<SubtitleStyleConfig> = _subtitleStyleFlow.asStateFlow()

  private val _preferredQualityFlow = MutableStateFlow(QUALITY_LOWEST)
  val preferredQualityFlow: StateFlow<String> = _preferredQualityFlow.asStateFlow()

  private val _autoPlayNextEpisodeFlow = MutableStateFlow(true)
  val autoPlayNextEpisodeFlow: StateFlow<Boolean> = _autoPlayNextEpisodeFlow.asStateFlow()

  private val _videoResizeModeFlow = MutableStateFlow(RESIZE_MODE_FILL)
  val videoResizeModeFlow: StateFlow<String> = _videoResizeModeFlow.asStateFlow()

  private val _strictIframeSandboxFlow = MutableStateFlow(true)
  val strictIframeSandboxFlow: StateFlow<Boolean> = _strictIframeSandboxFlow.asStateFlow()

  private val _blockedAdsCountFlow = MutableStateFlow(0)
  val blockedAdsCountFlow: StateFlow<Int> = _blockedAdsCountFlow.asStateFlow()

  private val _audioBoostPercentFlow = MutableStateFlow(100)
  val audioBoostPercentFlow: StateFlow<Int> = _audioBoostPercentFlow.asStateFlow()

  private val _watchedEpisodesFlow = MutableStateFlow<Set<String>>(emptySet())
  val watchedEpisodesFlow: StateFlow<Set<String>> = _watchedEpisodesFlow.asStateFlow()

  private val _upNextFlow = MutableStateFlow<List<UpNextItem>>(emptyList())
  val upNextFlow: StateFlow<List<UpNextItem>> = _upNextFlow.asStateFlow()

  private val _traktUsernameFlow = MutableStateFlow("")
  val traktUsernameFlow: StateFlow<String> = _traktUsernameFlow.asStateFlow()

  private val _traktConnectedFlow = MutableStateFlow(false)
  val traktConnectedFlow: StateFlow<Boolean> = _traktConnectedFlow.asStateFlow()

  private val _traktLastSyncMsFlow = MutableStateFlow(0L)
  val traktLastSyncMsFlow: StateFlow<Long> = _traktLastSyncMsFlow.asStateFlow()

  private val _traktCalendarFlow = MutableStateFlow<List<TraktCalendarItem>>(emptyList())
  val traktCalendarFlow: StateFlow<List<TraktCalendarItem>> = _traktCalendarFlow.asStateFlow()

  private val _traktWatchlistFlow = MutableStateFlow<List<TraktWatchlistItem>>(emptyList())
  val traktWatchlistFlow: StateFlow<List<TraktWatchlistItem>> = _traktWatchlistFlow.asStateFlow()

  private val _traktKeysVersionFlow = MutableStateFlow(0L)
  val traktKeysVersionFlow: StateFlow<Long> = _traktKeysVersionFlow.asStateFlow()

  init {
    loadBookmarks()
    loadHistory()
    loadDisplayMode()
    loadSubtitleStyle()
    loadPreferredQuality()
    loadVideoResizeMode()
    _autoPlayNextEpisodeFlow.value = prefs.getBoolean(KEY_AUTO_PLAY_NEXT_EPISODE, true)
    _strictIframeSandboxFlow.value = prefs.getBoolean(KEY_STRICT_IFRAME_SANDBOX, true)
    _blockedAdsCountFlow.value = prefs.getInt(KEY_BLOCKED_ADS_COUNT, 0).coerceAtLeast(0)
    _audioBoostPercentFlow.value = prefs.getInt(KEY_AUDIO_BOOST_PERCENT, 100).coerceIn(0, 300)
    _traktUsernameFlow.value = prefs.getString(KEY_TRAKT_USERNAME, "") ?: ""
    _traktConnectedFlow.value = !(prefs.getString(KEY_TRAKT_ACCESS_TOKEN, "") ?: "").isBlank()
    _traktLastSyncMsFlow.value = prefs.getLong(KEY_TRAKT_LAST_SYNC_MS, 0L)
    loadWatchedEpisodes()
    loadUpNextQueue()
    loadTraktCalendar()
    loadTraktWatchlist()
    loadPortals()
    AppDiagnosticsLogger.log(
      "Prefs",
      "Loaded prefs: quality=${_preferredQualityFlow.value}, resize=${_videoResizeModeFlow.value}, audioBoost=${_audioBoostPercentFlow.value}%, subSize=${_subtitleStyleFlow.value.fontSizeSp.toInt()}sp"
    )
  }

  fun loadPortals() {
    for (portal in StreamingPortal.values()) {
      val prefix = "portal_${portal.id}"
      portal.displayNameArabic = prefs.getString("${prefix}_name_ar", portal.displayNameArabic) ?: portal.displayNameArabic
      portal.displayNameEnglish = prefs.getString("${prefix}_name_en", portal.displayNameEnglish) ?: portal.displayNameEnglish
      portal.homeUrl = prefs.getString("${prefix}_home_url", portal.homeUrl) ?: portal.homeUrl
      portal.searchBaseUrl = prefs.getString("${prefix}_search_url", portal.searchBaseUrl) ?: portal.searchBaseUrl
      portal.isEnabled = prefs.getBoolean("${prefix}_enabled", portal.isEnabled)
    }
  }

  fun savePortal(
    portal: StreamingPortal,
    nameAr: String,
    nameEn: String,
    homeUrl: String,
    searchUrl: String,
    enabled: Boolean
  ) {
    val prefix = "portal_${portal.id}"
    prefs.edit()
      .putString("${prefix}_name_ar", nameAr)
      .putString("${prefix}_name_en", nameEn)
      .putString("${prefix}_home_url", homeUrl)
      .putString("${prefix}_search_url", searchUrl)
      .putBoolean("${prefix}_enabled", enabled)
      .apply()

    // Reload the updated portals in-memory immediately
    loadPortals()
  }

  private fun loadDisplayMode() {
    val modeStr = prefs.getString(KEY_DISPLAY_MODE, null)
    if (modeStr != null) {
      try {
        _displayModeFlow.value = AppDisplayMode.valueOf(modeStr)
      } catch (_: Exception) {
        _displayModeFlow.value = AppDisplayMode.MOBILE
      }
    } else {
      _displayModeFlow.value = AppDisplayMode.MOBILE
    }
  }

  fun getDisplayMode(): AppDisplayMode = _displayModeFlow.value

  fun setDisplayMode(mode: AppDisplayMode) {
    _displayModeFlow.value = mode
    prefs.edit()
      .putString(KEY_DISPLAY_MODE, mode.name)
      .putBoolean(KEY_HAS_SELECTED_MODE, true)
      .apply()
  }

  fun hasSelectedDisplayMode(): Boolean {
    return prefs.getBoolean(KEY_HAS_SELECTED_MODE, false)
  }

  private fun loadPreferredQuality() {
    val raw = prefs.getString(KEY_PREFERRED_VIDEO_QUALITY, QUALITY_LOWEST)
    _preferredQualityFlow.value = normalizeQualityId(raw)
  }

  fun getPreferredQuality(): String = _preferredQualityFlow.value

  fun setPreferredQuality(qualityId: String) {
    val normalized = normalizeQualityId(qualityId)
    _preferredQualityFlow.value = normalized
    // Use commit() so quality preference survives sudden 1GB RAM Android TV process kills
    prefs.edit()
      .putString(KEY_PREFERRED_VIDEO_QUALITY, normalized)
      .commit()
    AppDiagnosticsLogger.log("Prefs", "Saved preferred video quality: $normalized")
  }

  private fun loadSubtitleStyle() {
    val textColor = prefs.getLong(KEY_SUB_TEXT_COLOR, 0xFFFFD700L)
    val bgColor = prefs.getLong(KEY_SUB_BG_COLOR, 0xBF000000L)
    val fontSize = prefs.getFloat(KEY_SUB_FONT_SIZE, 20f).coerceIn(12f, 60f)
    val bottomOffset = prefs.getInt(KEY_SUB_BOTTOM_OFFSET, 28).coerceIn(8, 140)
    val edgeStyle = SubtitleEdgeStyle.fromId(prefs.getString(KEY_SUB_EDGE_STYLE, SubtitleEdgeStyle.DROP_SHADOW.id))
    val syncOffset = prefs.getLong(KEY_SUB_SYNC_OFFSET, 0L)
    _subtitleStyleFlow.value = SubtitleStyleConfig(
      textColorArgb = textColor,
      backgroundColorArgb = bgColor,
      fontSizeSp = fontSize,
      bottomOffsetDp = bottomOffset,
      edgeStyle = edgeStyle,
      syncOffsetMs = syncOffset
    )
  }

  fun getSubtitleStyle(): SubtitleStyleConfig = _subtitleStyleFlow.value

  fun setSubtitleStyle(style: SubtitleStyleConfig) {
    val clampedStyle = style.copy(
      fontSizeSp = style.fontSizeSp.coerceIn(12f, 60f),
      bottomOffsetDp = style.bottomOffsetDp.coerceIn(8, 140)
    )
    _subtitleStyleFlow.value = clampedStyle
    // Use commit() so subtitle styling is flushed immediately and persists across app restarts/SIGKILL
    prefs.edit()
      .putLong(KEY_SUB_TEXT_COLOR, clampedStyle.textColorArgb)
      .putLong(KEY_SUB_BG_COLOR, clampedStyle.backgroundColorArgb)
      .putFloat(KEY_SUB_FONT_SIZE, clampedStyle.fontSizeSp)
      .putInt(KEY_SUB_BOTTOM_OFFSET, clampedStyle.bottomOffsetDp)
      .putString(KEY_SUB_EDGE_STYLE, clampedStyle.edgeStyle.id)
      .putLong(KEY_SUB_SYNC_OFFSET, clampedStyle.syncOffsetMs)
      .commit()
  }

  fun isAutoPlayNextEpisodeEnabled(): Boolean = _autoPlayNextEpisodeFlow.value

  fun setAutoPlayNextEpisodeEnabled(enabled: Boolean) {
    _autoPlayNextEpisodeFlow.value = enabled
    prefs.edit().putBoolean(KEY_AUTO_PLAY_NEXT_EPISODE, enabled).apply()
  }

  private fun loadVideoResizeMode() {
    val raw = prefs.getString(KEY_VIDEO_RESIZE_MODE, RESIZE_MODE_FILL)
    _videoResizeModeFlow.value = normalizeVideoResizeMode(raw)
  }

  fun getVideoResizeMode(): String = _videoResizeModeFlow.value

  fun setVideoResizeMode(mode: String) {
    val normalized = normalizeVideoResizeMode(mode)
    _videoResizeModeFlow.value = normalized
    prefs.edit()
      .putString(KEY_VIDEO_RESIZE_MODE, normalized)
      .commit()
    AppDiagnosticsLogger.log("Prefs", "Saved preferred video resize mode: $normalized")
  }

  // =========================================================================
  // CROSS-SOURCE RESUME PLAYBACK PROGRESS (Independent of Portal / Server URL)
  // =========================================================================

  private fun loadAllPlaybackProgressLocked(): MutableMap<String, PlaybackProgressRecord> {
    val map = LinkedHashMap<String, PlaybackProgressRecord>()
    val raw = prefs.getString(KEY_PLAYBACK_PROGRESS_JSON, null) ?: return map
    try {
      val arr = JSONArray(raw)
      for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val key = obj.optString("contentKey", "")
        if (key.isNotBlank()) {
          map[key] = PlaybackProgressRecord(
            contentKey = key,
            displayTitle = obj.optString("displayTitle", ""),
            positionMs = obj.optLong("positionMs", 0L),
            durationMs = obj.optLong("durationMs", 0L),
            updatedAtMs = obj.optLong("updatedAtMs", 0L)
          )
        }
      }
    } catch (_: Exception) {}
    return map
  }

  private fun persistPlaybackProgressLocked(map: Map<String, PlaybackProgressRecord>) {
    val sorted = map.values
      .sortedByDescending { it.updatedAtMs }
      .take(120)
    val arr = JSONArray()
    for (rec in sorted) {
      arr.put(
        JSONObject().apply {
          put("contentKey", rec.contentKey)
          put("displayTitle", rec.displayTitle)
          put("positionMs", rec.positionMs)
          put("durationMs", rec.durationMs)
          put("updatedAtMs", rec.updatedAtMs)
        }
      )
    }
    prefs.edit().putString(KEY_PLAYBACK_PROGRESS_JSON, arr.toString()).apply()
  }

  @Synchronized
  fun savePlaybackProgress(
    contentKey: String,
    displayTitle: String,
    positionMs: Long,
    durationMs: Long
  ) {
    if (contentKey.isBlank()) return
    val map = loadAllPlaybackProgressLocked()

    // If >= 95% watched, mark as completed and clear the saved resume position
    if (durationMs > 0L && positionMs >= (durationMs * 0.95).toLong()) {
      if (map.remove(contentKey) != null) {
        persistPlaybackProgressLocked(map)
      }
      return
    }

    // Only persist meaningful resume positions (> 15 seconds)
    if (positionMs <= 15_000L) return

    map[contentKey] = PlaybackProgressRecord(
      contentKey = contentKey,
      displayTitle = displayTitle,
      positionMs = positionMs,
      durationMs = durationMs.coerceAtLeast(0L),
      updatedAtMs = System.currentTimeMillis()
    )
    persistPlaybackProgressLocked(map)
  }

  @Synchronized
  fun getPlaybackProgress(contentKey: String): PlaybackProgressRecord? {
    if (contentKey.isBlank()) return null
    val record = loadAllPlaybackProgressLocked()[contentKey] ?: return null
    if (record.positionMs <= 15_000L) return null
    if (record.durationMs > 0L && record.positionMs >= (record.durationMs * 0.95).toLong()) return null
    return record
  }

  @Synchronized
  fun clearPlaybackProgress(contentKey: String) {
    if (contentKey.isBlank()) return
    val map = loadAllPlaybackProgressLocked()
    if (map.remove(contentKey) != null) {
      persistPlaybackProgressLocked(map)
    }
  }

  fun getActivePortal(): StreamingPortal {
    val portalId = prefs.getString(KEY_ACTIVE_PORTAL, StreamingPortal.TOP_CINEMA.id)
    return StreamingPortal.fromId(portalId ?: StreamingPortal.TOP_CINEMA.id)
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

  fun isAdGuardDnsEnabled(): Boolean {
    return prefs.getBoolean(KEY_ADGUARD_DNS, true)
  }

  fun setAdGuardDnsEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_ADGUARD_DNS, enabled).apply()
  }

  fun isStrictPopupBlockerEnabled(): Boolean {
    return prefs.getBoolean(KEY_STRICT_POPUP_BLOCKER, true)
  }

  fun setStrictPopupBlockerEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_STRICT_POPUP_BLOCKER, enabled).apply()
  }

  fun isStrictIframeSandboxEnabled(): Boolean = _strictIframeSandboxFlow.value

  fun setStrictIframeSandboxEnabled(enabled: Boolean) {
    _strictIframeSandboxFlow.value = enabled
    prefs.edit().putBoolean(KEY_STRICT_IFRAME_SANDBOX, enabled).apply()
  }

  @Synchronized
  fun incrementBlockedAdsCount(delta: Int = 1) {
    val updated = (_blockedAdsCountFlow.value + delta).coerceAtLeast(0)
    _blockedAdsCountFlow.value = updated
    prefs.edit().putInt(KEY_BLOCKED_ADS_COUNT, updated).apply()
  }

  @Synchronized
  fun resetBlockedAdsCount() {
    _blockedAdsCountFlow.value = 0
    prefs.edit().putInt(KEY_BLOCKED_ADS_COUNT, 0).apply()
  }

  fun getAudioBoostPercent(): Int = _audioBoostPercentFlow.value

  fun setAudioBoostPercent(percent: Int) {
    val clamped = percent.coerceIn(0, 300)
    _audioBoostPercentFlow.value = clamped
    prefs.edit().putInt(KEY_AUDIO_BOOST_PERCENT, clamped).apply()
  }

  // =========================================================================
  // LOCAL WATCHED EPISODES TRACKER & UP NEXT QUEUE (التالي للمشاهدة)
  // =========================================================================

  private fun loadWatchedEpisodes() {
    val raw = prefs.getString(KEY_WATCHED_EPISODES_JSON, null)
    if (raw.isNullOrBlank()) {
      _watchedEpisodesFlow.value = emptySet()
      return
    }
    val set = LinkedHashSet<String>()
    try {
      val arr = JSONArray(raw)
      for (i in 0 until arr.length()) {
        val k = arr.optString(i, "").trim()
        if (k.isNotBlank()) set.add(k)
      }
    } catch (_: Exception) {}
    _watchedEpisodesFlow.value = set
  }

  private fun saveWatchedEpisodes(set: Set<String>) {
    _watchedEpisodesFlow.value = set
    val arr = JSONArray()
    for (k in set) {
      arr.put(k)
    }
    prefs.edit().putString(KEY_WATCHED_EPISODES_JSON, arr.toString()).apply()
  }

  fun isEpisodeWatched(showTitle: String, seasonNumber: Int, episodeNumber: Int): Boolean {
    val key = buildEpisodeWatchedKey(showTitle, seasonNumber, episodeNumber)
    return key.isNotBlank() && _watchedEpisodesFlow.value.contains(key)
  }

  @Synchronized
  fun markEpisodeWatched(
    showTitle: String,
    seasonNumber: Int,
    episodeNumber: Int,
    preferredPortal: StreamingPortal = StreamingPortal.WATCH_SERIES,
    nextEpisodeWatchUrl: String = "",
    autoEnqueueNext: Boolean = true
  ) {
    val cleanShow = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(showTitle).ifBlank { showTitle }.trim()
    if (cleanShow.isBlank() || episodeNumber <= 0) return
    val s = seasonNumber.coerceAtLeast(1)
    val e = episodeNumber.coerceAtLeast(1)
    val key = buildEpisodeWatchedKey(cleanShow, s, e)
    if (key.isNotBlank()) {
      val updated = _watchedEpisodesFlow.value.toMutableSet()
      updated.add(key)
      saveWatchedEpisodes(updated)
    }
    if (autoEnqueueNext) {
      enqueueNextEpisode(
        showTitle = cleanShow,
        seasonNumber = s,
        episodeNumber = e + 1,
        preferredPortal = preferredPortal,
        watchUrl = nextEpisodeWatchUrl
      )
    }
  }

  @Synchronized
  fun unmarkEpisodeWatched(showTitle: String, seasonNumber: Int, episodeNumber: Int) {
    val key = buildEpisodeWatchedKey(showTitle, seasonNumber, episodeNumber)
    if (key.isBlank()) return
    val updated = _watchedEpisodesFlow.value.toMutableSet()
    if (updated.remove(key)) {
      saveWatchedEpisodes(updated)
    }
  }

  // =========================================================================
  // OFFICIAL TRAKT API v2 OAUTH CREDENTIALS & LOCAL CACHE (Zero WebView)
  // =========================================================================

  @Synchronized
  fun saveTraktAppKeys(clientId: String, clientSecret: String) {
    prefs.edit()
      .putString(KEY_TRAKT_CLIENT_ID, clientId.trim())
      .putString(KEY_TRAKT_CLIENT_SECRET, clientSecret.trim())
      .apply()
    _traktKeysVersionFlow.value = System.currentTimeMillis()
  }

  @Synchronized
  fun clearAdminTraktAppKeys() {
    prefs.edit()
      .remove(KEY_TRAKT_CLIENT_ID)
      .remove(KEY_TRAKT_CLIENT_SECRET)
      .apply()
    _traktKeysVersionFlow.value = System.currentTimeMillis()
  }

  @Synchronized
  fun saveTraktOAuthCredentials(
    clientId: String = "",
    clientSecret: String = "",
    accessToken: String = "",
    refreshToken: String = "",
    expiresAtMs: Long = 0L,
    username: String = ""
  ) {
    val editor = prefs.edit()
    if (clientId.isNotBlank() && !isPlaceholderTraktValue(clientId) && clientId.trim() != getBuiltInTraktClientId()) {
      editor.putString(KEY_TRAKT_CLIENT_ID, clientId.trim())
    }
    if (clientSecret.isNotBlank() && !isPlaceholderTraktValue(clientSecret) && clientSecret.trim() != getBuiltInTraktClientSecret()) {
      editor.putString(KEY_TRAKT_CLIENT_SECRET, clientSecret.trim())
    }
    if (accessToken.isNotBlank()) {
      editor.putString(KEY_TRAKT_ACCESS_TOKEN, accessToken.trim())
      _traktConnectedFlow.value = true
    }
    if (refreshToken.isNotBlank()) editor.putString(KEY_TRAKT_REFRESH_TOKEN, refreshToken.trim())
    if (expiresAtMs > 0L) editor.putLong(KEY_TRAKT_TOKEN_EXPIRES_AT_MS, expiresAtMs)
    if (username.isNotBlank()) {
      val cleanUser = username.trim()
      _traktUsernameFlow.value = cleanUser
      editor.putString(KEY_TRAKT_USERNAME, cleanUser)
    }
    editor.apply()
  }

  private fun isPlaceholderTraktValue(raw: String): Boolean {
    val clean = raw.trim()
    return clean.isBlank() ||
      clean.equals("TRAKT_CLIENT_ID_DEFAULT_VALUE", ignoreCase = true) ||
      clean.equals("TRAKT_CLIENT_SECRET_DEFAULT_VALUE", ignoreCase = true) ||
      clean.equals("YOUR_TRAKT_CLIENT_ID", ignoreCase = true) ||
      clean.equals("YOUR_TRAKT_CLIENT_SECRET", ignoreCase = true) ||
      clean.equals("dummy_key", ignoreCase = true) ||
      clean.equals("null", ignoreCase = true)
  }

  fun getBuiltInTraktClientId(): String {
    val raw = try { BuildConfig.TRAKT_CLIENT_ID } catch (_: Throwable) { "" }
    return if (isPlaceholderTraktValue(raw)) "" else raw.trim()
  }

  fun getBuiltInTraktClientSecret(): String {
    val raw = try {
      (BuildConfig::class.java.getField("TRAKT_CLIENT_SECRET").get(null) as? String).orEmpty()
    } catch (_: Throwable) {
      ""
    }
    val clean = if (isPlaceholderTraktValue(raw)) "" else raw.trim()
    val builtInId = getBuiltInTraktClientId()
    return if (clean == builtInId) "" else clean
  }

  fun hasBuiltInTraktSecrets(): Boolean =
    getBuiltInTraktClientId().isNotBlank()

  fun getStoredAdminTraktClientId(): String {
    val raw = prefs.getString(KEY_TRAKT_CLIENT_ID, "") ?: ""
    return if (isPlaceholderTraktValue(raw)) "" else raw.trim()
  }

  fun getStoredAdminTraktClientSecret(): String {
    val raw = prefs.getString(KEY_TRAKT_CLIENT_SECRET, "") ?: ""
    val clean = if (isPlaceholderTraktValue(raw)) "" else raw.trim()
    val adminId = getStoredAdminTraktClientId()
    return if (clean == adminId) "" else clean
  }

  fun hasAdminTraktKeysOverride(): Boolean =
    getStoredAdminTraktClientId().isNotBlank()

  /**
   * Resolves effective Trakt Client ID: Admin Override (from password-protected Settings) -> BuildConfig Secret.
   */
  fun getTraktClientId(): String =
    getStoredAdminTraktClientId().ifBlank { getBuiltInTraktClientId() }

  /**
   * Resolves effective Trakt Client Secret (optional for Public OAuth Clients):
   * Admin Override -> BuildConfig Secret (or blank when using Client-ID-only public Trakt apps).
   */
  fun getTraktClientSecret(): String =
    if (hasAdminTraktKeysOverride()) getStoredAdminTraktClientSecret() else getBuiltInTraktClientSecret()

  fun hasEffectiveTraktKeys(): Boolean =
    getTraktClientId().isNotBlank()

  fun getTraktAccessToken(): String = prefs.getString(KEY_TRAKT_ACCESS_TOKEN, "") ?: ""
  fun getTraktRefreshToken(): String = prefs.getString(KEY_TRAKT_REFRESH_TOKEN, "") ?: ""
  fun getTraktTokenExpiresAtMs(): Long = prefs.getLong(KEY_TRAKT_TOKEN_EXPIRES_AT_MS, 0L)
  fun isTraktConnected(): Boolean = _traktConnectedFlow.value && getTraktAccessToken().isNotBlank() && getTraktClientId().isNotBlank()
  fun getTraktUsername(): String = _traktUsernameFlow.value.ifBlank {
    prefs.getString(KEY_TRAKT_USERNAME, "") ?: ""
  }

  @Synchronized
  fun disconnectTrakt(clearAppKeys: Boolean = false) {
    _traktUsernameFlow.value = ""
    _traktConnectedFlow.value = false
    _traktLastSyncMsFlow.value = 0L
    _traktCalendarFlow.value = emptyList()
    _traktWatchlistFlow.value = emptyList()
    val editor = prefs.edit()
      .remove(KEY_TRAKT_ACCESS_TOKEN)
      .remove(KEY_TRAKT_REFRESH_TOKEN)
      .remove(KEY_TRAKT_TOKEN_EXPIRES_AT_MS)
      .remove(KEY_TRAKT_USERNAME)
      .remove(KEY_TRAKT_LAST_SYNC_MS)
      .remove(KEY_TRAKT_CALENDAR_JSON)
      .remove(KEY_TRAKT_WATCHLIST_JSON)
    if (clearAppKeys) {
      editor.remove(KEY_TRAKT_CLIENT_ID).remove(KEY_TRAKT_CLIENT_SECRET)
      _traktKeysVersionFlow.value = System.currentTimeMillis()
    }
    editor.apply()
    // Keep non-Trakt local items only
    saveUpNextQueue(_upNextFlow.value.filterNot { it.fromTrakt })
  }

  @Synchronized
  fun saveTraktCalendar(items: List<TraktCalendarItem>) {
    _traktCalendarFlow.value = items
    val arr = JSONArray()
    for (item in items) {
      arr.put(
        JSONObject().apply {
          put("id", item.id)
          put("showTitle", item.showTitle)
          put("seasonNumber", item.seasonNumber)
          put("episodeNumber", item.episodeNumber)
          put("episodeTitle", item.episodeTitle)
          put("firstAiredIso", item.firstAiredIso)
          put("posterUrl", item.posterUrl)
          put("traktShowId", item.traktShowId)
          put("traktEpisodeId", item.traktEpisodeId)
          put("imdbId", item.imdbId)
        }
      )
    }
    prefs.edit().putString(KEY_TRAKT_CALENDAR_JSON, arr.toString()).apply()
  }

  private fun loadTraktCalendar() {
    val raw = prefs.getString(KEY_TRAKT_CALENDAR_JSON, null) ?: return
    val list = mutableListOf<TraktCalendarItem>()
    try {
      val arr = JSONArray(raw)
      for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val title = obj.optString("showTitle", "").trim()
        if (title.isBlank()) continue
        list.add(
          TraktCalendarItem(
            id = obj.optString("id", "cal_$i"),
            showTitle = title,
            seasonNumber = obj.optInt("seasonNumber", 1).coerceAtLeast(1),
            episodeNumber = obj.optInt("episodeNumber", 1).coerceAtLeast(1),
            episodeTitle = obj.optString("episodeTitle", ""),
            firstAiredIso = obj.optString("firstAiredIso", ""),
            posterUrl = obj.optString("posterUrl", ""),
            traktShowId = obj.optLong("traktShowId", 0L),
            traktEpisodeId = obj.optString("traktEpisodeId", ""),
            imdbId = obj.optString("imdbId", "")
          )
        )
      }
    } catch (_: Exception) {}
    _traktCalendarFlow.value = list
  }

  @Synchronized
  fun saveTraktWatchlist(items: List<TraktWatchlistItem>) {
    _traktWatchlistFlow.value = items
    val arr = JSONArray()
    for (item in items) {
      arr.put(
        JSONObject().apply {
          put("id", item.id)
          put("title", item.title)
          put("mediaType", item.mediaType)
          put("year", item.year)
          put("posterUrl", item.posterUrl)
          put("traktId", item.traktId)
          put("imdbId", item.imdbId)
        }
      )
    }
    prefs.edit().putString(KEY_TRAKT_WATCHLIST_JSON, arr.toString()).apply()
  }

  private fun loadTraktWatchlist() {
    val raw = prefs.getString(KEY_TRAKT_WATCHLIST_JSON, null) ?: return
    val list = mutableListOf<TraktWatchlistItem>()
    try {
      val arr = JSONArray(raw)
      for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val title = obj.optString("title", "").trim()
        if (title.isBlank()) continue
        list.add(
          TraktWatchlistItem(
            id = obj.optString("id", "wl_$i"),
            title = title,
            mediaType = obj.optString("mediaType", "show"),
            year = obj.optString("year", ""),
            posterUrl = obj.optString("posterUrl", ""),
            traktId = obj.optLong("traktId", 0L),
            imdbId = obj.optString("imdbId", "")
          )
        )
      }
    } catch (_: Exception) {}
    _traktWatchlistFlow.value = list
  }

  @Synchronized
  fun replaceSingleUpNextItem(updated: UpNextItem) {
    val list = _upNextFlow.value.toMutableList()
    val idx = list.indexOfFirst {
      (updated.traktShowId > 0L && it.traktShowId == updated.traktShowId) ||
        (updated.seriesKey.isNotBlank() && it.seriesKey == updated.seriesKey) ||
        it.id == updated.id
    }
    if (idx >= 0) {
      list.removeAt(idx)
    }
    list.add(0, updated)
    saveUpNextQueue(list.take(80))
  }

  private fun loadUpNextQueue() {
    val raw = prefs.getString(KEY_UP_NEXT_JSON, null)
    if (raw.isNullOrBlank()) {
      _upNextFlow.value = emptyList()
      return
    }
    val list = mutableListOf<UpNextItem>()
    val seenSeries = mutableSetOf<String>()
    try {
      val arr = JSONArray(raw)
      for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val showTitle = obj.optString("showTitle", "").trim()
        val s = obj.optInt("seasonNumber", 1).coerceAtLeast(1)
        val e = obj.optInt("episodeNumber", 1).coerceAtLeast(1)
        if (showTitle.isBlank()) continue
        val seriesKey = obj.optString("seriesKey", "").ifBlank {
          buildNormalizedContentKey(showTitle, null, null, isSeries = false)
        }
        if (seriesKey.isNotBlank() && !seenSeries.add(seriesKey)) continue
        list.add(
          UpNextItem(
            id = obj.optString("id", UUID.randomUUID().toString()),
            seriesKey = seriesKey,
            showTitle = showTitle,
            seasonNumber = s,
            episodeNumber = e,
            episodeTitle = obj.optString("episodeTitle", ""),
            posterUrl = obj.optString("posterUrl", ""),
            watchedCount = obj.optInt("watchedCount", 0).coerceAtLeast(0),
            totalCount = obj.optInt("totalCount", 0).coerceAtLeast(0),
            traktSlug = obj.optString("traktSlug", ""),
            traktShowId = obj.optLong("traktShowId", 0L),
            imdbId = obj.optString("imdbId", ""),
            traktEpisodeUrl = obj.optString("traktEpisodeUrl", ""),
            traktEpisodeId = obj.optString("traktEpisodeId", ""),
            fromTrakt = obj.optBoolean("fromTrakt", false),
            preferredPortal = StreamingPortal.fromId(obj.optString("preferredPortal", StreamingPortal.WATCH_SERIES.id)),
            watchUrl = obj.optString("watchUrl", ""),
            updatedAtMs = obj.optLong("updatedAtMs", System.currentTimeMillis())
          )
        )
      }
    } catch (_: Exception) {}
    _upNextFlow.value = list
  }

  private fun saveUpNextQueue(list: List<UpNextItem>) {
    _upNextFlow.value = list
    val arr = JSONArray()
    for (item in list) {
      arr.put(
        JSONObject().apply {
          put("id", item.id)
          put("seriesKey", item.seriesKey)
          put("showTitle", item.showTitle)
          put("seasonNumber", item.seasonNumber)
          put("episodeNumber", item.episodeNumber)
          put("episodeTitle", item.episodeTitle)
          put("posterUrl", item.posterUrl)
          put("watchedCount", item.watchedCount)
          put("totalCount", item.totalCount)
          put("traktSlug", item.traktSlug)
          put("traktShowId", item.traktShowId)
          put("imdbId", item.imdbId)
          put("traktEpisodeUrl", item.traktEpisodeUrl)
          put("traktEpisodeId", item.traktEpisodeId)
          put("fromTrakt", item.fromTrakt)
          put("preferredPortal", item.preferredPortal.id)
          put("watchUrl", item.watchUrl)
          put("updatedAtMs", item.updatedAtMs)
        }
      )
    }
    prefs.edit().putString(KEY_UP_NEXT_JSON, arr.toString()).apply()
  }

  /**
   * Merges real Progress ("Next to Watch") items extracted from the user's logged-in Trakt account
   * while preserving user-preferred portal choices and any non-duplicate local Up Next items.
   */
  @Synchronized
  fun mergeTraktProgressItems(traktItems: List<UpNextItem>) {
    if (traktItems.isEmpty()) return
    val existingByKey = _upNextFlow.value.associateBy { it.seriesKey }
    val seenKeys = mutableSetOf<String>()
    val merged = mutableListOf<UpNextItem>()

    for (raw in traktItems) {
      val key = raw.seriesKey.ifBlank {
        buildNormalizedContentKey(raw.showTitle, null, null, isSeries = false)
      }
      if (key.isNotBlank() && !seenKeys.add(key)) continue
      val prev = existingByKey[key]
      merged.add(
        raw.copy(
          seriesKey = key,
          posterUrl = raw.posterUrl.ifBlank { prev?.posterUrl.orEmpty() },
          preferredPortal = prev?.preferredPortal ?: raw.preferredPortal,
          fromTrakt = true
        )
      )
    }

    // Keep local (non-Trakt) Up Next items that aren't already represented by Trakt
    for (local in _upNextFlow.value) {
      if (!local.fromTrakt && local.seriesKey.isNotBlank() && seenKeys.add(local.seriesKey)) {
        merged.add(local)
      }
    }

    val now = System.currentTimeMillis()
    _traktLastSyncMsFlow.value = now
    prefs.edit().putLong(KEY_TRAKT_LAST_SYNC_MS, now).apply()
    saveUpNextQueue(merged.take(80))
  }

  /**
   * Optimistically marks the given Up Next episode as watched locally and immediately advances
   * its row to the next episode (while Trakt two-way sync runs in the background to fetch the
   * exact next episode title / season rollover).
   */
  @Synchronized
  fun advanceUpNextItemLocally(item: UpNextItem) {
    val cleanShow = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(item.showTitle)
      .ifBlank { item.showTitle }
      .trim()
    val s = item.seasonNumber.coerceAtLeast(1)
    val e = item.episodeNumber.coerceAtLeast(1)
    val watchedKey = buildEpisodeWatchedKey(cleanShow, s, e)
    if (watchedKey.isNotBlank()) {
      val updatedSet = _watchedEpisodesFlow.value.toMutableSet()
      updatedSet.add(watchedKey)
      saveWatchedEpisodes(updatedSet)
    }

    val nextEp = e + 1
    val nextWatchedCount = if (item.totalCount > 0) {
      (item.watchedCount + 1).coerceAtMost(item.totalCount)
    } else {
      item.watchedCount + 1
    }
    val nextTraktUrl = if (item.traktSlug.isNotBlank()) {
      "https://trakt.tv/shows/${item.traktSlug}/seasons/$s/episodes/$nextEp"
    } else {
      ""
    }
    val advancedItem = item.copy(
      seasonNumber = s,
      episodeNumber = nextEp,
      episodeTitle = "",
      watchedCount = nextWatchedCount,
      traktEpisodeUrl = nextTraktUrl,
      traktEpisodeId = "",
      watchUrl = "",
      updatedAtMs = System.currentTimeMillis()
    )

    val seriesKey = item.seriesKey.ifBlank {
      buildNormalizedContentKey(cleanShow, null, null, isSeries = false)
    }
    val remaining = _upNextFlow.value.filterNot { existing ->
      existing.id == item.id ||
        (seriesKey.isNotBlank() && existing.seriesKey == seriesKey) ||
        existing.showTitle.equals(cleanShow, ignoreCase = true)
    }.toMutableList()

    // If all episodes in the show are now finished, remove from active queue; otherwise place at top (Recently Watched)
    if (item.totalCount <= 0 || nextWatchedCount < item.totalCount) {
      remaining.add(0, advancedItem)
    }
    saveUpNextQueue(remaining.take(80))
  }

  @Synchronized
  fun enqueueNextEpisode(
    showTitle: String,
    seasonNumber: Int,
    episodeNumber: Int,
    preferredPortal: StreamingPortal = StreamingPortal.WATCH_SERIES,
    watchUrl: String = ""
  ) {
    val cleanShow = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(showTitle).ifBlank { showTitle }.trim()
    if (cleanShow.isBlank() || episodeNumber <= 0) return
    val seriesKey = buildNormalizedContentKey(cleanShow, null, null, isSeries = false)
    val prev = _upNextFlow.value.firstOrNull { existing ->
      (seriesKey.isNotBlank() && existing.seriesKey == seriesKey) ||
        existing.showTitle.equals(cleanShow, ignoreCase = true)
    }
    val current = _upNextFlow.value.filterNot { existing ->
      (seriesKey.isNotBlank() && existing.seriesKey == seriesKey) ||
        existing.showTitle.equals(cleanShow, ignoreCase = true)
    }.toMutableList()
    val s = seasonNumber.coerceAtLeast(1)
    val e = episodeNumber.coerceAtLeast(1)
    val updatedWatched = if (prev != null && prev.totalCount > 0) {
      (prev.watchedCount + 1).coerceAtMost(prev.totalCount)
    } else {
      prev?.watchedCount ?: 0
    }
    current.add(
      0,
      UpNextItem(
        id = prev?.id ?: UUID.randomUUID().toString(),
        seriesKey = seriesKey,
        showTitle = prev?.showTitle?.ifBlank { cleanShow } ?: cleanShow,
        seasonNumber = s,
        episodeNumber = e,
        episodeTitle = "",
        posterUrl = prev?.posterUrl.orEmpty(),
        watchedCount = updatedWatched,
        totalCount = prev?.totalCount ?: 0,
        traktSlug = prev?.traktSlug.orEmpty(),
        traktEpisodeUrl = if (!prev?.traktSlug.isNullOrBlank()) {
          "https://trakt.tv/shows/${prev!!.traktSlug}/seasons/$s/episodes/$e"
        } else {
          ""
        },
        traktEpisodeId = "",
        fromTrakt = prev?.fromTrakt ?: false,
        preferredPortal = preferredPortal,
        watchUrl = watchUrl,
        updatedAtMs = System.currentTimeMillis()
      )
    )
    saveUpNextQueue(current.take(80))
  }

  @Synchronized
  fun removeUpNextItem(id: String) {
    val updated = _upNextFlow.value.filterNot { it.id == id }
    saveUpNextQueue(updated)
  }

  @Synchronized
  fun clearUpNext() {
    saveUpNextQueue(emptyList())
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

  fun addHistory(
    title: String,
    url: String,
    portal: StreamingPortal,
    seasonNumber: Int = 0,
    episodeNumber: Int = 0,
    isSeries: Boolean = false
  ) {
    if (url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
    // CRITICAL: Never persist third-party embed iframe URLs (vidtube, vidlink, vidsrc, etc.) or raw streams
    // as Watch History entries — Resume Playback must always reopen the canonical Portal Watch Page!
    if (StreamingPortal.isEmbedIframeUrl(url)) return
    val lowerUrl = url.lowercase()
    // Skip transient search URLs so Continue Watching only shows real movies & series episodes
    if (lowerUrl.contains("/search/") ||
      lowerUrl.contains("/search?") ||
      lowerUrl.contains("?s=") ||
      lowerUrl.contains("&s=") ||
      lowerUrl.contains("search.php")
    ) return

    val canonicalUrl = StreamingPortal.sanitizePortalWatchUrl(url, portal)
    val rawTitle = title.ifBlank { canonicalUrl }

    val parsedInfo = SearchAndSubtitlesService.formatStandardizedEpisodeInfo(
      seriesTitle = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(rawTitle),
      pageTitle = rawTitle,
      lastSearchQuery = "",
      watchPageUrl = canonicalUrl,
      embedUrl = "",
      serverName = "",
      isSeriesHint = isSeries || seasonNumber > 0 || episodeNumber > 0,
      selectedSeasonHint = seasonNumber,
      currentEpisodeHint = episodeNumber
    )

    val resolvedIsSeries = isSeries || parsedInfo.isSeries || seasonNumber > 0 || episodeNumber > 0
    val resolvedSeason = when {
      seasonNumber > 0 -> seasonNumber
      resolvedIsSeries && (parsedInfo.seasonNumber ?: 0) > 0 -> parsedInfo.seasonNumber ?: 1
      else -> 0
    }
    val resolvedEpisode = when {
      episodeNumber > 0 -> episodeNumber
      resolvedIsSeries && (parsedInfo.episodeNumber ?: 0) > 0 -> parsedInfo.episodeNumber ?: 1
      else -> 0
    }
    val cleanDisplayTitle = if (parsedInfo.cleanTitle.isNotBlank() &&
      !parsedInfo.cleanTitle.equals("Asser TV", ignoreCase = true)
    ) {
      parsedInfo.cleanTitle
    } else {
      SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(rawTitle).ifBlank { rawTitle }
    }

    val incomingShowKey = if (resolvedIsSeries) {
      buildNormalizedContentKey(cleanDisplayTitle, null, null, isSeries = false)
    } else {
      ""
    }

    // UNIFIED CONTINUE WATCHING: deduplicate EVERY series into ONE single card across ALL portals & episodes
    val current = _historyFlow.value.filterNot { existing ->
      if (existing.url == canonicalUrl) return@filterNot true
      if (resolvedIsSeries && incomingShowKey.isNotBlank()) {
        val existingBase = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(existing.title)
          .ifBlank { existing.title }
        val existingKey = buildNormalizedContentKey(existingBase, null, null, isSeries = false)
        existingKey.isNotBlank() && existingKey == incomingShowKey
      } else {
        false
      }
    }.toMutableList()

    current.add(
      0,
      HistoryEntry(
        id = UUID.randomUUID().toString(),
        title = cleanDisplayTitle,
        url = canonicalUrl,
        portal = portal,
        seasonNumber = resolvedSeason,
        episodeNumber = resolvedEpisode,
        isSeries = resolvedIsSeries
      )
    )
    // Keep last 40 visited entries
    val trimmed = current.take(40)
    saveHistory(trimmed)
  }

  fun removeHistoryEntry(id: String) {
    val current = _historyFlow.value.filterNot { it.id == id }
    saveHistory(current)
  }

  fun clearHistory() {
    saveHistory(emptyList())
  }

  private fun loadBookmarks() {
    val raw = prefs.getString(KEY_BOOKMARKS_JSON, null)
    if (raw == null) {
      _bookmarksFlow.value = emptyList()
      return
    }

    val list = mutableListOf<Bookmark>()
    try {
      val jsonArray = JSONArray(raw)
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        list.add(
          Bookmark(
            id = obj.getString("id"),
            title = obj.getString("title"),
            url = obj.getString("url"),
            portal = StreamingPortal.fromId(obj.getString("portal")),
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
      val obj = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("url", item.url)
        put("portal", item.portal.id)
        put("timestamp", item.timestamp)
      }
      jsonArray.put(obj)
    }
    prefs.edit().putString(KEY_BOOKMARKS_JSON, jsonArray.toString()).apply()
  }

  private fun loadHistory() {
    val raw = prefs.getString(KEY_HISTORY_JSON, null)
    if (raw == null) {
      _historyFlow.value = emptyList()
      return
    }

    val list = mutableListOf<HistoryEntry>()
    val seenSeriesPortalKeys = mutableSetOf<String>()
    try {
      val jsonArray = JSONArray(raw)
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        val portal = StreamingPortal.fromId(obj.getString("portal"))
        val rawUrl = obj.getString("url")
        val sanitizedUrl = StreamingPortal.sanitizePortalWatchUrl(rawUrl, portal)
        val rawTitle = obj.getString("title")
        val storedSeason = obj.optInt("seasonNumber", 0)
        val storedEpisode = obj.optInt("episodeNumber", 0)
        val storedIsSeries = obj.optBoolean("isSeries", false)

        val parsedInfo = if (storedSeason <= 0 || storedEpisode <= 0) {
          SearchAndSubtitlesService.formatStandardizedEpisodeInfo(
            seriesTitle = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(rawTitle),
            pageTitle = rawTitle,
            lastSearchQuery = "",
            watchPageUrl = sanitizedUrl,
            embedUrl = "",
            serverName = "",
            isSeriesHint = storedIsSeries || storedSeason > 0 || storedEpisode > 0,
            selectedSeasonHint = storedSeason,
            currentEpisodeHint = storedEpisode
          )
        } else {
          null
        }

        val resolvedIsSeries = storedIsSeries || (parsedInfo?.isSeries == true) || storedSeason > 0 || storedEpisode > 0
        val resolvedSeason = if (storedSeason > 0) storedSeason else if (resolvedIsSeries) (parsedInfo?.seasonNumber ?: 0) else 0
        val resolvedEpisode = if (storedEpisode > 0) storedEpisode else if (resolvedIsSeries) (parsedInfo?.episodeNumber ?: 0) else 0
        val cleanTitle = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(rawTitle).ifBlank { rawTitle }

        if (resolvedIsSeries) {
          // Cross-portal unified deduplication: one single Continue Watching card per series
          val showKey = buildNormalizedContentKey(cleanTitle, null, null, isSeries = false)
          if (showKey.isNotBlank() && !seenSeriesPortalKeys.add(showKey)) {
            continue
          }
        }

        list.add(
          HistoryEntry(
            id = obj.getString("id"),
            title = cleanTitle,
            url = sanitizedUrl,
            portal = portal,
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            seasonNumber = resolvedSeason,
            episodeNumber = resolvedEpisode,
            isSeries = resolvedIsSeries
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
      val obj = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("url", item.url)
        put("portal", item.portal.id)
        put("timestamp", item.timestamp)
        put("seasonNumber", item.seasonNumber)
        put("episodeNumber", item.episodeNumber)
        put("isSeries", item.isSeries)
      }
      jsonArray.put(obj)
    }
    prefs.edit().putString(KEY_HISTORY_JSON, jsonArray.toString()).apply()
  }

  fun getGitHubRepo(): String {
    val saved = prefs.getString(KEY_GITHUB_REPO, DEFAULT_GITHUB_REPO) ?: DEFAULT_GITHUB_REPO
    return if (saved.equals("Savant47/cinema-tv", ignoreCase = true) || saved.isBlank()) {
      DEFAULT_GITHUB_REPO
    } else {
      saved
    }
  }

  fun setGitHubRepo(repo: String) {
    prefs.edit().putString(KEY_GITHUB_REPO, repo.trim()).apply()
  }

  fun getGitHubToken(): String {
    return prefs.getString(KEY_GITHUB_TOKEN, "") ?: ""
  }

  fun setGitHubToken(token: String) {
    prefs.edit().putString(KEY_GITHUB_TOKEN, token.trim()).apply()
  }

  /**
   * Exports all user data (history, bookmarks, display mode, portal configs) to a backup JSON string.
   */
  fun exportUserDataJson(): String {
    val root = JSONObject()
    root.put("version", 1)
    root.put("timestamp", System.currentTimeMillis())
    root.put("bookmarks", prefs.getString(KEY_BOOKMARKS_JSON, "[]"))
    root.put("history", prefs.getString(KEY_HISTORY_JSON, "[]"))
    root.put("playback_progress", prefs.getString(KEY_PLAYBACK_PROGRESS_JSON, "[]"))
    root.put("display_mode", prefs.getString(KEY_DISPLAY_MODE, AppDisplayMode.MOBILE.name))
    root.put("active_portal", prefs.getString(KEY_ACTIVE_PORTAL, StreamingPortal.TOP_CINEMA.id))
    root.put("github_repo", getGitHubRepo())
    root.put("preferred_video_quality", getPreferredQuality())
    root.put("video_resize_mode", getVideoResizeMode())
    val sub = getSubtitleStyle()
    root.put("sub_text_color", sub.textColorArgb)
    root.put("sub_bg_color", sub.backgroundColorArgb)
    root.put("sub_font_size", sub.fontSizeSp.toDouble())
    root.put("sub_bottom_offset", sub.bottomOffsetDp)
    root.put("sub_edge_style", sub.edgeStyle.id)
    root.put("sub_sync_offset", sub.syncOffsetMs)
    root.put("watched_episodes", prefs.getString(KEY_WATCHED_EPISODES_JSON, "[]"))
    root.put("up_next_queue", prefs.getString(KEY_UP_NEXT_JSON, "[]"))
    root.put("audio_boost_percent", getAudioBoostPercent())
    root.put("strict_iframe_sandbox", isStrictIframeSandboxEnabled())
    root.put("blocked_ads_count", _blockedAdsCountFlow.value)
    return root.toString(2)
  }

  /**
   * Restores user data from a backup JSON string if needed.
   */
  fun restoreUserDataJson(jsonString: String): Boolean {
    return try {
      val root = JSONObject(jsonString)
      val editor = prefs.edit()
      if (root.has("bookmarks")) editor.putString(KEY_BOOKMARKS_JSON, root.getString("bookmarks"))
      if (root.has("history")) editor.putString(KEY_HISTORY_JSON, root.getString("history"))
      if (root.has("playback_progress")) editor.putString(KEY_PLAYBACK_PROGRESS_JSON, root.getString("playback_progress"))
      if (root.has("display_mode")) editor.putString(KEY_DISPLAY_MODE, root.getString("display_mode"))
      if (root.has("active_portal")) editor.putString(KEY_ACTIVE_PORTAL, root.getString("active_portal"))
      if (root.has("github_repo")) editor.putString(KEY_GITHUB_REPO, root.getString("github_repo"))
      if (root.has("preferred_video_quality")) editor.putString(KEY_PREFERRED_VIDEO_QUALITY, normalizeQualityId(root.getString("preferred_video_quality")))
      if (root.has("video_resize_mode")) editor.putString(KEY_VIDEO_RESIZE_MODE, normalizeVideoResizeMode(root.getString("video_resize_mode")))
      if (root.has("sub_text_color")) editor.putLong(KEY_SUB_TEXT_COLOR, root.getLong("sub_text_color"))
      if (root.has("sub_bg_color")) editor.putLong(KEY_SUB_BG_COLOR, root.getLong("sub_bg_color"))
      if (root.has("sub_font_size")) editor.putFloat(KEY_SUB_FONT_SIZE, root.getDouble("sub_font_size").toFloat())
      if (root.has("sub_bottom_offset")) editor.putInt(KEY_SUB_BOTTOM_OFFSET, root.getInt("sub_bottom_offset"))
      if (root.has("sub_edge_style")) editor.putString(KEY_SUB_EDGE_STYLE, root.getString("sub_edge_style"))
      if (root.has("sub_sync_offset")) editor.putLong(KEY_SUB_SYNC_OFFSET, root.getLong("sub_sync_offset"))
      if (root.has("watched_episodes")) editor.putString(KEY_WATCHED_EPISODES_JSON, root.getString("watched_episodes"))
      if (root.has("up_next_queue")) editor.putString(KEY_UP_NEXT_JSON, root.getString("up_next_queue"))
      if (root.has("audio_boost_percent")) editor.putInt(KEY_AUDIO_BOOST_PERCENT, root.getInt("audio_boost_percent").coerceIn(0, 300))
      if (root.has("strict_iframe_sandbox")) editor.putBoolean(KEY_STRICT_IFRAME_SANDBOX, root.getBoolean("strict_iframe_sandbox"))
      if (root.has("blocked_ads_count")) editor.putInt(KEY_BLOCKED_ADS_COUNT, root.getInt("blocked_ads_count").coerceAtLeast(0))
      editor.commit()
      loadBookmarks()
      loadHistory()
      loadDisplayMode()
      loadSubtitleStyle()
      loadPreferredQuality()
      loadVideoResizeMode()
      loadWatchedEpisodes()
      loadUpNextQueue()
      _audioBoostPercentFlow.value = prefs.getInt(KEY_AUDIO_BOOST_PERCENT, 100).coerceIn(0, 300)
      _strictIframeSandboxFlow.value = prefs.getBoolean(KEY_STRICT_IFRAME_SANDBOX, true)
      _blockedAdsCountFlow.value = prefs.getInt(KEY_BLOCKED_ADS_COUNT, 0).coerceAtLeast(0)
      true
    } catch (_: Exception) {
      false
    }
  }

  companion object {
    const val ADMIN_PASSWORD = "ahayabasharya89a"

    fun verifyAdminPassword(input: String): Boolean {
      return input.trim() == ADMIN_PASSWORD
    }

    fun buildEpisodeWatchedKey(showTitle: String, seasonNumber: Int, episodeNumber: Int): String {
      if (showTitle.isBlank() || episodeNumber <= 0) return ""
      return buildNormalizedContentKey(
        rawTitle = showTitle,
        seasonNumber = seasonNumber.coerceAtLeast(1),
        episodeNumber = episodeNumber.coerceAtLeast(1),
        isSeries = true
      )
    }

    private const val KEY_BOOKMARKS_JSON = "bookmarks_json"
    private const val KEY_HISTORY_JSON = "history_json"
    private const val KEY_PLAYBACK_PROGRESS_JSON = "playback_progress_json_v1"
    private const val KEY_DISPLAY_MODE = "display_mode"
    private const val KEY_HAS_SELECTED_MODE = "has_selected_asser_browser_mode_v2"
    private const val KEY_CONTROL_MODE = "control_mode"
    private const val KEY_ACTIVE_PORTAL = "active_portal"
    private const val KEY_AD_BLOCK = "ad_block_enabled"
    private const val KEY_ADGUARD_DNS = "adguard_dns_enabled"
    private const val KEY_STRICT_POPUP_BLOCKER = "strict_popup_blocker_enabled"
    private const val KEY_STRICT_IFRAME_SANDBOX = "strict_iframe_sandbox_enabled_v1"
    private const val KEY_BLOCKED_ADS_COUNT = "blocked_ads_count_v1"
    private const val KEY_AUDIO_BOOST_PERCENT = "audio_boost_percent_v1"
    private const val KEY_WATCHED_EPISODES_JSON = "watched_episodes_json_v1"
    private const val KEY_UP_NEXT_JSON = "up_next_queue_json_v1"
    private const val KEY_TRAKT_CLIENT_ID = "trakt_client_id_v2"
    private const val KEY_TRAKT_CLIENT_SECRET = "trakt_client_secret_v2"
    private const val KEY_TRAKT_ACCESS_TOKEN = "trakt_access_token_v2"
    private const val KEY_TRAKT_REFRESH_TOKEN = "trakt_refresh_token_v2"
    private const val KEY_TRAKT_TOKEN_EXPIRES_AT_MS = "trakt_token_expires_at_ms_v2"
    private const val KEY_TRAKT_USERNAME = "trakt_username_v1"
    private const val KEY_TRAKT_LAST_SYNC_MS = "trakt_last_sync_ms_v1"
    private const val KEY_TRAKT_CALENDAR_JSON = "trakt_calendar_json_v2"
    private const val KEY_TRAKT_WATCHLIST_JSON = "trakt_watchlist_json_v2"
    private const val KEY_GITHUB_REPO = "github_repo_slug"
    private const val KEY_GITHUB_TOKEN = "github_pat_token"
    private const val KEY_SUB_TEXT_COLOR = "subtitle_text_color_argb"
    private const val KEY_SUB_BG_COLOR = "subtitle_bg_color_argb"
    private const val KEY_SUB_FONT_SIZE = "subtitle_font_size_sp"
    private const val KEY_SUB_BOTTOM_OFFSET = "subtitle_bottom_offset_dp"
    private const val KEY_SUB_EDGE_STYLE = "subtitle_edge_style_v1"
    private const val KEY_SUB_SYNC_OFFSET = "subtitle_sync_offset_ms"
    private const val KEY_PREFERRED_VIDEO_QUALITY = "preferred_video_quality_v1"
    private const val KEY_AUTO_PLAY_NEXT_EPISODE = "auto_play_next_episode_enabled"
    private const val KEY_VIDEO_RESIZE_MODE = "video_resize_mode_v1"
    const val QUALITY_LOWEST = "lowest"
    const val QUALITY_AUTO = "auto"
    const val RESIZE_MODE_FILL = "fill"
    const val RESIZE_MODE_STRETCH = "stretch"
    const val RESIZE_MODE_FIT = "fit"
    const val DEFAULT_GITHUB_REPO = "savant47/Apk-apps"

    fun normalizeVideoResizeMode(raw: String?): String {
      return when (raw?.trim()?.lowercase()) {
        RESIZE_MODE_STRETCH, "fill_stretch" -> RESIZE_MODE_STRETCH
        RESIZE_MODE_FIT, "original" -> RESIZE_MODE_FIT
        RESIZE_MODE_FILL, "zoom", "crop" -> RESIZE_MODE_FILL
        else -> RESIZE_MODE_FILL
      }
    }

    fun normalizeQualityId(raw: String?): String {
      val cleaned = raw?.trim()?.lowercase()?.removeSuffix("p") ?: return QUALITY_LOWEST
      return when {
        cleaned.isBlank() || cleaned == "lowest" || cleaned == "data_saver" -> QUALITY_LOWEST
        cleaned == "auto" -> QUALITY_AUTO
        cleaned.toIntOrNull() != null -> cleaned
        else -> QUALITY_LOWEST
      }
    }

    fun qualityShortLabel(qualityId: String): String {
      return when (normalizeQualityId(qualityId)) {
        QUALITY_LOWEST -> "أقل جودة (توفير الباقة)"
        QUALITY_AUTO -> "تلقائي Auto"
        else -> "${qualityId.removeSuffix("p")}p"
      }
    }

    /**
     * Builds a portal-independent, server-independent normalized content key so playback
     * progress seamlessly carries across TopCinema, WatchSeries, LaRoza, EgyDead, and all servers.
     * Examples:
     *   - "مسلسل VisionQuest الموسم الأول الحلقة 1 مترجم" -> "series:visionquest:s1:e1"
     *   - "VisionQuest - S1E1" -> "series:visionquest:s1:e1"
     *   - "فيلم A Talent for Murder 2026 مترجم اون لاين" -> "movie:a talent for murder"
     */
    fun buildNormalizedContentKey(
      rawTitle: String,
      seasonNumber: Int?,
      episodeNumber: Int?,
      isSeries: Boolean
    ): String {
      if (rawTitle.isBlank()) return ""
      var cleaned = rawTitle.lowercase()
        // Remove Arabic diacritics
        .replace(Regex("[\u064B-\u065F\u0670]"), "")
        // Normalize Arabic letter variants
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ة', 'ه')
        .replace('ى', 'ي')
        // Strip season/episode markers from title so S1E1 & "الموسم 1 الحلقة 1" yield identical base title
        .replace(Regex("\\bs\\d{1,2}\\s*e\\d{1,4}\\b"), " ")
        .replace(Regex("\\b(?:season|episode|ep|halka)\\s*\\d{1,4}\\b"), " ")
        .replace(Regex("(?:الموسم|موسم)\\s*(?:الاول|الثاني|الثالث|الرابع|الخامس|السادس|السابع|الثامن|التاسع|العاشر|\\d{1,2})"), " ")
        .replace(Regex("(?:الحلقه|حلقه)\\s*\\d{1,4}"), " ")
        // Strip years like 2024, (2026)
        .replace(Regex("\\b(?:19|20)\\d{2}\\b"), " ")
        // Strip Arabic/English boilerplate streaming words
        .replace(
          Regex("(?:^|\\s)(?:مشاهده|تحميل|فيلم|مسلسل|مترجم|مترجمه|مدبلج|مدبلجه|اون|لاين|اونلاين|كامل|كامله|والاخيره|الاخيره|بجوده|عاليه|توب|سينما|لاروزا|ايجي|ديد|watch|online|free|hd|fhd|uhd|4k|1080p|720p|480p|web-dl|webrip|bluray|topcinema|watchseries|laroza|egydead)(?=\\s|$)"),
          " "
        )
        // Keep only letters (Latin & Arabic), digits, and spaces
        .replace(Regex("[^a-z0-9\\u0621-\\u064A]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

      if (cleaned.isBlank()) {
        cleaned = rawTitle.lowercase().replace(Regex("[^a-z0-9\\u0621-\\u064A]+"), "").take(48)
      }
      if (cleaned.isBlank()) return ""

      return if (isSeries || (episodeNumber != null && episodeNumber > 0)) {
        val s = (seasonNumber ?: 1).coerceAtLeast(1)
        val e = (episodeNumber ?: 1).coerceAtLeast(1)
        "series:$cleaned:s$s:e$e"
      } else {
        "movie:$cleaned"
      }
    }
  }
}
