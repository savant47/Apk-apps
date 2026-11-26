package com.example.ui

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.AppRuntimeMemoryGuard
import com.example.data.AppDiagnosticsLogger
import com.example.data.TraktSyncResult
import com.example.data.TraktSyncService
import com.example.data.TvPreferencesRepository
import com.example.model.StreamingPortal
import com.example.model.TraktParsedLink
import com.example.model.UpNextItem
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaFocusGlow
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.ClipboxWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

object TraktUrlParser {
  private val showEpisodeRegex = Regex(
    """(?:https?://)?(?:www\.|app\.)?trakt\.tv/shows/([^/?#]+)/seasons/(\d+)/episodes/(\d+)""",
    RegexOption.IGNORE_CASE
  )
  private val showSeasonRegex = Regex(
    """(?:https?://)?(?:www\.|app\.)?trakt\.tv/shows/([^/?#]+)/seasons/(\d+)""",
    RegexOption.IGNORE_CASE
  )
  private val showOnlyRegex = Regex(
    """(?:https?://)?(?:www\.|app\.)?trakt\.tv/shows/([^/?#]+)""",
    RegexOption.IGNORE_CASE
  )
  private val movieRegex = Regex(
    """(?:https?://)?(?:www\.|app\.)?trakt\.tv/movies/([^/?#]+)""",
    RegexOption.IGNORE_CASE
  )

  fun slugToTitle(slug: String): String {
    val cleanSlug = slug
      .substringBefore('?')
      .substringBefore('#')
      .trim('/')
      .replace(Regex("-(?:19|20)\\d{2}$"), "")
    return cleanSlug
      .split('-', '_')
      .filter { it.isNotBlank() }
      .joinToString(" ") { word ->
        word.replaceFirstChar { ch ->
          if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
        }
      }
      .trim()
  }

  fun parse(url: String?): TraktParsedLink? {
    if (url.isNullOrBlank()) return null
    val trimmed = url.trim()

    showEpisodeRegex.find(trimmed)?.let { m ->
      val slug = m.groupValues[1]
      val s = m.groupValues[2].toIntOrNull()?.coerceAtLeast(1) ?: 1
      val e = m.groupValues[3].toIntOrNull()?.coerceAtLeast(1) ?: 1
      val title = slugToTitle(slug)
      if (title.isNotBlank()) {
        return TraktParsedLink(
          slug = slug,
          showTitle = title,
          seasonNumber = s,
          episodeNumber = e,
          isMovie = false,
          originalUrl = trimmed
        )
      }
    }

    showSeasonRegex.find(trimmed)?.let { m ->
      val slug = m.groupValues[1]
      val s = m.groupValues[2].toIntOrNull()?.coerceAtLeast(1) ?: 1
      val title = slugToTitle(slug)
      if (title.isNotBlank()) {
        return TraktParsedLink(
          slug = slug,
          showTitle = title,
          seasonNumber = s,
          episodeNumber = 1,
          isMovie = false,
          originalUrl = trimmed
        )
      }
    }

    movieRegex.find(trimmed)?.let { m ->
      val slug = m.groupValues[1]
      val title = slugToTitle(slug)
      if (title.isNotBlank()) {
        return TraktParsedLink(
          slug = slug,
          showTitle = title,
          seasonNumber = null,
          episodeNumber = null,
          isMovie = true,
          originalUrl = trimmed
        )
      }
    }

    showOnlyRegex.find(trimmed)?.let { m ->
      val slug = m.groupValues[1]
      if (slug.lowercase() in setOf("trending", "popular", "favorited", "watched", "collected", "anticipated", "boxoffice")) {
        return null
      }
      val title = slugToTitle(slug)
      if (title.isNotBlank()) {
        return TraktParsedLink(
          slug = slug,
          showTitle = title,
          seasonNumber = 1,
          episodeNumber = 1,
          isMovie = false,
          originalUrl = trimmed
        )
      }
    }

    return null
  }
}

private enum class TraktViewMode {
  NEXT_TO_WATCH,
  ACCOUNT_WEBVIEW
}

private enum class TraktSortMode(val label: String) {
  RECENTLY_WATCHED("الأحدث مشاهدة"),
  FEWEST_LEFT("الأقل متبقي")
}

