package com.example.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.AppDiagnosticsLogger
import com.example.data.AppDisplayMode
import com.example.data.SearchAndSubtitlesService
import com.example.data.TraktFullSyncResult
import com.example.data.TraktSyncService
import com.example.data.TvPreferencesRepository
import com.example.data.db.AccountAuthRepository
import com.example.data.db.UserAccountEntity
import com.example.model.AppMainTab
import com.example.model.ControlMode
import com.example.model.ScrapedEpisodeItem
import com.example.model.ScrapedSeasonItem
import com.example.model.ScrapedSeriesStructure
import com.example.model.SearchResultItem
import com.example.model.StreamingPortal
import com.example.model.SubtitleItem
import com.example.model.UpNextItem
import com.example.model.VideoQualityOption
import com.example.tv.TvKeyHandler
import com.example.tv.TvPlayerBridge
import com.example.tv.TvWebChromeClient
import com.example.tv.TvWebViewClient
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.ClipboxWhite
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicReference

private data class EpisodeScrapeRequest(
  val requestKey: String,
  val targetUrl: String,
  val portal: StreamingPortal,
  val titleHint: String,
  val selectedSeasonNum: Int,
  val selectedEpisodeNum: Int = 0,
  val autoLoadBestEmbed: Boolean,
  val nonce: Long
)

private data class PendingWebViewLoad(
  val targetUrl: String,
  val isDirectEmbed: Boolean,
  val refererPage: String,
  val isGeneralBrowser: Boolean
)

interface KeyEventProvider {
  fun setKeyEventCallback(callback: ActivityKeyEventCallback?)
}

fun interface ActivityKeyEventCallback {
  fun onDispatchKeyEvent(event: KeyEvent): Boolean
}

