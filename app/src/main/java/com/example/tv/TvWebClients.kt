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
import android.widget.Toast
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
    // Strictly prevent opening popup windows, blank tabs, or unattached WebViews
    return false
  }

  override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
    // Suppress scam / malware alert popups ("Your phone is full", "Update required", etc.)
    result?.confirm()
    return true
  }

  override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
    // Suppress intrusive confirm popups
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

  // Comprehensive ad / popunder / redirect / tracking network blacklist
  private val adKeywords = listOf(
    "luugy", "popads", "propellerads", "adsterra", "onclick", "exoclick",
    "doubleclick", "bet365", "1xbet", "trafficjunky", "ad-delivery",
    "adtrue", "juicyads", "syndication", "histats", "trackvoluum",
    "monetag", "hilltopads", "richpush", "clickadu", "realsrv",
    "ero-advertising", "yllix", "adnxs", "googlesyndication", "outbrain",
    "taboola", "criteo", "mgid", "yadro", "adclick", "adservice",
    "adnetwork", "directrev", "poppush", "pushnoti", "popunder",
    "popcash", "adkeeper", "adcash", "adcolony", "inmobi", "vungle",
    "applovin", "unityads", "cpalead", "ogads", "mgid", "zergnet",
    "revcontent", "taboola", "trafficstars", "trafficfactory"
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

    // 3. Check known ad keywords
    val lower = url.lowercase()
    if (isAdBlockEnabled()) {
      for (ad in adKeywords) {
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
        // Intercepted unauthorized main frame popup/redirect
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
    if (clean.contains("luugy") || clean.contains("adsterra") || clean.contains("propeller") || clean.contains("popads")) {
      return false
    }

    return clean.contains("watchseries") ||
      clean.contains("topcinema") ||
      clean.contains("themoviedb.org") ||
      clean.contains("tmdb.org") ||
      clean.contains("opensubtitles") ||
      clean.contains("cloudflare") ||
      clean.contains("google.com") ||
      clean.contains("recaptcha") ||
      clean.contains("hcaptcha") ||
      clean.contains("vidcloud") ||
      clean.contains("upcloud") ||
      clean.contains("megacloud") ||
      clean.contains("streamwish") ||
      clean.contains("filemoon") ||
      clean.contains("dood") ||
      clean.contains("streamtape") ||
      clean.contains("mp4upload") ||
      clean.contains("rabbitstream") ||
      clean.contains("mixdrop")
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
      val url = request?.url?.toString()?.lowercase() ?: return null
      for (ad in adKeywords) {
        if (url.contains(ad)) {
          // Return empty response to drop the ad request immediately
          return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
          )
        }
      }
    }
    return super.shouldInterceptRequest(view, request)
  }

  override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
    super.onPageStarted(view, url, favicon)
    // Pre-emptive anti-popup neutralization before page scripts can execute
    val preInjectJs = """
      (function() {
        try {
          window.open = function() {
            console.log('Prevented window.open popup');
            return null;
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

    // Neutralize aggressive ad overlays, kill transparent clickjacking layers, and enforce popup suppression
    val clearAdOverlaysJs = """
      (function() {
        function clean() {
          // 1. Prevent window.open
          window.open = function(u) {
            console.log('Blocked popup to: ' + u);
            return null;
          };

          // 2. Remove obvious transparent popunder or ad hijacking layers
          var badSelectors = [
            'iframe[src*="ad"]', 'iframe[src*="pop"]', 'iframe[src*="bet"]',
            'iframe[src*="luugy"]', 'iframe[src*="propeller"]', 'iframe[src*="onclick"]',
            'div[class*="popunder"]', 'div[id*="popunder"]', 'div[class*="ad-overlay"]',
            'div[style*="z-index: 2147483647"]', 'div[style*="z-index: 999999"]',
            'a[href*="luugy"]', 'a[href*="onclick"]', 'a[href*="adsterra"]'
          ];
          try {
            document.querySelectorAll(badSelectors.join(',')).forEach(function(el) {
              if (!el.closest('.player--iframe') && !el.closest('.watch--area') && !el.closest('#player') && !el.closest('video')) {
                el.remove();
              }
            });
          } catch(e) {}

          // 3. Intercept malicious click-jacking on link tags
          document.addEventListener('click', function(e) {
            var target = e.target;
            while (target && target.tagName !== 'A' && target.tagName !== 'BODY') {
              target = target.parentElement;
            }
            if (target && target.tagName === 'A' && target.href) {
              var h = target.href.toLowerCase();
              if (h.indexOf('luugy') !== -1 || h.indexOf('onclick') !== -1 || h.indexOf('adsterra') !== -1 || h.indexOf('propeller') !== -1) {
                e.preventDefault();
                e.stopPropagation();
                console.log('Blocked malicious ad click');
                return false;
              }
            }
          }, true);

          // 4. Ensure legitimate video players, iframes, watch buttons and servers remain 100% interactive
          var interactiveEls = document.querySelectorAll(
            '#player, .player--iframe, iframe, .watch--area, .watch--servers--list, ' +
            'a.watch, a.watchAndDownlaod, a[href*="/watch/"], .Som--Info, .Watch--Movie, ' +
            '.Small--Box, .recent--block, ul.WatchServers, .server--item'
          );
          for (var j = 0; j < interactiveEls.length; j++) {
            interactiveEls[j].style.pointerEvents = 'auto';
          }

          // 5. If on a watch/player page, smoothly bring the player area into focus
          if (window.location.href.indexOf('/watch') !== -1 || window.location.href.indexOf('embed') !== -1) {
            var playerEl = document.querySelector('.player--iframe, iframe[src*="embed"], #player, .watch--servers--list');
            if (playerEl) {
              playerEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            }
          }
        }
        clean();
        setTimeout(clean, 1000);
        setTimeout(clean, 2500);
      })();
    """.trimIndent()
    try {
      view?.evaluateJavascript(clearAdOverlaysJs, null)
    } catch (_: Exception) {}

    onPageFinishedCallback(url)
  }
}