private class AsserTraktJsBridge(
  private val onPayload: (String) -> Unit
) {
  private val mainHandler = Handler(Looper.getMainLooper())

  @JavascriptInterface
  fun onTraktExtracted(rawJson: String?) {
    if (rawJson.isNullOrBlank()) return
    mainHandler.post {
      onPayload(rawJson)
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TraktPortalScreen(
  repository: TvPreferencesRepository,
  traktSyncService: TraktSyncService,
  onPlayTraktTarget: (
    showTitle: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    isMovie: Boolean,
    preferredPortal: StreamingPortal
  ) -> Unit,
  onMarkUpNextItemWatched: (UpNextItem) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val upNextItems by repository.upNextFlow.collectAsState()
  val traktUsername by repository.traktUsernameFlow.collectAsState()

  // Default to the native Next-to-Watch table if we already synced items; otherwise open Trakt Account to sign in & extract
  var activeMode by remember {
    mutableStateOf(
      if (repository.upNextFlow.value.isNotEmpty()) TraktViewMode.NEXT_TO_WATCH
      else TraktViewMode.ACCOUNT_WEBVIEW
    )
  }
  var sortMode by remember { mutableStateOf(TraktSortMode.RECENTLY_WATCHED) }
  var selectedLaunchPortal by remember { mutableStateOf(StreamingPortal.WATCH_SERIES) }
  var isSyncingCookie by remember { mutableStateOf(false) }
  var markingItemId by remember { mutableStateOf<String?>(null) }
  var extractedCountBanner by remember { mutableIntStateOf(0) }

  // WebView state (only active in ACCOUNT_WEBVIEW mode)
  var traktWebViewRef by remember { mutableStateOf<WebView?>(null) }
  var currentTraktUrl by remember { mutableStateOf("https://trakt.tv/users/me/progress") }
  var loadProgress by remember { mutableIntStateOf(0) }
  var detectedLink by remember { mutableStateOf<TraktParsedLink?>(null) }

  // Collapsible manual/paste Trakt launcher bar
  var showManualBar by remember { mutableStateOf(false) }
  var manualInput by remember { mutableStateOf("") }
  var manualSeason by remember { mutableStateOf("1") }
  var manualEpisode by remember { mutableStateOf("1") }

  fun safelyDestroyTraktWebView() {
    val wv = traktWebViewRef ?: return
    traktWebViewRef = null
    try {
      wv.stopLoading()
      wv.webChromeClient = null
      wv.webViewClient = WebViewClient()
      wv.removeJavascriptInterface("AsserTraktBridge")
      wv.visibility = View.GONE
      val parent = wv.parent as? ViewGroup
      parent?.removeView(wv)
      wv.removeAllViews()
      wv.destroy()
      AppDiagnosticsLogger.recordBreadcrumb(context, "TRAKT_WEBVIEW_DESTROYED", "Freed Trakt WebView RAM")
    } catch (_: Throwable) {}
  }

  // CRITICAL Android 9 TV RAM protection: immediately destroy Trakt WebView when switching back to table or leaving tab
  DisposableEffect(activeMode) {
    onDispose {
      if (activeMode == TraktViewMode.ACCOUNT_WEBVIEW) {
        safelyDestroyTraktWebView()
      }
    }
  }

  fun captureTraktCookiesAndTriggerDomExtract(wv: WebView?, showFeedbackIfEmpty: Boolean = false) {
    if (wv == null) return
    try {
      val cookie = CookieManager.getInstance().getCookie("https://trakt.tv").orEmpty()
      if (cookie.isNotBlank()) {
        repository.saveTraktSession(cookie = cookie)
      }
    } catch (_: Throwable) {}
    try {
      wv.evaluateJavascript(TraktSyncService.TRAKT_DOM_EXTRACTOR_JS) { result ->
        if (showFeedbackIfEmpty) {
          val count = result?.trim()?.toIntOrNull() ?: 0
          if (count <= 0) {
            Toast.makeText(
              context,
              "افتح صفحة Progress في حسابك بـ Trakt ثم اضغط «استخراج الحلقات»",
              Toast.LENGTH_SHORT
            ).show()
          }
        }
      }
    } catch (_: Throwable) {}
  }

  fun syncFromSavedCookie() {
    val cookie = repository.getTraktCookie()
    val userHint = repository.getTraktUsername()
    if (cookie.isBlank()) {
      Toast.makeText(context, "سجّل دخولك أولاً في تبويب «حسابي في Trakt» للمزامنة", Toast.LENGTH_SHORT).show()
      activeMode = TraktViewMode.ACCOUNT_WEBVIEW
      return
    }
    if (isSyncingCookie) return
    isSyncingCookie = true
    scope.launch {
      try {
        when (val res = traktSyncService.fetchProgressViaCookie(cookie, userHint)) {
          is TraktSyncResult.Success -> {
            repository.saveTraktSession(
              username = res.username,
              csrfToken = res.csrfToken
            )
            if (res.items.isNotEmpty()) {
              repository.mergeTraktProgressItems(res.items)
              Toast.makeText(
                context,
                "تمت مزامنة ${res.items.size} مسلسل من Trakt ✓",
                Toast.LENGTH_SHORT
              ).show()
            } else {
              Toast.makeText(
                context,
                "افتح صفحة Progress داخل «حسابي في Trakt» لسحب الحلقات",
                Toast.LENGTH_SHORT
              ).show()
            }
          }
          TraktSyncResult.NotLoggedIn -> {
            Toast.makeText(context, "انتهت جلسة Trakt — سجّل دخولك مجدداً", Toast.LENGTH_SHORT).show()
            activeMode = TraktViewMode.ACCOUNT_WEBVIEW
          }
          is TraktSyncResult.Error -> {
            Toast.makeText(context, "تعذر التحديث: ${res.message}", Toast.LENGTH_SHORT).show()
          }
        }
      } finally {
        isSyncingCookie = false
      }
    }
  }

  fun launchManualEntry() {
    val raw = manualInput.trim()
    if (raw.isBlank()) {
      Toast.makeText(context, "اكتب اسم المسلسل أو الصق رابط Trakt", Toast.LENGTH_SHORT).show()
      return
    }
    val parsed = TraktUrlParser.parse(raw)
    if (parsed != null) {
      onPlayTraktTarget(
        parsed.showTitle,
        parsed.seasonNumber,
        parsed.episodeNumber,
        parsed.isMovie,
        selectedLaunchPortal
      )
    } else {
      val s = manualSeason.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
      val e = manualEpisode.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
      onPlayTraktTarget(raw, s, e, false, selectedLaunchPortal)
    }
  }

  val displayedItems = remember(upNextItems, sortMode) {
    when (sortMode) {
      TraktSortMode.RECENTLY_WATCHED -> upNextItems
      TraktSortMode.FEWEST_LEFT -> upNextItems.sortedWith(
        compareBy<UpNextItem> { item ->
          if (item.totalCount > 0) (item.totalCount - item.watchedCount).coerceAtLeast(0)
          else Int.MAX_VALUE
        }.thenByDescending { it.updatedAtMs }
      )
    }
  }

  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Column(
      modifier = modifier
        .fillMaxSize()
        .background(CinemaDarkBackground)
        .testTag("trakt_portal_screen")
    ) {
      // Top Trakt Header: Primary Mode Switcher (Next to Watch Table vs My Trakt Account WebView)
      Surface(
        color = CinemaSurface,
        border = BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            TraktQuickChip(
              label = if (upNextItems.isNotEmpty()) "التالي للمشاهدة (${upNextItems.size})" else "التالي للمشاهدة",
              icon = Icons.Default.QueuePlayNext,
              isActive = activeMode == TraktViewMode.NEXT_TO_WATCH,
              onClick = { activeMode = TraktViewMode.NEXT_TO_WATCH },
              testTag = "btn_trakt_mode_next_to_watch"
            )

            TraktQuickChip(
              label = if (traktUsername.isNotBlank()) "حسابي (@$traktUsername)" else "حسابي في Trakt",
              icon = Icons.Default.AccountCircle,
              isActive = activeMode == TraktViewMode.ACCOUNT_WEBVIEW,
              onClick = { activeMode = TraktViewMode.ACCOUNT_WEBVIEW },
              testTag = "btn_trakt_mode_account"
            )

            // Live Cookie Sync Button (works without opening WebView!)
            Surface(
              onClick = { syncFromSavedCookie() },
              color = CinemaDarkBackground,
              shape = RoundedCornerShape(50),
              border = BorderStroke(1.dp, CinemaSuccess.copy(alpha = 0.75f)),
              modifier = Modifier
                .heightIn(min = 40.dp)
                .testTag("btn_trakt_sync_now")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
              ) {
                if (isSyncingCookie) {
                  CircularProgressIndicator(
                    color = CinemaSuccess,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                  )
                } else {
                  Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "مزامنة Trakt",
                    tint = CinemaSuccess,
                    modifier = Modifier.size(17.dp)
                  )
                }
                Text(
                  text = if (isSyncingCookie) "جاري المزامنة..." else "مزامنة",
                  color = CinemaSuccess,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.ExtraBold,
                  maxLines = 1,
                  softWrap = false
                )
              }
            }

            // Launch Source Selector (WatchSeries / TopCinema)
            listOf(StreamingPortal.WATCH_SERIES, StreamingPortal.TOP_CINEMA).forEach { portal ->
              val isSelected = selectedLaunchPortal == portal
              Surface(
                onClick = { selectedLaunchPortal = portal },
                color = if (isSelected) ClipboxWhite else CinemaDarkBackground,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, if (isSelected) ClipboxWhite else CinemaBorder),
                modifier = Modifier
                  .heightIn(min = 38.dp)
                  .testTag("btn_trakt_source_${portal.id}")
              ) {
                Box(
                  contentAlignment = Alignment.Center,
                  modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                ) {
                  Text(
                    text = portal.displayNameEnglish,
                    color = if (isSelected) Color.Black else Color.LightGray,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                  )
                }
              }
            }
          }

          // Secondary Context Strip depending on mode
          if (activeMode == TraktViewMode.ACCOUNT_WEBVIEW) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              TraktQuickChip(
                label = "رجوع",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                isActive = false,
                onClick = {
                  if (traktWebViewRef?.canGoBack() == true) {
                    traktWebViewRef?.goBack()
                  }
                },
                testTag = "btn_trakt_back"
              )

              TraktQuickChip(
                label = "Progress",
                icon = Icons.Default.TrackChanges,
                isActive = currentTraktUrl.contains("/progress", ignoreCase = true),
                onClick = { traktWebViewRef?.loadUrl("https://trakt.tv/users/me/progress") },
                testTag = "btn_trakt_progress"
              )

              TraktQuickChip(
                label = "Dashboard",
                icon = Icons.Default.Dashboard,
                isActive = currentTraktUrl.contains("/dashboard", ignoreCase = true),
                onClick = { traktWebViewRef?.loadUrl("https://trakt.tv/dashboard") },
                testTag = "btn_trakt_dashboard"
              )

              TraktQuickChip(
                label = "Watchlist",
                icon = Icons.Default.ListAlt,
                isActive = currentTraktUrl.contains("/watchlist", ignoreCase = true),
                onClick = { traktWebViewRef?.loadUrl("https://trakt.tv/users/me/watchlist") },
                testTag = "btn_trakt_watchlist"
              )

              TraktQuickChip(
                label = "Calendar",
                icon = Icons.Default.CalendarMonth,
                isActive = currentTraktUrl.contains("/calendars", ignoreCase = true),
                onClick = { traktWebViewRef?.loadUrl("https://trakt.tv/calendars/my/shows") },
                testTag = "btn_trakt_calendar"
              )

              // Explicit DOM Extraction Trigger Button
              Surface(
                onClick = {
                  captureTraktCookiesAndTriggerDomExtract(traktWebViewRef, showFeedbackIfEmpty = true)
                },
                color = CinemaSuccess.copy(alpha = 0.2f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, CinemaSuccess),
                modifier = Modifier
                  .heightIn(min = 40.dp)
                  .testTag("btn_trakt_extract_now")
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "استخراج الحلقات",
                    tint = CinemaSuccess,
                    modifier = Modifier.size(16.dp)
                  )
                  Text(
                    text = "استخراج الحلقات",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                  )
                }
              }

              TraktQuickChip(
                label = "رابط سريع",
                icon = Icons.Default.Link,
                isActive = showManualBar,
                onClick = { showManualBar = !showManualBar },
                testTag = "btn_trakt_manual_toggle"
              )

              TraktQuickChip(
                label = "تحديث",
                icon = Icons.Default.Refresh,
                isActive = false,
                onClick = { traktWebViewRef?.reload() },
                testTag = "btn_trakt_reload"
              )
            }

            if (loadProgress in 1..99) {
              LinearProgressIndicator(
                progress = { loadProgress / 100f },
                color = CinemaPrimary,
                trackColor = CinemaDarkBackground,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(3.dp)
              )
            }
          } else {
            // Sort / Filter pills for Next to Watch Table
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              TraktSortMode.values().forEach { mode ->
                val selected = sortMode == mode
                Surface(
                  onClick = { sortMode = mode },
                  color = if (selected) CinemaSurfaceVariant else CinemaDarkBackground,
                  shape = RoundedCornerShape(10.dp),
                  border = BorderStroke(1.dp, if (selected) CinemaAccent else CinemaBorder),
                  modifier = Modifier
                    .heightIn(min = 36.dp)
                    .testTag("btn_trakt_sort_${mode.name.lowercase()}")
                ) {
                  Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                  ) {
                    Text(
                      text = mode.label,
                      color = if (selected) CinemaAccent else Color.LightGray,
                      fontSize = 13.sp,
                      fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                      maxLines = 1,
                      softWrap = false
                    )
                  }
                }
              }

              if (traktUsername.isNotBlank()) {
                Surface(
                  color = CinemaSuccess.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(10.dp),
                  border = BorderStroke(1.dp, CinemaSuccess.copy(alpha = 0.55f))
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(CinemaSuccess)
                    )
                    Text(
                      text = "@$traktUsername متصل",
                      color = CinemaSuccess,
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      softWrap = false
                    )
                  }
                }
              }
            }
          }
        }
      }

      // =====================================================================
      // MODE 0: NATIVE "NEXT TO WATCH" PROGRESS TABLE (Modeled on Screenshot)
      // =====================================================================
      if (activeMode == TraktViewMode.NEXT_TO_WATCH) {
        if (displayedItems.isEmpty()) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(20.dp),
            contentAlignment = Alignment.Center
          ) {
            Surface(
              color = CinemaSurface,
              shape = RoundedCornerShape(18.dp),
              border = BorderStroke(1.dp, CinemaBorder),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.TrackChanges,
                  contentDescription = null,
                  tint = CinemaPrimary,
                  modifier = Modifier.size(48.dp)
                )
                Text(
                  text = "لا توجد حلقات مسحوبة بعد",
                  color = Color.White,
                  fontSize = 19.sp,
                  fontWeight = FontWeight.ExtraBold,
                  textAlign = TextAlign.Center
                )
                Text(
                  text = "افتح حسابك في Trakt وسجّل الدخول لتستخرج الحلقات تلقائياً في جدول «التالي للمشاهدة» مع زر تمت المشاهدة ✓",
                  color = Color.LightGray,
                  fontSize = 14.5.sp,
                  textAlign = TextAlign.Center
                )
                Button(
                  onClick = { activeMode = TraktViewMode.ACCOUNT_WEBVIEW },
                  colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
                  shape = RoundedCornerShape(12.dp),
                  contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_trakt_open_account_empty")
                ) {
                  Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "افتح حسابي في Trakt واستخرج الحلقات",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                  )
                }
              }
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .testTag("trakt_next_to_watch_list"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(displayedItems, key = { it.id }) { item ->
              TraktNextToWatchRow(
                item = item,
                isMarkingWatched = markingItemId == item.id,
                onPlay = {
                  onPlayTraktTarget(
                    item.showTitle,
                    item.seasonNumber,
                    item.episodeNumber,
                    false,
                    selectedLaunchPortal
                  )
                },
                onMarkWatched = {
                  if (markingItemId != null) return@TraktNextToWatchRow
                  markingItemId = item.id
                  onMarkUpNextItemWatched(item)
                  scope.launch {
                    delay(750L)
                    if (markingItemId == item.id) {
                      markingItemId = null
                    }
                  }
                }
              )
            }
          }
        }
      } else {
        // ===================================================================
        // MODE 1: LOGGED-IN TRAKT ACCOUNT WEBVIEW + LIVE DOM EXTRACTION BRIDGE
        // ===================================================================

        // Green Extraction Confirmation Banner once shows are captured from the user's Trakt account
        if (extractedCountBanner > 0) {
          Surface(
            color = CinemaSuccess.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, CinemaSuccess),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("trakt_extracted_banner")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "تم استخراج $extractedCountBanner مسلسل من حسابك ✓",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(modifier = Modifier.width(8.dp))
              Button(
                onClick = {
                  extractedCountBanner = 0
                  activeMode = TraktViewMode.NEXT_TO_WATCH
                },
                colors = ButtonDefaults.buttonColors(containerColor = CinemaSuccess),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                  .heightIn(min = 40.dp)
                  .testTag("btn_trakt_view_extracted_table")
              ) {
                Text(
                  text = "عرض الجدول",
                  color = Color.Black,
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.Black,
                  maxLines = 1,
                  softWrap = false
                )
              }
            }
          }
        }

        // Collapsible Manual / Paste Trakt Link Launcher Bar
        if (showManualBar) {
          Surface(
            color = CinemaSurfaceVariant,
            border = BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.55f)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OutlinedTextField(
                  value = manualInput,
                  onValueChange = { manualInput = it },
                  placeholder = { Text("الصق رابط Trakt أو اكتب اسم المسلسل...", fontSize = 13.sp, color = Color.Gray) },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                  keyboardActions = KeyboardActions(onGo = { launchManualEntry() }),
                  colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CinemaAccent,
                    unfocusedBorderColor = CinemaBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = CinemaDarkBackground,
                    unfocusedContainerColor = CinemaDarkBackground
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("input_trakt_manual_url")
                )

                Surface(
                  onClick = {
                    try {
                      val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                      val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                      if (!clip.isNullOrBlank()) {
                        manualInput = clip
                        TraktUrlParser.parse(clip)?.let { parsed ->
                          detectedLink = parsed
                          parsed.seasonNumber?.let { manualSeason = it.toString() }
                          parsed.episodeNumber?.let { manualEpisode = it.toString() }
                        }
                      } else {
                        Toast.makeText(context, "الحافظة فارغة", Toast.LENGTH_SHORT).show()
                      }
                    } catch (_: Throwable) {}
                  },
                  color = CinemaSurface,
                  shape = RoundedCornerShape(10.dp),
                  border = BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.6f)),
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_trakt_paste_clipboard")
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "لصق", tint = CinemaAccent, modifier = Modifier.size(18.dp))
                    Text("لصق", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                  }
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OutlinedTextField(
                  value = manualSeason,
                  onValueChange = { manualSeason = it.filter { ch -> ch.isDigit() }.take(2) },
                  label = { Text("موسم", fontSize = 12.sp) },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CinemaAccent,
                    unfocusedBorderColor = CinemaBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = CinemaDarkBackground,
                    unfocusedContainerColor = CinemaDarkBackground
                  ),
                  modifier = Modifier.width(82.dp)
                )

                OutlinedTextField(
                  value = manualEpisode,
                  onValueChange = { manualEpisode = it.filter { ch -> ch.isDigit() }.take(3) },
                  label = { Text("حلقة", fontSize = 12.sp) },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CinemaAccent,
                    unfocusedBorderColor = CinemaBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = CinemaDarkBackground,
                    unfocusedContainerColor = CinemaDarkBackground
                  ),
                  modifier = Modifier.width(82.dp)
                )

                Button(
                  onClick = { launchManualEntry() },
                  colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("btn_trakt_manual_play")
                ) {
                  Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("تشغيل في Asser TV", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                }
              }
            }
          }
        }

        // Main Trakt.tv WebView + Floating Auto-Detected Episode Launcher Bar
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        ) {
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
              AppRuntimeMemoryGuard.hasWebViewBeenCreated = true
              val startUrl = if (traktUsername.isNotBlank()) {
                "https://trakt.tv/users/$traktUsername/progress"
              } else {
                "https://trakt.tv/users/me/progress"
              }
              AppDiagnosticsLogger.recordBreadcrumb(ctx, "TRAKT_WEBVIEW_CREATE", startUrl)
              WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.parseColor("#0D1117"))

                try {
                  CookieManager.getInstance().setAcceptCookie(true)
                  CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                } catch (_: Throwable) {}

                settings.apply {
                  javaScriptEnabled = true
                  domStorageEnabled = true
                  databaseEnabled = false
                  loadWithOverviewMode = true
                  useWideViewPort = true
                  setSupportMultipleWindows(false)
                  javaScriptCanOpenWindowsAutomatically = false
                  mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                  }
                  userAgentString =
                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                }

                addJavascriptInterface(
                  AsserTraktJsBridge { rawJson ->
                    val payload = traktSyncService.parseDomExtractionJson(rawJson)
                    val cookie = try {
                      CookieManager.getInstance().getCookie("https://trakt.tv").orEmpty()
                    } catch (_: Throwable) {
                      ""
                    }
                    if (cookie.isNotBlank() || payload.username.isNotBlank() || payload.csrfToken.isNotBlank()) {
                      repository.saveTraktSession(
                        cookie = cookie,
                        username = payload.username,
                        csrfToken = payload.csrfToken
                      )
                    }
                    if (payload.items.isNotEmpty()) {
                      repository.mergeTraktProgressItems(payload.items)
                      extractedCountBanner = payload.items.size
                      AppDiagnosticsLogger.log(
                        "TraktBridge",
                        "Captured ${payload.items.size} shows from live WebView DOM (user=${payload.username})"
                      )
                    }
                  },
                  "AsserTraktBridge"
                )

                webChromeClient = object : WebChromeClient() {
                  override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loadProgress = newProgress
                  }
                }

                webViewClient = object : WebViewClient() {
                  private fun inspectUrl(url: String?) {
                    if (url.isNullOrBlank()) return
                    currentTraktUrl = url
                    val parsed = TraktUrlParser.parse(url)
                    if (parsed != null) {
                      detectedLink = parsed
                    }
                  }

                  override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val target = request?.url?.toString() ?: return false
                    inspectUrl(target)
                    return false
                  }

                  @Deprecated("Deprecated in Java")
                  override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    inspectUrl(url)
                    return false
                  }

                  override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    inspectUrl(url)
                    try {
                      CookieManager.getInstance().flush()
                      val cookie = CookieManager.getInstance().getCookie("https://trakt.tv").orEmpty()
                      if (cookie.isNotBlank()) {
                        repository.saveTraktSession(cookie = cookie)
                      }
                    } catch (_: Throwable) {}

                    // Auto-extract Progress / Up Next cards from Trakt pages (immediate + delayed for async hydration)
                    val lower = url?.lowercase().orEmpty()
                    val isExtractablePage = lower.contains("/progress") ||
                      lower.contains("/dashboard") ||
                      lower.contains("/users/")
                    if (isExtractablePage && !lower.contains("/auth/signin") && !lower.contains("/login")) {
                      captureTraktCookiesAndTriggerDomExtract(view)
                      view?.postDelayed({ captureTraktCookiesAndTriggerDomExtract(view) }, 1600L)
                      view?.postDelayed({ captureTraktCookiesAndTriggerDomExtract(view) }, 3600L)
                    }
                  }
                }

                traktWebViewRef = this
                loadUrl(startUrl)
              }
            }
          )

          // Floating Bottom Launcher Bar when user opens any Episode / Show / Movie on Trakt
          val activeDetected = detectedLink
          if (activeDetected != null) {
            val epTag = if (!activeDetected.isMovie && activeDetected.seasonNumber != null && activeDetected.episodeNumber != null) {
              "S%02dE%02d".format(Locale.US, activeDetected.seasonNumber, activeDetected.episodeNumber)
            } else {
              ""
            }
            Surface(
              color = CinemaSurface.copy(alpha = 0.97f),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(2.dp, CinemaAccent),
              shadowElevation = 14.dp,
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(10.dp)
                .testTag("trakt_floating_play_bar")
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = if (epTag.isNotBlank()) "${activeDetected.showTitle} $epTag" else activeDetected.showTitle,
                    color = CinemaAccent,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                  )

                  Spacer(modifier = Modifier.width(8.dp))

                  Surface(
                    onClick = { detectedLink = null },
                    color = CinemaDarkBackground,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(34.dp)
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إخفاء",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                      )
                    }
                  }
                }

                Button(
                  onClick = {
                    onPlayTraktTarget(
                      activeDetected.showTitle,
                      activeDetected.seasonNumber,
                      activeDetected.episodeNumber,
                      activeDetected.isMovie,
                      selectedLaunchPortal
                    )
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
                  shape = RoundedCornerShape(12.dp),
                  contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("btn_trakt_play_now_in_asser_tv")
                ) {
                  Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = if (activeDetected.isMovie) {
                      "تشغيل هذا الفيلم الآن في Asser TV"
                    } else {
                      "تشغيل هذه الحلقة الآن في Asser TV"
                    },
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Screenshot-faithful Progress / Next-to-Watch row card:
 * Left tall poster, right column with Show Title, `S.03 E.01` pill + Episode Title,
 * `25/145 (120 left)` + red horizontal progress bar, and bottom Play + outlined `✓` Watched pill.
 */
@Composable
private fun TraktNextToWatchRow(
  item: UpNextItem,
  isMarkingWatched: Boolean,
  onPlay: () -> Unit,
  onMarkWatched: () -> Unit
) {
  val context = LocalContext.current
  val cardInteraction = remember { MutableInteractionSource() }
  val isCardFocused by cardInteraction.collectIsFocusedAsState()
  val watchedInteraction = remember { MutableInteractionSource() }
  val isWatchedFocused by watchedInteraction.collectIsFocusedAsState()

  val seasonCode = item.seasonNumber.toString().padStart(2, '0')
  val epCode = item.episodeNumber.toString().padStart(2, '0')
  val formattedCode = "S.$seasonCode E.$epCode"

  val progressFraction = if (item.totalCount > 0) {
    (item.watchedCount.toFloat() / item.totalCount.toFloat()).coerceIn(0f, 1f)
  } else {
    0f
  }
  val leftCount = if (item.totalCount > 0) (item.totalCount - item.watchedCount).coerceAtLeast(0) else 0
  val progressLabel = if (item.totalCount > 0) {
    "${item.watchedCount}/${item.totalCount} ($leftCount left)"
  } else {
    "Season ${item.seasonNumber} • Episode ${item.episodeNumber}"
  }

  // Use LTR layout inside each row card to cleanly align poster left, English show/episode info & red bar, and Watched pill right
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Surface(
      onClick = onPlay,
      interactionSource = cardInteraction,
      color = CinemaSurface,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(
        width = if (isCardFocused) 2.5.dp else 1.dp,
        color = if (isCardFocused) CinemaFocusGlow else CinemaBorder
      ),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("trakt_next_row_${item.id}")
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left: Tall Show Poster
        Box(
          modifier = Modifier
            .width(76.dp)
            .height(112.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
              Brush.verticalGradient(
                colors = listOf(Color(0xFF1F2937), Color(0xFF111827))
              )
            )
            .border(1.dp, CinemaBorder, RoundedCornerShape(10.dp)),
          contentAlignment = Alignment.Center
        ) {
          val initials = remember(item.showTitle) {
            item.showTitle
              .split(" ")
              .filter { it.isNotBlank() }
              .take(2)
              .joinToString("") { it.first().uppercaseChar().toString() }
              .ifBlank { "TV" }
          }
          Text(
            text = initials,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
          )

          if (item.posterUrl.isNotBlank()) {
            AsyncImage(
              model = remember(item.posterUrl) {
                ImageRequest.Builder(context)
                  .data(item.posterUrl)
                  .size(180, 260)
                  .allowHardware(Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                  .crossfade(false)
                  .build()
              },
              contentDescription = item.showTitle,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }
        }

        // Right Column: Title, S.xx E.yy + Episode Title, Progress + Red Bar, Play & Watched ✓ Actions
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = item.showTitle,
            color = if (isCardFocused) CinemaFocusGlow else Color.White,
            fontSize = 17.5.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Surface(
              color = CinemaSurfaceVariant,
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
            ) {
              Text(
                text = formattedCode,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                maxLines = 1,
                softWrap = false
              )
            }

            if (item.episodeTitle.isNotBlank()) {
              Text(
                text = item.episodeTitle,
                color = Color(0xFFD1D5DB),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
            }
          }

          // Progress Counter + Sleek Red Bar (matching screenshot)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = progressLabel,
              color = Color(0xFF9CA3AF),
              fontSize = 12.5.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false
            )

            if (item.totalCount > 0) {
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(4.dp)
                  .clip(RoundedCornerShape(50))
                  .background(Color(0xFF2D333B))
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth(progressFraction.coerceAtLeast(0.03f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(CinemaPrimary)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(2.dp))

          // Bottom Action Row: Play button + Outlined Pill Watched `✓` Button
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Surface(
              onClick = onPlay,
              color = CinemaPrimary,
              shape = RoundedCornerShape(50),
              modifier = Modifier
                .heightIn(min = 38.dp)
                .testTag("btn_trakt_row_play_${item.id}")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.PlayArrow,
                  contentDescription = "تشغيل",
                  tint = Color.White,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = "تشغيل",
                  color = Color.White,
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.ExtraBold,
                  maxLines = 1,
                  softWrap = false
                )
              }
            }

            // Outlined Rounded Pill `✓` Watched Button (matches screenshot!)
            Surface(
              onClick = onMarkWatched,
              interactionSource = watchedInteraction,
              color = if (isWatchedFocused) CinemaSuccess else Color.Transparent,
              shape = RoundedCornerShape(50),
              border = BorderStroke(
                width = if (isWatchedFocused) 2.dp else 1.5.dp,
                color = if (isWatchedFocused) ClipboxWhite else Color.White.copy(alpha = 0.85f)
              ),
              modifier = Modifier
                .width(92.dp)
                .heightIn(min = 38.dp)
                .testTag("btn_trakt_row_watched_${item.id}")
            ) {
              Box(contentAlignment = Alignment.Center) {
                if (isMarkingWatched) {
                  CircularProgressIndicator(
                    color = if (isWatchedFocused) Color.Black else Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                  )
                } else {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "تمت المشاهدة - انتقال للحلقة التالية",
                    tint = if (isWatchedFocused) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TraktQuickChip(
  label: String,
  icon: ImageVector,
  isActive: Boolean,
  onClick: () -> Unit,
  testTag: String
) {
  Surface(
    onClick = onClick,
    color = if (isActive) CinemaPrimary else CinemaDarkBackground,
    shape = RoundedCornerShape(50),
    border = BorderStroke(1.dp, if (isActive) CinemaAccent else CinemaBorder),
    modifier = Modifier
      .heightIn(min = 40.dp)
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isActive) Color.White else CinemaAccent,
        modifier = Modifier.size(16.dp)
      )
      Text(
        text = label,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false
      )
    }
  }
}
