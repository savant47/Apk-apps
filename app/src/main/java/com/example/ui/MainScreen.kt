package com.example.ui

import android.app.Activity
import android.content.res.Configuration
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.AppDisplayMode
import com.example.data.TvPreferencesRepository
import com.example.model.ControlMode
import com.example.model.StreamingPortal
import com.example.tv.TvKeyHandler
import com.example.tv.TvWebChromeClient
import com.example.tv.TvWebViewClient
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary

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
  var isHomeActive by remember { mutableStateOf(true) }

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
  DisposableEffect(activity) {
    val keyEventProvider = activity as? KeyEventProvider
    keyEventProvider?.setKeyEventCallback { event ->
      tvKeyHandler.handleKeyEvent(event)
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

  fun navigateTo(url: String, portal: StreamingPortal? = null, title: String? = null) {
    val cleanUrl = url.replace("topcinema.site", "topcinema.io")
    currentUrl = cleanUrl
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
          isBarVisible = isBarVisible,
          loadingProgress = loadingProgress,
          pageTitle = pageTitle,
          isBookmarked = repository.isBookmarked(currentUrl),
          onGoToHome = { isHomeActive = true },
          onOpenSubtitles = { showSubtitlesDialog = true },
          onSearchDirect = { portal, query ->
            val searchUrl = portal.buildSearchUrl(query)
            navigateTo(searchUrl, portal, "بحث: $query")
            Toast.makeText(context, "جاري البحث عن $query في ${portal.displayNameArabic}...", Toast.LENGTH_SHORT).show()
          },
          onPortalSelected = { portal ->
            navigateTo(portal.homeUrl, portal, portal.displayNameArabic)
          },
          onHomeClick = {
            navigateTo(activePortal.homeUrl, activePortal, activePortal.displayNameArabic)
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
            val title = pageTitle.ifBlank { activePortal.displayNameArabic }
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
          onToggleBarVisibility = { isBarVisible = !isBarVisible },
          modifier = if (displayMode == AppDisplayMode.MOBILE) Modifier.statusBarsPadding() else Modifier
        )
      }
    },
    bottomBar = {
      if (!isHomeActive && isBarVisible && !isFullscreenVideo) {
        TvNavBar(
          activePortal = activePortal,
          controlMode = controlMode,
          currentDisplayMode = displayMode,
          onGoToHome = { isHomeActive = true },
          onSelectPortal = { portal ->
            navigateTo(portal.homeUrl, portal, portal.displayNameArabic)
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
      HomeDashboardScreen(
        repository = repository,
        onLaunchUrl = { url, portal, title ->
          navigateTo(url, portal, title)
        },
        onOpenBookmarks = { showFavoritesDialog = true },
        onOpenHistory = { showHistoryDialog = true },
        onOpenDisplayMode = { showDisplayModeDialog = true },
        onOpenAdBlockSettings = { showAdBlockDialog = true },
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .then(if (displayMode == AppDisplayMode.MOBILE) Modifier.statusBarsPadding() else Modifier)
      )
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
                setLayerType(View.LAYER_TYPE_NONE, null)

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

                  userAgentString = if (displayMode == AppDisplayMode.MOBILE) {
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                  } else {
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                  }
                }

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

        // Quick Player Jump Button: appears on TopCinema and WatchSeries DudeBar media pages
        val isTopCinemaMedia = activePortal == StreamingPortal.TOP_CINEMA &&
          (currentUrl.contains("مسلسل") || currentUrl.contains("حلقة") || currentUrl.contains("فيلم") || currentUrl.contains("mobland", ignoreCase = true) || currentUrl.contains("%d9%85%d8%b3"))
        val isWatchSeriesMedia = activePortal == StreamingPortal.WATCH_SERIES &&
          (currentUrl.contains("/tv/") || currentUrl.contains("/movie/") || currentUrl.contains("/watch/"))
        val isOnWatchPage = currentUrl.contains("/watch")

        if (!isHomeActive && (isTopCinemaMedia || isWatchSeriesMedia) && !isFullscreenVideo) {
          FloatingActionButton(
            onClick = {
              if (isWatchSeriesMedia) {
                // Smoothly focus the WatchSeries DudeBar player and seasons/episodes
                webViewRef?.evaluateJavascript(
                  """
                  (function() {
                    var el = document.querySelector('#player, #iframe-embed, .player-embed, .seasons, .servers-list, iframe[src*="embed"], iframe');
                    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    // Attempt to click CC button on the player controls if available
                    var cc = document.querySelector('.jw-icon-cc, .vjs-subs-caps-button, button[aria-label*="Captions"], button[aria-label*="Subtitles"], .art-control-subtitle');
                    if (cc) cc.click();
                  })();
                  """.trimIndent(),
                  null
                )
                Toast.makeText(context, "تم التركيز على مشغل WatchSeries DudeBar ومواسم المسلسل 📺", Toast.LENGTH_SHORT).show()
              } else if (isOnWatchPage) {
                // Focus and center the video player iframe on TopCinema
                webViewRef?.evaluateJavascript(
                  """
                  (function() {
                    var el = document.querySelector('.player--iframe, iframe[src*="embed"], #player, .watch--servers--list');
                    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                  })();
                  """.trimIndent(),
                  null
                )
                Toast.makeText(context, "تم التركيز على مشغل الفيديو والسيرفرات", Toast.LENGTH_SHORT).show()
              } else {
                // Jump to the direct streaming /watch/ page
                val cleanBase = currentUrl.trimEnd('/')
                val watchUrl = "$cleanBase/watch/"
                navigateTo(watchUrl, StreamingPortal.TOP_CINEMA, pageTitle)
                Toast.makeText(context, "جاري فتح مشغل الفيديو وسيرفرات المشاهدة...", Toast.LENGTH_SHORT).show()
              }
            },
            containerColor = if (isWatchSeriesMedia) CinemaSecondary else CinemaPrimary,
            contentColor = Color.White,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(end = 24.dp, bottom = if (isBarVisible) 80.dp else 24.dp)
              .testTag("btn_jump_to_watch_player")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = if (isWatchSeriesMedia) "المشغل والترجمة [CC] 🎬" else if (isOnWatchPage) "تركيز على المشغل 🎬" else "مشاهدة الآن / المشغل 🎬",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
          }
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

  if (showDisplayModeDialog) {
    DisplayModeSelectionDialog(
      currentMode = displayMode,
      onModeSelected = { mode ->
        repository.setDisplayMode(mode)
        showDisplayModeDialog = false
        // Update user agent to match selected mode
        webViewRef?.settings?.userAgentString = if (mode == AppDisplayMode.MOBILE) {
          "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        } else {
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        }
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
}
