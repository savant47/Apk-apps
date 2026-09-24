package com.example.tv

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import java.io.ByteArrayInputStream

class TvWebChromeClient(
  private val fullscreenContainer: FrameLayout,
  private val onFullscreenChanged: (Boolean) -> Unit,
  private val onProgressUpdate: (Int) -> Unit,
  private val onTitleUpdate: (String) -> Unit
) : WebChromeClient() {

  private var customView: View? = null
  private var customViewCallback: CustomViewCallback? = null

  override fun onProgressChanged(view: WebView?, newProgress: Int) {
    super.onProgressChanged(view, newProgress)
    onProgressUpdate(newProgress)
  }

  override fun onReceivedTitle(view: WebView?, title: String?) {
    super.onReceivedTitle(view, title)
    if (!title.isNullOrBlank()) {
      onTitleUpdate(title)
    }
  }

  override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
    if (customView != null) {
      onHideCustomView()
      return
    }

    customView = view
    customViewCallback = callback

    if (view != null) {
      view.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      fullscreenContainer.addView(view)
      fullscreenContainer.visibility = View.VISIBLE
      fullscreenContainer.bringToFront()
      fullscreenContainer.requestFocus()
      onFullscreenChanged(true)
    }
  }

  override fun onHideCustomView() {
    val view = customView ?: return
    fullscreenContainer.removeView(view)
    fullscreenContainer.visibility = View.GONE
    customView = null
    customViewCallback?.onCustomViewHidden()
    customViewCallback = null
    onFullscreenChanged(false)
  }

  fun isCustomViewShowing(): Boolean = customView != null

  override fun onCreateWindow(
    view: WebView?,
    isDialog: Boolean,
    isUserGesture: Boolean,
    resultMsg: Message?
  ): Boolean {
    // Strictly prevent opening separate blank popup windows that hijack streaming
    return false
  }

  override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
    result?.confirm()
    return true
  }

  override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
    result?.cancel()
    return true
  }
}

