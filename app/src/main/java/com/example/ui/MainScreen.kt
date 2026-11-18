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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.ClipboxWhite
import kotlinx.coroutines.launch
import org.json.JSONArray

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
  var activeEmbedRefererUrl by remember { mutableStateOf("") }
  var isCurrentUrlDirectEmbed by remember { mutableStateOf(false) }

  // Virtual Mouse Coordinates
  var cursorX by remember { mutableFloatStateOf(960f) }
  var cursorY by remember { mutableFloatStateOf(540f) }

  // Data from Preferences
  val bookmarks by repository.bookmarksFlow.collectAsState()
  val history by repository.historyFlow.collectAsState()

  // References
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var fullscreenContainerRef by remember { mutableStateOf<FrameLayout?>(null) }
  var webChromeClientRef by remember { mutableStateOf<TvWebChromeClient?>(null) }

  var lastBackPressTime by remember { mutableStateOf(0L) }
  var webViewRecreateKey by remember { mutableIntStateOf(0) }
  var detectedVideoUrl by remember { mutableStateOf<String?>(null) }
  var detectedVideoHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
  var playNativeVideoUrl by remember { mutableStateOf<String?>(null) }
  var preferNativePlayer by remember { mutableStateOf(true) }
  var userManuallyDismissedNativePlayer by remember { mutableStateOf(false) }
  var activeSubtitleItem by remember { mutableStateOf<SubtitleItem?>(null) }

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
      if (playNativeVideoUrl != null) "المشغل الأساسي للتطبيق (Native ExoPlayer)"
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
  // Unified Desktop Chrome 128 User-Agent used across Scraper, WebView Embeds, and NativeTvExoPlayer
  // so IP + UA bound .m3u8 tokens (VidTube / StreamWish / VidShare) never fail with HTTP 403!
  val desktopChromeUa =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

  fun mergeDetectedQualities(newOptions: List<VideoQualityOption>) {
    if (newOptions.isEmpty()) return
    val merged = mutableListOf<VideoQualityOption>()
    merged.add(VideoQualityOption(id = "auto", label = "تلقائي (Auto)", height = 0))
    val combined = (newOptions + detectedVideoQualities)
      .filter { it.id != "auto" && it.height > 0 }
      .distinctBy { it.height }
      .sortedByDescending { it.height }
    merged.addAll(combined)
    detectedVideoQualities = merged
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

    // If the quality option has its own direct MP4 file URL and Native Player is active:
    if (directVariantUrl.startsWith("http") && playNativeVideoUrl != null && qualityId != "auto") {
      if (directVariantUrl.contains(".mp4", ignoreCase = true) && directVariantUrl != playNativeVideoUrl) {
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
    val lower = url.lowercase()
    if ((lower.contains(".ts") || lower.contains(".m4s")) && detectedVideoUrl?.contains(".m3u8") == true) {
      return
    }
    if (url == detectedVideoUrl && playNativeVideoUrl != null) return

    AppDiagnosticsLogger.log("STREAM", "التقاط رابط فيديو مباشر ($sourceTag): $url")
    detectedVideoUrl = url
    if (headers.isNotEmpty()) {
      detectedVideoHeaders = headers
    }

    val effectiveReferer = headers["Referer"]
      ?: headers["referer"]
      ?: activeEmbedRefererUrl.ifBlank { currentUrl.ifBlank { activeWatchPageUrl } }

    if (lower.contains(".m3u8")) {
      scope.launch {
        val hlsQualities = searchService.parseHlsQualitiesFromManifest(url, effectiveReferer)
        if (hlsQualities.isNotEmpty()) {
          mergeDetectedQualities(hlsQualities)
        }
      }
    }

    if (!isGeneralBrowserSession && (preferNativePlayer || !userManuallyDismissedNativePlayer)) {
      preferNativePlayer = true
      webViewRef?.post {
        webViewRef?.evaluateJavascript(
          "(function() { document.querySelectorAll('video').forEach(function(v) { v.pause(); }); })()",
          null
        )
      }
      playNativeVideoUrl = url
    }
  }

  fun loadServerIntoWebView(
    targetWebView: WebView?,
    targetServerUrl: String,
    isDirectEmbed: Boolean,
    portalRefererPageUrl: String = activeWatchPageUrl.ifBlank { activePortal.homeUrl },
    resetNativePlayer: Boolean = true
  ) {
    val cleanTargetUrl = targetServerUrl
      .replace("topcinema.site", "topcinema.io")
      .replace("video.php?vid=", "play.php?vid=")
    currentUrl = cleanTargetUrl
    activeEmbedRefererUrl = cleanTargetUrl
    isCurrentUrlDirectEmbed = isDirectEmbed
    if (resetNativePlayer) {
      playNativeVideoUrl = null
      detectedVideoUrl = null
    }

    val wv = targetWebView ?: return
    wv.settings.userAgentString = desktopChromeUa

    val refererPage = portalRefererPageUrl
      .replace("topcinema.site", "topcinema.io")
      .replace("video.php?vid=", "play.php?vid=")
      .ifBlank { activePortal.homeUrl }
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

  fun refreshSeriesSeasonsAndEpisodes(
    targetUrl: String,
    portal: StreamingPortal,
    titleHint: String,
    selectedSeasonNum: Int = 1,
    autoLoadBestEmbed: Boolean = false
  ) {
    isScrapingSeries = true
    detectedVideoQualities = emptyList()
    currentVideoQuality = "auto"
    userManuallyDismissedNativePlayer = false

    scope.launch {
      val scraped = searchService.scrapeSeasonsAndEpisodes(
        pageUrl = targetUrl,
        portal = portal,
        titleHint = titleHint.ifBlank { lastSearchQuery.ifBlank { pageTitle } },
        selectedSeasonNumber = selectedSeasonNum
      )
      seriesStructure = scraped
      isScrapingSeries = false

      if (scraped.detectedQualities.isNotEmpty()) {
        mergeDetectedQualities(scraped.detectedQualities)
      }

      val resolvedPortalWatchUrl = scraped.watchServers.firstOrNull { !it.isDirectEmbed }?.embedUrl ?: targetUrl
      activeWatchPageUrl = resolvedPortalWatchUrl

      // If the scraper resolved a direct .m3u8 / .mp4 stream URL, launch NativeTvExoPlayer immediately by default!
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
          playNativeVideoUrl = scraped.directStreamUrl
        }
      }

      // Also load the best clean embed server in the background WebView (as fallback or to sniff dynamic streams)
      if (autoLoadBestEmbed && scraped.bestCleanPlayerUrl.isNotBlank()) {
        val bestServer = scraped.watchServers.firstOrNull { it.embedUrl == scraped.bestCleanPlayerUrl }
        val isDirect = bestServer?.isDirectEmbed ?: (scraped.bestCleanPlayerUrl != resolvedPortalWatchUrl)
        AppDiagnosticsLogger.activeServerName = bestServer?.name ?: "تلقائي"
        loadServerIntoWebView(
          targetWebView = webViewRef,
          targetServerUrl = scraped.bestCleanPlayerUrl,
          isDirectEmbed = isDirect,
          portalRefererPageUrl = resolvedPortalWatchUrl,
          resetNativePlayer = scraped.directStreamUrl.isBlank()
        )
      }
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
    var cleanUrl = url
      .replace("topcinema.site", "topcinema.io")
      .replace("video.php?vid=", "play.php?vid=")
    if (!isGeneralBrowser &&
        targetPortal == StreamingPortal.TOP_CINEMA &&
        !cleanUrl.contains("/watch") &&
        !cleanUrl.contains("/search") &&
        !cleanUrl.contains("/series/")
    ) {
      cleanUrl = "${cleanUrl.trimEnd('/')}/watch/"
    }

    currentUrl = cleanUrl
    activeWatchPageUrl = cleanUrl
    activeEmbedRefererUrl = cleanUrl
    isCurrentUrlDirectEmbed = false
    isGeneralBrowserSession = isGeneralBrowser
    preferNativePlayer = !isGeneralBrowser
    userManuallyDismissedNativePlayer = false
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
    isHomeActive = false
    val useAndroidUa = isGeneralBrowser && (cleanUrl.contains("google.") || displayMode == AppDisplayMode.MOBILE)
    webViewRef?.settings?.userAgentString = if (useAndroidUa) cleanAndroidChromeUa else desktopChromeUa
    webViewRef?.loadUrl(cleanUrl, mapOf("Referer" to targetPortal.homeUrl))

    if (!isGeneralBrowser) {
      refreshSeriesSeasonsAndEpisodes(
        targetUrl = cleanUrl,
        portal = targetPortal,
        titleHint = title ?: lastSearchQuery,
        selectedSeasonNum = selectedSeasonNum,
        autoLoadBestEmbed = autoScrapeAndLoadEmbed
      )
    }
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
          // Clean Clipbox Theater Player Top Bar for Asser TV
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
                .padding(horizontal = 10.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              // Back to Search Results button
              Surface(
                onClick = { isHomeActive = true },
                color = CinemaSurface,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, CinemaBorder),
                modifier = Modifier
                  .heightIn(min = 36.dp)
                  .testTag("btn_back_to_asser_tv_search")
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "العودة للبحث",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "رجوع للبحث",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                  )
                }
              }

              Spacer(modifier = Modifier.width(8.dp))

              // Active Movie/Series Title
              Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
              ) {
                Text(
                  text = pageTitle.ifBlank { lastSearchQuery.ifBlank { "مشغل Asser TV المباشر" } },
                  color = Color.White,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.ExtraBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                val qBadge = if (currentVideoQuality == "auto") "تلقائي" else "${currentVideoQuality}p"
                Text(
                  text = "المصدر: ${activePortal.displayNameArabic} • الجودة: $qBadge",
                  color = CinemaAccent,
                  fontSize = 10.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }

              Spacer(modifier = Modifier.width(6.dp))

              // Quick Diagnostics, Update, Subtitles, Side Panel & Fullscreen Controls
              Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                IconButton(
                  onClick = { showDiagnosticsDialog = true },
                  modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(CinemaSurface)
                    .testTag("btn_top_diagnostics_log")
                ) {
                  Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "سجل الأعطال واللوج",
                    tint = CinemaSecondary,
                    modifier = Modifier.size(17.dp)
                  )
                }

                IconButton(
                  onClick = { showUpdateDialog = true },
                  modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(CinemaSurface)
                    .testTag("btn_top_github_update")
                ) {
                  Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "التحديث الداخلي من جيت هب",
                    tint = CinemaAccent,
                    modifier = Modifier.size(17.dp)
                  )
                }

                IconButton(
                  onClick = { showSubtitlesDialog = true },
                  modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(CinemaSurface)
                ) {
                  Icon(
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = "الترجمة",
                    tint = CinemaAccent,
                    modifier = Modifier.size(17.dp)
                  )
                }

                IconButton(
                  onClick = { isWatchSidePanelOpen = !isWatchSidePanelOpen },
                  modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isWatchSidePanelOpen) ClipboxWhite else CinemaSurface)
                ) {
                  Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = "إظهار/إخفاء لوحة المصادر والحلقات",
                    tint = if (isWatchSidePanelOpen) Color.Black else Color.White,
                    modifier = Modifier.size(17.dp)
                  )
                }

                IconButton(
                  onClick = {
                    toggleVideoFullscreen(true)
                  },
                  modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(CinemaSurface)
                    .testTag("btn_top_video_fullscreen")
                ) {
                  Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "ملء شاشة الفيديو",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
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

      @Composable
      fun WebViewPlayerBox(boxModifier: Modifier) {
        Box(
          modifier = boxModifier
            .background(Color.Black)
        ) {
          key(webViewRecreateKey) {
            AndroidView(
              factory = { ctx ->
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

                  settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    allowContentAccess = true
                    allowFileAccess = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cacheMode = WebSettings.LOAD_DEFAULT

                    offscreenPreRaster = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                      safeBrowsingEnabled = false
                    }

                    val useAndroidUa = isGeneralBrowserSession && (currentUrl.contains("google.") || displayMode == AppDisplayMode.MOBILE)
                    userAgentString = if (useAndroidUa) cleanAndroidChromeUa else desktopChromeUa
                  }

                  addJavascriptInterface(
                    TvPlayerBridge(
                      onVideoEnded = {},
                      onVideoTimeUpdate = { _, _ -> },
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
                      onQualitiesDetected = { jsonStr ->
                        post {
                          try {
                            val arr = JSONArray(jsonStr)
                            val list = mutableListOf<VideoQualityOption>()
                            for (i in 0 until arr.length()) {
                              val obj = arr.optJSONObject(i) ?: continue
                              val h = obj.optInt("height", 0)
                              if (h in 140..4320) {
                                list.add(
                                  VideoQualityOption(
                                    id = h.toString(),
                                    label = obj.optString("label").ifBlank { "${h}p" },
                                    height = h
                                  )
                                )
                              }
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
                      if (isGeneralBrowserSession || !isCurrentUrlDirectEmbed) {
                        currentUrl = url
                      }
                      val detected = StreamingPortal.fromUrl(url)
                      activePortal = detected
                      repository.setActivePortal(detected)
                      val title = pageTitle.ifBlank { url }
                      repository.addHistory(title, url, detected)
                    },
                    onRenderProcessGoneCallback = {
                      webViewRecreateKey++
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

                  val rawUrl = if (currentUrl.isNotBlank() && currentUrl.startsWith("http")) currentUrl else activePortal.homeUrl
                  if (!isGeneralBrowserSession && isCurrentUrlDirectEmbed) {
                    loadServerIntoWebView(
                      targetWebView = this,
                      targetServerUrl = rawUrl,
                      isDirectEmbed = true,
                      portalRefererPageUrl = activeWatchPageUrl.ifBlank { activePortal.homeUrl }
                    )
                  } else {
                    val targetUrl = rawUrl
                      .replace("topcinema.site", "topcinema.io")
                      .replace("video.php?vid=", "play.php?vid=")
                    loadUrl(targetUrl, mapOf("Referer" to activePortal.homeUrl))
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
          // (Placed at TopStart so it NEVER covers the bottom seekbar or controls of the embedded player!)
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
                  .heightIn(min = 32.dp)
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
                    modifier = Modifier.size(16.dp)
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
                  .heightIn(min = 32.dp)
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

          if (showPrimaryNativePlayer) {
            NativeTvExoPlayer(
              videoUrl = playNativeVideoUrl ?: "",
              title = pageTitle.ifBlank { lastSearchQuery },
              refererUrl = activeEmbedRefererUrl.ifBlank { currentUrl.ifBlank { activeWatchPageUrl } },
              capturedHeaders = detectedVideoHeaders,
              userAgent = desktopChromeUa,
              selectedQuality = currentVideoQuality,
              activeSubtitle = activeSubtitleItem,
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
                }
              },
              onFallbackToWebPlayer = {
                playNativeVideoUrl = null
                webViewRef?.evaluateJavascript(
                  "(function() { document.querySelectorAll('video').forEach(function(v) { v.play(); }); })()",
                  null
                )
                Toast.makeText(
                  context,
                  "تم التبديل لمشغل السيرفر المدمج (يمكنك نسخ اللوج 🐞 لمعرفة السبب)",
                  Toast.LENGTH_SHORT
                ).show()
              },
              modifier = Modifier.fillMaxSize()
            )
          }
        }
      }

      val isNativeActiveNow = playNativeVideoUrl != null
      val hasDetectedDirectStream = !detectedVideoUrl.isNullOrBlank()

      fun switchOrRetryNativePlayer() {
        userManuallyDismissedNativePlayer = false
        preferNativePlayer = true
        val existingUrl = detectedVideoUrl ?: seriesStructure.directStreamUrl
        if (existingUrl.isNotBlank()) {
          webViewRef?.evaluateJavascript(
            "(function() { document.querySelectorAll('video').forEach(function(v) { v.pause(); }); })()",
            null
          )
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
            WebViewPlayerBox(boxModifier = Modifier.weight(0.36f).fillMaxWidth())
            WatchSidePanel(
              activePortal = activePortal,
              currentUrl = currentUrl,
              activeWatchPageUrl = activeWatchPageUrl,
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
                AppDiagnosticsLogger.activeServerName = server.name
                loadServerIntoWebView(
                  targetWebView = webViewRef,
                  targetServerUrl = server.embedUrl,
                  isDirectEmbed = server.isDirectEmbed,
                  portalRefererPageUrl = activeWatchPageUrl.ifBlank { activePortal.homeUrl }
                )
                scope.launch {
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
                    playNativeVideoUrl = resolution.primaryStreamUrl
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
                  autoLoadBestEmbed = true
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
            WebViewPlayerBox(boxModifier = Modifier.weight(1f).fillMaxHeight())
            WatchSidePanel(
              activePortal = activePortal,
              currentUrl = currentUrl,
              activeWatchPageUrl = activeWatchPageUrl,
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
                AppDiagnosticsLogger.activeServerName = server.name
                loadServerIntoWebView(
                  targetWebView = webViewRef,
                  targetServerUrl = server.embedUrl,
                  isDirectEmbed = server.isDirectEmbed,
                  portalRefererPageUrl = activeWatchPageUrl.ifBlank { activePortal.homeUrl }
                )
                scope.launch {
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
                    playNativeVideoUrl = resolution.primaryStreamUrl
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
                  autoLoadBestEmbed = true
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
        WebViewPlayerBox(
          boxModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        )
      }
    }
  }

  // Dialogs
  if (showSubtitlesDialog) {
    OpenSubtitlesDialog(
      initialQuery = lastSearchQuery.ifBlank { pageTitle.ifBlank { "Silo" } },
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
                  var ccBtn = doc.querySelector('.jw-icon-cc, .vjs-subs-caps-button, button[aria-label*="Captions"], button[aria-label*="Subtitles"]');
                  if (ccBtn) {
                    ccBtn.click();
                    applied = true;
                  }
                }
              } catch(e) {}
            }

            var rootCc = document.querySelector('.jw-icon-cc, .vjs-subs-caps-button, button[aria-label*="Captions"], button[aria-label*="Subtitles"], .art-control-subtitle');
            if (rootCc) {
              rootCc.click();
              applied = true;
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
