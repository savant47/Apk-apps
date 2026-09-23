package com.example.tv

import android.graphics.Bitmap
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

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
    // Intercept window.open popups common on video streaming sites
    // Prevent spam windows from stealing focus on Android TV
    try {
      val tempWebView = WebView(view?.context ?: return false)
      tempWebView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
          val url = request?.url?.toString() ?: return false
          if (isWhitelistedStreamDomain(url)) {
            // Load in parent WebView
            this@TvWebChromeClient.let { }
          }
          return true // Block popunder
        }
      }
      val transport = resultMsg?.obj as? WebView.WebViewTransport
      transport?.webView = tempWebView
      resultMsg?.sendToTarget()
    } catch (_: Exception) {}
    return true
  }

  private fun isWhitelistedStreamDomain(url: String): Boolean {
    return url.contains("topcinema", ignoreCase = true) ||
        url.contains("watchseries", ignoreCase = true)
  }
}

class TvWebViewClient(
  private val isAdBlockEnabled: () -> Boolean,
  private val onPageFinishedCallback: (String) -> Unit
) : WebViewClient() {

  // Blacklist of known aggressive ad / popunder networks that disrupt video streaming on TV
  private val adKeywords = listOf(
    "popads", "propellerads", "adsterra", "onclick", "exoclick",
    "doubleclick", "bet365", "1xbet", "trafficjunky", "ad-delivery",
    "adtrue", "juicyads", "syndication", "histats", "trackvoluum"
  )

  override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
    val url = request?.url?.toString() ?: return false

    // Allow intent or tel only if explicit, block unwanted external protocols
    if (url.startsWith("market://") || url.startsWith("intent://") || url.startsWith("intent:")) {
      return true // Suppress intent redirect hijacking on TV
    }

    if (isAdBlockEnabled()) {
      val lower = url.lowercase()
      for (ad in adKeywords) {
        if (lower.contains(ad)) {
          return true // Block ad redirect
        }
      }
    }

    // Load navigation directly in WebView
    return false
  }

  override fun shouldInterceptRequest(
    view: WebView?,
    request: WebResourceRequest?
  ): WebResourceResponse? {
    if (isAdBlockEnabled()) {
      val url = request?.url?.toString()?.lowercase() ?: return null
      for (ad in adKeywords) {
        if (url.contains(ad)) {
          // Return empty dummy response
          return WebResourceResponse("text/plain", "UTF-8", null)
        }
      }
    }
    return super.shouldInterceptRequest(view, request)
  }

  override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
    super.onPageStarted(view, url, favicon)
  }

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    if (!url.isNullOrBlank()) {
      onPageFinishedCallback(url)
    }

    // Inject TV optimizations:
    // 1) Remove aggressive popup overlays and unclosable floating divs
    // 2) Improve video player responsiveness on large 16:9 screens
    // 3) Enhance link focus highlighting for TV remote navigation
    val tvOptimizationScript = """
      (function() {
        try {
          // Remove popup banners and spam overlays
          var adSelectors = [
            'iframe[src*="ads"]', 'iframe[src*="ad"]',
            'div[class*="popup"]', 'div[class*="overlay-ad"]',
            'div[id*="pop-overlay"]', 'div[class*="floating-banner"]',
            'div[id*="ad-container"]', 'a[href*="onclick"]'
          ];
          adSelectors.forEach(function(sel) {
            document.querySelectorAll(sel).forEach(function(el) {
              if (el && !el.querySelector('video') && !el.querySelector('iframe')) {
                el.style.display = 'none';
              }
            });
          });

          // Prevent window.open hijacking
          window.open = function(url, name, specs) {
            console.log('Blocked popup window: ' + url);
            return null;
          };

          // Smooth TV scrolling style
          var style = document.createElement('style');
          style.innerHTML = 'a:focus, button:focus, input:focus, select:focus, [tabindex]:focus { outline: 3px solid #38BDF8 !important; box-shadow: 0 0 12px #38BDF8 !important; }';
          document.head.appendChild(style);
        } catch(e) {
          console.error(e);
        }
      })();
    """.trimIndent()

    view?.evaluateJavascript(tvOptimizationScript, null)
  }
}
