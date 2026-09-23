package com.example.ui

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.MediaCatalogRepository
import com.example.data.TvPreferencesRepository
import com.example.model.ControlMode
import com.example.model.MediaItem
import com.example.model.StreamingPortal
import com.example.tv.TvKeyHandler
import com.example.tv.TvWebChromeClient
import com.example.tv.TvWebViewClient
import com.example.ui.home.TvHomeScreen
import com.example.ui.player.TvMediaPlayerScreen
import com.example.ui.search.TvSearchScreen
import com.example.ui.theme.CinemaDarkBackground

@Composable
fun MainScreen(
  repository: TvPreferencesRepository,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val activity = context as? Activity

  // Active Screen & Player State
  var currentDestination by remember { mutableStateOf(TvNavDestination.HOME) }
  var activePlayerMedia by remember { mutableStateOf<MediaItem?>(null) }
  var activeResumePosition by remember { mutableLongStateOf(0L) }

  // Web State
  var activePortal by remember { mutableStateOf(repository.getActivePortal()) }
  var controlMode by remember { mutableStateOf(repository.getControlMode()) }
  var isBarVisible by remember { mutableStateOf(true) }
  var isFullscreenVideo by remember { mutableStateOf(false) }
  var loadingProgress by remember { mutableIntStateOf(0) }
  var pageTitle by remember { mutableStateOf("") }
  var currentUrl by remember { mutableStateOf(activePortal.homeUrl) }

  // Virtual Mouse Coordinates
  var cursorX by remember { mutableFloatStateOf(960f) }
  var cursorY by remember { mutableFloatStateOf(540f) }

  // Dialog States
  var showFavoritesDialog by remember { mutableStateOf(false) }
  var showHistoryDialog by remember { mutableStateOf(false) }
  var showGuideDialog by remember { mutableStateOf(false) }

  // Data
  val bookmarks by repository.bookmarksFlow.collectAsState()
  val history by repository.historyFlow.collectAsState()
  val watchProgress by repository.watchProgressFlow.collectAsState()

  // References
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var fullscreenContainerRef by remember { mutableStateOf<FrameLayout?>(null) }
  var webChromeClientRef by remember { mutableStateOf<TvWebChromeClient?>(null) }

  var lastBackPressTime by remember { mutableStateOf(0L) }

  // Key Handler for Android TV / BeoutQ Remote
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
        if (activePlayerMedia != null) {
          activePlayerMedia = null
          activeResumePosition = 0L
          true
        } else if (webChromeClientRef?.isCustomViewShowing() == true) {
          webChromeClientRef?.onHideCustomView()
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
        } else if (currentDestination != TvNavDestination.HOME) {
          currentDestination = TvNavDestination.HOME
          true
        } else if (currentDestination == TvNavDestination.DIRECT_WEB && webViewRef?.canGoBack() == true) {
          webViewRef?.goBack()
          true
        } else {
          val now = System.currentTimeMillis()
          if (now - lastBackPressTime < 2500) {
            activity?.finish()
          } else {
            lastBackPressTime = now
            Toast.makeText(context, "اضغط Back مرة أخرى للخروج من التطبيق", Toast.LENGTH_SHORT).show()
          }
          true
        }
      }
    )
  }

  // Intercept Key Events from Activity
  DisposableEffect(Unit) {
    activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    val callback = object : ActivityKeyEventCallback {
      override fun onDispatchKeyEvent(event: KeyEvent): Boolean {
        // If in Web mode with virtual mouse, allow custom remote key interception
        if (currentDestination == TvNavDestination.DIRECT_WEB && controlMode == ControlMode.VIRTUAL_MOUSE) {
          if (tvKeyHandler.handleKeyEvent(event)) return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
          if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            return tvKeyHandler.handleKeyEvent(event)
          }
          if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            isBarVisible = !isBarVisible
            return true
          }
        }
        return false
      }
    }
    (activity as? KeyEventProvider)?.setKeyEventCallback(callback)

    onDispose {
      (activity as? KeyEventProvider)?.setKeyEventCallback(null)
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(CinemaDarkBackground)
      .onGloballyPositioned { coordinates ->
        val width = coordinates.size.width.toFloat()
        val height = coordinates.size.height.toFloat()
        tvKeyHandler.updateScreenBounds(width, height)
      }
  ) {
    // Check if player is currently active
    val currentMedia = activePlayerMedia
    if (currentMedia != null) {
      TvMediaPlayerScreen(
        mediaItem = currentMedia,
        initialPositionMs = activeResumePosition,
        onProgressUpdate = { pos, dur ->
          repository.saveWatchProgress(currentMedia, pos, dur)
        },
        onClosePlayer = {
          activePlayerMedia = null
          activeResumePosition = 0L
        },
        onOpenInWebPortal = { portalUrl ->
          activePlayerMedia = null
          activeResumePosition = 0L
          currentDestination = TvNavDestination.DIRECT_WEB
          webViewRef?.loadUrl(portalUrl)
        },
        modifier = Modifier.fillMaxSize()
      )
    } else {
      // Main App Layout
      Column(modifier = Modifier.fillMaxSize()) {
        // Top Navigation Bar
        if (isBarVisible) {
          TvNavBar(
            currentDestination = currentDestination,
            onNavigate = { dest ->
              currentDestination = dest
              if (dest == TvNavDestination.TOP_CINEMA) {
                activePortal = StreamingPortal.TOP_CINEMA
              } else if (dest == TvNavDestination.WATCH_SERIES) {
                activePortal = StreamingPortal.WATCH_SERIES
              }
            },
            onOpenBookmarks = { showFavoritesDialog = true },
            onOpenHistory = { showHistoryDialog = true },
            onOpenRemoteGuide = { showGuideDialog = true }
          )
        }

        // Content Area based on Destination
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        ) {
          when (currentDestination) {
            TvNavDestination.HOME,
            TvNavDestination.TOP_CINEMA,
            TvNavDestination.WATCH_SERIES -> {
              TvHomeScreen(
                onPlayMedia = { item, resumePosition ->
                  activePlayerMedia = item
                  activeResumePosition = resumePosition
                },
                onOpenPortalUrl = { url ->
                  currentDestination = TvNavDestination.DIRECT_WEB
                  webViewRef?.loadUrl(url)
                },
                isBookmarked = { url -> repository.isBookmarked(url) },
                onToggleBookmark = { item ->
                  if (repository.isBookmarked(item.portalUrl)) {
                    val b = repository.getBookmarks().find { it.url == item.portalUrl }
                    if (b != null) repository.removeBookmark(b.id)
                    Toast.makeText(context, "تمت الإزالة من المفضلة", Toast.LENGTH_SHORT).show()
                  } else {
                    repository.addBookmark(item.arabicTitle, item.portalUrl, item.portal)
                    Toast.makeText(context, "تمت الإضافة إلى المفضلة ★", Toast.LENGTH_SHORT).show()
                  }
                },
                recentlyWatched = watchProgress,
                onRemoveRecent = { id -> repository.removeWatchProgress(id) },
                onClearAllRecents = {
                  repository.clearWatchHistory()
                  Toast.makeText(context, "تم مسح سجل المشاهدة", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxSize()
              )
            }

            TvNavDestination.SEARCH -> {
              TvSearchScreen(
                onSelectMedia = { item ->
                  activePlayerMedia = item
                  activeResumePosition = 0L
                },
                modifier = Modifier.fillMaxSize()
              )
            }

            TvNavDestination.DIRECT_WEB -> {
              // Direct Hardware-Accelerated Web Portal
              Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                  factory = { ctx ->
                    WebView(ctx).apply {
                      layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                      )

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
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 AndroidTV QS285"
                      }

                      isFocusable = true
                      isFocusableInTouchMode = true
                      requestFocus()

                      webViewClient = TvWebViewClient(
                        isAdBlockEnabled = { repository.isAdBlockEnabled() },
                        onPageFinishedCallback = { url ->
                          currentUrl = url
                          val detected = StreamingPortal.fromUrl(url)
                          activePortal = detected
                          repository.setActivePortal(detected)
                          repository.addHistory(title ?: url, url, detected)
                        }
                      )

                      webViewRef = this
                      loadUrl(activePortal.homeUrl)
                    }
                  },
                  modifier = Modifier.fillMaxSize()
                )

                // Fullscreen Video Container for HTML5 Web players
                AndroidView(
                  factory = { ctx ->
                    FrameLayout(ctx).apply {
                      layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                      )
                      visibility = View.GONE
                      fullscreenContainerRef = this

                      webViewRef?.let { wv ->
                        val chromeClient = TvWebChromeClient(
                          fullscreenContainer = this,
                          onFullscreenChanged = { isFs ->
                            isFullscreenVideo = isFs
                            isBarVisible = !isFs
                          },
                          onProgressUpdate = { p -> loadingProgress = p },
                          onTitleUpdate = { t -> pageTitle = t }
                        )
                        webChromeClientRef = chromeClient
                        wv.webChromeClient = chromeClient
                      }
                    }
                  },
                  modifier = Modifier.fillMaxSize()
                )

                // Virtual Mouse Overlay (when in mouse mode)
                VirtualMouseOverlay(
                  controlMode = controlMode,
                  cursorX = cursorX,
                  cursorY = cursorY
                )

                // Floating Web Controller
                if (!isFullscreenVideo) {
                  TvTopBar(
                    activePortal = activePortal,
                    controlMode = controlMode,
                    isBarVisible = isBarVisible,
                    loadingProgress = loadingProgress,
                    pageTitle = pageTitle,
                    isBookmarked = repository.isBookmarked(currentUrl),
                    onPortalSelected = { portal ->
                      activePortal = portal
                      repository.setActivePortal(portal)
                      webViewRef?.loadUrl(portal.homeUrl)
                    },
                    onHomeClick = { webViewRef?.loadUrl(activePortal.homeUrl) },
                    onBackClick = {
                      if (webViewRef?.canGoBack() == true) webViewRef?.goBack()
                      else Toast.makeText(context, "أنت في أول صفحة", Toast.LENGTH_SHORT).show()
                    },
                    onForwardClick = { if (webViewRef?.canGoForward() == true) webViewRef?.goForward() },
                    onRefreshClick = { webViewRef?.reload() },
                    onToggleControlMode = {
                      val newMode = if (controlMode == ControlMode.VIRTUAL_MOUSE) ControlMode.DPAD_NAV else ControlMode.VIRTUAL_MOUSE
                      controlMode = newMode
                      repository.setControlMode(newMode)
                      val msg = if (newMode == ControlMode.VIRTUAL_MOUSE) "وضع الماوس مفعّل: استخدم الأسهم للتحريك و OK للنقر" else "وضع الأسهم مفعّل للتنقل والتمرير"
                      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    onBookmarkClick = {
                      val title = webViewRef?.title ?: pageTitle
                      repository.addBookmark(title, currentUrl, activePortal)
                      Toast.makeText(context, "تمت الإضافة إلى المفضلة ★", Toast.LENGTH_SHORT).show()
                    },
                    onOpenBookmarks = { showFavoritesDialog = true },
                    onOpenHistory = { showHistoryDialog = true },
                    onZoomIn = { webViewRef?.zoomIn() },
                    onZoomOut = { webViewRef?.zoomOut() },
                    onToggleFullscreen = { isBarVisible = !isBarVisible },
                    onOpenGuide = { showGuideDialog = true },
                    onToggleBarVisibility = { isBarVisible = !isBarVisible }
                  )
                }
              }
            }
          }
        }
      }
    }

    // Global Dialogs
    if (showFavoritesDialog) {
      FavoritesDialog(
        bookmarks = bookmarks,
        onSelectBookmark = { item ->
          currentDestination = TvNavDestination.DIRECT_WEB
          activePortal = item.portal
          webViewRef?.loadUrl(item.url)
        },
        onRemoveBookmark = { id -> repository.removeBookmark(id) },
        onDismiss = { showFavoritesDialog = false }
      )
    }

    if (showHistoryDialog) {
      HistoryDialog(
        history = history,
        recentlyWatched = watchProgress,
        onResumeMedia = { item ->
          val fullItem = MediaCatalogRepository.findById(item.mediaId) ?: MediaItem(
            id = item.mediaId,
            title = item.title,
            arabicTitle = item.arabicTitle,
            portal = item.portal,
            category = item.category,
            genres = listOf("أكشن"),
            year = 2024,
            rating = item.rating,
            qualityBadge = item.qualityBadge,
            duration = item.arabicTitle,
            description = item.arabicTitle,
            posterUrl = item.posterUrl,
            backdropUrl = item.backdropUrl,
            portalUrl = item.portalUrl,
            defaultStreamUrl = item.defaultStreamUrl
          )
          activePlayerMedia = fullItem
          activeResumePosition = item.positionMs
        },
        onRemoveRecent = { id -> repository.removeWatchProgress(id) },
        onClearRecents = {
          repository.clearWatchHistory()
          Toast.makeText(context, "تم مسح سجل المشاهدة", Toast.LENGTH_SHORT).show()
        },
        onSelectEntry = { entry ->
          currentDestination = TvNavDestination.DIRECT_WEB
          activePortal = entry.portal
          webViewRef?.loadUrl(entry.url)
        },
        onClearHistory = { repository.clearHistory() },
        onDismiss = { showHistoryDialog = false }
      )
    }

    if (showGuideDialog) {
      RemoteGuideDialog(onDismiss = { showGuideDialog = false })
    }
  }
}

interface ActivityKeyEventCallback {
  fun onDispatchKeyEvent(event: KeyEvent): Boolean
}

interface KeyEventProvider {
  fun setKeyEventCallback(callback: ActivityKeyEventCallback?)
}
