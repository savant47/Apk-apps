package com.example.ui

import android.app.Activity
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.AppDisplayMode
import com.example.data.TvPreferencesRepository
import com.example.model.AppMainTab
import com.example.model.ControlMode
import com.example.model.StreamingPortal
import com.example.tv.TvKeyHandler
import com.example.tv.TvWebChromeClient
import com.example.tv.TvWebViewClient
import com.example.tv.TvAutoPlayBridge
import com.example.tv.TvPlayerBridge
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaAccent

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

  // Display Mode & Dialogs
  val displayMode by repository.displayModeFlow.collectAsState()
  var showDisplayModeDialog by remember { mutableStateOf(false) }
  var showAdBlockDialog by remember { mutableStateOf(false) }
  var showFavoritesDialog by remember { mutableStateOf(false) }
  var showHistoryDialog by remember { mutableStateOf(false) }
  var showGuideDialog by remember { mutableStateOf(false) }
  var showSubtitlesDialog by remember { mutableStateOf(false) }
  var showQualityDialog by remember { mutableStateOf(false) }
  var showUpdateDialog by remember { mutableStateOf(false) }
  var currentVideoQuality by remember { mutableStateOf("auto") }
  var isHomeActive by remember { mutableStateOf(true) }
  var selectedMainTab by remember { mutableStateOf(AppMainTab.BROWSER) }
  var isGeneralBrowserSession by remember { mutableStateOf(true) }

  // Portal & Control Mode
  var activePortal by remember { mutableStateOf(repository.getActivePortal()) }
  var controlMode by remember { mutableStateOf(repository.getControlMode()) }

  // Web State
  var isBarVisible by remember { mutableStateOf(true) }
  var isFullscreenVideo by remember { mutableStateOf(false) }
  var loadingProgress by remember { mutableIntStateOf(0) }
  var pageTitle by remember { mutableStateOf("") }
  var currentUrl by remember {
    mutableStateOf(activePortal.homeUrl.replace("topcinema.site", "topcinema.io"))
  }

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
  var playNativeVideoUrl by remember { mutableStateOf<String?>(null) }

  // Key Handler for Android TV Remote / D-pad / Keyboard
  val tvKeyHandler = remember {
    TvKeyHandler(
      getWebView = { webViewRef },
      getControlMode = { controlMode },
      onToggleControlMode = {
        val newMode = if (controlMode == ControlMode.VIRTUAL_MOUSE) ControlMode.DPAD_NAV else ControlMode.VIRTUAL_MOUSE
        controlMode = newMode
        repository.setControlMode(newMode)
        val msg = if (newMode == ControlMode.VIRTUAL_MOUSE) "تم تفعيل وضع الماوس (تحريك بالأسهم + نقر بـ OK)" else "تم تفعيل وضع الأسهم للتصفح والتمرير"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
      },
      onToggleTopBar = {
        isBarVisible = !isBarVisible
      },
      onToggleFullscreen = {
        isBarVisible = !isBarVisible
      },
      onCursorMoved = { x, y ->
        cursorX = x
        cursorY = y
      },
      onBackHandled = {
        if (showDisplayModeDialog) {
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
        } else if (webChromeClientRef?.isCustomViewShowing() == true) {
          webChromeClientRef?.onHideCustomView()
          true
        } else if (!isHomeActive) {
          if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
          } else {
            isHomeActive = true
          }
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

  // Hook Activity KeyEvent to TV Key Handler
  DisposableEffect(activity, playNativeVideoUrl) {
    val keyEventProvider = activity as? KeyEventProvider
    keyEventProvider?.setKeyEventCallback { event ->
      if (playNativeVideoUrl != null) {
        // Let the Native ExoPlayer view handle remote keys natively!
        false
      } else {
        tvKeyHandler.handleKeyEvent(event)
      }
    }
    onDispose {
      keyEventProvider?.setKeyEventCallback(null)
    }
  }

  // Compose BackHandler for Mobile & System gesture back
  BackHandler {
    if (showDisplayModeDialog) {
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
    } else if (webChromeClientRef?.isCustomViewShowing() == true) {
      webChromeClientRef?.onHideCustomView()
    } else if (!isHomeActive) {
      if (webViewRef?.canGoBack() == true) {
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

  fun navigateTo(
    url: String,
    portal: StreamingPortal? = null,
    title: String? = null,
    isGeneralBrowser: Boolean = false
  ) {
    val cleanUrl = url.replace("topcinema.site", "topcinema.io")
    currentUrl = cleanUrl
    isGeneralBrowserSession = isGeneralBrowser
    detectedVideoUrl = null
    playNativeVideoUrl = null
    if (title != null) pageTitle = title
    if (portal != null) {
      activePortal = portal
      repository.setActivePortal(portal)
    }
    isHomeActive = false
    webViewRef?.loadUrl(cleanUrl)
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CinemaDarkBackground,
    topBar = {
      if (!isHomeActive && isBarVisible && !isFullscreenVideo) {
        TvTopBar(
          activePortal = activePortal,
          currentUrl = currentUrl,
          isGeneralBrowserMode = isGeneralBrowserSession,
          isBarVisible = isBarVisible,
          loadingProgress = loadingProgress,
          pageTitle = pageTitle,
          isBookmarked = repository.isBookmarked(currentUrl),
          onGoToHome = { isHomeActive = true },
          onOpenSubtitles = { showSubtitlesDialog = true },
          onSearchDirect = { portal, query ->
            val searchUrl = portal.buildSearchUrl(query)
            navigateTo(searchUrl, portal, "بحث: $query", isGeneralBrowser = false)
            Toast.makeText(context, "جاري البحث عن $query في ${portal.displayNameArabic}...", Toast.LENGTH_SHORT).show()
          },
          onOmniboxNavigate = { url, title ->
            navigateTo(url, null, title, isGeneralBrowser = true)
          },
          onPortalSelected = { portal ->
            navigateTo(portal.homeUrl, portal, portal.displayNameArabic, isGeneralBrowser = false)
          },
          onHomeClick = {
            if (isGeneralBrowserSession) {
              navigateTo("https://www.google.com", null, "Google", isGeneralBrowser = true)
            } else {
              navigateTo(activePortal.homeUrl, activePortal, activePortal.displayNameArabic, isGeneralBrowser = false)
            }
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
            val title = pageTitle.ifBlank { if (isGeneralBrowserSession) currentUrl else activePortal.displayNameArabic }
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
            isBarVisible = !isBarVisible
            if (!isBarVisible) {
              Toast.makeText(context, "تم إخفاء الشريط العلوي - اضغط السهم أعلى الشاشة أو زر القائمة لإظهاره", Toast.LENGTH_SHORT).show()
            }
          },
          onOpenGuide = { showGuideDialog = true },
          onOpenAdBlockSettings = { showAdBlockDialog = true },
          onOpenQuality = { showQualityDialog = true },
          onOpenUpdate = { showUpdateDialog = true },
          onToggleBarVisibility = { isBarVisible = !isBarVisible },
          modifier = if (displayMode == AppDisplayMode.MOBILE) Modifier.statusBarsPadding() else Modifier
        )
      }
    },
    bottomBar = {
      if (isBarVisible && !isFullscreenVideo) {
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
          onOpenAdBlock = { showAdBlockDialog = true }
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
              navigateTo(url, portal, title, isGeneralBrowser = false)
            },
            onOpenBookmarks = { showFavoritesDialog = true },
            onOpenHistory = { showHistoryDialog = true },
            onOpenDisplayMode = { showDisplayModeDialog = true },
            onOpenAdBlockSettings = { showAdBlockDialog = true },
            onOpenUpdate = { showUpdateDialog = true },
            modifier = Modifier.weight(1f)
          )
        }
      }
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .background(Color.Black)
      ) {
        // Unified AndroidView hosting both WebView and HTML5 Fullscreen container
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
                
                // Set Layer Type dynamically: Use Software Layer in emulators to completely avoid initializing EGL/Mesa drivers, 
                // preventing 'Failed to open rendernode' errors in container logs. Physical TV devices (ARM) keep LAYER_TYPE_NONE
                // for full hardware acceleration.
                val arch = (System.getProperty("os.arch") ?: "").lowercase()
                val hardware = (Build.HARDWARE ?: "").lowercase()
                val fingerprint = (Build.FINGERPRINT ?: "").lowercase()
                val model = (Build.MODEL ?: "").lowercase()
                val product = (Build.PRODUCT ?: "").lowercase()
                val brand = (Build.BRAND ?: "").lowercase()
                val manufacturer = (Build.MANUFACTURER ?: "").lowercase()

                val isEmulator = arch.contains("x86") || arch.contains("i686") || arch.contains("amd64") ||
                    (Build.SUPPORTED_ABIS ?: emptyArray()).any { it.lowercase().contains("x86") } ||
                    hardware.contains("goldfish") ||
                    hardware.contains("ranchu") ||
                    hardware.contains("cuttlefish") ||
                    hardware.contains("cutf") ||
                    hardware.contains("dummy") ||
                    fingerprint.startsWith("generic") ||
                    fingerprint.contains("cuttlefish") ||
                    model.contains("google_sdk") ||
                    model.contains("emulator") ||
                    model.contains("cuttlefish") ||
                    product.contains("sdk") ||
                    product.contains("cuttlefish") ||
                    brand.contains("generic") ||
                    manufacturer.contains("unknown") ||
                    manufacturer.contains("android") ||
                    (brand.contains("google") && !hardware.contains("pixel") && !hardware.contains("nexus") && !hardware.contains("shiba") && !hardware.contains("husky") && !hardware.contains("felix"))

                // Set Software layer in emulators to completely avoid initializing EGL/Mesa drivers, 
                // preventing 'Failed to open rendernode' errors in container logs. Physical TV devices (ARM) keep LAYER_TYPE_NONE
                // for full hardware acceleration.
                if (isEmulator) {
                  setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                } else {
                  setLayerType(View.LAYER_TYPE_NONE, null)
                }

                isClickable = true
                isLongClickable = true
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus()


                // Native touch handling: let WebView scroll freely without continuous Compose state updates
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

                  userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                }

                addJavascriptInterface(
                  TvPlayerBridge(
                    onVideoEnded = {},
                    onVideoTimeUpdate = { _, _ -> },
                    onCenterClickFiltered = { _, _ -> },
                    onVideoDetected = { url ->
                      if (url.startsWith("http") && url != detectedVideoUrl) {
                        detectedVideoUrl = url
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
                    isFullscreenVideo = isFs
                    isBarVisible = !isFs
                  },
                  onProgressUpdate = { p -> loadingProgress = p },
                  onTitleUpdate = { t -> pageTitle = t }
                )
                webChromeClientRef = chromeClient
                webChromeClient = chromeClient

                webViewClient = TvWebViewClient(
                  isAdBlockEnabled = { repository.isAdBlockEnabled() },
                  isGeneralBrowserMode = { isGeneralBrowserSession },
                  onPageFinishedCallback = { url ->
                    currentUrl = url
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
                    Toast.makeText(context, "🚫 تم منع إعلان منبثق ($blockedHostOrUrl)", Toast.LENGTH_SHORT).show()
                  },
                  onVideoDetectedCallback = { url ->
                    if (url.startsWith("http") && url != detectedVideoUrl) {
                      detectedVideoUrl = url
                    }
                  }
                )

                val rawUrl = if (currentUrl.isNotBlank() && currentUrl.startsWith("http")) currentUrl else activePortal.homeUrl
                val targetUrl = rawUrl.replace("topcinema.site", "topcinema.io")
                loadUrl(targetUrl)
              }

              webViewRef = webView
              rootLayout.addView(webView)
              rootLayout.addView(fsContainer)
              rootLayout
            },
            update = { _ ->
              // Intentionally empty: all URL loads are handled explicitly via navigateTo() to eliminate freeze/reload loops
            },
            modifier = Modifier.fillMaxSize()
          )
        }

        // Non-blocking Virtual Mouse Pointer (small 36dp Canvas, never blocks touch events)
        if (displayMode == AppDisplayMode.TV && controlMode == ControlMode.VIRTUAL_MOUSE) {
          VirtualMouseOverlay(
            controlMode = controlMode,
            cursorX = cursorX,
            cursorY = cursorY,
            modifier = Modifier.align(Alignment.TopStart)
          )
        }

        // Floating Data-Saver Player Banner (Arabic UI)
        if (detectedVideoUrl != null && playNativeVideoUrl == null) {
          Box(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 24.dp)
              .background(Color(0xEE111111), RoundedCornerShape(12.dp))
              .padding(12.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Column {
                Text(
                  text = "🎬 تم رصد رابط فيديو البث المباشر!",
                  color = Color.White,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "اضغط لتشغيله بالمشغل الأصلي الموفر للباقة (جودة 480p تلقائياً)",
                  color = CinemaAccent,
                  fontSize = 11.sp
                )
              }
              androidx.compose.material3.Button(
                onClick = {
                  // Pause the webview video player so we don't have dual audio streams running in background
                  webViewRef?.evaluateJavascript(
                    "(function() { document.querySelectorAll('video').forEach(function(v) { v.pause(); }); })()",
                    null
                  )
                  playNativeVideoUrl = detectedVideoUrl
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaAccent),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("play_native_video_button")
              ) {
                Text("شغل الآن بالمشغل الأصلي", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
              }
            }
          }
        }

        // Fullscreen Native ExoPlayer Overlay with strict 480p cap to save internet quota
        if (playNativeVideoUrl != null) {
          NativeTvExoPlayer(
            videoUrl = playNativeVideoUrl!!,
            title = pageTitle,
            refererUrl = currentUrl,
            onDismiss = {
              playNativeVideoUrl = null
            },
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }
  }

  // Dialogs
  if (showSubtitlesDialog) {
    OpenSubtitlesDialog(
      initialQuery = pageTitle.ifBlank { "Silo" },
      onDismiss = { showSubtitlesDialog = false },
      onApplySubtitle = { sub ->
        showSubtitlesDialog = false
        val jsInject = """
          (function() {
            var applied = false;
            // 1. Direct video in root document
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
              track.src = '${sub.downloadUrl}';
              track.default = true;
              v.appendChild(track);
              track.mode = 'showing';
              applied = true;
            }

            // 2. Check accessible iframes (DudeBar / UpCloud / VidCloud / TopCinema players)
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
                    iTrack.src = '${sub.downloadUrl}';
                    iTrack.default = true;
                    iv.appendChild(iTrack);
                    iTrack.mode = 'showing';
                    applied = true;
                  }
                  // Try triggering CC menu in iframe
                  var ccBtn = doc.querySelector('.jw-icon-cc, .vjs-subs-caps-button, button[aria-label*="Captions"], button[aria-label*="Subtitles"]');
                  if (ccBtn) {
                    ccBtn.click();
                    applied = true;
                  }
                }
              } catch(e) {}
            }

            // 3. Trigger CC button in root document if present
            var rootCc = document.querySelector('.jw-icon-cc, .vjs-subs-caps-button, button[aria-label*="Captions"], button[aria-label*="Subtitles"], .art-control-subtitle');
            if (rootCc) {
              rootCc.click();
              applied = true;
            }

            return applied ? 'APPLIED' : 'QUEUED';
          })();
        """.trimIndent()
        webViewRef?.evaluateJavascript(jsInject) { res ->
          val note = if (activePortal == StreamingPortal.WATCH_SERIES) {
            "تم تفعيل ترجمة ${sub.languageName}! (يمكنك أيضاً النقر على زر [CC] بالمشغل لاختيار العربية فوراً)"
          } else {
            "تم تفعيل ترجمة: ${sub.languageName}"
          }
          Toast.makeText(context, note, Toast.LENGTH_LONG).show()
        }
      }
    )
  }

  if (showFavoritesDialog) {
    FavoritesDialog(
      bookmarks = bookmarks,
      onSelectBookmark = { bookmark ->
        activePortal = bookmark.portal
        repository.setActivePortal(bookmark.portal)
        currentUrl = bookmark.url
        webViewRef?.loadUrl(bookmark.url)
        showFavoritesDialog = false
      },
      onRemoveBookmark = { id -> repository.removeBookmark(id) },
      onDismiss = { showFavoritesDialog = false }
    )
  }

  if (showHistoryDialog) {
    HistoryDialog(
      history = history,
      onSelectEntry = { entry ->
        activePortal = entry.portal
        repository.setActivePortal(entry.portal)
        currentUrl = entry.url
        webViewRef?.loadUrl(entry.url)
        showHistoryDialog = false
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
        currentVideoQuality = qualityId
        showQualityDialog = false
        val js = """
          (function() {
            var qId = '$qualityId';
            try {
              if (typeof jwplayer !== 'undefined' && jwplayer().setCurrentQuality) {
                var levels = jwplayer().getQualityLevels();
                for (var i = 0; i < levels.length; i++) {
                  if (qId === 'auto' || levels[i].label.indexOf(qId) !== -1 || (levels[i].height && levels[i].height.toString() === qId)) {
                    jwplayer().setCurrentQuality(i);
                    break;
                  }
                }
              }
            } catch(e) {}
            try {
              var items = document.querySelectorAll('.jw-settings-item, .vjs-menu-item, .art-setting-item');
              for (var j = 0; j < items.length; j++) {
                var text = items[j].innerText || items[j].textContent || '';
                if (qId === 'auto' && (text.indexOf('Auto') !== -1 || text.indexOf('تلقائي') !== -1)) {
                  items[j].click();
                  break;
                } else if (text.indexOf(qId) !== -1) {
                  items[j].click();
                  break;
                }
              }
            } catch(e) {}
          })();
        """.trimIndent()
        webViewRef?.evaluateJavascript(js, null)
        Toast.makeText(context, "تم تطبيق جودة الفيديو: $qualityLabel", Toast.LENGTH_SHORT).show()
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
        // Keep Desktop user agent across both TV and Phone so video servers always expose all bitrates (240p - 1080p)
        webViewRef?.settings?.userAgentString =
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        if (mode == AppDisplayMode.TV) {
          activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
          Toast.makeText(context, "تم التحويل إلى وضع شاشة أندرويد تي في (أفقي 16:9) 📺", Toast.LENGTH_SHORT).show()
        } else {
          activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
          Toast.makeText(context, "تم التحويل إلى وضع الهاتف المحمول (رأسي/لمس) 📱", Toast.LENGTH_SHORT).show()
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
}
