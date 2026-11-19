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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.data.TvPreferencesRepository
import com.example.model.AppMainTab
import com.example.model.ControlMode
import com.example.model.ScrapedEpisodeItem
import com.example.model.ScrapedSeriesStructure
import com.example.model.SearchResultItem
import com.example.model.StreamingPortal
import com.example.model.SubtitleItem
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

  var currentVideoQuality by remember { mutableStateOf("auto") }
  var detectedVideoQualities by remember { mutableStateOf<List<VideoQualityOption>>(emptyList()) }
  var isHomeActive by remember { mutableStateOf(true) }
  var selectedMainTab by remember { mutableStateOf(AppMainTab.BROWSER) }
  var isGeneralBrowserSession by remember { mutableStateOf(true) }

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
  var currentUrl by remember {
    mutableStateOf(activePortal.homeUrl.replace("topcinema.site", "topcinema.io"))
  }
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
  var webVideoCurrentTimeMs by remember { mutableStateOf(0L) }
  val failedNativeStreamUrls = remember { mutableSetOf<String>() }

  // Ensure permissive SSL is installed early for all streaming CDNs
  LaunchedEffect(Unit) {
    SearchAndSubtitlesService.installPermissiveSslForStreaming()
  }

  // Keep AppDiagnosticsLogger live summary synchronized
  LaunchedEffect(
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
    AppDiagnosticsLogger.activeWatchUrl = activeWatchPageUrl.ifBlank { currentUrl }
    AppDiagnosticsLogger.currentWebViewUrl = currentUrl
    AppDiagnosticsLogger.activePlayerMode =
      if (playNativeVideoUrl != null) "Native ExoPlayer (يعمل بنجاح ✅)"
      else "مشغل السيرفر المدمج (WebView)"
    AppDiagnosticsLogger.extractedStreamUrl = detectedVideoUrl ?: ""
    AppDiagnosticsLogger.streamReferer = activeEmbedRefererUrl.ifBlank { currentUrl }
    AppDiagnosticsLogger.detectedQualitiesSummary =
      if (detectedVideoQualities.isEmpty()) "تلقائي فقط (لم يتم رصد جودات فرعية بعد)"
      else detectedVideoQualities.joinToString(" , ") { it.label }
    AppDiagnosticsLogger.selectedQualityId = currentVideoQuality
  }

  val cleanAndroidChromeUa = remember(context) {
    try {
      WebSettings.getDefaultUserAgent(context)
        .replace("; wv", "")
        .replace("Version/4.0 ", "")
    } catch (_: Exception) {
      "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }
  }
  val desktopChromeUa =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

  fun mergeDetectedQualities(newOptions: List<VideoQualityOption>) {
    if (newOptions.isEmpty()) return
    // Combine and preserve existing higher/lower qualities from Master HLS so single-track reports never wipe them out!
    val combinedVariants = (newOptions + detectedVideoQualities)
      .filter { it.id != "auto" && !it.isAuto && it.height > 0 }
      .distinctBy { it.height }
      .sortedByDescending { it.height }

    val bestHeight = combinedVariants.firstOrNull()?.height ?: 0
    val autoLabel = if (bestHeight > 0) "تلقائي Auto (${bestHeight}p)" else "تلقائي (Auto)"

    val merged = mutableListOf<VideoQualityOption>()
    merged.add(VideoQualityOption(id = "auto", label = autoLabel, height = 0, isAuto = true))
    merged.addAll(combinedVariants)
    detectedVideoQualities = merged
    AppDiagnosticsLogger.detectedQualitiesSummary = merged.joinToString { it.label }
  }

  /**
   * Hard-destroys the extractor WebView and kills its ~150MB Chromium renderer process
   * immediately once NativeTvExoPlayer takes over or the user exits the watch view.
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
      try {
        wv.stopLoading()
        wv.removeJavascriptInterface("AndroidTvBridge")
        wv.loadUrl("about:blank")
        wv.onPause()
        wv.pauseTimers()
        wv.clearHistory()
        wv.clearCache(true)
        wv.removeAllViews()
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.destroy()
      } catch (_: Throwable) {}
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
    currentVideoQuality = qualityId
    AppDiagnosticsLogger.selectedQualityId = qualityId
    AppDiagnosticsLogger.log("QUALITY", "اختيار الجودة: $qualityLabel (id=$qualityId) | url=$directVariantUrl")

    val currentActiveStream = playNativeVideoUrl ?: ""
    val isCurrentMasterHls = currentActiveStream.contains("master.m3u8", ignoreCase = true) ||
        currentActiveStream.contains(".urlset", ignoreCase = true)

    // Switch URL only if it's a direct MP4 variant OR if we are not currently on a multi-variant master.m3u8
    if (directVariantUrl.startsWith("http") && playNativeVideoUrl != null && qualityId != "auto") {
      if ((directVariantUrl.contains(".mp4", ignoreCase = true) || !isCurrentMasterHls) &&
          directVariantUrl != playNativeVideoUrl
      ) {
        playNativeVideoUrl = directVariantUrl
      }
    }

    val js = """
      (function() {
        var qId = '$qualityId';
        window.__asserTargetQuality = qId;
        function applyInWindow(win) {
          var matched = false;
          try {
            if (typeof win.jwplayer === 'function' && win.jwplayer().setCurrentQuality) {
              var levels = win.jwplayer().getQualityLevels() || [];
              if (qId === 'auto') {
                win.jwplayer().setCurrentQuality(0);
                matched = true;
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
      wv.loadUrl(cleanTargetUrl, mapOf("Referer" to refererOrigin))
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
    autoLoadBestEmbed: Boolean = false,
    force: Boolean = false
  ) {
    val normalizedKey = "${portal.id}|${targetUrl.trim().trimEnd('/').lowercase()}|s$selectedSeasonNum"
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
    currentVideoQuality = "auto"
    userManuallyDismissedNativePlayer = false
    failedNativeStreamUrls.clear()

    activeEpisodeScrapeRequest = EpisodeScrapeRequest(
      requestKey = normalizedKey,
      targetUrl = targetUrl,
      portal = portal,
      titleHint = titleHint.ifBlank { lastSearchQuery.ifBlank { pageTitle } },
      selectedSeasonNum = selectedSeasonNum,
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
        selectedSeasonNumber = req.selectedSeasonNum
      )
      lastResolvedScrapeKeyRef.set(req.requestKey)
      seriesStructure = scraped
      isScrapingSeries = false

      AppDiagnosticsLogger.recordBreadcrumb(
        context,
        "SCRAPE_DONE",
        "servers=${scraped.watchServers.size}, direct=${scraped.directStreamUrl.isNotBlank()}"
      )

      if (scraped.detectedQualities.isNotEmpty()) {
        mergeDetectedQualities(scraped.detectedQualities)
      }

      val resolvedPortalWatchUrl = scraped.watchServers.firstOrNull { !it.isDirectEmbed }?.embedUrl ?: req.targetUrl
      if (!StreamingPortal.isEmbedIframeUrl(resolvedPortalWatchUrl)) {
        activeWatchPageUrl = resolvedPortalWatchUrl
        val canonicalTitle = pageTitle.ifBlank { req.titleHint.ifBlank { req.portal.displayNameArabic } }
        repository.addHistory(canonicalTitle, resolvedPortalWatchUrl, req.portal)
      }

      // If the scraper resolved a direct .m3u8 / .mp4 stream URL, kill any WebView and launch NativeTvExoPlayer!
      if (scraped.directStreamUrl.isNotBlank()) {
        val streamReferer = scraped.directStreamReferer.ifBlank {
          scraped.bestCleanPlayerUrl.ifBlank { resolvedPortalWatchUrl }
        }
        activeEmbedRefererUrl = streamReferer
        detectedVideoUrl = scraped.directStreamUrl
        detectedVideoHeaders = mapOf(
          "Referer" to streamReferer,
          "User-Agent" to desktopChromeUa
        )
        if (preferNativePlayer) {
          hardDestroyWebView("Direct stream resolved by scraper")
          playNativeVideoUrl = scraped.directStreamUrl
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
    selectedSeasonNum: Int = 1
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
    currentVideoQuality = "auto"
    playNativeVideoUrl = null
    if (title != null) pageTitle = title
    if (portal != null) {
      activePortal = portal
      repository.setActivePortal(portal)
    }
    if (!isGeneralBrowser && !isEmbedTarget && cleanUrl.startsWith("http")) {
      repository.addHistory(
        title = (title ?: pageTitle).ifBlank { targetPortal.displayNameArabic },
        url = cleanUrl,
        portal = targetPortal
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
      refreshSeriesSeasonsAndEpisodes(
        targetUrl = cleanUrl,
        portal = targetPortal,
        titleHint = title ?: lastSearchQuery,
        selectedSeasonNum = selectedSeasonNum,
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

  fun extractEpisodeNumberFromText(raw: String): Int? {
    if (raw.isBlank()) return null
    val decoded = decodeUrlSafely(raw)
    val tvPathMatch = Regex("/(?:tv|series)/\\d+/(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
      .find(decoded)
    if (tvPathMatch != null) {
      return tvPathMatch.groupValues[2].toIntOrNull()
    }
    val arabicMatch = Regex("(?:الحلق[ةه]|حلق[ةه])[\\s\\-_]*(\\d{1,4})")
      .find(decoded)
    if (arabicMatch != null) {
      return arabicMatch.groupValues[1].toIntOrNull()
    }
    val englishMatch = Regex("(?:^|[\\s\\-_/])(?:episode|ep|halka)[\\s\\-_]*(\\d{1,4})(?:[\\s\\-_/]|$)", RegexOption.IGNORE_CASE)
      .find(decoded)
    if (englishMatch != null) {
      return englishMatch.groupValues[1].toIntOrNull()
    }
    return null
  }

  fun resolveNextEpisode(): ScrapedEpisodeItem? {
    if (isGeneralBrowserSession) return null
    val currentWatchUrl = activeWatchPageUrl.ifBlank { currentUrl }
    val normalizedCurrent = decodeUrlSafely(currentWatchUrl).trimEnd('/').lowercase()
    val currentSeason = seriesStructure.selectedSeasonNumber.coerceAtLeast(1)
    val currentEpNum = extractEpisodeNumberFromText(currentWatchUrl)
      ?: extractEpisodeNumberFromText(pageTitle)
      ?: if (seriesStructure.isSeries && seriesStructure.currentEpisodeNumber > 0) {
        seriesStructure.currentEpisodeNumber
      } else {
        null
      }

    // 1. Direct nextEpisode detected by scraper
    seriesStructure.nextEpisode?.let { directNext ->
      val normalizedNext = decodeUrlSafely(directNext.url).trimEnd('/').lowercase()
      if (directNext.url.isNotBlank() && normalizedNext != normalizedCurrent) {
        return directNext
      }
    }

    // 2. Next episode in scraped episode list for current season
    val sortedEpisodes = seriesStructure.episodes.sortedBy { it.episodeNumber }
    if (sortedEpisodes.isNotEmpty()) {
      val currentIndex = sortedEpisodes.indexOfFirst { ep ->
        val normEp = decodeUrlSafely(ep.url).trimEnd('/').lowercase()
        normEp == normalizedCurrent ||
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
      val tvPathRegex = Regex("(/(?:tv|series)/\\d+/\\d+/)(\\d+)", RegexOption.IGNORE_CASE)
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

  val nextEpisodeItem = remember(
    seriesStructure,
    activeWatchPageUrl,
    currentUrl,
    pageTitle,
    isGeneralBrowserSession
  ) {
    resolveNextEpisode()
  }

  fun playNextEpisodeAutomatically(nextEp: ScrapedEpisodeItem) {
    val showName = seriesStructure.seriesTitle.ifBlank { lastSearchQuery }
    val nextTitle = if (showName.isNotBlank() && !nextEp.title.contains(showName, ignoreCase = true)) {
      "$showName - ${nextEp.title}"
    } else {
      nextEp.title
    }
    AppDiagnosticsLogger.log(
      "AutoNext",
      "تشغيل الحلقة التالية تلقائياً: $nextTitle | URL=${nextEp.url} | Season=${nextEp.seasonNumber}"
    )
    Toast.makeText(
      context,
      "جاري تشغيل الحلقة التالية تلقائياً: ${nextEp.title}",
      Toast.LENGTH_SHORT
    ).show()
    navigateTo(
      url = nextEp.url,
      portal = activePortal,
      title = nextTitle,
      isGeneralBrowser = false,
      autoScrapeAndLoadEmbed = true,
      selectedSeasonNum = nextEp.seasonNumber
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
        if (showDiagnosticsDialog) {
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

  DisposableEffect(activity, playNativeVideoUrl) {
    val keyEventProvider = activity as? KeyEventProvider
    keyEventProvider?.setKeyEventCallback { event ->
      if (playNativeVideoUrl != null) {
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
    if (showDiagnosticsDialog) {
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
                try {
                  wv.stopLoading()
                  wv.removeJavascriptInterface("AndroidTvBridge")
                  wv.loadUrl("about:blank")
                  wv.onPause()
                  wv.pauseTimers()
                  wv.clearHistory()
                  wv.clearCache(true)
                  wv.removeAllViews()
                  (wv.parent as? ViewGroup)?.removeView(wv)
                  wv.destroy()
                } catch (_: Throwable) {}
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

              val webView = WebView(ctx).apply {
              layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
              )

              setBackgroundColor(android.graphics.Color.BLACK)
              setLayerType(View.LAYER_TYPE_NONE, null)

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

              CookieManager.getInstance().setAcceptCookie(true)
              CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

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
                }
              )
              webChromeClientRef = chromeClient
              webChromeClient = chromeClient

              webViewClient = TvWebViewClient(
                isAdBlockEnabled = { repository.isAdBlockEnabled() },
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
                  val now = System.currentTimeMillis()
                  if (now - lastRendererCrashTimeMs < 10_000L) {
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
                    webViewRecreateKey++
                  } else {
                    AppDiagnosticsLogger.log(
                      "WebViewOOM",
                      "تم إيقاف إعادة التحميل التلقائي مؤقتاً لحماية الرام بعد تكرار تعطل صفحة $currentUrl",
                      isError = true
                    )
                    Toast.makeText(
                      context,
                      "الصفحة ثقيلة جداً على الرام - جرّب سيرفراً آخر أو المشغل الأساسي",
                      Toast.LENGTH_LONG
                    ).show()
                  }
                },
                onAdBlockedCallback = { blockedHostOrUrl ->
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
              .heightIn(min = 30.dp)
              .testTag("btn_embedded_video_fullscreen")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = if (isFullscreenVideo) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = "تكبير الفيديو",
                tint = CinemaAccent,
                modifier = Modifier.size(15.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (isFullscreenVideo) "تصغير الشاشة" else "تكبير الفيديو",
                color = Color.White,
                fontSize = 10.sp,
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
              .heightIn(min = 30.dp)
              .testTag("btn_embedded_diagnostics")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = "اللوج",
                tint = CinemaSecondary,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "اللوج",
                color = CinemaSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          if (nextEpisodeItem != null) {
            Surface(
              onClick = { playNextEpisodeAutomatically(nextEpisodeItem) },
              color = CinemaPrimary.copy(alpha = 0.88f),
              shape = RoundedCornerShape(50),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
              modifier = Modifier
                .heightIn(min = 30.dp)
                .testTag("btn_embedded_next_episode")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.SkipNext,
                  contentDescription = "الحلقة التالية",
                  tint = Color.White,
                  modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = "التالية",
                  color = Color.White,
                  fontSize = 10.sp,
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
            .testTag("btn_open_watch_side_panel")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.VideoLibrary,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = if (isFullscreenVideo) "إظهار الحلقات والسيرفرات" else "السيرفرات والحلقات",
              color = Color.White,
              fontSize = 10.sp,
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

        NativeTvExoPlayer(
          videoUrl = playNativeVideoUrl ?: "",
          title = pageTitle.ifBlank { lastSearchQuery },
          refererUrl = activeEmbedRefererUrl.ifBlank { currentUrl.ifBlank { activeWatchPageUrl } },
          capturedHeaders = detectedVideoHeaders,
          userAgent = desktopChromeUa,
          selectedQuality = currentVideoQuality,
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
            val show = seriesStructure.seriesTitle.ifBlank { "" }
            if (show.isNotBlank() && !ep.title.contains(show, ignoreCase = true)) {
              "$show • ${ep.title}"
            } else {
              ep.title
            }
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
                    .heightIn(min = 32.dp)
                    .testTag("btn_back_to_asser_tv_search")
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                      contentDescription = "العودة للبحث",
                      tint = Color.White,
                      modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "رجوع",
                      color = Color.White,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.ExtraBold,
                      maxLines = 1,
                      softWrap = false
                    )
                  }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 2. Full Readable Movie/Series Title & Quality Subtitle (Center)
                Column(
                  modifier = Modifier.weight(1f),
                  horizontalAlignment = Alignment.Start
                ) {
                  Text(
                    text = pageTitle.ifBlank { lastSearchQuery.ifBlank { "مشغل Asser TV المباشر" } },
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  val qBadge = if (currentVideoQuality == "auto") "تلقائي" else "${currentVideoQuality}p"
                  Text(
                    text = "${activePortal.displayNameArabic} • الجودة: $qBadge",
                    color = CinemaAccent,
                    fontSize = 10.sp,
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
                    contentDescription = "إظهار/إخفاء لوحة المصادر والحلقات",
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
    },
    bottomBar = {
      if (isBarVisible && !isFullscreenVideo && (isHomeActive || isGeneralBrowserSession)) {
        TvNavBar(
          activePortal = activePortal,
          isHomeActive = isHomeActive,
          selectedMainTab = selectedMainTab,
          controlMode = controlMode,
          currentDisplayMode = displayMode,
          onGoToBrowserHome = {
            selectedMainTab = AppMainTab.BROWSER
            isHomeActive = true
          },
          onGoToAsserTvHome = {
            selectedMainTab = AppMainTab.ASSER_TV
            isHomeActive = true
          },
          onSelectPortal = { portal ->
            navigateTo(portal.homeUrl, portal, portal.displayNameArabic, isGeneralBrowser = false)
          },
          onOpenBookmarks = { showFavoritesDialog = true },
          onOpenHistory = { showHistoryDialog = true },
          onToggleControlMode = {
            val newMode = if (controlMode == ControlMode.VIRTUAL_MOUSE) ControlMode.DPAD_NAV else ControlMode.VIRTUAL_MOUSE
            controlMode = newMode
            repository.setControlMode(newMode)
            val msg = if (newMode == ControlMode.VIRTUAL_MOUSE) "تم تفعيل وضع الماوس (تحريك بالأسهم + نقر بـ OK)" else "تم تفعيل وضع الأسهم للتصفح والتمرير"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
          },
          onOpenModeDialog = { showDisplayModeDialog = true },
          modifier = if (displayMode == AppDisplayMode.MOBILE) Modifier.navigationBarsPadding() else Modifier
        )
      }
    }
  ) { innerPadding ->
    if (isHomeActive) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .then(if (displayMode == AppDisplayMode.MOBILE) Modifier.statusBarsPadding() else Modifier)
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
          onOpenDiagnostics = { showDiagnosticsDialog = true }
        )

        if (selectedMainTab == AppMainTab.BROWSER) {
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
        } else {
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
            modifier = Modifier.weight(1f),
            searchService = searchService
          )
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
              detectedVideoUrl = resolution.primaryStreamUrl
              detectedVideoHeaders = mapOf(
                "Referer" to resolution.refererUrl.ifBlank { targetEmbed },
                "User-Agent" to desktopChromeUa
              )
              if (resolution.detectedQualities.isNotEmpty()) {
                mergeDetectedQualities(resolution.detectedQualities)
              }
              playNativeVideoUrl = resolution.primaryStreamUrl
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

      if (showSidePanelLayout) {
        if (isPortrait) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding)
          ) {
            Box(modifier = Modifier.weight(0.36f).fillMaxWidth()) {
              movablePlayerBox()
            }
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
                    activeEmbedRefererUrl = resolution.refererUrl.ifBlank { server.embedUrl }
                    detectedVideoUrl = resolution.primaryStreamUrl
                    detectedVideoHeaders = mapOf(
                      "Referer" to activeEmbedRefererUrl,
                      "User-Agent" to desktopChromeUa
                    )
                    if (resolution.detectedQualities.isNotEmpty()) {
                      mergeDetectedQualities(resolution.detectedQualities)
                    }
                    hardDestroyWebView("Selected server resolved direct stream")
                    playNativeVideoUrl = resolution.primaryStreamUrl
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
                navigateTo(item.url, portal, item.title, isGeneralBrowser = false, autoScrapeAndLoadEmbed = true)
              },
              onSwitchPortalDirect = { portal ->
                val url = if (lastSearchQuery.isNotBlank()) portal.buildSearchUrl(lastSearchQuery) else portal.homeUrl
                navigateTo(url, portal, portal.displayNameArabic, isGeneralBrowser = false, autoScrapeAndLoadEmbed = false)
              },
              onSelectSeason = { season ->
                navigateTo(
                  url = season.url,
                  portal = activePortal,
                  title = "${seriesStructure.seriesTitle} - ${season.title}",
                  isGeneralBrowser = false,
                  autoScrapeAndLoadEmbed = true,
                  selectedSeasonNum = season.seasonNumber
                )
              },
              onSelectEpisode = { episode ->
                navigateTo(
                  url = episode.url,
                  portal = activePortal,
                  title = "${seriesStructure.seriesTitle} - ${episode.title}",
                  isGeneralBrowser = false,
                  autoScrapeAndLoadEmbed = true,
                  selectedSeasonNum = episode.seasonNumber
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
              isCompactMobileSheet = true,
              modifier = Modifier
                .weight(0.64f)
                .fillMaxWidth()
                .then(if (displayMode == AppDisplayMode.MOBILE) Modifier.navigationBarsPadding() else Modifier)
            )
          }
        } else {
          Row(
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding)
          ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
              movablePlayerBox()
            }
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
                    activeEmbedRefererUrl = resolution.refererUrl.ifBlank { server.embedUrl }
                    detectedVideoUrl = resolution.primaryStreamUrl
                    detectedVideoHeaders = mapOf(
                      "Referer" to activeEmbedRefererUrl,
                      "User-Agent" to desktopChromeUa
                    )
                    if (resolution.detectedQualities.isNotEmpty()) {
                      mergeDetectedQualities(resolution.detectedQualities)
                    }
                    hardDestroyWebView("Selected server resolved direct stream")
                    playNativeVideoUrl = resolution.primaryStreamUrl
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
                navigateTo(item.url, portal, item.title, isGeneralBrowser = false, autoScrapeAndLoadEmbed = true)
              },
              onSwitchPortalDirect = { portal ->
                val url = if (lastSearchQuery.isNotBlank()) portal.buildSearchUrl(lastSearchQuery) else portal.homeUrl
                navigateTo(url, portal, portal.displayNameArabic, isGeneralBrowser = false, autoScrapeAndLoadEmbed = false)
              },
              onSelectSeason = { season ->
                navigateTo(
                  url = season.url,
                  portal = activePortal,
                  title = "${seriesStructure.seriesTitle} - ${season.title}",
                  isGeneralBrowser = false,
                  autoScrapeAndLoadEmbed = true,
                  selectedSeasonNum = season.seasonNumber
                )
              },
              onSelectEpisode = { episode ->
                navigateTo(
                  url = episode.url,
                  portal = activePortal,
                  title = "${seriesStructure.seriesTitle} - ${episode.title}",
                  isGeneralBrowser = false,
                  autoScrapeAndLoadEmbed = true,
                  selectedSeasonNum = episode.seasonNumber
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
              isCompactMobileSheet = false,
              modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
            )
          }
        }
      } else {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
          movablePlayerBox()
        }
      }
    }
  }

  // Dialogs
  if (showSubtitlesDialog) {
    OpenSubtitlesDialog(
      initialQuery = lastSearchQuery.ifBlank { pageTitle.ifBlank { "VisionQuest" } },
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
        navigateTo(entry.url, entry.portal, entry.title, isGeneralBrowser = false)
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
