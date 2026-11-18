package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.Bookmark
import com.example.model.ControlMode
import com.example.model.HistoryEntry
import com.example.model.StreamingPortal
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

class TvPreferencesRepository(context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences("cinema_tv_prefs", Context.MODE_PRIVATE)

  private val _bookmarksFlow = MutableStateFlow<List<Bookmark>>(emptyList())
  val bookmarksFlow: StateFlow<List<Bookmark>> = _bookmarksFlow.asStateFlow()

  private val _historyFlow = MutableStateFlow<List<HistoryEntry>>(emptyList())
  val historyFlow: StateFlow<List<HistoryEntry>> = _historyFlow.asStateFlow()

  private val _displayModeFlow = MutableStateFlow<AppDisplayMode>(AppDisplayMode.MOBILE)
  val displayModeFlow: StateFlow<AppDisplayMode> = _displayModeFlow.asStateFlow()

  init {
    loadBookmarks()
    loadHistory()
    loadDisplayMode()
    loadPortals()
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
    try {
      val jsonArray = JSONArray(raw)
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        list.add(
          HistoryEntry(
            id = obj.getString("id"),
            title = obj.getString("title"),
            url = obj.getString("url"),
            portal = StreamingPortal.fromId(obj.getString("portal")),
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
      val obj = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("url", item.url)
        put("portal", item.portal.id)
        put("timestamp", item.timestamp)
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
    root.put("display_mode", prefs.getString(KEY_DISPLAY_MODE, AppDisplayMode.MOBILE.name))
    root.put("active_portal", prefs.getString(KEY_ACTIVE_PORTAL, StreamingPortal.TOP_CINEMA.id))
    root.put("github_repo", getGitHubRepo())
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
      if (root.has("display_mode")) editor.putString(KEY_DISPLAY_MODE, root.getString("display_mode"))
      if (root.has("active_portal")) editor.putString(KEY_ACTIVE_PORTAL, root.getString("active_portal"))
      if (root.has("github_repo")) editor.putString(KEY_GITHUB_REPO, root.getString("github_repo"))
      editor.apply()
      loadBookmarks()
      loadHistory()
      loadDisplayMode()
      true
    } catch (_: Exception) {
      false
    }
  }

  companion object {
    private const val KEY_BOOKMARKS_JSON = "bookmarks_json"
    private const val KEY_HISTORY_JSON = "history_json"
    private const val KEY_DISPLAY_MODE = "display_mode"
    private const val KEY_HAS_SELECTED_MODE = "has_selected_asser_browser_mode_v2"
    private const val KEY_CONTROL_MODE = "control_mode"
    private const val KEY_ACTIVE_PORTAL = "active_portal"
    private const val KEY_AD_BLOCK = "ad_block_enabled"
    private const val KEY_ADGUARD_DNS = "adguard_dns_enabled"
    private const val KEY_STRICT_POPUP_BLOCKER = "strict_popup_blocker_enabled"
    private const val KEY_GITHUB_REPO = "github_repo_slug"
    private const val KEY_GITHUB_TOKEN = "github_pat_token"
    const val DEFAULT_GITHUB_REPO = "savant47/Apk-apps"
  }
}