class TvWebViewClient(
  private val isAdBlockEnabled: () -> Boolean,
  private val onPageFinishedCallback: (String) -> Unit,
  private val onRenderProcessGoneCallback: (() -> Unit)? = null,
  private val onAdBlockedCallback: ((String) -> Unit)? = null
) : WebViewClient() {

  // Known malicious ad / popunder / redirect domains
  private val adHostKeywords = listOf(
    "luugy", "popads", "propellerads", "adsterra", "exoclick",
    "bet365", "1xbet", "trafficjunky", "ad-delivery",
    "adtrue", "juicyads", "histats", "trackvoluum", "monetag",
    "hilltopads", "richpush", "clickadu", "realsrv", "ero-advertising",
    "yllix", "directrev", "poppush", "pushnoti", "popunder", "popcash",
    "adkeeper", "adcash", "adcolony", "cpalead", "trafficstars"
  )

  override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
    val url = request?.url?.toString() ?: return false
    val isMainFrame = request?.isForMainFrame ?: true
    return handleUrlLoading(view, url, isMainFrame)
  }

  @Deprecated("Deprecated in Java")
  override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    return handleUrlLoading(view, url, true)
  }

  private fun handleUrlLoading(view: WebView?, url: String, isForMainFrame: Boolean): Boolean {
    // 1. Suppress market/intent app-hijacking attempts
    if (url.startsWith("market://") || url.startsWith("intent://") || url.startsWith("intent:") || url.contains("play.google.com/store")) {
      return true
    }

    // 2. Auto-fix outdated topcinema.site domain to topcinema.io
    if (url.contains("topcinema.site")) {
      val fixed = url.replace("topcinema.site", "topcinema.io")
      view?.loadUrl(fixed)
      return true
    }

    // 3. Check known malicious ad keywords in URL
    val lower = url.lowercase()
    if (isAdBlockEnabled()) {
      for (ad in adHostKeywords) {
        if (lower.contains(ad)) {
          onAdBlockedCallback?.invoke(url)
          return true // Strictly block ad navigation
        }
      }
    }

    // 4. Strict MainFrame Protection: When the main page URL is attempting to navigate,
    // ensure it only goes to authorized streaming portals or video embed hosts.
    // If it's an unauthorized third-party redirect (like luugy.com, scam sites, etc.), BLOCK IT!
    if (isForMainFrame && isAdBlockEnabled()) {
      val host = try {
        Uri.parse(url).host?.lowercase() ?: ""
      } catch (_: Exception) { "" }

      if (host.isNotBlank() && !isAllowedMainFrameHost(host)) {
        onAdBlockedCallback?.invoke(host)
        return true // Cancel navigation and keep user on the streaming page
      }
    }

    // Load navigation directly in the same WebView
    return false
  }

  private fun isAllowedMainFrameHost(host: String): Boolean {
    val clean = host.lowercase()
    // Explicit blacklist check even if spoofed in subdomains
    if (clean.contains("luugy") || clean.contains("adsterra") || clean.contains("propeller") || clean.contains("popads") || clean.contains("bet365") || clean.contains("1xbet")) {
      return false
    }

    return isStreamingOrCdnHost(clean) ||
      clean.contains("player") ||
      clean.contains("embed")
  }

  private fun isStreamingOrCdnHost(host: String): Boolean {
    val clean = host.lowercase()
    return clean.contains("watchseries") ||
      clean.contains("topcinema") ||
      clean.contains("vidfast") ||
      clean.contains("videasy") ||
      clean.contains("vidrock") ||
      clean.contains("vidup") ||
      clean.contains("mapple") ||
      clean.contains("vidlink") ||
      clean.contains("vidsrc") ||
      clean.contains("2embed") ||
      clean.contains("autoembed") ||
      clean.contains("superembed") ||
      clean.contains("smashystream") ||
      clean.contains("apiplayer") ||
      clean.contains("vidcore") ||
      clean.contains("yapgrid") ||
      clean.contains("111movies") ||
      clean.contains("nontongo") ||
      clean.contains("peachify") ||
      clean.contains("xpass") ||
      clean.contains("vidzee") ||
      clean.contains("vidtube") ||
      clean.contains("streamwish") ||
      clean.contains("filelions") ||
      clean.contains("filemoon") ||
      clean.contains("dood") ||
      clean.contains("updown") ||
      clean.contains("byse") ||
      clean.contains("firestream") ||
      clean.contains("streamtape") ||
      clean.contains("mp4upload") ||
      clean.contains("rabbitstream") ||
      clean.contains("mixdrop") ||
      clean.contains("boredflix") ||
      clean.contains("flixbaba") ||
      clean.contains("moviepire") ||
      clean.contains("cinegram") ||
      clean.contains("themoviedb.org") ||
      clean.contains("tmdb.org") ||
      clean.contains("opensubtitles") ||
      clean.contains("cloudflare") ||
      clean.contains("google.com") ||
      clean.contains("recaptcha") ||
      clean.contains("hcaptcha") ||
      clean.contains("flagsapi") ||
      clean.contains("wsrv.nl") ||
      clean.contains("gstatic") ||
      clean.contains("googletagmanager")
  }

  override fun onReceivedSslError(
    view: WebView?,
    handler: SslErrorHandler?,
    error: SslError?
  ) {
    // Proceed for video CDN servers on older Android versions/TV boxes
    handler?.proceed()
  }

  override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
    view?.let { webView ->
      try {
        val parent = webView.parent as? ViewGroup
        parent?.removeView(webView)
        webView.destroy()
      } catch (_: Exception) {}
    }
    onRenderProcessGoneCallback?.invoke()
    return true
  }

  override fun shouldInterceptRequest(
    view: WebView?,
    request: WebResourceRequest?
  ): WebResourceResponse? {
    if (isAdBlockEnabled()) {
      val uri = request?.url ?: return null
      val host = uri.host?.lowercase() ?: ""

      // Never block any sub-resource from valid streaming servers, video CDNs, or APIs!
      if (isStreamingOrCdnHost(host)) {
        return super.shouldInterceptRequest(view, request)
      }

      // Only drop actual malicious ad host requests
      for (ad in adHostKeywords) {
        if (host.contains(ad)) {
          return WebResourceResponse(
            "application/javascript",
            "UTF-8",
            ByteArrayInputStream("/*ad blocked*/".toByteArray())
          )
        }
      }
    }
    return super.shouldInterceptRequest(view, request)
  }

  override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
    super.onPageStarted(view, url, favicon)
    val preInjectJs = """
      (function() {
        try {
          var safeDummyWindow = {
            closed: false,
            focus: function() {},
            close: function() {},
            location: { href: '' },
            document: { write: function() {}, open: function() {}, close: function() {} }
          };
          window.open = function(u) {
            console.log('Safe window.open handled: ' + u);
            return safeDummyWindow;
          };
          window.alert = function() { return true; };
          window.confirm = function() { return false; };
        } catch(e) {}
      })();
    """.trimIndent()
    try {
      view?.evaluateJavascript(preInjectJs, null)
    } catch (_: Exception) {}
  }

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    if (url.isNullOrBlank() || url == "about:blank" || url.startsWith("data:")) {
      return
    }

    val enhancePlayerJs = """
      (function() {
        function setupPlayers() {
          // 1. Remove standalone ad overlays not part of video player
          try {
            var ads = document.querySelectorAll('a[href*="luugy"], a[href*="adsterra"], a[href*="propeller"], div[id*="popunder"]');
            ads.forEach(function(el) {
              if (!el.closest('#player') && !el.closest('#movie-player') && !el.closest('.movie-player-wrap') && !el.closest('.player--iframe')) {
                el.remove();
              }
            });
          } catch(e) {}

          // 2. Ensure all server lists, player containers, and buttons are responsive
          try {
            var clickableItems = document.querySelectorAll(
              '.servers .server-item, .server--item, .watch--servers--list li, ' +
              '#movie-player, #player, .movie-player-wrap, .btn-play, ' +
              '.player--iframe, iframe'
            );
            clickableItems.forEach(function(el) {
              el.style.pointerEvents = 'auto';
            });

            // 3. Make server buttons on TopCinema explicitly trigger server switch on click
            var tcServers = document.querySelectorAll('.watch--servers--list li.server--item');
            tcServers.forEach(function(item) {
              item.style.cursor = 'pointer';
              if (!item.getAttribute('data-tc-ready')) {
                item.setAttribute('data-tc-ready', 'true');
                item.addEventListener('click', function() {
                  console.log('Selected TopCinema server: ' + item.innerText);
                });
              }
            });

            // 4. Ensure .btn-play on WatchSeries starts playback smoothly
            var playBtn = document.querySelector('.btn-play');
            if (playBtn && !playBtn.getAttribute('data-app-ready')) {
              playBtn.setAttribute('data-app-ready', 'true');
              playBtn.style.cursor = 'pointer';
              playBtn.style.pointerEvents = 'auto';
            }
          } catch(e) {}
        }

        setupPlayers();
        setTimeout(setupPlayers, 1000);
        setTimeout(setupPlayers, 3000);
      })();
    """.trimIndent()
    try {
      view?.evaluateJavascript(enhancePlayerJs, null)
    } catch (_: Exception) {}

    onPageFinishedCallback(url)
  }
}