@Composable
fun MainScreen(
  repository: TvPreferencesRepository,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val activity = context as? Activity
  val configuration = LocalConfiguration.current
  val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
  val scope = rememberCoroutineScope()
  val searchService = remember { SearchAndSubtitlesService() }
  val traktSyncService = remember { TraktSyncService() }
  val accountRepository = remember { AccountAuthRepository(context.applicationContext) }
  val currentAccount by accountRepository.currentAccountFlow.collectAsState()

  // Display Mode & Dialogs
  val displayMode by repository.displayModeFlow.collectAsState()
  var showDisplayModeDialog by remember { mutableStateOf(!repository.hasSelectedDisplayMode()) }
  var showAdBlockDialog by remember { mutableStateOf(false) }
  var showFavoritesDialog by remember { mutableStateOf(false) }
  var showHistoryDialog by remember { mutableStateOf(false) }
  var showGuideDialog by remember { mutableStateOf(false) }
  var showSubtitlesDialog by remember { mutableStateOf(false) }
  var showQualityDialog by remember { mutableStateOf(false) }
  var showUpdateDialog by remember { mutableStateOf(false) }
  var showDiagnosticsDialog by remember { mutableStateOf(false) }
  var showAccountDialog by remember { mutableStateOf(false) }
  var showSettingsDialog by remember { mutableStateOf(false) }
  var isSyncingTrakt by remember { mutableStateOf(false) }

  suspend fun getValidTraktCredentials(forceRefresh: Boolean = false): Pair<String, String>? {
    val clientId = repository.getTraktClientId().trim()
    val clientSecret = repository.getTraktClientSecret().trim()
    var accessToken = repository.getTraktAccessToken().trim()
    val refreshToken = repository.getTraktRefreshToken().trim()
    val expiresAtMs = repository.getTraktTokenExpiresAtMs()
    if (clientId.isBlank() || accessToken.isBlank()) return null

    val isExpiredOrNear = expiresAtMs > 0L && System.currentTimeMillis() >= (expiresAtMs - 5 * 60_000L)
    if ((forceRefresh || isExpiredOrNear) && refreshToken.isNotBlank()) {
      val refreshed = traktSyncService.refreshAccessToken(clientId, clientSecret, refreshToken)
      if (refreshed != null) {
        accessToken = refreshed.accessToken
        val currentUsername = repository.getTraktUsername()
        repository.saveTraktOAuthCredentials(
          clientId = clientId,
          clientSecret = clientSecret,
          accessToken = refreshed.accessToken,
          refreshToken = refreshed.refreshToken,
          expiresAtMs = refreshed.expiresAtMs,
          username = currentUsername
        )
        accountRepository.saveTraktConfigForCurrentAccount(
          clientId = clientId,
          clientSecret = clientSecret,
          accessToken = refreshed.accessToken,
          refreshToken = refreshed.refreshToken,
          expiresAtMs = refreshed.expiresAtMs,
          traktUsername = currentUsername
        )
      }
    }
    return clientId to accessToken
  }

  fun triggerFullTraktSync(showToast: Boolean = true) {
    if (isSyncingTrakt) return
    scope.launch {
      isSyncingTrakt = true
      try {
        var creds = getValidTraktCredentials(forceRefresh = false)
        if (creds == null) {
          if (showToast) {
            Toast.makeText(context, "يرجى ربط حساب Trakt بالكود أولاً", Toast.LENGTH_SHORT).show()
          }
          return@launch
        }
        var result = traktSyncService.syncFullAccountData(creds.first, creds.second)
        if (result is TraktFullSyncResult.Unauthorized) {
          val refreshedCreds = getValidTraktCredentials(forceRefresh = true)
          if (refreshedCreds != null) {
            creds = refreshedCreds
            result = traktSyncService.syncFullAccountData(refreshedCreds.first, refreshedCreds.second)
          }
        }
        when (val finalRes = result) {
          is TraktFullSyncResult.Success -> {
            val resolvedUsername = finalRes.username.ifBlank { repository.getTraktUsername() }
            if (resolvedUsername.isNotBlank()) {
              repository.saveTraktOAuthCredentials(
                clientId = repository.getTraktClientId(),
                clientSecret = repository.getTraktClientSecret(),
                accessToken = repository.getTraktAccessToken(),
                refreshToken = repository.getTraktRefreshToken(),
                expiresAtMs = repository.getTraktTokenExpiresAtMs(),
                username = resolvedUsername
              )
              accountRepository.saveTraktConfigForCurrentAccount(
                clientId = repository.getTraktClientId(),
                clientSecret = repository.getTraktClientSecret(),
                accessToken = repository.getTraktAccessToken(),
                refreshToken = repository.getTraktRefreshToken(),
                expiresAtMs = repository.getTraktTokenExpiresAtMs(),
                traktUsername = resolvedUsername
              )
            }
            repository.mergeTraktProgressItems(
              traktItems = finalRes.upNextItems,
              stats = finalRes.stats,
              watchedEpisodeKeys = finalRes.watchedEpisodeKeys
            )
            repository.saveTraktCalendar(emptyList())
            repository.saveTraktWatchlist(emptyList())
            if (showToast) {
              val s = finalRes.stats
              Toast.makeText(
                context,
                "تمت المزامنة مع Trakt ✓ (المسحوب ${s.totalWatchedFetched} • المكتمل المستبعد ${s.completedFilteredCount} • الصافي ${finalRes.upNextItems.size})",
                Toast.LENGTH_LONG
              ).show()
            }
          }
          is TraktFullSyncResult.Unauthorized -> {
            val errMsg = "فشلت المزامنة مع Trakt: انتهت صلاحية الجلسة — يرجى إعادة الربط بالكود"
            repository.setTraktLastSyncError(errMsg)
            Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
          }
          is TraktFullSyncResult.Error -> {
            repository.setTraktLastSyncError(finalRes.message)
            Toast.makeText(context, finalRes.message, Toast.LENGTH_LONG).show()
          }
        }
      } finally {
        isSyncingTrakt = false
      }
    }
  }

  fun buildTraktSyncItemForEpisode(
    rawShowTitle: String,
    seasonNumber: Int,
    episodeNumber: Int
  ): Pair<UpNextItem, UpNextItem?> {
    val cleanShow = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(rawShowTitle)
      .ifBlank { rawShowTitle }
      .trim()
    val showKey = TvPreferencesRepository.buildNormalizedContentKey(cleanShow, null, null, isSeries = false)
    val matchedUpNext = repository.upNextFlow.value.firstOrNull {
      (showKey.isNotBlank() && it.seriesKey == showKey) ||
        it.showTitle.equals(cleanShow, ignoreCase = true)
    }
    val itemForSync = (matchedUpNext ?: UpNextItem(
      id = "trakt_ep_${showKey}_${seasonNumber}_$episodeNumber",
      seriesKey = showKey,
      showTitle = cleanShow,
      seasonNumber = seasonNumber.coerceAtLeast(1),
      episodeNumber = episodeNumber.coerceAtLeast(1),
      fromTrakt = true
    )).copy(
      seasonNumber = seasonNumber.coerceAtLeast(1),
      episodeNumber = episodeNumber.coerceAtLeast(1),
      traktEpisodeId = if (
        matchedUpNext != null &&
        matchedUpNext.seasonNumber == seasonNumber &&
        matchedUpNext.episodeNumber == episodeNumber
      ) {
        matchedUpNext.traktEpisodeId
      } else {
        ""
      }
    )
    return itemForSync to matchedUpNext
  }

  fun sendPlayerScrobbleToTrakt(
    showTitle: String,
    seasonNumber: Int,
    episodeNumber: Int,
    action: String,
    progressPercent: Float
  ) {
    if (!repository.isTraktConnected() || showTitle.isBlank() || episodeNumber <= 0) return
    val (itemForSync, _) = buildTraktSyncItemForEpisode(showTitle, seasonNumber, episodeNumber)
    scope.launch {
      val creds = getValidTraktCredentials(forceRefresh = false)
      if (creds == null) {
        val errMsg = "فشل إرسال حالة التشغيل (Scrobble $action) إلى Trakt: تعذر التحقق من الجلسة"
        repository.setTraktLastSyncError(errMsg)
        return@launch
      }
      val ok = traktSyncService.scrobbleEpisode(
        clientId = creds.first,
        accessToken = creds.second,
        action = action,
        item = itemForSync,
        progressPercent = progressPercent
      )
      if (!ok) {
        val errMsg = "تعذر مزامنة حالة المشاهدة (Scrobble $action) مع Trakt"
        repository.setTraktLastSyncError(errMsg)
      }
    }
  }

  fun syncPlayerEpisodeWithTrakt(
    showTitle: String,
    seasonNumber: Int,
    episodeNumber: Int,
    markWatched: Boolean,
    useScrobbleStop: Boolean = false,
    showToast: Boolean = true
  ) {
    val cleanShow = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(showTitle)
      .ifBlank { showTitle }
      .trim()
    if (cleanShow.isBlank() || episodeNumber <= 0) return
    val s = seasonNumber.coerceAtLeast(1)
    val e = episodeNumber.coerceAtLeast(1)
    val epLabel = "S%02dE%02d".format(java.util.Locale.US, s, e)

    if (!repository.isTraktConnected()) {
      val errMsg = "يرجى ربط حساب Trakt أولاً لتحديث سجل المشاهدة ($epLabel)"
      repository.setTraktLastSyncError(errMsg)
      if (showToast) {
        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
      }
      return
    }

    val (itemForSync, matchedUpNext) = buildTraktSyncItemForEpisode(cleanShow, s, e)
    scope.launch {
      val creds = getValidTraktCredentials(forceRefresh = false)
      if (creds == null) {
        val errMsg = "فشل الاتصال بـ Trakt لتحديث $epLabel — يرجى إعادة الربط أو المحاولة مجدداً"
        repository.setTraktLastSyncError(errMsg)
        if (showToast) {
          Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
        }
        return@launch
      }

      val synced = if (markWatched) {
        if (useScrobbleStop) {
          traktSyncService.scrobbleEpisode(creds.first, creds.second, "stop", itemForSync, 100f)
        } else {
          traktSyncService.markEpisodeWatched(creds.first, creds.second, itemForSync)
        }
      } else {
        traktSyncService.unmarkEpisodeWatched(creds.first, creds.second, itemForSync)
      }

      if (synced) {
        repository.setTraktLastSyncError(null)
        if (markWatched) {
          repository.markEpisodeWatched(cleanShow, s, e)
          if (showToast) {
            Toast.makeText(context, "تم تسجيل $epLabel كمُشاهد في Trakt ✓", Toast.LENGTH_SHORT).show()
          }
        } else {
          repository.unmarkEpisodeWatched(cleanShow, s, e)
          if (showToast) {
            Toast.makeText(context, "تم إزالة $epLabel من سجل Trakt ✓", Toast.LENGTH_SHORT).show()
          }
        }

        if (itemForSync.traktShowId > 0L) {
          val updatedNext = traktSyncService.fetchSingleShowNextUp(creds.first, creds.second, itemForSync)
          if (updatedNext != null) {
            repository.replaceSingleUpNextItem(updatedNext)
          } else if (matchedUpNext != null) {
            repository.removeUpNextItem(matchedUpNext.id)
          }
        } else {
          triggerFullTraktSync(showToast = false)
        }
      } else {
        val errMsg = if (markWatched) {
          "فشل إرسال مشاهدة الحلقة $epLabel إلى Trakt — تحقق من الاتصال وحاول مجدداً"
        } else {
          "فشل إلغاء مشاهدة الحلقة $epLabel في Trakt — تحقق من الاتصال وحاول مجدداً"
        }
        repository.setTraktLastSyncError(errMsg)
        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
      }
    }
  }

  fun markUpNextItemWatchedOfficial(item: UpNextItem, showToast: Boolean = true) {
    val epLabel = "S%02dE%02d".format(java.util.Locale.US, item.seasonNumber, item.episodeNumber)
    if (!repository.isTraktConnected()) {
      val errMsg = "يرجى ربط حساب Trakt أولاً لتحديث التالي للمشاهدة"
      repository.setTraktLastSyncError(errMsg)
      Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
      return
    }
    scope.launch {
      val creds = getValidTraktCredentials(forceRefresh = false)
      if (creds == null) {
        val errMsg = "فشل تحديث $epLabel في Trakt: انتهت صلاحية الجلسة"
        repository.setTraktLastSyncError(errMsg)
        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
        return@launch
      }
      val synced = traktSyncService.markEpisodeWatched(creds.first, creds.second, item)
      if (synced) {
        repository.setTraktLastSyncError(null)
        repository.advanceUpNextItemLocally(item)
        if (showToast) {
          Toast.makeText(context, "تم تعليم $epLabel كمُشاهد وتحديث Trakt ✓", Toast.LENGTH_SHORT).show()
        }
        if (item.traktShowId > 0L) {
          val updatedNext = traktSyncService.fetchSingleShowNextUp(creds.first, creds.second, item)
          if (updatedNext != null) {
            repository.replaceSingleUpNextItem(updatedNext)
          } else {
            // No more aired unwatched episodes left for this show — remove finished show from Next to Watch!
            repository.removeUpNextItem(item.id)
          }
        } else {
          triggerFullTraktSync(showToast = false)
        }
      } else {
        val errMsg = "فشل تسجيل الحلقة $epLabel كمُشاهدة في Trakt — يرجى إعادة المحاولة"
        repository.setTraktLastSyncError(errMsg)
        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
      }
    }
  }

  // Restore saved Trakt tokens from active Room account if needed, and ALWAYS read watched progress from Trakt on app open
  LaunchedEffect(currentAccount?.id) {
    val acc = currentAccount
    if (acc != null && acc.traktAccessToken.isNotBlank() && acc.traktClientId.isNotBlank() && !repository.isTraktConnected()) {
      repository.saveTraktOAuthCredentials(
        clientId = acc.traktClientId,
        clientSecret = acc.traktClientSecret,
        accessToken = acc.traktAccessToken,
        refreshToken = acc.traktRefreshToken,
        expiresAtMs = acc.traktTokenExpiresAtMs,
        username = acc.traktUsername
      )
    }
    if (repository.isTraktConnected()) {
      triggerFullTraktSync(showToast = false)
    }
  }

  val currentVideoQuality by repository.preferredQualityFlow.collectAsState()
  val currentVideoResizeMode by repository.videoResizeModeFlow.collectAsState()
  var detectedVideoQualities by remember { mutableStateOf<List<VideoQualityOption>>(emptyList()) }
  var isHomeActive by remember { mutableStateOf(true) }
  var selectedMainTab by remember { mutableStateOf(AppMainTab.ASSER_TV) }
  var isGeneralBrowserSession by remember { mutableStateOf(false) }

  // Watch Side Panel & Scraped Series State
  var isWatchSidePanelOpen by remember { mutableStateOf(true) }
  var lastSearchQuery by remember { mutableStateOf("") }
  var lastSearchPortalResults by remember {
    mutableStateOf<Map<StreamingPortal, List<SearchResultItem>>>(emptyMap())
  }
  var seriesStructure by remember { mutableStateOf(ScrapedSeriesStructure()) }
  var isScrapingSeries by remember { mutableStateOf(false) }

  // Portal & Control Mode
  var activePortal by remember { mutableStateOf(repository.getActivePortal()) }
  var controlMode by remember { mutableStateOf(repository.getControlMode()) }

  // Web & Player State
  var isBarVisible by remember { mutableStateOf(true) }
  var isFullscreenVideo by remember { mutableStateOf(false) }
  var loadingProgress by remember { mutableIntStateOf(0) }
  var pageTitle by remember { mutableStateOf("") }
  var currentUrl by remember { mutableStateOf("") }
  var activeWatchPageUrl by remember { mutableStateOf("") }
  var activeServerName by remember { mutableStateOf("") }
  var activeEmbedRefererUrl by remember { mutableStateOf("") }
  var isCurrentUrlDirectEmbed by remember { mutableStateOf(false) }

  // Virtual Mouse Coordinates
  var cursorX by remember { mutableFloatStateOf(960f) }
  var cursorY by remember { mutableFloatStateOf(540f) }

  // Data from Preferences
  val bookmarks by repository.bookmarksFlow.collectAsState()
  val history by repository.historyFlow.collectAsState()

  // References & Zero-Background-WebView Lifecycle State
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var fullscreenContainerRef by remember { mutableStateOf<FrameLayout?>(null) }
  var webChromeClientRef by remember { mutableStateOf<TvWebChromeClient?>(null) }
  var isWebViewMounted by remember { mutableStateOf(false) }
  var pendingWebViewLoad by remember { mutableStateOf<PendingWebViewLoad?>(null) }
  val destroyedWebViews = remember {
    java.util.Collections.newSetFromMap(java.util.IdentityHashMap<WebView, Boolean>())
  }

  // Atomic deduplication guards so Compose recompositions NEVER double-load portals or embeds
  val inFlightScrapeKeyRef = remember { AtomicReference<String?>(null) }
  val lastResolvedScrapeKeyRef = remember { AtomicReference<String?>(null) }
  val lastWebViewLoadedKeyRef = remember { AtomicReference<String?>(null) }
  var activeEpisodeScrapeRequest by remember { mutableStateOf<EpisodeScrapeRequest?>(null) }
  var scrapeForceNonce by remember { mutableLongStateOf(0L) }
  var activeServerSelectionJob by remember { mutableStateOf<Job?>(null) }

  var lastBackPressTime by remember { mutableStateOf(0L) }
  var webViewRecreateKey by remember { mutableIntStateOf(0) }
  var lastRendererCrashTimeMs by remember { mutableLongStateOf(0L) }
  var rendererCrashCount by remember { mutableIntStateOf(0) }
  var detectedVideoUrl by remember { mutableStateOf<String?>(null) }
  var detectedVideoHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
  var playNativeVideoUrl by remember { mutableStateOf<String?>(null) }
  var preferNativePlayer by remember { mutableStateOf(true) }
  var userManuallyDismissedNativePlayer by remember { mutableStateOf(false) }
  var activeSubtitleItem by remember { mutableStateOf<SubtitleItem?>(null) }
  val subtitleStyle by repository.subtitleStyleFlow.collectAsState()
  val autoPlayNextEpisode by repository.autoPlayNextEpisodeFlow.collectAsState()
  val audioBoostPercent by repository.audioBoostPercentFlow.collectAsState()
  val watchedEpisodes by repository.watchedEpisodesFlow.collectAsState()
  var webVideoCurrentTimeMs by remember { mutableStateOf(0L) }
  val failedNativeStreamUrls = remember { mutableSetOf<String>() }

  // Standardized Episode Display Title (`Silo S01E01` / `Lost S04E15` / `Movie (2024)`)
  // Backed by derivedStateOf so movablePlayerBox (movableContentOf) ALWAYS observes live updates
  // instead of capturing the initial empty "Asser TV" value at composition time!
  val standardizedMediaInfo by remember {
    derivedStateOf {
      SearchAndSubtitlesService.formatStandardizedEpisodeInfo(
        seriesTitle = seriesStructure.seriesTitle,
        pageTitle = pageTitle,
        lastSearchQuery = lastSearchQuery,
        watchPageUrl = activeWatchPageUrl.ifBlank { currentUrl },
        embedUrl = activeEmbedRefererUrl.ifBlank { currentUrl },
        serverName = activeServerName,
        isSeriesHint = seriesStructure.isSeries,
        selectedSeasonHint = seriesStructure.selectedSeasonNumber,
        currentEpisodeHint = seriesStructure.currentEpisodeNumber
      )
    }
  }

  // Ensure permissive SSL is installed asynchronously off the main thread for streaming CDNs
  LaunchedEffect(Unit) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
      SearchAndSubtitlesService.installPermissiveSslForStreaming()
    }
  }

  // Keep AppDiagnosticsLogger live summary synchronized
  LaunchedEffect(
    isHomeActive,
    activePortal,
    activeWatchPageUrl,
    currentUrl,
    playNativeVideoUrl,
    detectedVideoUrl,
    activeEmbedRefererUrl,
    detectedVideoQualities,
    currentVideoQuality
  ) {
    AppDiagnosticsLogger.activePortalName = activePortal.displayNameArabic
    AppDiagnosticsLogger.activeWatchUrl = if (isHomeActive) "الشاشة الرئيسية" else activeWatchPageUrl.ifBlank { currentUrl }
    AppDiagnosticsLogger.currentWebViewUrl = if (isHomeActive && !isWebViewMounted) "NONE (غير مُحمّل لتوفير الرام)" else currentUrl
    AppDiagnosticsLogger.activePlayerMode = when {
      isHomeActive -> "الشاشة الرئيسية (وضع توفير الرام)"
      playNativeVideoUrl != null -> "Native ExoPlayer (يعمل بنجاح ✅)"
      isWebViewMounted -> "مشغل السيرفر المدمج (WebView)"
      else -> "جاهز (بدون WebView)"
    }
    AppDiagnosticsLogger.extractedStreamUrl = detectedVideoUrl ?: ""
    AppDiagnosticsLogger.streamReferer = activeEmbedRefererUrl.ifBlank { currentUrl }
    AppDiagnosticsLogger.detectedQualitiesSummary =
      if (detectedVideoQualities.isEmpty()) "تلقائي فقط (لم يتم رصد جودات فرعية بعد)"
      else detectedVideoQualities.joinToString(" , ") { it.label }
    AppDiagnosticsLogger.selectedQualityId = currentVideoQuality
  }

  // CRITICAL FOR 1GB RAM TV STICKS (SDK 28): NEVER call WebSettings.getDefaultUserAgent(context)
  // on startup — it synchronously boots the entire Chromium WebView engine (~180MB RAM + 15-60s freeze)!
  val cleanAndroidChromeUa = remember {
    "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE ?: "13"}; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
  }
  val desktopChromeUa =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

  fun mergeDetectedQualities(newOptions: List<VideoQualityOption>) {
    if (newOptions.isEmpty()) return
    // Combine and preserve existing higher/lower qualities from Master HLS so single-track reports never wipe them out!
    val combinedVariants = (newOptions + detectedVideoQualities)
      .filter {
        it.id != TvPreferencesRepository.QUALITY_AUTO &&
          it.id != TvPreferencesRepository.QUALITY_LOWEST &&
          !it.isAuto &&
          it.height > 0
      }
      .distinctBy { it.height }
      .sortedByDescending { it.height }

    val bestHeight = combinedVariants.firstOrNull()?.height ?: 0
    val lowestHeight = combinedVariants.lastOrNull()?.height ?: 0
    val lowestLabel = if (lowestHeight > 0) "توفير ${lowestHeight}p" else "توفير الباقة"
    val autoLabel = if (bestHeight > 0) "تلقائي ${bestHeight}p" else "تلقائي"

    val merged = mutableListOf<VideoQualityOption>()
    merged.add(
      VideoQualityOption(
        id = TvPreferencesRepository.QUALITY_LOWEST,
        label = lowestLabel,
        height = 0,
        isAuto = false
      )
    )
    merged.add(VideoQualityOption(id = TvPreferencesRepository.QUALITY_AUTO, label = autoLabel, height = 0, isAuto = true))
    merged.addAll(combinedVariants)
    detectedVideoQualities = merged
    AppDiagnosticsLogger.detectedQualitiesSummary = merged.joinToString { it.label }
  }

  /**
   * Resolves the best direct stream URL for non-adaptive MP4 multi-quality embeds
   * according to the user's saved quality preference (Data Saver LOWEST or target height cap).
   */
  fun selectPreferredDirectStreamUrl(
    primaryUrl: String,
    qualities: List<VideoQualityOption>
  ): String {
    if (primaryUrl.isBlank()) return primaryUrl
    val lower = primaryUrl.lowercase()
    // Adaptive HLS (.m3u8) / DASH (.mpd) manifests are filtered directly by ExoPlayer TrackSelector pre-prepare
    if (lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains(".urlset")) {
      return primaryUrl
    }
    val mp4Variants = qualities.filter { it.height > 0 && it.streamUrl.startsWith("http") }
    if (mp4Variants.isEmpty()) return primaryUrl
    val pref = TvPreferencesRepository.normalizeQualityId(currentVideoQuality)
    return when {
      pref == TvPreferencesRepository.QUALITY_LOWEST -> {
        mp4Variants.minByOrNull { it.height }?.streamUrl ?: primaryUrl
      }
      pref == TvPreferencesRepository.QUALITY_AUTO -> primaryUrl
      else -> {
        val cap = pref.toIntOrNull() ?: 480
        val notExceeding = mp4Variants.filter { it.height <= cap + 45 }.maxByOrNull { it.height }
        (notExceeding ?: mp4Variants.minByOrNull { it.height })?.streamUrl ?: primaryUrl
      }
    }
  }

  /**
   * Idempotent, crash-safe WebView teardown that avoids triggering new renderer IPCs
   * (`loadUrl("about:blank")`, `clearCache(true)`, global `pauseTimers()`) right before `destroy()`.
   */
  fun safelyDestroyWebView(wv: WebView?) {
    if (wv == null) return
    if (!destroyedWebViews.add(wv)) return
    try {
      wv.stopLoading()
      wv.webChromeClient = null
      wv.webViewClient = android.webkit.WebViewClient()
      wv.removeJavascriptInterface("AndroidTvBridge")
      wv.onPause()
      wv.visibility = View.GONE
      (wv.parent as? ViewGroup)?.removeView(wv)
      wv.removeAllViews()
      wv.destroy()
    } catch (_: Throwable) {}
  }

  /**
   * Hard-destroys the extractor WebView and frees its Chromium renderer process
   * once NativeTvExoPlayer takes over or the user exits the watch view.
   */
  fun hardDestroyWebView(reason: String) {
    val wv = webViewRef
    webViewRef = null
    fullscreenContainerRef = null
    webChromeClientRef = null
    pendingWebViewLoad = null
    lastWebViewLoadedKeyRef.set(null)
    isWebViewMounted = false
    if (wv != null) {
      safelyDestroyWebView(wv)
      AppDiagnosticsLogger.log("WebViewDestroy", "Hard-destroyed WebView ($reason)")
      AppDiagnosticsLogger.recordBreadcrumb(context, "WEBVIEW_DESTROYED", reason)
    }
  }

  fun stopBackgroundWebViewVideo() {
    hardDestroyWebView("stopBackgroundWebViewVideo -> freeing Chromium RAM")
  }

  // Ensure zero background WebView/portal execution whenever returning to Home or starting Native ExoPlayer
  LaunchedEffect(isHomeActive) {
    if (isHomeActive) {
      activeServerSelectionJob?.cancel()
      activeServerSelectionJob = null
      activeEpisodeScrapeRequest = null
      inFlightScrapeKeyRef.set(null)
      lastResolvedScrapeKeyRef.set(null)
      playNativeVideoUrl = null
      hardDestroyWebView("Returned to Home dashboard")
    }
  }

  LaunchedEffect(playNativeVideoUrl) {
    if (!playNativeVideoUrl.isNullOrBlank() && !isGeneralBrowserSession) {
      hardDestroyWebView("NativeTvExoPlayer handoff")
    }
  }

  // React to OS onLowMemory() / onTrimMemory() / Activity onStop() safely without crash loops
  LaunchedEffect(Unit) {
    com.example.AppRuntimeMemoryGuard.memoryTrimEvents.collect { trimLevel ->
      try {
        if (isHomeActive || trimLevel >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
          activeServerSelectionJob?.cancel()
          activeServerSelectionJob = null
          if (isHomeActive) {
            activeEpisodeScrapeRequest = null
            inFlightScrapeKeyRef.set(null)
          }
        }
        if (isHomeActive || playNativeVideoUrl != null) {
          hardDestroyWebView("OS memory trim (level=$trimLevel)")
        }
      } catch (_: Throwable) {}
    }
  }

  fun toggleVideoFullscreen(enable: Boolean) {
    isFullscreenVideo = enable
    isBarVisible = !enable
    if (!enable) {
      isWatchSidePanelOpen = true
    }

    activity?.let { act ->
      val window = act.window
      val insetsController = WindowCompat.getInsetsController(window, window.decorView)
      insetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      if (enable) {
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
      } else {
        insetsController.show(WindowInsetsCompat.Type.systemBars())
        if (displayMode == AppDisplayMode.TV) {
          act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
          act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
      }
    }

    AppDiagnosticsLogger.log("PLAYER", "تبديل وضع ملء الشاشة للفيديو: fullscreen=$enable")
  }

  fun applyVideoQualityInPlayer(qualityId: String, qualityLabel: String, directVariantUrl: String = "") {
    val normalizedId = TvPreferencesRepository.normalizeQualityId(qualityId)
    repository.setPreferredQuality(normalizedId)
    AppDiagnosticsLogger.selectedQualityId = normalizedId
    AppDiagnosticsLogger.log("QUALITY", "اختيار الجودة (محفوظة): $qualityLabel (id=$normalizedId) | url=$directVariantUrl")

    val currentActiveStream = playNativeVideoUrl ?: ""
    val isCurrentMasterHls = currentActiveStream.contains("master.m3u8", ignoreCase = true) ||
        currentActiveStream.contains(".urlset", ignoreCase = true) ||
        currentActiveStream.contains(".mpd", ignoreCase = true)

    // Switch URL only if it's a direct MP4 variant OR if we are not currently on a multi-variant adaptive manifest
    val resolvedDirectUrl = when {
      directVariantUrl.startsWith("http") -> directVariantUrl
      normalizedId == TvPreferencesRepository.QUALITY_LOWEST && !isCurrentMasterHls -> {
        detectedVideoQualities
          .filter { it.height > 0 && it.streamUrl.startsWith("http") }
          .minByOrNull { it.height }
          ?.streamUrl ?: ""
      }
      else -> ""
    }
    if (resolvedDirectUrl.startsWith("http") && playNativeVideoUrl != null && normalizedId != TvPreferencesRepository.QUALITY_AUTO) {
      if ((resolvedDirectUrl.contains(".mp4", ignoreCase = true) || !isCurrentMasterHls) &&
          resolvedDirectUrl != playNativeVideoUrl
      ) {
        playNativeVideoUrl = resolvedDirectUrl
      }
    }

    val js = """
      (function() {
        var qId = '$normalizedId';
        window.__asserTargetQuality = qId;
        function applyInWindow(win) {
          var matched = false;
          try {
            if (typeof win.jwplayer === 'function' && win.jwplayer().setCurrentQuality) {
              var levels = win.jwplayer().getQualityLevels() || [];
              if (qId === 'auto') {
                win.jwplayer().setCurrentQuality(0);
                matched = true;
              } else if (qId === 'lowest') {
                var lowestIdx = -1, lowestH = 99999;
                for (var li = 0; li < levels.length; li++) {
                  var lh = parseInt(levels[li].height || 0, 10);
                  if (lh > 0 && lh < lowestH) { lowestH = lh; lowestIdx = li; }
                }
                if (lowestIdx >= 0) {
                  win.jwplayer().setCurrentQuality(lowestIdx);
                  matched = true;
                }
              } else {
                for (var i = 0; i < levels.length; i++) {
                  var lbl = (levels[i].label || '').toLowerCase();
                  var h = levels[i].height ? levels[i].height.toString() : '';
                  if (lbl.indexOf(qId) !== -1 || h === qId) {
                    win.jwplayer().setCurrentQuality(i);
                    matched = true;
                    break;
                  }
                }
              }
            }
          } catch(e) {}
          try {
            if (win.hls && win.hls.levels) {
              if (qId === 'auto') {
                win.hls.currentLevel = -1;
                matched = true;
              } else if (qId === 'lowest') {
                var minIdx = -1, minH = 99999;
                for (var lk = 0; lk < win.hls.levels.length; lk++) {
                  var kh = win.hls.levels[lk].height || 0;
                  if (kh > 0 && kh < minH) { minH = kh; minIdx = lk; }
                }
                if (minIdx >= 0) {
                  win.hls.currentLevel = minIdx;
                  matched = true;
                }
              } else {
                for (var k = 0; k < win.hls.levels.length; k++) {
                  if (win.hls.levels[k].height && win.hls.levels[k].height.toString() === qId) {
                    win.hls.currentLevel = k;
                    matched = true;
                    break;
                  }
                }
              }
            }
          } catch(e) {}
          try {
            var doc = win.document;
            var items = doc.querySelectorAll('.jw-settings-item, .vjs-menu-item, .art-setting-item, [data-quality], li[data-embed], .servers li, .watch--servers--list li');
            for (var j = 0; j < items.length; j++) {
              var text = (items[j].innerText || items[j].textContent || items[j].getAttribute('data-quality') || '').toLowerCase();
              if (qId === 'auto' && (text.indexOf('auto') !== -1 || text.indexOf('تلقائي') !== -1)) {
                items[j].click();
                matched = true;
                break;
              } else if (qId !== 'auto' && text.indexOf(qId) !== -1) {
                items[j].click();
                matched = true;
                break;
              }
            }
          } catch(e) {}
          return matched;
        }
        applyInWindow(window);
        try {
          var frames = document.querySelectorAll('iframe');
          for (var f = 0; f < frames.length; f++) {
            try {
              if (frames[f].contentWindow) applyInWindow(frames[f].contentWindow);
            } catch(e) {}
          }
        } catch(e) {}
      })();
    """.trimIndent()
    webViewRef?.evaluateJavascript(js, null)
    Toast.makeText(context, "تم تفعيل جودة العرض: $qualityLabel", Toast.LENGTH_SHORT).show()
  }

  fun onStreamUrlIntercepted(url: String, headers: Map<String, String>, sourceTag: String) {
    if (!url.startsWith("http")) return
    // Unwrap proxy stream URLs (e.g. surge.flooderrr.store/mp/...&headers={...}&host=https://bcdnxw.hakunaymatata.com)
    val (resolvedUrl, resolvedHeaders) = searchService.unwrapProxyStreamUrl(url, headers)
    val lower = resolvedUrl.lowercase()

    // Ignore secondary bandwidth test probes (/mp/bt/) when a primary stream is already detected
    if (url.lowercase().contains("/mp/bt/") && !detectedVideoUrl.isNullOrBlank()) {
      return
    }
    // Never auto-relaunch a stream URL that already failed in NativeTvExoPlayer (prevents infinite fallback loops)
    if (failedNativeStreamUrls.contains(url) || failedNativeStreamUrls.contains(resolvedUrl)) {
      return
    }

    if ((lower.contains(".ts") || lower.contains(".m4s")) && detectedVideoUrl?.contains(".m3u8") == true) {
      return
    }
    if (resolvedUrl == detectedVideoUrl && playNativeVideoUrl != null) return

    // CRITICAL FIX: Never let a single-quality child variant playlist (e.g. index-v1-a1.m3u8)
    // overwrite a multi-quality Master HLS playlist (master.m3u8 / .urlset)!
    val existingUrl = playNativeVideoUrl ?: detectedVideoUrl ?: ""
    val existingIsMaster = existingUrl.contains("master.m3u8", ignoreCase = true) ||
        existingUrl.contains(".urlset", ignoreCase = true)
    val incomingIsChildVariant = (lower.contains("index-") ||
        lower.contains("chunklist") ||
        lower.contains("prog_index") ||
        Regex("_[lnhx]/index").containsMatchIn(lower)) &&
        !lower.contains("master.m3u8")

    if (existingIsMaster && incomingIsChildVariant) {
      AppDiagnosticsLogger.log(
        "STREAM",
        "تجاهل ملف الجودة الفرعي للحفاظ على ملف الجودات الرئيسي (Master M3U8): $resolvedUrl"
      )
      stopBackgroundWebViewVideo()
      return
    }

    AppDiagnosticsLogger.log("STREAM", "التقاط رابط فيديو مباشر ($sourceTag): $resolvedUrl")
    detectedVideoUrl = resolvedUrl
    if (resolvedHeaders.isNotEmpty()) {
      detectedVideoHeaders = resolvedHeaders
    }

    val effectiveReferer = resolvedHeaders["Referer"]
      ?: resolvedHeaders["referer"]
      ?: activeEmbedRefererUrl.ifBlank { currentUrl.ifBlank { activeWatchPageUrl } }

    if (lower.contains(".m3u8")) {
      scope.launch {
        val hlsQualities = searchService.parseHlsQualitiesFromManifest(resolvedUrl, effectiveReferer)
        if (hlsQualities.isNotEmpty()) {
          mergeDetectedQualities(hlsQualities)
        }
      }
    }

    if (!isGeneralBrowserSession && preferNativePlayer && !userManuallyDismissedNativePlayer) {
      AppDiagnosticsLogger.recordBreadcrumb(context, "STREAM_CAPTURED", "$sourceTag -> ExoPlayer")
      hardDestroyWebView("Direct stream captured via $sourceTag")
      playNativeVideoUrl = resolvedUrl
    }
  }

  fun executeWebViewLoad(
    wv: WebView,
    cleanTargetUrl: String,
    isDirectEmbed: Boolean,
    refererPage: String,
    isGeneralBrowser: Boolean = false
  ) {
    try {
      wv.onResume()
      wv.resumeTimers()
    } catch (_: Throwable) {}

    val useAndroidUa = isGeneralBrowser && (cleanTargetUrl.contains("google.") || displayMode == AppDisplayMode.MOBILE)
    wv.settings.userAgentString = if (useAndroidUa) cleanAndroidChromeUa else desktopChromeUa

    if (isGeneralBrowser) {
      wv.loadUrl(cleanTargetUrl, mapOf("Referer" to refererPage))
      return
    }

    val refererOrigin = try {
      val uri = java.net.URI(refererPage)
      "${uri.scheme}://${uri.host}/"
    } catch (_: Exception) {
      "https://topcinema.io/"
    }

    AppDiagnosticsLogger.log(
      "WEBVIEW",
      "تحميل سيرفر المشاهدة (directEmbed=$isDirectEmbed) | URL=$cleanTargetUrl | Referer=$refererOrigin"
    )
    AppDiagnosticsLogger.recordBreadcrumb(context, "WEBVIEW_LOAD", cleanTargetUrl)

    val lowerTarget = cleanTargetUrl.lowercase()
    val isStandaloneGlobalEmbed = lowerTarget.contains("vidlink.") ||
        lowerTarget.contains("vidsrc.") ||
        lowerTarget.contains("autoembed.") ||
        lowerTarget.contains("2embed.") ||
        lowerTarget.contains("multiembed.")

    if (isDirectEmbed && !isStandaloneGlobalEmbed) {
      val cleanIframeHtml = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <meta name="referrer" content="origin">
          <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            html, body {
              width: 100vw !important;
              height: 100vh !important;
              min-height: 100% !important;
              background-color: #070A0E !important;
              overflow: hidden !important;
            }
            #asser_clean_embed_frame {
              position: fixed !important;
              top: 0 !important;
              left: 0 !important;
              right: 0 !important;
              bottom: 0 !important;
              width: 100vw !important;
              height: 100vh !important;
              min-height: 230px !important;
              border: 0 !important;
              display: block !important;
              background-color: #000000 !important;
            }
          </style>
        </head>
        <body>
          <iframe
            id="asser_clean_embed_frame"
            src="$cleanTargetUrl"
            frameborder="0"
            scrolling="no"
            allowfullscreen="true"
            webkitallowfullscreen="true"
            mozallowfullscreen="true"
            allow="autoplay; fullscreen; picture-in-picture; encrypted-media"
            referrerpolicy="origin">
          </iframe>
        </body>
        </html>
      """.trimIndent()

      wv.loadDataWithBaseURL(
        refererPage,
        cleanIframeHtml,
        "text/html",
        "UTF-8",
        refererPage
      )
    } else {
      val mutableHeaders = java.util.HashMap<String, String>()
      mutableHeaders["Referer"] = refererOrigin
      wv.loadUrl(cleanTargetUrl, mutableHeaders)
    }
  }

  fun loadServerIntoWebView(
    targetWebView: WebView?,
    targetServerUrl: String,
    isDirectEmbed: Boolean,
    portalRefererPageUrl: String = activeWatchPageUrl.ifBlank { activePortal.homeUrl },
    resetNativePlayer: Boolean = true,
    forceReload: Boolean = false
  ) {
    val cleanTargetUrl = targetServerUrl
      .replace("topcinema.site", "topcinema.io")
      .replace("video.php?vid=", "play.php?vid=")
    val refererPage = portalRefererPageUrl
      .replace("topcinema.site", "topcinema.io")
      .replace("video.php?vid=", "play.php?vid=")
      .ifBlank { activePortal.homeUrl }
    val loadKey = "$cleanTargetUrl|$isDirectEmbed|$refererPage"

    // Atomic deduplication guard: never load the exact same embed URL twice
    if (!forceReload && isWebViewMounted && targetWebView != null && lastWebViewLoadedKeyRef.get() == loadKey) {
      AppDiagnosticsLogger.log("WEBVIEW", "تخطي إعادة تحميل مكررة لنفس السيرفر: $cleanTargetUrl")
      return
    }

    currentUrl = cleanTargetUrl
    activeEmbedRefererUrl = cleanTargetUrl
    isCurrentUrlDirectEmbed = isDirectEmbed
    if (resetNativePlayer) {
      playNativeVideoUrl = null
      detectedVideoUrl = null
    }
    lastWebViewLoadedKeyRef.set(loadKey)

    if (targetWebView != null && isWebViewMounted) {
      pendingWebViewLoad = null
      executeWebViewLoad(
        wv = targetWebView,
        cleanTargetUrl = cleanTargetUrl,
        isDirectEmbed = isDirectEmbed,
        refererPage = refererPage,
        isGeneralBrowser = false
      )
    } else {
      // Lazily mount/recreate WebView ONLY when needed for WebView playback or JS extraction
      pendingWebViewLoad = PendingWebViewLoad(
        targetUrl = cleanTargetUrl,
        isDirectEmbed = isDirectEmbed,
        refererPage = refererPage,
        isGeneralBrowser = false
      )
      isWebViewMounted = true
    }
  }

  fun refreshSeriesSeasonsAndEpisodes(
    targetUrl: String,
    portal: StreamingPortal,
    titleHint: String,
    selectedSeasonNum: Int = 1,
    selectedEpisodeNum: Int = 0,
    autoLoadBestEmbed: Boolean = false,
    force: Boolean = false
  ) {
    val normalizedKey = "${portal.id}|${targetUrl.trim().trimEnd('/').lowercase()}|s${selectedSeasonNum}e${selectedEpisodeNum}"
    if (!force && (inFlightScrapeKeyRef.get() == normalizedKey || lastResolvedScrapeKeyRef.get() == normalizedKey)) {
      AppDiagnosticsLogger.log("ScraperDedup", "Skipped duplicate scrape for $normalizedKey")
      return
    }

    if (force) {
      inFlightScrapeKeyRef.set(null)
      lastResolvedScrapeKeyRef.set(null)
      scrapeForceNonce += 1L
    }

    isScrapingSeries = true
    detectedVideoQualities = emptyList()
    userManuallyDismissedNativePlayer = false
    failedNativeStreamUrls.clear()

    val pureShowHint = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(
      titleHint.ifBlank { seriesStructure.seriesTitle.ifBlank { lastSearchQuery.ifBlank { pageTitle } } }
    ).ifBlank { titleHint.ifBlank { lastSearchQuery.ifBlank { pageTitle } } }

    activeEpisodeScrapeRequest = EpisodeScrapeRequest(
      requestKey = normalizedKey,
      targetUrl = targetUrl,
      portal = portal,
      titleHint = pureShowHint,
      selectedSeasonNum = selectedSeasonNum,
      selectedEpisodeNum = selectedEpisodeNum,
      autoLoadBestEmbed = autoLoadBestEmbed,
      nonce = scrapeForceNonce
    )
  }

  // Strictly keyed on (requestKey, nonce) so Compose recompositions NEVER re-trigger scraping or double-load portals!
  LaunchedEffect(activeEpisodeScrapeRequest?.requestKey, activeEpisodeScrapeRequest?.nonce) {
    val req = activeEpisodeScrapeRequest ?: return@LaunchedEffect
    if (!inFlightScrapeKeyRef.compareAndSet(null, req.requestKey)) {
      if (inFlightScrapeKeyRef.get() == req.requestKey) {
        return@LaunchedEffect
      }
      inFlightScrapeKeyRef.set(req.requestKey)
    }

    AppDiagnosticsLogger.recordBreadcrumb(context, "SCRAPE_START", "${req.portal.id}: ${req.targetUrl}")
    try {
      val scraped = searchService.scrapeSeasonsAndEpisodes(
        pageUrl = req.targetUrl,
        portal = req.portal,
        titleHint = req.titleHint,
        selectedSeasonNumber = req.selectedSeasonNum,
        selectedEpisodeNumber = req.selectedEpisodeNum
      )
      lastResolvedScrapeKeyRef.set(req.requestKey)
      val prevBaseTitle = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(seriesStructure.seriesTitle)
        .lowercase().replace(Regex("[^a-z0-9\\u0621-\\u064A]+"), " ").trim()
      val newBaseTitle = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(
        scraped.seriesTitle.ifBlank { req.titleHint }
      ).lowercase().replace(Regex("[^a-z0-9\\u0621-\\u064A]+"), " ").trim()
      val isSameShow = prevBaseTitle.isNotBlank() &&
        newBaseTitle.isNotBlank() &&
        (prevBaseTitle == newBaseTitle || prevBaseTitle.contains(newBaseTitle) || newBaseTitle.contains(prevBaseTitle))

      val mergedSeasons = if (isSameShow && seriesStructure.seasons.size > 1 && scraped.seasons.isNotEmpty()) {
        val byNum = linkedMapOf<Int, ScrapedSeasonItem>()
        for (oldSeason in seriesStructure.seasons) {
          if (StreamingPortal.belongsToPortalDomain(oldSeason.url, req.portal)) {
            byNum[oldSeason.seasonNumber] = oldSeason
          }
        }
        for (freshSeason in scraped.seasons) {
          val existing = byNum[freshSeason.seasonNumber]
          val freshIsOverview = freshSeason.url.contains("/series/", ignoreCase = true) ||
            freshSeason.url.contains("/season/", ignoreCase = true) ||
            freshSeason.url.contains("/serie/", ignoreCase = true)
          val existingIsOverview = existing?.url?.let {
            it.contains("/series/", ignoreCase = true) ||
              it.contains("/season/", ignoreCase = true) ||
              it.contains("/serie/", ignoreCase = true)
          } ?: false
          if (existing == null || freshIsOverview || !existingIsOverview) {
            byNum[freshSeason.seasonNumber] = freshSeason
          }
        }
        byNum.values.sortedBy { it.seasonNumber }
      } else {
        scraped.seasons
      }

      seriesStructure = if (mergedSeasons.size > scraped.seasons.size) {
        scraped.copy(seasons = mergedSeasons)
      } else {
        scraped
      }
      isScrapingSeries = false

      AppDiagnosticsLogger.recordBreadcrumb(
        context,
        "SCRAPE_DONE",
        "seasons=${seriesStructure.seasons.size}, activeS=${seriesStructure.selectedSeasonNumber}, eps=${seriesStructure.episodes.size}, servers=${scraped.watchServers.size}, direct=${scraped.directStreamUrl.isNotBlank()}"
      )

      if (scraped.detectedQualities.isNotEmpty()) {
        mergeDetectedQualities(scraped.detectedQualities)
      }

      val resolvedPortalWatchUrl = scraped.watchServers.firstOrNull { !it.isDirectEmbed }?.embedUrl ?: req.targetUrl
      if (!StreamingPortal.isEmbedIframeUrl(resolvedPortalWatchUrl)) {
        activeWatchPageUrl = resolvedPortalWatchUrl
        val canonicalTitle = scraped.seriesTitle.ifBlank {
          req.titleHint.ifBlank { pageTitle.ifBlank { req.portal.displayNameArabic } }
        }
        repository.addHistory(
          title = canonicalTitle,
          url = resolvedPortalWatchUrl,
          portal = req.portal,
          seasonNumber = if (scraped.isSeries) scraped.selectedSeasonNumber else 0,
          episodeNumber = if (scraped.isSeries) scraped.currentEpisodeNumber else 0,
          isSeries = scraped.isSeries
        )
      }

      // If the scraper resolved a direct .m3u8 / .mp4 stream URL, kill any WebView and launch NativeTvExoPlayer!
      if (scraped.directStreamUrl.isNotBlank()) {
        val chosenDirectUrl = selectPreferredDirectStreamUrl(scraped.directStreamUrl, scraped.detectedQualities)
        val streamReferer = scraped.directStreamReferer.ifBlank {
          scraped.bestCleanPlayerUrl.ifBlank { resolvedPortalWatchUrl }
        }
        activeEmbedRefererUrl = streamReferer
        detectedVideoUrl = chosenDirectUrl
        detectedVideoHeaders = mapOf(
          "Referer" to streamReferer,
          "User-Agent" to desktopChromeUa
        )
        if (preferNativePlayer) {
          hardDestroyWebView("Direct stream resolved by scraper")
          playNativeVideoUrl = chosenDirectUrl
        }
      }

      // Only mount & load the embed server into WebView if the scraper did NOT resolve a direct stream!
      if (req.autoLoadBestEmbed && scraped.bestCleanPlayerUrl.isNotBlank()) {
        val bestServer = scraped.watchServers.firstOrNull { it.embedUrl == scraped.bestCleanPlayerUrl }
        val isDirect = bestServer?.isDirectEmbed ?: (scraped.bestCleanPlayerUrl != resolvedPortalWatchUrl)
        val resolvedName = bestServer?.name ?: "تلقائي"
        activeServerName = resolvedName
        AppDiagnosticsLogger.activeServerName = resolvedName
        if (scraped.directStreamUrl.isBlank()) {
          loadServerIntoWebView(
            targetWebView = webViewRef,
            targetServerUrl = scraped.bestCleanPlayerUrl,
            isDirectEmbed = isDirect,
            portalRefererPageUrl = resolvedPortalWatchUrl,
            resetNativePlayer = true
          )
          // Automatic SPA embed watchdog: if a dynamic embed (e.g. VidLink) does not emit a stream within 9s, auto-switch to the next direct server (e.g. VidSrc)
          val fallbackServer = scraped.watchServers.firstOrNull {
            it.isDirectEmbed &&
              it.embedUrl != scraped.bestCleanPlayerUrl &&
              !it.embedUrl.startsWith("topcinema-ajax://")
          }
          if (fallbackServer != null && scraped.bestCleanPlayerUrl.contains("vidlink.", ignoreCase = true)) {
            scope.launch {
              kotlinx.coroutines.delay(9000L)
              if (!isHomeActive &&
                  playNativeVideoUrl == null &&
                  detectedVideoUrl.isNullOrBlank() &&
                  currentUrl == scraped.bestCleanPlayerUrl
              ) {
                AppDiagnosticsLogger.log(
                  "AutoFallback",
                  "سيرفر ${bestServer?.name ?: "الأول"} تأخر في الاستجابة — التحويل تلقائياً إلى ${fallbackServer.name}"
                )
                activeServerName = fallbackServer.name
                AppDiagnosticsLogger.activeServerName = fallbackServer.name
                loadServerIntoWebView(
                  targetWebView = webViewRef,
                  targetServerUrl = fallbackServer.embedUrl,
                  isDirectEmbed = true,
                  portalRefererPageUrl = resolvedPortalWatchUrl,
                  resetNativePlayer = false,
                  forceReload = true
                )
              }
            }
          }
        } else {
          currentUrl = scraped.bestCleanPlayerUrl
          isCurrentUrlDirectEmbed = isDirect
        }
      }
    } finally {
      inFlightScrapeKeyRef.compareAndSet(req.requestKey, null)
      isScrapingSeries = false
    }
  }

  fun navigateTo(
    url: String,
    portal: StreamingPortal? = null,
    title: String? = null,
    isGeneralBrowser: Boolean = false,
    autoScrapeAndLoadEmbed: Boolean = !isGeneralBrowser,
    selectedSeasonNum: Int = 1,
    selectedEpisodeNum: Int = 0
  ) {
    val targetPortal = portal ?: activePortal
    val cleanUrl = if (isGeneralBrowser) {
      url.trim()
    } else {
      StreamingPortal.sanitizePortalWatchUrl(url, targetPortal)
    }
    val isEmbedTarget = !isGeneralBrowser && StreamingPortal.isEmbedIframeUrl(cleanUrl)

    AppDiagnosticsLogger.recordBreadcrumb(
      context,
      if (isGeneralBrowser) "NAV_BROWSER" else "NAV_PORTAL",
      "${targetPortal.id}: $cleanUrl"
    )

    activeServerSelectionJob?.cancel()
    activeServerSelectionJob = null
    currentUrl = cleanUrl
    if (!isEmbedTarget) {
      activeWatchPageUrl = cleanUrl
    }
    activeEmbedRefererUrl = cleanUrl
    activeServerName = ""
    isCurrentUrlDirectEmbed = isEmbedTarget
    isGeneralBrowserSession = isGeneralBrowser
    preferNativePlayer = !isGeneralBrowser
    userManuallyDismissedNativePlayer = false
    failedNativeStreamUrls.clear()
    detectedVideoUrl = null
    detectedVideoHeaders = emptyMap()
    detectedVideoQualities = emptyList()
    playNativeVideoUrl = null
    // Clear previous episode's subtitle file so it never stays stuck on the new episode
    if (!isGeneralBrowser) {
      activeSubtitleItem = null
      if (selectedEpisodeNum > 0) {
        seriesStructure = seriesStructure.copy(
          selectedSeasonNumber = selectedSeasonNum.coerceAtLeast(1),
          currentSeason = selectedSeasonNum.coerceAtLeast(1),
          currentEpisodeNumber = selectedEpisodeNum
        )
      }
    }
    if (title != null) pageTitle = title
    if (portal != null) {
      activePortal = portal
      repository.setActivePortal(portal)
    }
    if (!isGeneralBrowser && !isEmbedTarget && cleanUrl.startsWith("http")) {
      repository.addHistory(
        title = (title ?: pageTitle).ifBlank { targetPortal.displayNameArabic },
        url = cleanUrl,
        portal = targetPortal,
        seasonNumber = if (selectedEpisodeNum > 0 || selectedSeasonNum > 1) selectedSeasonNum else 0,
        episodeNumber = selectedEpisodeNum,
        isSeries = selectedEpisodeNum > 0 || selectedSeasonNum > 1
      )
    }
    isHomeActive = false

    if (isGeneralBrowser) {
      val wv = webViewRef
      if (wv != null && isWebViewMounted) {
        executeWebViewLoad(
          wv = wv,
          cleanTargetUrl = cleanUrl,
          isDirectEmbed = false,
          refererPage = targetPortal.homeUrl,
          isGeneralBrowser = true
        )
      } else {
        pendingWebViewLoad = PendingWebViewLoad(
          targetUrl = cleanUrl,
          isDirectEmbed = false,
          refererPage = targetPortal.homeUrl,
          isGeneralBrowser = true
        )
        isWebViewMounted = true
      }
    } else {
      // Kill any previous portal WebView immediately before starting fresh single-shot OkHttp scraping
      hardDestroyWebView("Switching to new episode/portal")
      inFlightScrapeKeyRef.set(null)
      lastResolvedScrapeKeyRef.set(null)
      val pureTitleHint = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(
        title ?: seriesStructure.seriesTitle.ifBlank { lastSearchQuery }
      ).ifBlank { title ?: lastSearchQuery }
      refreshSeriesSeasonsAndEpisodes(
        targetUrl = cleanUrl,
        portal = targetPortal,
        titleHint = pureTitleHint,
        selectedSeasonNum = selectedSeasonNum,
        selectedEpisodeNum = selectedEpisodeNum,
        autoLoadBestEmbed = autoScrapeAndLoadEmbed,
        force = true
      )
    }
  }

  fun decodeUrlSafely(raw: String): String {
    return try {
      URLDecoder.decode(raw, "UTF-8")
    } catch (_: Exception) {
      raw
    }
  }

  fun normalizePortalUrlForCompare(raw: String): String {
    return decodeUrlSafely(raw)
      .trim()
      .lowercase()
      .removeSuffix("/")
      .removeSuffix("/watch")
      .removeSuffix("/")
  }

  fun extractEpisodeNumberFromText(raw: String): Int? {
    if (raw.isBlank()) return null
    val decoded = decodeUrlSafely(raw)
    val tvPathMatch = Regex("/(?:tv|series)/(?:[^/]+/)?\\d+/(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
      .find(decoded)
    if (tvPathMatch != null) {
      return tvPathMatch.groupValues[2].toIntOrNull()
    }
    val seMatch = Regex("\\bS0*\\d{1,2}\\s*E0*(\\d{1,3})\\b", RegexOption.IGNORE_CASE)
      .findAll(decoded)
      .lastOrNull()
    if (seMatch != null) {
      return seMatch.groupValues[1].toIntOrNull()
    }
    val xMatch = Regex("\\b0*\\d{1,2}x0*(\\d{1,3})\\b", RegexOption.IGNORE_CASE)
      .findAll(decoded)
      .lastOrNull()
    if (xMatch != null) {
      return xMatch.groupValues[1].toIntOrNull()
    }
    val arabicMatch = Regex("(?:الحلق[ةه]|حلق[ةه])[\\s\\-:_]*(\\d{1,4})")
      .findAll(decoded)
      .lastOrNull()
    if (arabicMatch != null) {
      return arabicMatch.groupValues[1].toIntOrNull()
    }
    val englishMatch = Regex("(?:^|[\\s\\-_/])(?:episode|ep|halka)[\\s\\-:_]*(\\d{1,4})(?:[\\s\\-_/]|$)", RegexOption.IGNORE_CASE)
      .findAll(decoded)
      .lastOrNull()
    if (englishMatch != null) {
      return englishMatch.groupValues[1].toIntOrNull()
    }
    return null
  }

  fun resolveNextEpisode(): ScrapedEpisodeItem? {
    if (isGeneralBrowserSession) return null
    val currentWatchUrl = activeWatchPageUrl.ifBlank { currentUrl }
    val normalizedCurrent = normalizePortalUrlForCompare(currentWatchUrl)
    val currentSeason = (standardizedMediaInfo.seasonNumber ?: seriesStructure.selectedSeasonNumber).coerceAtLeast(1)
    val currentEpNum = extractEpisodeNumberFromText(activeEmbedRefererUrl)
      ?: extractEpisodeNumberFromText(currentWatchUrl)
      ?: standardizedMediaInfo.episodeNumber
      ?: seriesStructure.currentEpisodeNumber.takeIf { it > 0 && seriesStructure.isSeries }
      ?: extractEpisodeNumberFromText(pageTitle)

    // 1. Next episode in scraped episode list for current season (highest accuracy)
    val sortedEpisodes = seriesStructure.episodes.sortedBy { it.episodeNumber }
    if (sortedEpisodes.isNotEmpty()) {
      val currentIndex = sortedEpisodes.indexOfFirst { ep ->
        val normEp = normalizePortalUrlForCompare(ep.url)
        (normEp.isNotBlank() && normEp == normalizedCurrent) ||
          (currentEpNum != null && ep.episodeNumber == currentEpNum)
      }
      if (currentIndex in 0 until sortedEpisodes.lastIndex) {
        return sortedEpisodes[currentIndex + 1]
      }
      if (currentEpNum != null) {
        val nextByNumber = sortedEpisodes.firstOrNull { it.episodeNumber > currentEpNum }
        if (nextByNumber != null) return nextByNumber
      }
    }

    // 2. Direct nextEpisode detected by scraper (must differ from current episode)
    seriesStructure.nextEpisode?.let { directNext ->
      val normalizedNext = normalizePortalUrlForCompare(directNext.url)
      if (directNext.url.isNotBlank() &&
          normalizedNext != normalizedCurrent &&
          (currentEpNum == null || directNext.episodeNumber > currentEpNum)
      ) {
        return directNext
      }
    }

    // 3. Fallback to first episode of next season if available
    if (seriesStructure.seasons.isNotEmpty()) {
      val nextSeason = seriesStructure.seasons.firstOrNull { it.seasonNumber == currentSeason + 1 }
      val maxEpInSeason = sortedEpisodes.maxOfOrNull { it.episodeNumber }
      if (nextSeason != null && (maxEpInSeason == null || (currentEpNum != null && currentEpNum >= maxEpInSeason))) {
        return ScrapedEpisodeItem(
          episodeNumber = 1,
          title = "الموسم ${nextSeason.seasonNumber} - الحلقة 1",
          url = nextSeason.url,
          seasonNumber = nextSeason.seasonNumber
        )
      }
    }

    // 4. Smart URL synthesis when episode list was not scraped on watch page
    val lowerTitleAndUrl = (decodeUrlSafely(currentWatchUrl) + " " + pageTitle).lowercase()
    val isMarkedFinalEpisode = lowerTitleAndUrl.contains("الاخيرة") ||
      lowerTitleAndUrl.contains("الأخيرة") ||
      lowerTitleAndUrl.contains("الاخيره") ||
      lowerTitleAndUrl.contains("الأخيره")
    if (sortedEpisodes.isEmpty() && !isMarkedFinalEpisode && currentEpNum != null && currentEpNum > 0) {
      val nextEpNum = currentEpNum + 1
      val decodedUrl = decodeUrlSafely(currentWatchUrl)
      val tvPathRegex = Regex("(/(?:tv|series)/(?:[^/]+/)?\\d+/\\d+/)(\\d+)", RegexOption.IGNORE_CASE)
      val tvMatch = tvPathRegex.find(decodedUrl)
      if (tvMatch != null) {
        val synthesizedUrl = decodedUrl.replaceRange(tvMatch.range, "${tvMatch.groupValues[1]}$nextEpNum")
        return ScrapedEpisodeItem(
          episodeNumber = nextEpNum,
          title = "الحلقة $nextEpNum",
          url = synthesizedUrl,
          seasonNumber = currentSeason
        )
      }
      val arabicUrlRegex = Regex("(الحلق[ةه]-?)(\\d+)")
      val arabicMatch = arabicUrlRegex.find(decodedUrl)
      if (arabicMatch != null) {
        val replaced = decodedUrl.replaceRange(arabicMatch.range, "${arabicMatch.groupValues[1]}$nextEpNum")
        val reEncoded = android.net.Uri.encode(replaced, ":/?#[]@!$&'()*+,;=-._~%")
        return ScrapedEpisodeItem(
          episodeNumber = nextEpNum,
          title = "الحلقة $nextEpNum",
          url = reEncoded,
          seasonNumber = currentSeason
        )
      }
      val englishUrlRegex = Regex("((?:episode|ep|halka)[-_]?)(\\d+)", RegexOption.IGNORE_CASE)
      val englishMatch = englishUrlRegex.find(decodedUrl)
      if (englishMatch != null) {
        val replaced = decodedUrl.replaceRange(englishMatch.range, "${englishMatch.groupValues[1]}$nextEpNum")
        return ScrapedEpisodeItem(
          episodeNumber = nextEpNum,
          title = "الحلقة $nextEpNum",
          url = replaced,
          seasonNumber = currentSeason
        )
      }
    }

    return null
  }

  // Backed by derivedStateOf so movablePlayerBox (movableContentOf) ALWAYS receives the live next episode!
  val nextEpisodeItem by remember {
    derivedStateOf {
      resolveNextEpisode()
    }
  }

  fun buildCleanEpisodeNavTitle(seasonNum: Int, episodeNum: Int, fallbackEpTitle: String): String {
    val pureShow = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(
      standardizedMediaInfo.cleanTitle.ifBlank {
        seriesStructure.seriesTitle.ifBlank { lastSearchQuery.ifBlank { pageTitle } }
      }
    )
    val s = seasonNum.coerceAtLeast(1).toString().padStart(2, '0')
    val e = episodeNum.coerceAtLeast(1).toString().padStart(2, '0')
    return if (pureShow.isNotBlank()) "$pureShow S${s}E${e}" else fallbackEpTitle
  }

  fun playNextEpisodeAutomatically(nextEp: ScrapedEpisodeItem) {
    val nextTitle = buildCleanEpisodeNavTitle(nextEp.seasonNumber, nextEp.episodeNumber, nextEp.title)
    AppDiagnosticsLogger.log(
      "AutoNext",
      "تشغيل الحلقة التالية تلقائياً: $nextTitle | URL=${nextEp.url} | S${nextEp.seasonNumber}E${nextEp.episodeNumber}"
    )
    Toast.makeText(
      context,
      "جاري تشغيل الحلقة التالية تلقائياً: $nextTitle",
      Toast.LENGTH_SHORT
    ).show()
    navigateTo(
      url = nextEp.url,
      portal = activePortal,
      title = nextTitle,
      isGeneralBrowser = false,
      autoScrapeAndLoadEmbed = true,
      selectedSeasonNum = nextEp.seasonNumber,
      selectedEpisodeNum = nextEp.episodeNumber
    )
  }

  // Key Handler for Android TV Remote / D-pad / Keyboard
  val tvKeyHandler = remember {
    TvKeyHandler(
      getWebView = { webViewRef },
      getControlMode = { controlMode },
      onToggleControlMode = {
        val newMode =
          if (controlMode == ControlMode.VIRTUAL_MOUSE) ControlMode.DPAD_NAV else ControlMode.VIRTUAL_MOUSE
        controlMode = newMode
        repository.setControlMode(newMode)
        val msg =
          if (newMode == ControlMode.VIRTUAL_MOUSE) "تم تفعيل وضع الماوس (تحريك بالأسهم + نقر بـ OK)" else "تم تفعيل وضع الأسهم للتصفح والتمرير"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
      },
      onToggleTopBar = {
        isBarVisible = !isBarVisible
      },
      onToggleFullscreen = {
        toggleVideoFullscreen(!isFullscreenVideo)
      },
      onCursorMoved = { x, y ->
        cursorX = x
        cursorY = y
      },
      onBackHandled = {
        if (showSettingsDialog) {
          showSettingsDialog = false
          true
        } else if (showAccountDialog) {
          showAccountDialog = false
          true
        } else if (showDiagnosticsDialog) {
          showDiagnosticsDialog = false
          true
        } else if (showUpdateDialog) {
          showUpdateDialog = false
          true
        } else if (showDisplayModeDialog) {
          showDisplayModeDialog = false
          true
        } else if (showAdBlockDialog) {
          showAdBlockDialog = false
          true
        } else if (showFavoritesDialog) {
          showFavoritesDialog = false
          true
        } else if (showHistoryDialog) {
          showHistoryDialog = false
          true
        } else if (showGuideDialog) {
          showGuideDialog = false
          true
        } else if (showSubtitlesDialog) {
          showSubtitlesDialog = false
          true
        } else if (showQualityDialog) {
          showQualityDialog = false
          true
        } else if (isFullscreenVideo) {
          toggleVideoFullscreen(false)
          true
        } else if (webChromeClientRef?.isCustomViewShowing() == true) {
          webChromeClientRef?.onHideCustomView()
          true
        } else if (!isHomeActive) {
          isHomeActive = true
          true
        } else {
          val now = System.currentTimeMillis()
          if (now - lastBackPressTime < 2500) {
            activity?.finish()
            true
          } else {
            lastBackPressTime = now
            Toast.makeText(context, "اضغط مرة أخرى للخروج من التطبيق", Toast.LENGTH_SHORT).show()
            true
          }
        }
      }
    )
  }

  DisposableEffect(activity) {
    val keyEventProvider = activity as? KeyEventProvider
    keyEventProvider?.setKeyEventCallback { event ->
      val anyModalOpen = showSettingsDialog ||
        showAccountDialog ||
        showDiagnosticsDialog ||
        showUpdateDialog ||
        showDisplayModeDialog ||
        showFavoritesDialog ||
        showHistoryDialog ||
        showGuideDialog ||
        showSubtitlesDialog ||
        showQualityDialog ||
        showAdBlockDialog
      // NEVER let WebView TvKeyHandler intercept D-Pad keys on Home Dashboard, Native ExoPlayer, or any open modal dialog!
      if (isHomeActive || playNativeVideoUrl != null || !isWebViewMounted || anyModalOpen) {
        false
      } else {
        tvKeyHandler.handleKeyEvent(event)
      }
    }
    onDispose {
      keyEventProvider?.setKeyEventCallback(null)
    }
  }

  BackHandler {
    if (showSettingsDialog) {
      showSettingsDialog = false
    } else if (showAccountDialog) {
      showAccountDialog = false
    } else if (showDiagnosticsDialog) {
      showDiagnosticsDialog = false
    } else if (showUpdateDialog) {
      showUpdateDialog = false
    } else if (showDisplayModeDialog) {
      showDisplayModeDialog = false
    } else if (showFavoritesDialog) {
      showFavoritesDialog = false
    } else if (showHistoryDialog) {
      showHistoryDialog = false
    } else if (showGuideDialog) {
      showGuideDialog = false
    } else if (showSubtitlesDialog) {
      showSubtitlesDialog = false
    } else if (showQualityDialog) {
      showQualityDialog = false
    } else if (isFullscreenVideo) {
      toggleVideoFullscreen(false)
    } else if (webChromeClientRef?.isCustomViewShowing() == true) {
      webChromeClientRef?.onHideCustomView()
    } else if (!isHomeActive) {
      if (isGeneralBrowserSession && webViewRef?.canGoBack() == true) {
        webViewRef?.goBack()
      } else {
        isHomeActive = true
      }
    } else {
      val now = System.currentTimeMillis()
      if (now - lastBackPressTime < 2500) {
        activity?.finish()
      } else {
        lastBackPressTime = now
        Toast.makeText(context, "اضغط مرة أخرى للخروج من التطبيق", Toast.LENGTH_SHORT).show()
      }
    }
  }

  @Composable
  fun WebViewPlayerBox(boxModifier: Modifier) {
    Box(
      modifier = boxModifier
        .background(Color.Black)
    ) {
      // Strictly mount WebView ONLY when needed (General Browser or WebView fallback/extraction).
      // While NativeTvExoPlayer plays, WebView stays completely unmounted & destroyed (0MB Chromium RAM).
      val shouldMountWebView = isWebViewMounted && (isGeneralBrowserSession || playNativeVideoUrl == null)
      if (shouldMountWebView) {
        key(webViewRecreateKey) {
          DisposableEffect(webViewRecreateKey) {
            onDispose {
              val wv = webViewRef
              if (wv != null) {
                webViewRef = null
                fullscreenContainerRef = null
                webChromeClientRef = null
                safelyDestroyWebView(wv)
              }
            }
          }
          AndroidView(
            factory = { ctx ->
              AppDiagnosticsLogger.recordBreadcrumb(ctx, "WEBVIEW_CREATE", currentUrl)
              val rootLayout = FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT
                )
              }

              val fsContainer = FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT
                )
                visibility = View.GONE
              }
              fullscreenContainerRef = fsContainer

              val webView = try {
                com.example.AppRuntimeMemoryGuard.hasWebViewBeenCreated = true
                WebView(ctx)
              } catch (e: Throwable) {
                AppDiagnosticsLogger.log(
                  "WebViewInit",
                  "تعذر تشغيل WebView على الجهاز: ${e.message}",
                  isError = true
                )
                isWebViewMounted = false
                return@AndroidView rootLayout
              }.apply {
              layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
              )

              setBackgroundColor(android.graphics.Color.BLACK)
              setLayerType(View.LAYER_TYPE_NONE, null)
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                  setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
                } catch (_: Throwable) {}
              }

              isClickable = true
              isLongClickable = true
              isFocusable = true
              isFocusableInTouchMode = true
              requestFocus()

              setOnTouchListener { _, event ->
                if (displayMode == AppDisplayMode.TV && controlMode == ControlMode.VIRTUAL_MOUSE) {
                  if (event.action == MotionEvent.ACTION_DOWN) {
                    cursorX = event.x
                    cursorY = event.y
                  }
                }
                false
              }

              try {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
              } catch (_: Throwable) {}

              if (webViewRecreateKey > 0) {
                try {
                  clearCache(false)
                } catch (_: Throwable) {}
              }

              settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // Permanent Low-RAM profile (Xiaomi TV Stick & all devices): minimize WebView memory footprint
                databaseEnabled = false
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                allowContentAccess = true
                allowFileAccess = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_NO_CACHE

                offscreenPreRaster = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                  safeBrowsingEnabled = false
                }

                val useAndroidUa = isGeneralBrowserSession && (currentUrl.contains("google.") || displayMode == AppDisplayMode.MOBILE)
                userAgentString = if (useAndroidUa) cleanAndroidChromeUa else desktopChromeUa
              }

              addJavascriptInterface(
                TvPlayerBridge(
                  onVideoEnded = {
                    post {
                      if (autoPlayNextEpisode) {
                        resolveNextEpisode()?.let { nextEp ->
                          playNextEpisodeAutomatically(nextEp)
                        }
                      }
                    }
                  },
                  onVideoTimeUpdate = { curSec, _ ->
                    post {
                      webVideoCurrentTimeMs = (curSec * 1000f).toLong()
                    }
                  },
                  onCenterClickFiltered = { _, _ -> },
                  onVideoDetected = { url ->
                    post {
                      onStreamUrlIntercepted(
                        url = url,
                        headers = mapOf(
                          "Referer" to activeEmbedRefererUrl.ifBlank { currentUrl },
                          "User-Agent" to desktopChromeUa
                        ),
                        sourceTag = "JS-Bridge"
                      )
                    }
                  },
                  onQualitiesDetected = { heightsList ->
                    post {
                      try {
                        val list = heightsList
                          .filter { it in 140..4320 }
                          .distinct()
                          .sortedDescending()
                          .map { h ->
                            val badge = when {
                              h >= 1080 -> "FHD"
                              h >= 720 -> "HD"
                              h >= 480 -> "SD"
                              else -> "توفير"
                            }
                            VideoQualityOption(
                              id = h.toString(),
                              label = "${h}p $badge",
                              height = h
                            )
                          }
                        if (list.isNotEmpty()) {
                          mergeDetectedQualities(list)
                          AppDiagnosticsLogger.log(
                            "QUALITY",
                            "تم التقاط الجودات من مشغل الويب: ${list.joinToString { it.label }}"
                          )
                        }
                      } catch (_: Exception) {
                      }
                    }
                  },
                  onAdBlocked = { reason ->
                    post {
                      repository.incrementBlockedAdsCount()
                      AppDiagnosticsLogger.log("ADBLOCK", "حجب نافذة/نقرة إعلانية: $reason")
                    }
                  }
                ),
                "AndroidTvBridge"
              )

              isVerticalScrollBarEnabled = false
              isHorizontalScrollBarEnabled = false

              val chromeClient = TvWebChromeClient(
                fullscreenContainer = fsContainer,
                onFullscreenChanged = { isFs ->
                  toggleVideoFullscreen(isFs)
                },
                onProgressUpdate = { p -> loadingProgress = p },
                onTitleUpdate = { t ->
                  if (isGeneralBrowserSession || pageTitle.isBlank()) {
                    pageTitle = t
                  }
                },
                onPopupBlocked = {
                  repository.incrementBlockedAdsCount()
                  AppDiagnosticsLogger.log("ADBLOCK", "منع نافذة منبثقة (onCreateWindow)")
                }
              )
              webChromeClientRef = chromeClient
              webChromeClient = chromeClient

              webViewClient = TvWebViewClient(
                isAdBlockEnabled = { repository.isAdBlockEnabled() },
                isStrictIframeSandboxEnabled = { repository.isStrictIframeSandboxEnabled() },
                isGeneralBrowserMode = { isGeneralBrowserSession },
                onPageFinishedCallback = { url ->
                  val isEmbed = StreamingPortal.isEmbedIframeUrl(url)
                  if (isGeneralBrowserSession) {
                    currentUrl = url
                    val detected = StreamingPortal.fromUrl(url)
                    val title = pageTitle.ifBlank { url }
                    if (!isEmbed) {
                      repository.addHistory(title, url, detected)
                    }
                  } else if (!isCurrentUrlDirectEmbed && !isEmbed) {
                    currentUrl = url
                    val detected = StreamingPortal.fromUrl(url)
                    if (StreamingPortal.belongsToPortalDomain(url, detected)) {
                      activePortal = detected
                      repository.setActivePortal(detected)
                      activeWatchPageUrl = StreamingPortal.sanitizePortalWatchUrl(url, detected)
                      val title = pageTitle.ifBlank { url }
                      repository.addHistory(title, activeWatchPageUrl, detected)
                    }
                  }
                },
                onRenderProcessGoneCallback = {
                  // Dead WebView was already detached & destroyed inside TvWebViewClient.onRenderProcessGone.
                  // Mark it destroyed and clear refs immediately so DisposableEffect never double-destroys it.
                  webViewRef?.let { destroyedWebViews.add(it) }
                  webViewRef = null
                  fullscreenContainerRef = null
                  webChromeClientRef = null
                  lastWebViewLoadedKeyRef.set(null)

                  val now = System.currentTimeMillis()
                  if (now - lastRendererCrashTimeMs < 15_000L) {
                    rendererCrashCount++
                  } else {
                    rendererCrashCount = 1
                  }
                  lastRendererCrashTimeMs = now

                  if (rendererCrashCount <= 3) {
                    AppDiagnosticsLogger.log(
                      "WebViewOOM",
                      "إعادة بناء WebView بعد توقف محرك العرض (محاولة $rendererCrashCount/3)",
                      isError = true
                    )
                    // Wait briefly so Chromium finishes tearing down the crashed renderer before spawning a fresh one
                    scope.launch {
                      kotlinx.coroutines.delay(400L)
                      if (isWebViewMounted && (isGeneralBrowserSession || playNativeVideoUrl == null)) {
                        webViewRecreateKey++
                      }
                    }
                  } else {
                    isWebViewMounted = false
                    pendingWebViewLoad = null
                    AppDiagnosticsLogger.log(
                      "WebViewOOM",
                      "تم إيقاف WebView لحماية الذاكرة بعد تكرار تعطل صفحة $currentUrl",
                      isError = true
                    )
                    Toast.makeText(
                      context,
                      "تعذر تحميل الصفحة - جرّب سيرفراً آخر",
                      Toast.LENGTH_LONG
                    ).show()
                  }
                },
                onAdBlockedCallback = { blockedHostOrUrl ->
                  repository.incrementBlockedAdsCount()
                  AppDiagnosticsLogger.log("ADBLOCK", "منع إعلان: $blockedHostOrUrl")
                },
                onVideoDetectedCallback = { url, headers ->
                  post {
                    onStreamUrlIntercepted(
                      url = url,
                      headers = headers,
                      sourceTag = "WebView-Interceptor"
                    )
                  }
                }
              )

              val queued = pendingWebViewLoad
              pendingWebViewLoad = null
              if (queued != null) {
                executeWebViewLoad(
                  wv = this,
                  cleanTargetUrl = queued.targetUrl,
                  isDirectEmbed = queued.isDirectEmbed,
                  refererPage = queued.refererPage,
                  isGeneralBrowser = queued.isGeneralBrowser
                )
              } else {
                val rawUrl = if (currentUrl.isNotBlank() && currentUrl.startsWith("http")) currentUrl else activePortal.homeUrl
                if (!isGeneralBrowserSession && isCurrentUrlDirectEmbed && playNativeVideoUrl == null) {
                  executeWebViewLoad(
                    wv = this,
                    cleanTargetUrl = rawUrl,
                    isDirectEmbed = true,
                    refererPage = activeWatchPageUrl.ifBlank { activePortal.homeUrl },
                    isGeneralBrowser = false
                  )
                } else if (isGeneralBrowserSession) {
                  executeWebViewLoad(
                    wv = this,
                    cleanTargetUrl = rawUrl,
                    isDirectEmbed = false,
                    refererPage = activePortal.homeUrl,
                    isGeneralBrowser = true
                  )
                }
              }
            }

            webViewRef = webView
            rootLayout.addView(webView)
            rootLayout.addView(fsContainer)
            rootLayout
          },
          update = { _ -> },
          modifier = Modifier.fillMaxSize()
        )
        }
      }

      if (displayMode == AppDisplayMode.TV && controlMode == ControlMode.VIRTUAL_MOUSE) {
        VirtualMouseOverlay(
          controlMode = controlMode,
          cursorX = cursorX,
          cursorY = cursorY,
          modifier = Modifier.align(Alignment.TopStart)
        )
      }

      val isSocialOrSearchFeed = currentUrl.contains("facebook.com") ||
          currentUrl.contains("google.com") ||
          currentUrl.contains("instagram.com") ||
          currentUrl.contains("youtube.com")
      val showPrimaryNativePlayer = !isGeneralBrowserSession &&
          !isSocialOrSearchFeed &&
          (playNativeVideoUrl != null || (preferNativePlayer && isScrapingSeries))

      // Floating Top-Left Video Fullscreen & Diagnostics Controls when using Embedded WebView Player
      if (!isGeneralBrowserSession && !showPrimaryNativePlayer) {
        Row(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            onClick = { toggleVideoFullscreen(!isFullscreenVideo) },
            color = Color.Black.copy(alpha = 0.78f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.7f)),
            modifier = Modifier
              .heightIn(min = 36.dp)
              .testTag("btn_embedded_video_fullscreen")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = if (isFullscreenVideo) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = "تكبير",
                tint = CinemaAccent,
                modifier = Modifier.size(17.dp)
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = if (isFullscreenVideo) "تصغير" else "تكبير",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
              )
            }
          }

          Surface(
            onClick = { showDiagnosticsDialog = true },
            color = Color.Black.copy(alpha = 0.78f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, CinemaSecondary.copy(alpha = 0.7f)),
            modifier = Modifier
              .heightIn(min = 36.dp)
              .testTag("btn_embedded_diagnostics")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = "اللوج",
                tint = CinemaSecondary,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "اللوج",
                color = CinemaSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          nextEpisodeItem?.let { nextEp ->
            Surface(
              onClick = { playNextEpisodeAutomatically(nextEp) },
              color = CinemaPrimary.copy(alpha = 0.88f),
              shape = RoundedCornerShape(50),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
              modifier = Modifier
                .heightIn(min = 36.dp)
                .testTag("btn_embedded_next_episode")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.SkipNext,
                  contentDescription = "الحلقة التالية",
                  tint = Color.White,
                  modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "التالية",
                  color = Color.White,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.ExtraBold
                )
              }
            }
          }
        }
      }

      // Floating Toggle Button when Top Bar or Side Panel is hidden
      if (!isGeneralBrowserSession && (!isWatchSidePanelOpen || !isBarVisible || isFullscreenVideo) && !showPrimaryNativePlayer) {
        Surface(
          onClick = {
            if (isFullscreenVideo) {
              toggleVideoFullscreen(false)
            } else {
              isBarVisible = true
              isWatchSidePanelOpen = true
            }
          },
          color = CinemaPrimary.copy(alpha = 0.92f),
          shape = RoundedCornerShape(50),
          border = BorderStroke(1.dp, ClipboxWhite.copy(alpha = 0.6f)),
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .heightIn(min = 38.dp)
            .testTag("btn_open_watch_side_panel")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.VideoLibrary,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = "الحلقات",
              color = Color.White,
              fontSize = 13.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              softWrap = false
            )
          }
        }
      }

      // Live synchronized Subtitle Cue Overlay when using Embedded WebView Player
      if (!isGeneralBrowserSession && !showPrimaryNativePlayer && activeSubtitleItem != null) {
        ActiveSubtitleCueOverlay(
          activeSubtitle = activeSubtitleItem,
          currentPositionMs = webVideoCurrentTimeMs,
          subtitleStyle = subtitleStyle,
          modifier = Modifier
            .align(Alignment.BottomCenter)
        )
      }

      if (showPrimaryNativePlayer) {
        val currentWatchUrlForKey = activeWatchPageUrl.ifBlank { currentUrl }
        val activeEpNumForKey = extractEpisodeNumberFromText(currentWatchUrlForKey)
          ?: extractEpisodeNumberFromText(pageTitle)
          ?: if (seriesStructure.isSeries && seriesStructure.currentEpisodeNumber > 0) {
            seriesStructure.currentEpisodeNumber
          } else {
            null
          }
        val activeSeasonForKey = seriesStructure.selectedSeasonNumber.coerceAtLeast(1)
        val rawTitleForKey = seriesStructure.seriesTitle.ifBlank {
          pageTitle.ifBlank { lastSearchQuery }
        }
        val activeContentKey = remember(
          rawTitleForKey,
          activeSeasonForKey,
          activeEpNumForKey,
          seriesStructure.isSeries
        ) {
          TvPreferencesRepository.buildNormalizedContentKey(
            rawTitle = rawTitleForKey,
            seasonNumber = activeSeasonForKey,
            episodeNumber = activeEpNumForKey,
            isSeries = seriesStructure.isSeries || activeEpNumForKey != null
          )
        }
        val savedResumePosMs = remember(activeContentKey, currentWatchUrlForKey) {
          repository.getPlaybackProgress(activeContentKey)?.positionMs ?: 0L
        }

        val activeShowCleanForWatched = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(
          standardizedMediaInfo.cleanTitle.ifBlank { rawTitleForKey }
        ).ifBlank { rawTitleForKey }
        val activeEpNumResolved = (activeEpNumForKey ?: standardizedMediaInfo.episodeNumber ?: 0).coerceAtLeast(0)
        val activeSeasonResolved = (standardizedMediaInfo.seasonNumber ?: activeSeasonForKey).coerceAtLeast(1)
        val currentEpWatchedKey = if (activeEpNumResolved > 0) {
          TvPreferencesRepository.buildEpisodeWatchedKey(activeShowCleanForWatched, activeSeasonResolved, activeEpNumResolved)
        } else {
          ""
        }
        val isCurrentEpWatchedInPlayer = currentEpWatchedKey.isNotBlank() && watchedEpisodes.contains(currentEpWatchedKey)

        NativeTvExoPlayer(
          videoUrl = playNativeVideoUrl ?: "",
          title = standardizedMediaInfo.displayTitle,
          refererUrl = activeEmbedRefererUrl.ifBlank { currentUrl.ifBlank { activeWatchPageUrl } },
          capturedHeaders = detectedVideoHeaders,
          userAgent = desktopChromeUa,
          selectedQuality = currentVideoQuality,
          selectedResizeMode = currentVideoResizeMode,
          onSelectResizeMode = { mode ->
            repository.setVideoResizeMode(mode)
          },
          availableQualities = detectedVideoQualities,
          onSelectQualityOption = { option ->
            applyVideoQualityInPlayer(option.id, option.label, option.streamUrl)
          },
          activeSubtitle = activeSubtitleItem,
          subtitleStyle = subtitleStyle,
          onUpdateSubtitleStyle = { newStyle ->
            repository.setSubtitleStyle(newStyle)
          },
          nextEpisodeTitle = nextEpisodeItem?.let { ep ->
            buildCleanEpisodeNavTitle(ep.seasonNumber, ep.episodeNumber, ep.title)
          },
          isAutoPlayNextEnabled = autoPlayNextEpisode,
          onToggleAutoPlayNext = { enabled ->
            repository.setAutoPlayNextEpisodeEnabled(enabled)
          },
          onPlayNextEpisode = {
            nextEpisodeItem?.let { playNextEpisodeAutomatically(it) }
          },
          contentKey = activeContentKey,
          savedResumePositionMs = savedResumePosMs,
          onSavePlaybackProgress = { posMs, durMs ->
            if (activeContentKey.isNotBlank()) {
              repository.savePlaybackProgress(
                contentKey = activeContentKey,
                displayTitle = pageTitle.ifBlank { rawTitleForKey },
                positionMs = posMs,
                durationMs = durMs
              )
            }
          },
          onClearPlaybackProgress = {
            if (activeContentKey.isNotBlank()) {
              repository.clearPlaybackProgress(activeContentKey)
            }
          },
          isFullscreen = isFullscreenVideo,
          onToggleFullscreen = {
            toggleVideoFullscreen(!isFullscreenVideo)
          },
          onOpenSubtitles = {
            showSubtitlesDialog = true
          },
          onOpenDiagnostics = {
            showDiagnosticsDialog = true
          },
          audioBoostPercent = audioBoostPercent,
          onAudioBoostChanged = { pct ->
            repository.setAudioBoostPercent(pct)
          },
          isCurrentEpisodeWatched = isCurrentEpWatchedInPlayer,
          onScrobbleEvent = { action, progressPct ->
            if (activeEpNumResolved > 0 && activeShowCleanForWatched.isNotBlank()) {
              sendPlayerScrobbleToTrakt(
                showTitle = activeShowCleanForWatched,
                seasonNumber = activeSeasonResolved,
                episodeNumber = activeEpNumResolved,
                action = action,
                progressPercent = progressPct
              )
            }
          },
          onAutoMarkWatched = {
            if (activeEpNumResolved > 0 && activeShowCleanForWatched.isNotBlank()) {
              syncPlayerEpisodeWithTrakt(
                showTitle = activeShowCleanForWatched,
                seasonNumber = activeSeasonResolved,
                episodeNumber = activeEpNumResolved,
                markWatched = true,
                useScrobbleStop = true,
                showToast = true
              )
            }
          },
          onToggleWatchedManual = {
            if (activeEpNumResolved > 0 && activeShowCleanForWatched.isNotBlank()) {
              syncPlayerEpisodeWithTrakt(
                showTitle = activeShowCleanForWatched,
                seasonNumber = activeSeasonResolved,
                episodeNumber = activeEpNumResolved,
                markWatched = !isCurrentEpWatchedInPlayer,
                useScrobbleStop = false,
                showToast = true
              )
            }
          },
          externalPause = showSubtitlesDialog,
          onQualitiesDetected = { exoQualities ->
            mergeDetectedQualities(exoQualities)
          },
          onDismiss = {
            if (isFullscreenVideo) {
              toggleVideoFullscreen(false)
            } else {
              userManuallyDismissedNativePlayer = true
              preferNativePlayer = false
              playNativeVideoUrl = null
              val fallbackServerUrl = activeEmbedRefererUrl.ifBlank { currentUrl }
              loadServerIntoWebView(
                targetWebView = webViewRef,
                targetServerUrl = fallbackServerUrl,
                isDirectEmbed = isCurrentUrlDirectEmbed,
                portalRefererPageUrl = activeWatchPageUrl.ifBlank { activePortal.homeUrl },
                resetNativePlayer = false,
                forceReload = true
              )
            }
          },
          onFallbackToWebPlayer = {
            playNativeVideoUrl?.let { failedNativeStreamUrls.add(it) }
            detectedVideoUrl?.let { failedNativeStreamUrls.add(it) }
            preferNativePlayer = false
            playNativeVideoUrl = null
            val fallbackServerUrl = activeEmbedRefererUrl.ifBlank { currentUrl }
            loadServerIntoWebView(
              targetWebView = webViewRef,
              targetServerUrl = fallbackServerUrl,
              isDirectEmbed = isCurrentUrlDirectEmbed,
              portalRefererPageUrl = activeWatchPageUrl.ifBlank { activePortal.homeUrl },
              resetNativePlayer = false,
              forceReload = true
            )
            Toast.makeText(
              context,
              "تم التبديل لمشغل السيرفر المدمج",
              Toast.LENGTH_SHORT
            ).show()
          },
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }

  // Movable Player Box so toggling Fullscreen or rotating Portrait <-> Landscape
  // NEVER disposes or reloads NativeTvExoPlayer or WebView!
  val movablePlayerBox = remember {
    movableContentOf {
      WebViewPlayerBox(boxModifier = Modifier.fillMaxSize())
    }
  }

  // Display-aware 10-foot TV font scale boost across the whole app (kept gentle on Mobile to prevent clipping)
  val baseDensity = LocalDensity.current
  val uiFontBoost = if (displayMode == AppDisplayMode.TV) 1.15f else 1.04f
  val boostedDensity = remember(baseDensity, uiFontBoost) {
    Density(
      density = baseDensity.density,
      fontScale = (baseDensity.fontScale * uiFontBoost).coerceAtMost(1.42f)
    )
  }

  CompositionLocalProvider(LocalDensity provides boostedDensity) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CinemaDarkBackground,
    topBar = {
      if (!isHomeActive && isBarVisible && !isFullscreenVideo) {
        if (isGeneralBrowserSession) {
          TvTopBar(
            activePortal = activePortal,
            currentUrl = currentUrl,
            isGeneralBrowserMode = true,
            currentDisplayMode = displayMode,
            isBarVisible = isBarVisible,
            loadingProgress = loadingProgress,
            pageTitle = pageTitle,
            isBookmarked = repository.isBookmarked(currentUrl),
            onGoToHome = { isHomeActive = true },
            onOpenSubtitles = { showSubtitlesDialog = true },
            onSearchDirect = { portal, query ->
              lastSearchQuery = query
              scope.launch {
                lastSearchPortalResults = searchService.searchAllPortals(query)
              }
              val searchUrl = portal.buildSearchUrl(query)
              navigateTo(searchUrl, portal, "بحث: $query", isGeneralBrowser = false)
            },
            onOmniboxNavigate = { url, title ->
              navigateTo(url, null, title, isGeneralBrowser = true)
            },
            onPortalSelected = { portal ->
              navigateTo(portal.homeUrl, portal, portal.displayNameArabic, isGeneralBrowser = false)
            },
            onHomeClick = {
              navigateTo("https://www.google.com", null, "Google", isGeneralBrowser = true)
            },
            onBackClick = {
              if (webViewRef?.canGoBack() == true) webViewRef?.goBack()
              else isHomeActive = true
            },
            onForwardClick = {
              if (webViewRef?.canGoForward() == true) webViewRef?.goForward()
            },
            onRefreshClick = {
              webViewRef?.reload()
            },
            onBookmarkClick = {
              val title = pageTitle.ifBlank { currentUrl }
              if (repository.isBookmarked(currentUrl)) {
                val currentBookmarks = repository.getBookmarks()
                currentBookmarks.find { it.url == currentUrl }?.let {
                  repository.removeBookmark(it.id)
                  Toast.makeText(context, "تمت الإزالة من المفضلة", Toast.LENGTH_SHORT).show()
                }
              } else {
                repository.addBookmark(title, currentUrl, activePortal)
                Toast.makeText(context, "تمت الإضافة إلى المفضلة", Toast.LENGTH_SHORT).show()
              }
            },
            onToggleFullscreen = {
              toggleVideoFullscreen(!isFullscreenVideo)
            },
            onOpenGuide = { showGuideDialog = true },
            onOpenAdBlockSettings = { showAdBlockDialog = true },
            onOpenQuality = { showQualityDialog = true },
            onOpenUpdate = { showUpdateDialog = true },
            onToggleBarVisibility = { isBarVisible = !isBarVisible },
            modifier = if (displayMode == AppDisplayMode.MOBILE) Modifier.statusBarsPadding() else Modifier
          )
        } else {
          // Clean, Balanced RTL Top Bar for Asser TV Watch Screen (Mobile & TV/Tablet)
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
              color = CinemaDarkBackground,
              border = BorderStroke(1.dp, CinemaBorder),
              modifier = Modifier
                .fillMaxWidth()
                .then(if (displayMode == AppDisplayMode.MOBILE) Modifier.statusBarsPadding() else Modifier)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                // 1. Compact Back Button (Right side in RTL)
                Surface(
                  onClick = { isHomeActive = true },
                  color = CinemaSurface,
                  shape = RoundedCornerShape(10.dp),
                  border = BorderStroke(1.dp, CinemaBorder),
                  modifier = Modifier
                    .heightIn(min = 38.dp)
                    .testTag("btn_back_to_asser_tv_search")
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                      contentDescription = "العودة",
                      tint = Color.White,
                      modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                      text = "رجوع",
                      color = Color.White,
                      fontSize = 14.sp,
                      fontWeight = FontWeight.ExtraBold,
                      maxLines = 1,
                      softWrap = false
                    )
                  }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 2. Clean Portal & Show Header (Exact episode tag SxxExx is shown prominently INSIDE the player)
                Column(
                  modifier = Modifier.weight(1f),
                  horizontalAlignment = Alignment.Start
                ) {
                  Text(
                    text = standardizedMediaInfo.cleanTitle.ifBlank {
                      activePortal.displayNameArabic
                    },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = activePortal.displayNameArabic,
                    color = CinemaAccent,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 3. Crisp Non-Overlapping 32dp Action Buttons (Left side in RTL)
                Row(
                  horizontalArrangement = Arrangement.spacedBy(5.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  WatchTopBarSquareBtn(
                    icon = Icons.Default.BugReport,
                    tint = CinemaAccent,
                    containerColor = CinemaSurface,
                    contentDescription = "سجل الأعطال واللوج",
                    onClick = { showDiagnosticsDialog = true },
                    testTag = "btn_top_diagnostics_log"
                  )

                  WatchTopBarSquareBtn(
                    icon = Icons.Default.SystemUpdate,
                    tint = CinemaSuccess,
                    containerColor = CinemaSurface,
                    contentDescription = "التحديث الداخلي من جيت هب",
                    onClick = { showUpdateDialog = true },
                    testTag = "btn_top_github_update"
                  )

                  WatchTopBarSquareBtn(
                    icon = Icons.Default.VideoLibrary,
                    tint = if (isWatchSidePanelOpen) Color.Black else Color.White,
                    containerColor = if (isWatchSidePanelOpen) ClipboxWhite else CinemaSurface,
                    contentDescription = "إظهار/إخفاء لوحة البوابات والحلقات",
                    onClick = { isWatchSidePanelOpen = !isWatchSidePanelOpen },
                    testTag = "btn_top_toggle_side_panel"
                  )

                  WatchTopBarSquareBtn(
                    icon = Icons.Default.Fullscreen,
                    tint = Color.White,
                    containerColor = CinemaPrimary,
                    contentDescription = "ملء شاشة الفيديو",
                    onClick = { toggleVideoFullscreen(true) },
                    testTag = "btn_top_video_fullscreen"
                  )
                }
              }
            }
          }
        }
      }
    }
  ) { innerPadding ->
    if (isHomeActive) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .then(if (displayMode == AppDisplayMode.MOBILE) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
      ) {
        AppMainTabSwitcherBar(
          selectedTab = selectedMainTab,
          onSelectTab = { tab -> selectedMainTab = tab },
          controlMode = controlMode,
          displayMode = displayMode,
          onToggleControlMode = {
            val newMode = if (controlMode == ControlMode.VIRTUAL_MOUSE) ControlMode.DPAD_NAV else ControlMode.VIRTUAL_MOUSE
            controlMode = newMode
            repository.setControlMode(newMode)
            val msg = if (newMode == ControlMode.VIRTUAL_MOUSE) "تم تفعيل وضع الماوس (تحريك بالأسهم + نقر بـ OK)" else "تم تفعيل وضع الأسهم للتصفح والتمرير"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
          },
          onOpenDisplayMode = { showDisplayModeDialog = true },
          onOpenBookmarks = { showFavoritesDialog = true },
          onOpenHistory = { showHistoryDialog = true },
          onOpenAdBlock = { showAdBlockDialog = true },
          onOpenUpdate = { showUpdateDialog = true },
          onOpenDiagnostics = { showDiagnosticsDialog = true },
          accountDisplayName = currentAccount?.displayName.orEmpty(),
          onOpenAccount = { showAccountDialog = true },
          onOpenSettings = { showSettingsDialog = true }
        )

        fun resolveAndLaunchShowEpisode(
          showTitle: String,
          seasonNumber: Int?,
          episodeNumber: Int?,
          isMovie: Boolean,
          preferredPortal: StreamingPortal,
          directWatchUrl: String = ""
        ) {
          val cleanShow = showTitle.trim()
          if (cleanShow.isBlank()) return
          val targetSeason = (seasonNumber ?: 1).coerceAtLeast(1)
          val targetEp = if (isMovie) 0 else (episodeNumber ?: 1).coerceAtLeast(1)
          val epTag = if (!isMovie && targetEp > 0) {
            " S${targetSeason.toString().padStart(2, '0')}E${targetEp.toString().padStart(2, '0')}"
          } else {
            ""
          }

          if (directWatchUrl.startsWith("http")) {
            lastSearchQuery = cleanShow
            isWatchSidePanelOpen = true
            navigateTo(
              url = directWatchUrl,
              portal = preferredPortal,
              title = "$cleanShow$epTag",
              isGeneralBrowser = false,
              autoScrapeAndLoadEmbed = true,
              selectedSeasonNum = targetSeason,
              selectedEpisodeNum = targetEp
            )
            return
          }

          Toast.makeText(
            context,
            "جاري فتح $cleanShow$epTag عبر ${preferredPortal.displayNameArabic}...",
            Toast.LENGTH_SHORT
          ).show()

          scope.launch {
            val allResults = searchService.searchAllPortals(cleanShow)
            lastSearchQuery = cleanShow
            lastSearchPortalResults = allResults

            val portalOrder = listOf(
              preferredPortal,
              StreamingPortal.WATCH_SERIES,
              StreamingPortal.TOP_CINEMA,
              StreamingPortal.LAROZA,
              StreamingPortal.EGY_DEAD,
              StreamingPortal.FLIXER
            ).distinct()

            var chosenPortal = preferredPortal
            var bestResult: SearchResultItem? = null
            for (p in portalOrder) {
              val hits = allResults[p].orEmpty()
              if (hits.isNotEmpty()) {
                chosenPortal = p
                bestResult = hits.firstOrNull {
                  it.title.contains(cleanShow, ignoreCase = true)
                } ?: hits.first()
                break
              }
            }

            if (bestResult != null) {
              var targetUrl = bestResult.url
              // For WatchSeries TV URLs (/tv/slug/id or /tv/slug/id/1/1), jump directly to exact S/E!
              if (!isMovie && chosenPortal == StreamingPortal.WATCH_SERIES && targetUrl.contains("/tv/", ignoreCase = true)) {
                val wsMatch = Regex("""(https?://[^/]+/tv/[^/]+/\d+)(?:/\d+/\d+)?""", RegexOption.IGNORE_CASE).find(targetUrl)
                if (wsMatch != null) {
                  targetUrl = "${wsMatch.groupValues[1]}/$targetSeason/$targetEp"
                }
              }
              isWatchSidePanelOpen = true
              navigateTo(
                url = targetUrl,
                portal = chosenPortal,
                title = "$cleanShow$epTag",
                isGeneralBrowser = false,
                autoScrapeAndLoadEmbed = true,
                selectedSeasonNum = targetSeason,
                selectedEpisodeNum = targetEp
              )
            } else {
              // Fallback: open portal search page directly
              isWatchSidePanelOpen = true
              navigateTo(
                url = preferredPortal.buildSearchUrl(cleanShow),
                portal = preferredPortal,
                title = cleanShow,
                isGeneralBrowser = false,
                autoScrapeAndLoadEmbed = false
              )
            }
          }
        }

        when (selectedMainTab) {
          AppMainTab.BROWSER -> {
            BrowserHomeScreen(
              repository = repository,
              onOpenWebUrl = { url, title ->
                val detectedPortal = StreamingPortal.values().firstOrNull { p ->
                  p.homeUrl.isNotBlank() && url.contains(p.homeUrl.removePrefix("https://").removePrefix("http://").trimEnd('/'))
                }
                if (detectedPortal != null) {
                  navigateTo(url, detectedPortal, title, isGeneralBrowser = false)
                } else {
                  navigateTo(url, null, title, isGeneralBrowser = true)
                }
              },
              onSwitchToAsserTv = {
                selectedMainTab = AppMainTab.ASSER_TV
              },
              onOpenBookmarks = { showFavoritesDialog = true },
              onOpenHistory = { showHistoryDialog = true },
              modifier = Modifier.weight(1f)
            )
          }

          AppMainTab.TRAKT -> {
            TraktPortalScreen(
              repository = repository,
              traktSyncService = traktSyncService,
              onPlayTraktTarget = { showTitle, s, e, isMovie, preferredPortal ->
                resolveAndLaunchShowEpisode(
                  showTitle = showTitle,
                  seasonNumber = s,
                  episodeNumber = e,
                  isMovie = isMovie,
                  preferredPortal = preferredPortal
                )
              },
              onMarkUpNextItemWatched = { item ->
                markUpNextItemWatchedOfficial(item, showToast = true)
              },
              onTraktSyncNow = {
                triggerFullTraktSync(showToast = true)
              },
              onTraktCredentialsSaved = { clientId, clientSecret, accessToken, refreshToken, expiresAtMs, username ->
                scope.launch {
                  accountRepository.saveTraktConfigForCurrentAccount(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAtMs = expiresAtMs,
                    traktUsername = username
                  )
                }
                triggerFullTraktSync(showToast = true)
              },
              onTraktDisconnected = {
                scope.launch {
                  accountRepository.saveTraktConfigForCurrentAccount(
                    clientId = repository.getTraktClientId(),
                    clientSecret = repository.getTraktClientSecret(),
                    accessToken = "",
                    refreshToken = "",
                    expiresAtMs = 0L,
                    traktUsername = ""
                  )
                }
              },
              onOpenSettings = { showSettingsDialog = true },
              isSyncingNow = isSyncingTrakt,
              modifier = Modifier.weight(1f)
            )
          }

          AppMainTab.ASSER_TV -> {
            HomeDashboardScreen(
              repository = repository,
              onLaunchUrl = { url, portal, title ->
                isWatchSidePanelOpen = true
                navigateTo(url, portal, title, isGeneralBrowser = false, autoScrapeAndLoadEmbed = true)
              },
              onLaunchResultWithSources = { url, portal, title, query, resultsMap ->
                lastSearchQuery = query
                lastSearchPortalResults = resultsMap
                isWatchSidePanelOpen = true
                navigateTo(url, portal, title, isGeneralBrowser = false, autoScrapeAndLoadEmbed = true)
              },
              onOpenBookmarks = { showFavoritesDialog = true },
              onOpenHistory = { showHistoryDialog = true },
              onOpenDisplayMode = { showDisplayModeDialog = true },
              onOpenAdBlockSettings = { showAdBlockDialog = true },
              onOpenUpdate = { showUpdateDialog = true },
              onOpenSettings = { showSettingsDialog = true },
              onOpenTrakt = {
                selectedMainTab = AppMainTab.TRAKT
                isHomeActive = true
              },
              onPlayUpNext = { item ->
                resolveAndLaunchShowEpisode(
                  showTitle = item.showTitle,
                  seasonNumber = item.seasonNumber,
                  episodeNumber = item.episodeNumber,
                  isMovie = false,
                  preferredPortal = item.preferredPortal,
                  directWatchUrl = item.watchUrl
                )
              },
              onMarkUpNextWatched = { item ->
                markUpNextItemWatchedOfficial(item, showToast = true)
              },
              onPlayTraktCalendarItem = { calItem ->
                resolveAndLaunchShowEpisode(
                  showTitle = calItem.showTitle,
                  seasonNumber = calItem.seasonNumber,
                  episodeNumber = calItem.episodeNumber,
                  isMovie = false,
                  preferredPortal = StreamingPortal.WATCH_SERIES
                )
              },
              onPlayTraktWatchlistItem = { wlItem ->
                resolveAndLaunchShowEpisode(
                  showTitle = wlItem.title,
                  seasonNumber = if (wlItem.mediaType == "movie") null else 1,
                  episodeNumber = if (wlItem.mediaType == "movie") null else 1,
                  isMovie = wlItem.mediaType == "movie",
                  preferredPortal = StreamingPortal.WATCH_SERIES
                )
              },
              onSyncTraktFromHome = {
                triggerFullTraktSync(showToast = true)
              },
              onResumeHistory = { entry ->
                lastSearchQuery = entry.title
                isWatchSidePanelOpen = true
                navigateTo(
                  url = entry.url,
                  portal = entry.portal,
                  title = entry.title,
                  isGeneralBrowser = false,
                  autoScrapeAndLoadEmbed = true,
                  selectedSeasonNum = entry.seasonNumber.coerceAtLeast(1),
                  selectedEpisodeNum = entry.episodeNumber
                )
              },
              modifier = Modifier.weight(1f),
              searchService = searchService
            )
          }
        }
      }
    } else {
      val showSidePanelLayout = !isGeneralBrowserSession && !isFullscreenVideo && isWatchSidePanelOpen
      val isNativeActiveNow = playNativeVideoUrl != null
      val hasDetectedDirectStream = !detectedVideoUrl.isNullOrBlank()

      fun switchOrRetryNativePlayer() {
        userManuallyDismissedNativePlayer = false
        preferNativePlayer = true
        val existingUrl = detectedVideoUrl ?: seriesStructure.directStreamUrl
        if (existingUrl.isNotBlank()) {
          stopBackgroundWebViewVideo()
          playNativeVideoUrl = existingUrl
          Toast.makeText(context, "تم تفعيل المشغل الأساسي للتطبيق", Toast.LENGTH_SHORT).show()
        } else {
          Toast.makeText(context, "جاري استخراج البث المباشر للمشغل الأساسي...", Toast.LENGTH_SHORT).show()
          val targetEmbed = activeEmbedRefererUrl.ifBlank { currentUrl }
          scope.launch {
            val resolution = searchService.extractDirectStreamFromServer(
              embedUrl = targetEmbed,
              refererUrl = activeWatchPageUrl.ifBlank { activePortal.homeUrl }
            )
            if (resolution.primaryStreamUrl.isNotBlank()) {
              val chosenUrl = selectPreferredDirectStreamUrl(resolution.primaryStreamUrl, resolution.detectedQualities)
              detectedVideoUrl = chosenUrl
              detectedVideoHeaders = mapOf(
                "Referer" to resolution.refererUrl.ifBlank { targetEmbed },
                "User-Agent" to desktopChromeUa
              )
              if (resolution.detectedQualities.isNotEmpty()) {
                mergeDetectedQualities(resolution.detectedQualities)
              }
              playNativeVideoUrl = chosenUrl
            } else {
              Toast.makeText(
                context,
                "اضغط تشغيل داخل الفيديو ليقوم التطبيق بالتقاط البث تلقائياً",
                Toast.LENGTH_SHORT
              ).show()
            }
          }
        }
      }

      if (isPortrait) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .then(if (isFullscreenVideo) Modifier else Modifier.padding(innerPadding))
        ) {
          Box(
            modifier = if (showSidePanelLayout) {
              Modifier.weight(0.36f).fillMaxWidth()
            } else {
              Modifier.fillMaxSize()
            }
          ) {
            movablePlayerBox()
          }
          if (showSidePanelLayout) {
            WatchSidePanel(
              activePortal = activePortal,
              currentUrl = currentUrl,
              activeWatchPageUrl = activeWatchPageUrl,
              activeServerName = activeServerName,
              searchQuery = lastSearchQuery,
              portalResultsMap = lastSearchPortalResults,
              seriesStructure = seriesStructure,
              isScrapingSeries = isScrapingSeries,
              currentQuality = currentVideoQuality,
              detectedQualities = detectedVideoQualities,
              isNativePlayerActive = isNativeActiveNow,
              hasDetectedDirectStream = hasDetectedDirectStream,
              onSwitchToNativePlayer = { switchOrRetryNativePlayer() },
              onToggleVideoFullscreen = { toggleVideoFullscreen(!isFullscreenVideo) },
              onOpenDiagnostics = { showDiagnosticsDialog = true },
              onOpenUpdate = { showUpdateDialog = true },
              onSelectWatchServer = { server ->
                userManuallyDismissedNativePlayer = false
                preferNativePlayer = true
                activeEmbedRefererUrl = server.embedUrl
                activeServerName = server.name
                AppDiagnosticsLogger.activeServerName = server.name
                AppDiagnosticsLogger.recordBreadcrumb(context, "SELECT_SERVER", server.name)
                activeServerSelectionJob?.cancel()
                activeServerSelectionJob = scope.launch {
                  val resolution = searchService.extractDirectStreamFromServer(
                    embedUrl = server.embedUrl,
                    refererUrl = activeWatchPageUrl.ifBlank { activePortal.homeUrl }
                  )
                  if (resolution.primaryStreamUrl.isNotBlank() && preferNativePlayer) {
                    val chosenUrl = selectPreferredDirectStreamUrl(resolution.primaryStreamUrl, resolution.detectedQualities)
                    activeEmbedRefererUrl = resolution.refererUrl.ifBlank { server.embedUrl }
                    detectedVideoUrl = chosenUrl
                    detectedVideoHeaders = mapOf(
                      "Referer" to activeEmbedRefererUrl,
                      "User-Agent" to desktopChromeUa
                    )
                    if (resolution.detectedQualities.isNotEmpty()) {
                      mergeDetectedQualities(resolution.detectedQualities)
                    }
                    hardDestroyWebView("Selected server resolved direct stream")
                    playNativeVideoUrl = chosenUrl
                  } else {
                    val resolvedTarget = resolution.refererUrl.takeIf { it.startsWith("http") }
                      ?: server.embedUrl.takeIf { it.startsWith("http") }
                      ?: activeWatchPageUrl.ifBlank { activePortal.homeUrl }
                    loadServerIntoWebView(
                      targetWebView = webViewRef,
                      targetServerUrl = resolvedTarget,
                      isDirectEmbed = server.isDirectEmbed,
                      portalRefererPageUrl = activeWatchPageUrl.ifBlank { activePortal.homeUrl },
                      forceReload = true
                    )
                  }
                }
                Toast.makeText(context, "جاري تشغيل: ${server.name}", Toast.LENGTH_SHORT).show()
              },
              onSelectSearchResult = { item, portal ->
                val targetSeason = (standardizedMediaInfo.seasonNumber ?: seriesStructure.selectedSeasonNumber).coerceAtLeast(1)
                val targetEpisode = (standardizedMediaInfo.episodeNumber ?: seriesStructure.currentEpisodeNumber).coerceAtLeast(0)
                val navTitle = if (targetEpisode > 0) {
                  buildCleanEpisodeNavTitle(targetSeason, targetEpisode, item.title)
                } else {
                  item.title
                }
                navigateTo(
                  url = item.url,
                  portal = portal,
                  title = navTitle,
                  isGeneralBrowser = false,
                  autoScrapeAndLoadEmbed = true,
                  selectedSeasonNum = targetSeason,
                  selectedEpisodeNum = targetEpisode
                )
              },
              onSwitchPortalDirect = { portal ->
                val url = if (lastSearchQuery.isNotBlank()) portal.buildSearchUrl(lastSearchQuery) else portal.homeUrl
                navigateTo(url, portal, portal.displayNameArabic, isGeneralBrowser = false, autoScrapeAndLoadEmbed = false)
              },
              onSelectSeason = { season ->
                val pureShow = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(
                  standardizedMediaInfo.cleanTitle.ifBlank { seriesStructure.seriesTitle.ifBlank { lastSearchQuery } }
                )
                navigateTo(
                  url = season.url,
                  portal = activePortal,
                  title = if (pureShow.isNotBlank()) "$pureShow - ${season.title}" else season.title,
                  isGeneralBrowser = false,
                  autoScrapeAndLoadEmbed = true,
                  selectedSeasonNum = season.seasonNumber,
                  selectedEpisodeNum = 1
                )
              },
              onSelectEpisode = { episode ->
                navigateTo(
                  url = episode.url,
                  portal = activePortal,
                  title = buildCleanEpisodeNavTitle(episode.seasonNumber, episode.episodeNumber, episode.title),
                  isGeneralBrowser = false,
                  autoScrapeAndLoadEmbed = true,
                  selectedSeasonNum = episode.seasonNumber,
                  selectedEpisodeNum = episode.episodeNumber
                )
              },
              onRescrapePage = {
                userManuallyDismissedNativePlayer = false
                preferNativePlayer = true
                refreshSeriesSeasonsAndEpisodes(
                  targetUrl = activeWatchPageUrl.ifBlank { currentUrl },
                  portal = activePortal,
                  titleHint = lastSearchQuery.ifBlank { pageTitle },
                  selectedSeasonNum = seriesStructure.selectedSeasonNumber,
                  autoLoadBestEmbed = true,
                  force = true
                )
              },
              onSelectQuality = { option ->
                applyVideoQualityInPlayer(option.id, option.label, option.streamUrl)
              },
              onOpenSubtitles = { showSubtitlesDialog = true },
              onClosePanel = { isWatchSidePanelOpen = false },
              watchedEpisodes = watchedEpisodes,
              seriesTitleForWatched = standardizedMediaInfo.cleanTitle.ifBlank {
                seriesStructure.seriesTitle.ifBlank { lastSearchQuery }
              },
              isCurrentEpisodeWatched = run {
                val showT = standardizedMediaInfo.cleanTitle.ifBlank { seriesStructure.seriesTitle.ifBlank { lastSearchQuery } }
                val sN = (standardizedMediaInfo.seasonNumber ?: seriesStructure.selectedSeasonNumber).coerceAtLeast(1)
                val eN = (standardizedMediaInfo.episodeNumber ?: seriesStructure.currentEpisodeNumber).coerceAtLeast(0)
                val k = if (eN > 0) TvPreferencesRepository.buildEpisodeWatchedKey(showT, sN, eN) else ""
                k.isNotBlank() && watchedEpisodes.contains(k)
              },
              onToggleCurrentEpisodeWatched = {
                val showT = standardizedMediaInfo.cleanTitle.ifBlank { seriesStructure.seriesTitle.ifBlank { lastSearchQuery } }
                val sN = (standardizedMediaInfo.seasonNumber ?: seriesStructure.selectedSeasonNumber).coerceAtLeast(1)
                val eN = (standardizedMediaInfo.episodeNumber ?: seriesStructure.currentEpisodeNumber).coerceAtLeast(0)
                if (showT.isNotBlank() && eN > 0) {
                  val already = repository.isEpisodeWatched(showT, sN, eN)
                  syncPlayerEpisodeWithTrakt(
                    showTitle = showT,
                    seasonNumber = sN,
                    episodeNumber = eN,
                    markWatched = !already,
                    useScrobbleStop = false,
                    showToast = true
                  )
                }
              },
              isCompactMobileSheet = true,
              modifier = Modifier
                .weight(0.64f)
                .fillMaxWidth()
                .then(if (displayMode == AppDisplayMode.MOBILE) Modifier.navigationBarsPadding() else Modifier)
            )
          }
        }
      } else {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .then(if (isFullscreenVideo) Modifier else Modifier.padding(innerPadding))
        ) {
          Box(
            modifier = if (showSidePanelLayout) {
              Modifier.weight(1f).fillMaxHeight()
            } else {
              Modifier.fillMaxSize()
            }
          ) {
            movablePlayerBox()
          }
          if (showSidePanelLayout) {
            WatchSidePanel(
              activePortal = activePortal,
              currentUrl = currentUrl,
              activeWatchPageUrl = activeWatchPageUrl,
              activeServerName = activeServerName,
              searchQuery = lastSearchQuery,
              portalResultsMap = lastSearchPortalResults,
              seriesStructure = seriesStructure,
              isScrapingSeries = isScrapingSeries,
              currentQuality = currentVideoQuality,
              detectedQualities = detectedVideoQualities,
              isNativePlayerActive = isNativeActiveNow,
              hasDetectedDirectStream = hasDetectedDirectStream,
              onSwitchToNativePlayer = { switchOrRetryNativePlayer() },
              onToggleVideoFullscreen = { toggleVideoFullscreen(!isFullscreenVideo) },
              onOpenDiagnostics = { showDiagnosticsDialog = true },
              onOpenUpdate = { showUpdateDialog = true },
              onSelectWatchServer = { server ->
                userManuallyDismissedNativePlayer = false
                preferNativePlayer = true
                activeEmbedRefererUrl = server.embedUrl
                activeServerName = server.name
                AppDiagnosticsLogger.activeServerName = server.name
                AppDiagnosticsLogger.recordBreadcrumb(context, "SELECT_SERVER", server.name)
                activeServerSelectionJob?.cancel()
                activeServerSelectionJob = scope.launch {
                  val resolution = searchService.extractDirectStreamFromServer(
                    embedUrl = server.embedUrl,
                    refererUrl = activeWatchPageUrl.ifBlank { activePortal.homeUrl }
                  )
                  if (resolution.primaryStreamUrl.isNotBlank() && preferNativePlayer) {
                    val chosenUrl = selectPreferredDirectStreamUrl(resolution.primaryStreamUrl, resolution.detectedQualities)
                    activeEmbedRefererUrl = resolution.refererUrl.ifBlank { server.embedUrl }
                    detectedVideoUrl = chosenUrl
                    detectedVideoHeaders = mapOf(
                      "Referer" to activeEmbedRefererUrl,
                      "User-Agent" to desktopChromeUa
                    )
                    if (resolution.detectedQualities.isNotEmpty()) {
                      mergeDetectedQualities(resolution.detectedQualities)
                    }
                    hardDestroyWebView("Selected server resolved direct stream")
                    playNativeVideoUrl = chosenUrl
                  } else {
                    val resolvedTarget = resolution.refererUrl.takeIf { it.startsWith("http") }
                      ?: server.embedUrl.takeIf { it.startsWith("http") }
                      ?: activeWatchPageUrl.ifBlank { activePortal.homeUrl }
                    loadServerIntoWebView(
                      targetWebView = webViewRef,
                      targetServerUrl = resolvedTarget,
                      isDirectEmbed = server.isDirectEmbed,
                      portalRefererPageUrl = activeWatchPageUrl.ifBlank { activePortal.homeUrl },
                      forceReload = true
                    )
                  }
                }
                Toast.makeText(context, "جاري تشغيل: ${server.name}", Toast.LENGTH_SHORT).show()
              },
              onSelectSearchResult = { item, portal ->
                val targetSeason = (standardizedMediaInfo.seasonNumber ?: seriesStructure.selectedSeasonNumber).coerceAtLeast(1)
                val targetEpisode = (standardizedMediaInfo.episodeNumber ?: seriesStructure.currentEpisodeNumber).coerceAtLeast(0)
                val navTitle = if (targetEpisode > 0) {
                  buildCleanEpisodeNavTitle(targetSeason, targetEpisode, item.title)
                } else {
                  item.title
                }
                navigateTo(
                  url = item.url,
                  portal = portal,
                  title = navTitle,
                  isGeneralBrowser = false,
                  autoScrapeAndLoadEmbed = true,
                  selectedSeasonNum = targetSeason,
                  selectedEpisodeNum = targetEpisode
                )
              },
              onSwitchPortalDirect = { portal ->
                val url = if (lastSearchQuery.isNotBlank()) portal.buildSearchUrl(lastSearchQuery) else portal.homeUrl
                navigateTo(url, portal, portal.displayNameArabic, isGeneralBrowser = false, autoScrapeAndLoadEmbed = false)
              },
              onSelectSeason = { season ->
                val pureShow = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(
                  standardizedMediaInfo.cleanTitle.ifBlank { seriesStructure.seriesTitle.ifBlank { lastSearchQuery } }
                )
                navigateTo(
                  url = season.url,
                  portal = activePortal,
                  title = if (pureShow.isNotBlank()) "$pureShow - ${season.title}" else season.title,
                  isGeneralBrowser = false,
                  autoScrapeAndLoadEmbed = true,
                  selectedSeasonNum = season.seasonNumber,
                  selectedEpisodeNum = 1
                )
              },
              onSelectEpisode = { episode ->
                navigateTo(
                  url = episode.url,
                  portal = activePortal,
                  title = buildCleanEpisodeNavTitle(episode.seasonNumber, episode.episodeNumber, episode.title),
                  isGeneralBrowser = false,
                  autoScrapeAndLoadEmbed = true,
                  selectedSeasonNum = episode.seasonNumber,
                  selectedEpisodeNum = episode.episodeNumber
                )
              },
              onRescrapePage = {
                userManuallyDismissedNativePlayer = false
                preferNativePlayer = true
                refreshSeriesSeasonsAndEpisodes(
                  targetUrl = activeWatchPageUrl.ifBlank { currentUrl },
                  portal = activePortal,
                  titleHint = lastSearchQuery.ifBlank { pageTitle },
                  selectedSeasonNum = seriesStructure.selectedSeasonNumber,
                  autoLoadBestEmbed = true,
                  force = true
                )
              },
              onSelectQuality = { option ->
                applyVideoQualityInPlayer(option.id, option.label, option.streamUrl)
              },
              onOpenSubtitles = { showSubtitlesDialog = true },
              onClosePanel = { isWatchSidePanelOpen = false },
              watchedEpisodes = watchedEpisodes,
              seriesTitleForWatched = standardizedMediaInfo.cleanTitle.ifBlank {
                seriesStructure.seriesTitle.ifBlank { lastSearchQuery }
              },
              isCurrentEpisodeWatched = run {
                val showT = standardizedMediaInfo.cleanTitle.ifBlank { seriesStructure.seriesTitle.ifBlank { lastSearchQuery } }
                val sN = (standardizedMediaInfo.seasonNumber ?: seriesStructure.selectedSeasonNumber).coerceAtLeast(1)
                val eN = (standardizedMediaInfo.episodeNumber ?: seriesStructure.currentEpisodeNumber).coerceAtLeast(0)
                val k = if (eN > 0) TvPreferencesRepository.buildEpisodeWatchedKey(showT, sN, eN) else ""
                k.isNotBlank() && watchedEpisodes.contains(k)
              },
              onToggleCurrentEpisodeWatched = {
                val showT = standardizedMediaInfo.cleanTitle.ifBlank { seriesStructure.seriesTitle.ifBlank { lastSearchQuery } }
                val sN = (standardizedMediaInfo.seasonNumber ?: seriesStructure.selectedSeasonNumber).coerceAtLeast(1)
                val eN = (standardizedMediaInfo.episodeNumber ?: seriesStructure.currentEpisodeNumber).coerceAtLeast(0)
                if (showT.isNotBlank() && eN > 0) {
                  val already = repository.isEpisodeWatched(showT, sN, eN)
                  syncPlayerEpisodeWithTrakt(
                    showTitle = showT,
                    seasonNumber = sN,
                    episodeNumber = eN,
                    markWatched = !already,
                    useScrobbleStop = false,
                    showToast = true
                  )
                }
              },
              isCompactMobileSheet = false,
              modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
            )
          }
        }
      }
    }
  }

  // Dialogs
  if (showSubtitlesDialog) {
    OpenSubtitlesDialog(
      initialQuery = standardizedMediaInfo.subtitleQuery.ifBlank {
        pageTitle.ifBlank { lastSearchQuery.ifBlank { "VisionQuest" } }
      },
      seasonNumber = standardizedMediaInfo.seasonNumber,
      episodeNumber = standardizedMediaInfo.episodeNumber,
      embedUrlHint = activeEmbedRefererUrl.ifBlank { currentUrl },
      refererUrlHint = activeWatchPageUrl.ifBlank { activePortal.homeUrl },
      activeSubtitle = activeSubtitleItem,
      subtitleStyle = subtitleStyle,
      onUpdateSubtitleStyle = { newStyle ->
        repository.setSubtitleStyle(newStyle)
      },
      onClearSubtitle = {
        activeSubtitleItem = null
        showSubtitlesDialog = false
        Toast.makeText(context, "تم إيقاف الترجمة", Toast.LENGTH_SHORT).show()
      },
      onDismiss = { showSubtitlesDialog = false },
      onApplySubtitle = { sub ->
        showSubtitlesDialog = false
        activeSubtitleItem = sub
        val trackSrc = sub.rawVttDataUri.ifBlank { sub.downloadUrl }
        val jsInject = """
          (function() {
            var applied = false;
            var videos = document.querySelectorAll('video');
            for (var i = 0; i < videos.length; i++) {
              var v = videos[i];
              var oldTrack = document.getElementById('cinema-tv-sub-track-' + i);
              if (oldTrack) oldTrack.remove();
              var track = document.createElement('track');
              track.id = 'cinema-tv-sub-track-' + i;
              track.kind = 'subtitles';
              track.label = '${sub.languageName}';
              track.srclang = '${sub.languageCode}';
              track.src = '$trackSrc';
              track.default = true;
              v.appendChild(track);
              if (v.textTracks && v.textTracks.length > 0) {
                for (var t = 0; t < v.textTracks.length; t++) {
                  v.textTracks[t].mode = 'showing';
                }
              }
              track.mode = 'showing';
              applied = true;
            }

            var iframes = document.querySelectorAll('iframe');
            for (var j = 0; j < iframes.length; j++) {
              try {
                var doc = iframes[j].contentDocument || iframes[j].contentWindow.document;
                if (doc) {
                  var ivideos = doc.querySelectorAll('video');
                  for (var k = 0; k < ivideos.length; k++) {
                    var iv = ivideos[k];
                    var iOldTrack = doc.getElementById('cinema-tv-sub-track-' + k);
                    if (iOldTrack) iOldTrack.remove();
                    var iTrack = doc.createElement('track');
                    iTrack.id = 'cinema-tv-sub-track-' + k;
                    iTrack.kind = 'subtitles';
                    iTrack.label = '${sub.languageName}';
                    iTrack.srclang = '${sub.languageCode}';
                    iTrack.src = '$trackSrc';
                    iTrack.default = true;
                    iv.appendChild(iTrack);
                    iTrack.mode = 'showing';
                    applied = true;
                  }
                }
              } catch(e) {}
            }
            return applied ? 'APPLIED' : 'QUEUED';
          })();
        """.trimIndent()
        webViewRef?.evaluateJavascript(jsInject) { _ ->
          Toast.makeText(
            context,
            "تم تفعيل الترجمة (${sub.sourceSite}): ${sub.releaseName}",
            Toast.LENGTH_LONG
          ).show()
        }
      },
      service = searchService
    )
  }

  if (showFavoritesDialog) {
    FavoritesDialog(
      bookmarks = bookmarks,
      onSelectBookmark = { bookmark ->
        showFavoritesDialog = false
        navigateTo(bookmark.url, bookmark.portal, bookmark.title, isGeneralBrowser = false)
      },
      onRemoveBookmark = { id -> repository.removeBookmark(id) },
      onDismiss = { showFavoritesDialog = false }
    )
  }

  if (showHistoryDialog) {
    HistoryDialog(
      history = history,
      onSelectEntry = { entry ->
        showHistoryDialog = false
        lastSearchQuery = entry.title
        isWatchSidePanelOpen = true
        navigateTo(
          url = entry.url,
          portal = entry.portal,
          title = entry.title,
          isGeneralBrowser = false,
          autoScrapeAndLoadEmbed = true,
          selectedSeasonNum = entry.seasonNumber.coerceAtLeast(1),
          selectedEpisodeNum = entry.episodeNumber
        )
      },
      onRemoveEntry = { id -> repository.removeHistoryEntry(id) },
      onClearHistory = { repository.clearHistory() },
      onDismiss = { showHistoryDialog = false }
    )
  }

  if (showGuideDialog) {
    RemoteGuideDialog(onDismiss = { showGuideDialog = false })
  }

  if (showQualityDialog) {
    VideoQualityDialog(
      currentQuality = currentVideoQuality,
      onSelectQuality = { qualityId, qualityLabel ->
        showQualityDialog = false
        applyVideoQualityInPlayer(qualityId, qualityLabel)
      },
      onDismiss = { showQualityDialog = false }
    )
  }

  if (showDisplayModeDialog) {
    DisplayModeSelectionDialog(
      currentMode = displayMode,
      onModeSelected = { mode ->
        repository.setDisplayMode(mode)
        showDisplayModeDialog = false
        val useAndroidUa = isGeneralBrowserSession && (currentUrl.contains("google.") || mode == AppDisplayMode.MOBILE)
        webViewRef?.settings?.userAgentString = if (useAndroidUa) cleanAndroidChromeUa else desktopChromeUa
        if (mode == AppDisplayMode.TV) {
          activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
          Toast.makeText(context, "تم التحويل إلى منظور الشاشة أو التابلت (أفقي 16:9) 📺", Toast.LENGTH_SHORT).show()
        } else {
          activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
          Toast.makeText(context, "تم التحويل إلى منظور الهاتف المحمول (رأسي/لمس) 📱", Toast.LENGTH_SHORT).show()
        }
        webViewRef?.reload()
      },
      onDismiss = { showDisplayModeDialog = false }
    )
  }

  if (showAdBlockDialog) {
    AdBlockSettingsDialog(
      repository = repository,
      onDismiss = { showAdBlockDialog = false },
      onSettingsChanged = {
        webViewRef?.reload()
      }
    )
  }

  if (showUpdateDialog) {
    AppUpdateDialog(
      repository = repository,
      onDismiss = { showUpdateDialog = false }
    )
  }

  if (showDiagnosticsDialog) {
    DiagnosticsLogDialog(
      onDismiss = { showDiagnosticsDialog = false }
    )
  }

  if (showAccountDialog) {
    AccountAuthDialog(
      accountRepository = accountRepository,
      onAccountAuthenticated = { acc: UserAccountEntity ->
        if (acc.traktClientId.isNotBlank() && acc.traktAccessToken.isNotBlank()) {
          repository.saveTraktOAuthCredentials(
            clientId = acc.traktClientId,
            clientSecret = acc.traktClientSecret,
            accessToken = acc.traktAccessToken,
            refreshToken = acc.traktRefreshToken,
            expiresAtMs = acc.traktTokenExpiresAtMs,
            username = acc.traktUsername
          )
          triggerFullTraktSync(showToast = false)
        } else if (acc.traktClientId.isNotBlank()) {
          repository.saveTraktAppKeys(acc.traktClientId, acc.traktClientSecret)
        } else if (repository.isTraktConnected()) {
          // Persist currently linked Trakt credentials into this newly logged-in account
          scope.launch {
            accountRepository.saveTraktConfigForCurrentAccount(
              clientId = repository.getTraktClientId(),
              clientSecret = repository.getTraktClientSecret(),
              accessToken = repository.getTraktAccessToken(),
              refreshToken = repository.getTraktRefreshToken(),
              expiresAtMs = repository.getTraktTokenExpiresAtMs(),
              traktUsername = repository.getTraktUsername()
            )
          }
        }
      },
      onAccountLoggedOut = {
        repository.disconnectTrakt()
      },
      onDismiss = { showAccountDialog = false }
    )
  }

  if (showSettingsDialog) {
    UnifiedSettingsDialog(
      repository = repository,
      displayMode = displayMode,
      controlMode = controlMode,
      onOpenDisplayMode = { showDisplayModeDialog = true },
      onToggleControlMode = {
        val newMode = if (controlMode == ControlMode.VIRTUAL_MOUSE) ControlMode.DPAD_NAV else ControlMode.VIRTUAL_MOUSE
        controlMode = newMode
        repository.setControlMode(newMode)
        val msg = if (newMode == ControlMode.VIRTUAL_MOUSE) "تم تفعيل وضع الماوس (تحريك بالأسهم + نقر بـ OK)" else "تم تفعيل وضع الأسهم للتصفح والتمرير"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
      },
      onOpenAdBlock = { showAdBlockDialog = true },
      onOpenUpdate = { showUpdateDialog = true },
      onOpenDiagnostics = { showDiagnosticsDialog = true },
      onAdminTraktKeysUpdated = {
        if (repository.isTraktConnected()) {
          triggerFullTraktSync(showToast = false)
        }
      },
      onDismiss = { showSettingsDialog = false }
    )
  }
  }
}

@Composable
private fun WatchTopBarSquareBtn(
  icon: ImageVector,
  tint: Color,
  containerColor: Color,
  contentDescription: String,
  onClick: () -> Unit,
  testTag: String
) {
  Surface(
    onClick = onClick,
    color = containerColor,
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(1.dp, CinemaBorder),
    modifier = Modifier
      .size(32.dp)
      .testTag(testTag)
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(16.dp)
      )
    }
  }
}
