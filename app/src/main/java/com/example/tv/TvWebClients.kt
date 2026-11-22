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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import android.util.Log
import com.example.data.AppDiagnosticsLogger

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
      try {
        // Safe check: Remove the view from any existing parent first to prevent IllegalStateException crashes
        val parent = view.parent as? ViewGroup
        parent?.removeView(view)

        view.layoutParams = FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )
        fullscreenContainer.setLayerType(View.LAYER_TYPE_NONE, null)
        fullscreenContainer.addView(view)
        fullscreenContainer.visibility = View.VISIBLE
        fullscreenContainer.bringToFront()
        fullscreenContainer.requestFocus()
        onFullscreenChanged(true)
      } catch (e: Exception) {
        callback?.onCustomViewHidden()
      }
    }
  }

  override fun onHideCustomView() {
    val view = customView ?: return
    try {
      fullscreenContainer.removeView(view)
      fullscreenContainer.visibility = View.GONE
      fullscreenContainer.setLayerType(View.LAYER_TYPE_NONE, null)
      customView = null
      customViewCallback?.onCustomViewHidden()
      customViewCallback = null
      onFullscreenChanged(false)
    } catch (e: Exception) {
      customView = null
      customViewCallback = null
      onFullscreenChanged(false)
    }
  }

  override fun getDefaultVideoPoster(): Bitmap? {
    return try {
      Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    } catch (_: Exception) {
      super.getDefaultVideoPoster()
    }
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

  override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
    try {
      val resources = request?.resources ?: emptyArray()
      // Exclude RESOURCE_PROTECTED_MEDIA_ID to prevent DrmHalHidl passthrough factory errors
      // and force HTML5 players to serve standard unencrypted HLS/MP4 streams
      val nonDrmResources = resources
        .filter { it != android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
        .toTypedArray()
      if (nonDrmResources.isNotEmpty()) {
        request?.grant(nonDrmResources)
      } else {
        request?.deny()
      }
    } catch (_: Exception) {}
  }

  override fun onGeolocationPermissionsShowPrompt(
    origin: String?,
    callback: android.webkit.GeolocationPermissions.Callback?
  ) {
    try {
      callback?.invoke(origin, true, false)
    } catch (_: Exception) {}
  }

  override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
    // Suppress noisy web console logs for ultra-lightweight performance on TV boxes and phones
    return true
  }
}

class TvWebViewClient(
  private val isAdBlockEnabled: () -> Boolean,
  private val onPageFinishedCallback: (String) -> Unit,
  private val onRenderProcessGoneCallback: (() -> Unit)? = null,
  private val onAdBlockedCallback: ((String) -> Unit)? = null,
  private val onVideoDetectedCallback: ((String, Map<String, String>) -> Unit)? = null,
  private val isGeneralBrowserMode: () -> Boolean = { false }
) : WebViewClient() {

  // Known malicious ad / popunder / redirect / heavy tracker domains (reduces RAM & OOM crashes on watchseries.bar)
  private val adHostKeywords = listOf(
    "luugy", "popads", "propellerads", "adsterra", "exoclick",
    "bet365", "1xbet", "trafficjunky", "ad-delivery",
    "adtrue", "juicyads", "histats", "trackvoluum", "monetag",
    "hilltopads", "richpush", "clickadu", "realsrv", "ero-advertising",
    "yllix", "directrev", "poppush", "pushnoti", "popunder", "popcash",
    "adkeeper", "adcash", "adcolony", "cpalead", "trafficstars",
    "onclickads", "adexchangeclear", "doubleclick.net",
    "acscdn", "dtscout", "amung.us", "taboola", "outbrain", "mgid",
    "adskeeper", "googlesyndication", "adservice.google", "pushwoosh",
    "onesignal", "mc.yandex", "clarity.ms", "hotjar", "sharethis",
    "disqus", "pubfuture", "bidgear", "galaksion", "vidazoo",
    "adnxs", "criteo", "rubiconproject", "pubmatic", "openx.net",
    "scorecardresearch", "quantserve", "facebook.net", "connect.facebook",
    "google-analytics", "analytics.google", "googletagmanager", "cloudflareinsights",
    "sentry.io", "mixpanel", "segment.io", "segment.com", "amplitude",
    "newrelic", "datadog", "matomo", "plausible", "statcounter", "facebook.com/tr"
  )

  private val heavyAssetExtensions = listOf(
    ".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp", ".ico", ".svg", ".avif",
    ".css", ".woff", ".woff2", ".ttf", ".otf", ".eot"
  )

  private fun emptyBlockedResponse(): WebResourceResponse =
    WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))

  /**
   * In Extractor WebView mode (not General Browser mode), block images, stylesheets,
   * fonts, and analytics so only lightweight HTML/JS player scripts run (~80% less RAM/CPU).
   */
  private fun shouldBlockHeavyExtractorAsset(urlLower: String, host: String): Boolean {
    if (isGeneralBrowserMode()) return false

    // Never block media streams, subtitles, or verification challenges
    if (urlLower.contains(".m3u8") ||
      urlLower.contains(".mp4") ||
      urlLower.contains(".mpd") ||
      urlLower.contains(".ts") ||
      urlLower.contains(".m4s") ||
      urlLower.contains(".vtt") ||
      urlLower.contains(".srt") ||
      urlLower.contains("videoplayback") ||
      urlLower.contains("/m3u8") ||
      urlLower.contains("recaptcha") ||
      urlLower.contains("hcaptcha") ||
      urlLower.contains("/cdn-cgi/") ||
      host.contains("cloudflare")
    ) {
      return false
    }

    // Strip query string / fragment before checking file extension
    val pathOnly = urlLower.substringBefore('?').substringBefore('#')
    for (ext in heavyAssetExtensions) {
      if (pathOnly.endsWith(ext)) {
        return true
      }
    }

    // Block known ad/analytics/font CDN hosts in extractor mode
    if (host.contains("fonts.googleapis.com") || host.contains("fonts.gstatic.com")) {
      return true
    }
    for (ad in adHostKeywords) {
      if (host.contains(ad) || urlLower.contains(ad)) {
        return true
      }
    }
    return false
  }

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
    // 1. Suppress market/intent app-hijacking attempts and non-standard schemes
    val isStandardProtocol = url.startsWith("http://") || 
                             url.startsWith("https://") || 
                             url.startsWith("javascript:") || 
                             url.startsWith("about:") || 
                             url.startsWith("file:") || 
                             url.startsWith("data:")
    if (!isStandardProtocol || url.startsWith("market://") || url.startsWith("intent://") || url.startsWith("intent:") || url.contains("play.google.com/store")) {
      return true
    }

    // 2. Auto-fix outdated topcinema.site domain to topcinema.io
    if (url.contains("topcinema.site")) {
      val fixed = url.replace("topcinema.site", "topcinema.io")
      view?.loadUrl(fixed)
      return true
    }

    // 3. Never block Google Search, reCAPTCHA, hCaptcha, or Cloudflare verification URLs
    val lower = url.lowercase()
    val isTrustedVerificationOrSearch = lower.contains("google.") ||
      lower.contains("gstatic.") ||
      lower.contains("googleapis.") ||
      lower.contains("recaptcha") ||
      lower.contains("hcaptcha") ||
      lower.contains("cloudflare") ||
      lower.contains("/sorry/")

    if (isAdBlockEnabled() && !isTrustedVerificationOrSearch) {
      for (ad in adHostKeywords) {
        if (lower.contains(ad)) {
          onAdBlockedCallback?.invoke(url)
          return true // Strictly block ad navigation
        }
      }
    }

    // 4. Smart MainFrame Protection:
    // If we are in general Asser Browser mode, allow navigating to any normal website (Google results, YouTube, news, etc.).
    // If we are inside an Asser TV streaming portal and not in general browser mode, protect against unauthorized popunder redirects.
    if (isForMainFrame && isAdBlockEnabled() && !isGeneralBrowserMode()) {
      val currentPageUrl = (view?.url ?: "").lowercase()
      val isCurrentlyOnStreamingPortal = currentPageUrl.contains("topcinema") ||
        currentPageUrl.contains("watchseries") ||
        currentPageUrl.contains("laroza") ||
        currentPageUrl.contains("egydead") ||
        currentPageUrl.contains("flixer")

      if (isCurrentlyOnStreamingPortal) {
        val host = try {
          Uri.parse(url).host?.lowercase() ?: ""
        } catch (_: Exception) { "" }

        if (host.isNotBlank() && !isAllowedMainFrameHost(host)) {
          onAdBlockedCallback?.invoke(host)
          return true // Cancel popunder redirect while inside streaming portal
        }
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
      clean.contains("embed") ||
      clean.contains("google") ||
      clean.contains("youtube") ||
      clean.contains("wikipedia") ||
      clean.contains("kooora") ||
      clean.contains("yallakora") ||
      clean.contains("facebook")
  }

  private fun isStreamingOrCdnHost(host: String): Boolean {
    val clean = host.lowercase()
    return clean.contains("watchseries") ||
      clean.contains("topcinema") ||
      clean.contains("flixer") ||
      clean.contains("laroza") ||
      clean.contains("llaroza") ||
      clean.contains("larooza") ||
      clean.contains("egydead") ||
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
      clean.contains("streamhg") ||
      clean.contains("megamax") ||
      clean.contains("voe") ||
      clean.contains("earnvids") ||
      clean.contains("streamhide") ||
      clean.contains("uqload") ||
      clean.contains("upstream") ||
      clean.contains("embedwish") ||
      clean.contains("sbplay") ||
      clean.contains("embedsito") ||
      clean.contains("calslot") ||
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
      clean.contains("gstatic")
  }

  override fun onReceivedSslError(
    view: WebView?,
    handler: SslErrorHandler?,
    error: SslError?
  ) {
    // Proceed for video CDN servers on older Android versions/TV boxes safely
    try {
      handler?.proceed()
    } catch (_: Exception) {
      try {
        handler?.cancel()
      } catch (_: Exception) {}
    }
  }

  override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
    val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      try {
        detail?.didCrash() ?: false
      } catch (_: Throwable) {
        false
      }
    } else {
      false
    }
    val priority = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      try {
        detail?.rendererPriorityAtExit() ?: -1
      } catch (_: Throwable) {
        -1
      }
    } else {
      -1
    }
    val crashedUrl = try { view?.url ?: "" } catch (_: Throwable) { "" }
    AppDiagnosticsLogger.log(
      "WebViewOOM",
      "WebView renderer gone (didCrash=$didCrash, priority=$priority, url=$crashedUrl) - safely destroying & recreating WebView",
      isError = true
    )

    view?.let { webView ->
      try {
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.removeJavascriptInterface("AndroidTvBridge")
        webView.visibility = View.GONE
        val parent = webView.parent as? ViewGroup
        parent?.removeView(webView)
        webView.removeAllViews()
        webView.destroy()
      } catch (_: Throwable) {
        try {
          webView.post {
            try {
              webView.destroy()
            } catch (_: Throwable) {}
          }
        } catch (_: Throwable) {}
      }
    }
    onRenderProcessGoneCallback?.invoke()
    // Return true so Android does NOT kill the entire app process on WebView OOM/renderer crash
    return true
  }

  // Android 7 (Nougat API 24/25) legacy iframe shouldInterceptRequest overload
  @Deprecated("Deprecated in Java")
  override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
    if (url.isNullOrBlank()) return null
    val urlLower = url.lowercase()
    val host = try { Uri.parse(url).host?.lowercase() ?: "" } catch (_: Exception) { "" }
    if ((urlLower.contains(".m3u8") || urlLower.contains(".mp4") || urlLower.contains("videoplayback") || urlLower.contains("/m3u8")) && !urlLower.contains(".ts")) {
      var isBlockedAd = false
      for (ad in adHostKeywords) {
        if (host.contains(ad)) { isBlockedAd = true; break }
      }
      if (!isBlockedAd) {
        if (view != null) {
          view.post {
            onVideoDetectedCallback?.invoke(url, emptyMap())
          }
        } else {
          onVideoDetectedCallback?.invoke(url, emptyMap())
        }
      }
    }
    if (shouldBlockHeavyExtractorAsset(urlLower, host)) {
      return emptyBlockedResponse()
    }
    if (isAdBlockEnabled()) {
      for (ad in adHostKeywords) {
        if (urlLower.contains(ad)) {
          if (!isStreamingOrCdnHost(host)) {
            return emptyBlockedResponse()
          }
        }
      }
    }
    return super.shouldInterceptRequest(view, url)
  }

  override fun shouldInterceptRequest(
    view: WebView?,
    request: WebResourceRequest?
  ): WebResourceResponse? {
    val uri = request?.url ?: return null
    val urlStr = uri.toString()
    val host = uri.host?.lowercase() ?: ""

    val urlLower = urlStr.lowercase()
    if ((urlLower.contains(".m3u8") || urlLower.contains(".mp4") || urlLower.contains(".mpd") || urlLower.contains("videoplayback") || urlLower.contains("/m3u8")) && !urlLower.contains(".ts")) {
      var isBlockedAd = false
      for (ad in adHostKeywords) {
        if (host.contains(ad)) { isBlockedAd = true; break }
      }
      if (!isBlockedAd) {
        val capturedHeaders = try {
          request.requestHeaders?.toMap() ?: emptyMap()
        } catch (_: Exception) {
          emptyMap()
        }
        AppDiagnosticsLogger.log(
          "WebViewIntercept",
          "Intercepted media stream: $urlStr | Referer=${capturedHeaders["Referer"] ?: capturedHeaders["referer"] ?: "none"}"
        )
        if (view != null) {
          view.post {
            onVideoDetectedCallback?.invoke(urlStr, capturedHeaders)
          }
        } else {
          onVideoDetectedCallback?.invoke(urlStr, capturedHeaders)
        }
      }
    }

    // 0. Fast Extractor Asset Blocker (strips images, CSS, fonts, and analytics in embed extraction mode)
    if (shouldBlockHeavyExtractorAsset(urlLower, host)) {
      return emptyBlockedResponse()
    }

    // 1. Core Ad Blocker Integration (Never block Google, reCAPTCHA, hCaptcha, or Cloudflare)
    val isTrustedCaptchaOrSearch = host.contains("google.") ||
      host.contains("gstatic.") ||
      host.contains("googleapis.") ||
      host.contains("recaptcha") ||
      host.contains("hcaptcha") ||
      host.contains("cloudflare") ||
      urlLower.contains("recaptcha") ||
      urlLower.contains("captcha") ||
      urlLower.contains("/sorry/")

    if (isAdBlockEnabled() && !isTrustedCaptchaOrSearch) {
      var isAd = false
      for (ad in adHostKeywords) {
        if (host.contains(ad)) {
          isAd = true
          break
        }
      }
      if (isAd && !isStreamingOrCdnHost(host)) {
        return emptyBlockedResponse()
      }
    }

    return super.shouldInterceptRequest(view, request)
  }

  override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
    super.onPageStarted(view, url, favicon)
    val urlLower = (url ?: "").lowercase()
    // Do not tamper with window.open/alert/confirm on Google Search, reCAPTCHA, or Cloudflare verification pages
    if (urlLower.contains("google.") ||
        urlLower.contains("gstatic.") ||
        urlLower.contains("recaptcha") ||
        urlLower.contains("hcaptcha") ||
        urlLower.contains("cloudflare") ||
        urlLower.contains("/sorry/")
    ) {
      return
    }
    // Polyfills for legacy Chromium (v53-58 on Android 7) + Popup Blocker
    val preInjectJs = """
      (function() {
        try {
          // 0. Disable EME/DRM probing to prevent DrmHalHidl passthrough factory errors and force standard HLS/MP4
          if (typeof navigator !== 'undefined' && navigator.requestMediaKeySystemAccess) {
            navigator.requestMediaKeySystemAccess = function() {
              return Promise.reject(new DOMException('EME disabled - using direct HLS/MP4', 'NotSupportedError'));
            };
          }

          // 1. Element.prototype.matches & closest polyfills for Chromium < 55
          if (!Element.prototype.matches) {
            Element.prototype.matches = Element.prototype.msMatchesSelector || 
                                        Element.prototype.webkitMatchesSelector || 
                                        function(s) {
                                          var matches = (this.document || this.ownerDocument).querySelectorAll(s),
                                              i = matches.length;
                                          while (--i >= 0 && matches.item(i) !== this) {}
                                          return i > -1;            
                                        };
          }
          if (!Element.prototype.closest) {
            Element.prototype.closest = function(s) {
              var el = this;
              do {
                if (el.matches(s)) return el;
                el = el.parentElement || el.parentNode;
              } while (el !== null && el.nodeType === 1);
              return null;
            };
          }

          // 2. Object.assign polyfill
          if (typeof Object.assign !== 'function') {
            Object.assign = function(target) {
              if (target == null) throw new TypeError('Cannot convert undefined or null to object');
              var to = Object(target);
              for (var index = 1; index < arguments.length; index++) {
                var nextSource = arguments[index];
                if (nextSource != null) {
                  for (var nextKey in nextSource) {
                    if (Object.prototype.hasOwnProperty.call(nextSource, nextKey)) {
                      to[nextKey] = nextSource[nextKey];
                    }
                  }
                }
              }
              return to;
            };
          }

          // 3. CustomEvent polyfill
          if (typeof window.CustomEvent !== 'function') {
            function CustomEvent(event, params) {
              params = params || { bubbles: false, cancelable: false, detail: null };
              var evt = document.createEvent('CustomEvent');
              evt.initCustomEvent(event, params.bubbles, params.cancelable, params.detail);
              return evt;
            }
            window.CustomEvent = CustomEvent;
          }

          // 3b. Modern JS & DOM Polyfills for Android 4.4 / 5 / 7+ WebViews
          if (typeof window.globalThis === 'undefined') { window.globalThis = window; }
          if (window.NodeList && !NodeList.prototype.forEach) {
            NodeList.prototype.forEach = Array.prototype.forEach;
          }
          if (!String.prototype.includes) {
            String.prototype.includes = function(search, start) {
              if (typeof start !== 'number') start = 0;
              if (start + search.length > this.length) return false;
              return this.indexOf(search, start) !== -1;
            };
          }
          if (!String.prototype.startsWith) {
            String.prototype.startsWith = function(search, rawPos) {
              var pos = rawPos > 0 ? rawPos | 0 : 0;
              return this.substring(pos, pos + search.length) === search;
            };
          }
          if (!Array.prototype.includes) {
            Array.prototype.includes = function(searchElement, fromIndex) {
              return this.indexOf(searchElement, fromIndex) !== -1;
            };
          }
          if (!Object.entries) {
            Object.entries = function(obj) {
              var ownProps = Object.keys(obj), i = ownProps.length, resArray = new Array(i);
              while (i--) resArray[i] = [ownProps[i], obj[ownProps[i]]];
              return resArray;
            };
          }
          if (!Object.values) {
            Object.values = function(obj) {
              var ownProps = Object.keys(obj), i = ownProps.length, resArray = new Array(i);
              while (i--) resArray[i] = obj[ownProps[i]];
              return resArray;
            };
          }
          if (typeof Promise !== 'undefined' && !Promise.prototype.finally) {
            Promise.prototype.finally = function(callback) {
              var P = this.constructor;
              return this.then(
                function(value) { return P.resolve(callback()).then(function() { return value; }); },
                function(reason) { return P.resolve(callback()).then(function() { throw reason; }); }
              );
            };
          }

          // 4. Safe window.open and popup suppressor
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

    try {
      android.webkit.CookieManager.getInstance().flush()
    } catch (_: Exception) {}

    val urlLower = url.lowercase()
    val isGoogleOrCaptchaOrGeneralWeb = urlLower.contains("google.") ||
      urlLower.contains("gstatic.") ||
      urlLower.contains("recaptcha") ||
      urlLower.contains("hcaptcha") ||
      urlLower.contains("cloudflare") ||
      urlLower.contains("/sorry/") ||
      urlLower.contains("youtube.com") ||
      urlLower.contains("wikipedia.org") ||
      isGeneralBrowserMode()

    if (isGoogleOrCaptchaOrGeneralWeb) {
      onPageFinishedCallback(url)
      return
    }

    val enhancePlayerJs = """
      (function() {
        if (window.__asserPlayerEnhanced) return;
        window.__asserPlayerEnhanced = true;
        // 1. Programmatic Pause Blocker to prevent mouse click-to-pause bug
        try {
          if (!HTMLVideoElement.prototype.originalPause) {
            HTMLVideoElement.prototype.originalPause = HTMLVideoElement.prototype.pause;
            HTMLVideoElement.prototype.pause = function() {
              if (window.blockPauseNextClick) {
                console.log('Blocked accidental video.pause() command via mouse/remote click!');
                window.blockPauseNextClick = false;
                return (window.Promise && window.Promise.resolve) ? window.Promise.resolve() : undefined;
              }
              return this.originalPause.apply(this, arguments);
            };
          }
        } catch(e) {}

        // 2. Click/Touch/Mouse Listener in Capture Phase to intercept accidental click pauses
        window.addEventListener('mousedown', function(e) {
          var target = e.target;
          if (!target) return;
          if (typeof isExplicitControl === 'function' && isExplicitControl(target)) return;

          // Check if click lies in player element or video player
          var playerWrap = target.closest('#player, #movie-player, .movie-player-wrap, .jwplayer, .video-js, .artplayer-app, .plyr, .player--iframe, video');
          if (playerWrap) {
            var rect = playerWrap.getBoundingClientRect();
            if (rect && rect.width > 0 && rect.height > 0) {
              var clickX = e.clientX - rect.left;
              var clickY = e.clientY - rect.top;
              var xRatio = clickX / rect.width;
              var yRatio = clickY / rect.height;
              // Central dead zone
              var isCenter = xRatio >= 0.12 && xRatio <= 0.88 && yRatio >= 0.12 && yRatio <= 0.85;
              if (isCenter) {
                window.blockPauseNextClick = true;
                setTimeout(function() { window.blockPauseNextClick = false; }, 350);
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
                console.log('Central mousedown click intercepted on video. Prevented playback toggle!');
              }
            }
          }
        }, true);

        window.addEventListener('click', function(e) {
          var target = e.target;
          if (!target) return;
          if (typeof isExplicitControl === 'function' && isExplicitControl(target)) return;

          // Check if click lies in player element or video player
          var playerWrap = target.closest('#player, #movie-player, .movie-player-wrap, .jwplayer, .video-js, .artplayer-app, .plyr, .player--iframe, video');
          if (playerWrap) {
            var rect = playerWrap.getBoundingClientRect();
            if (rect && rect.width > 0 && rect.height > 0) {
              var clickX = e.clientX - rect.left;
              var clickY = e.clientY - rect.top;
              var xRatio = clickX / rect.width;
              var yRatio = clickY / rect.height;
              // Central dead zone
              var isCenter = xRatio >= 0.12 && xRatio <= 0.88 && yRatio >= 0.12 && yRatio <= 0.85;
              if (isCenter) {
                window.blockPauseNextClick = true;
                setTimeout(function() { window.blockPauseNextClick = false; }, 350);
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
                console.log('Central click intercepted on video. Prevented playback toggle!');
                
                // Show control bar overlay temporarily
                var controlBar = document.querySelector('.jw-controlbar, .vjs-control-bar, .art-bottom, .plyr__controls');
                if (controlBar) {
                  controlBar.style.opacity = '1';
                  controlBar.style.visibility = 'visible';
                  setTimeout(function() {
                    controlBar.style.opacity = '';
                    controlBar.style.visibility = '';
                  }, 3500);
                }
              }
            }
          }
        }, true); // Capture!

        // 3. Dynamic Quality Controller & Clean Player Isolation (lightweight, non-thrashing):
        function enforceSelectedQuality() {
          try {
            var targetQ = window.__asserTargetQuality || 'auto';
            if (window.jwplayer && typeof window.jwplayer === 'function') {
              var p = window.jwplayer();
              if (p && p.getQualityLevels) {
                var levels = p.getQualityLevels();
                if (levels && levels.length > 0 && targetQ !== 'auto') {
                  for (var i = 0; i < levels.length; i++) {
                    var lbl = (levels[i].label || '').toLowerCase();
                    var h = levels[i].height ? levels[i].height.toString() : '';
                    if (lbl.indexOf(targetQ) !== -1 || h === targetQ) {
                      p.setCurrentQuality(i);
                      break;
                    }
                  }
                }
              }
            }
            if (window.Hls) {
              window.Hls.DefaultConfig = window.Hls.DefaultConfig || {};
              window.Hls.DefaultConfig.maxMaxBufferLength = 15;
            }
          } catch(e) {}
        }
        setInterval(enforceSelectedQuality, 5000);

        // 4b. Auto-upgrade LaRoza (video.php -> play.php) & TopCinema episode/movie pages (-> /watch/)
        // and isolate the video player container cleanly!
        try {
          var loc = window.location.href || '';
          if (loc.indexOf('video.php?vid=') !== -1) {
            window.location.replace(loc.replace('video.php?vid=', 'play.php?vid='));
            return;
          }
          if (window.location.hostname.indexOf('topcinema') !== -1 &&
              window.location.pathname.length > 8 &&
              window.location.pathname.indexOf('/watch') === -1 &&
              window.location.pathname.indexOf('/series/') === -1 &&
              window.location.pathname.indexOf('/category/') === -1 &&
              window.location.pathname.indexOf('/search') === -1) {
            var watchBtn = document.querySelector('a.watch, a[href*="/watch"]');
            if (watchBtn && watchBtn.href) {
              window.location.replace(watchBtn.href);
              return;
            }
          }
          if (!document.getElementById('asser-clean-player-css')) {
            var style = document.createElement('style');
            style.id = 'asser-clean-player-css';
            style.innerHTML = 'header, nav, footer, .navbar, #header, #footer, .site-header, .site-footer, .breadcrumb, .breadcrumbs, #comments, .comments-area, .share-buttons, .social-share, .pm-top-head, #pm-menu, .MasterHeader, .TopBar, .Single--Sidebar, .related--posts, .allepcont, .seasons--episodes { display: none !important; } body { background-color: #070A0E !important; padding-top: 0 !important; margin-top: 0 !important; } .player--iframe, #player, #movie-player, #Playerholder { width: 100% !important; min-height: 230px !important; margin: 0 auto !important; } .player--iframe iframe, #Playerholder iframe { width: 100% !important; height: 240px !important; min-height: 230px !important; display: block !important; }';
            document.head.appendChild(style);
          }
          var mainPlayer = document.querySelector('.player--iframe, #Playerholder, #player, #movie-player, iframe[src*="embed"], video');
          if (mainPlayer && mainPlayer.scrollIntoView) {
            mainPlayer.scrollIntoView({ behavior: 'instant', block: 'start' });
          }
        } catch(e) {}

        // 5. Video Source & Real Quality Sniffer + Auto-Play Trigger to feed Native ExoPlayer Bridge
        function sniffVideoUrls() {
          try {
            // Auto-start JWPlayer / Video.js / Plyr initial overlay once so the embed requests its .m3u8/.mp4 immediately
            if (!window.__asserAutoStartedEmbed) {
              if (window.jwplayer && typeof window.jwplayer === 'function') {
                var jp = window.jwplayer();
                if (jp && typeof jp.play === 'function' && jp.getState && jp.getState() === 'idle') {
                  window.__asserAutoStartedEmbed = true;
                  jp.play(true);
                }
              }
              var bigPlay = document.querySelector('.jw-display-icon-container .jw-icon-display, .vjs-big-play-button, .plyr__control--overlaid');
              if (bigPlay && !window.__asserAutoStartedEmbed) {
                window.__asserAutoStartedEmbed = true;
                bigPlay.click();
              }
            }

            // Sniff real available video qualities from JWPlayer / HLS.js / HTML5 Video
            var detectedQuals = [];
            var seenQ = {};
            function addQ(h) {
              var num = parseInt(h, 10);
              if (num && num >= 144 && num <= 2160 && !seenQ[num]) {
                seenQ[num] = true;
                detectedQuals.push(num);
              }
            }
            if (window.jwplayer && typeof window.jwplayer === 'function') {
              var p = window.jwplayer();
              if (p && p.getQualityLevels) {
                var levels = p.getQualityLevels() || [];
                for (var i = 0; i < levels.length; i++) {
                  var lbl = String(levels[i].label || '');
                  var m = lbl.match(/([0-9]{3,4})/);
                  if (m) addQ(m[1]);
                  else if (levels[i].height) addQ(levels[i].height);
                }
              }
            }
            if (window.hls && window.hls.levels) {
              for (var h = 0; h < window.hls.levels.length; h++) {
                if (window.hls.levels[h].height) addQ(window.hls.levels[h].height);
              }
            }

            var vids = document.querySelectorAll('video');
            vids.forEach(function(v) {
              if (v.videoHeight && v.videoHeight >= 144) {
                addQ(v.videoHeight);
              }
              var sources = v.querySelectorAll('source');
              sources.forEach(function(s) {
                var sz = s.getAttribute('size') || s.getAttribute('label') || s.getAttribute('res') || '';
                var m = String(sz).match(/([0-9]{3,4})/);
                if (m) addQ(m[1]);
              });

              var src = v.src || v.currentSrc || '';
              if (!src) {
                var source = v.querySelector('source');
                if (source) src = source.src || '';
              }
              if (src && src.startsWith('http') && src.indexOf('.ts') === -1 && window.__asserLastDetectedSrc !== src) {
                window.__asserLastDetectedSrc = src;
                if (window.AndroidTvBridge && typeof window.AndroidTvBridge.onVideoDetected === 'function') {
                  window.AndroidTvBridge.onVideoDetected(src);
                }
              }
            });

            if (detectedQuals.length > 0 && window.AndroidTvBridge && typeof window.AndroidTvBridge.onQualitiesDetected === 'function') {
              var qKey = detectedQuals.sort(function(a,b){return b-a;}).join(',');
              if (window.__lastReportedQuals !== qKey) {
                window.__lastReportedQuals = qKey;
                window.AndroidTvBridge.onQualitiesDetected(qKey);
              }
            }
          } catch(e) {}
        }
        setTimeout(sniffVideoUrls, 600);
        setInterval(sniffVideoUrls, 2500);

        // Listen to postMessage from cross-origin iframes
        window.addEventListener('message', function(event) {
          if (event.data === 'video_ended') {
            console.log('Parent received video_ended from iframe! Navigating to next episode...');
            window.playNextEpisode();
          }
        });


        window.playNextEpisode = function() {
          console.log('Semantic Search: Starting next episode retrieval...');
          
          var nextSelectors = [
            'a.next', 'a.next-episode', '.next-episode a', 'a[href*="/next"]', 'a[title*="التالية"]', 'a[title*="Next"]',
            'button.next', 'button.next-episode', '.btn-next', '.plyr__controls [data-plyr="next"]',
            'a.next_episode', '.episode-nav .next', 'a[title*="الحلقة التالية"]', 'a[title*="الحلقه التاليه"]', 'a[aria-label*="Next"]',
            '.episodes-list a.active + a', '.episodes-list li.active + li a', '.episodes-nav a.next'
          ];
          for (var i = 0; i < nextSelectors.length; i++) {
            var el = document.querySelector(nextSelectors[i]);
            if (el) {
              console.log('Semantic Search: Found via selector: ' + nextSelectors[i]);
              el.click();
              if (el.href) { window.location.href = el.href; return true; }
            }
          }

          var textSearches = [
            { tag: 'a', text: 'الحلقة التالية' },
            { tag: 'a', text: 'الحلقه التاليه' },
            { tag: 'a', text: 'التالي' },
            { tag: 'a', text: 'Next Episode' },
            { tag: 'a', text: 'Next' },
            { tag: 'button', text: 'الحلقة التالية' },
            { tag: 'button', text: 'الحلقه التاليه' },
            { tag: 'button', text: 'Next' },
            { tag: 'span', text: 'الحلقة التالية' },
            { tag: 'span', text: 'الحلقه التاليه' },
            { tag: 'div', text: 'الحلقة التالية' }
          ];
          
          function findByText(tag, text) {
            var els = document.querySelectorAll(tag);
            for (var i = 0; i < els.length; i++) {
              var t = (els[i].innerText || els[i].textContent || '').trim();
              if (t === text || t.indexOf(text) !== -1) {
                return els[i];
              }
            }
            return null;
          }

          for (var j = 0; j < textSearches.length; j++) {
            var found = findByText(textSearches[j].tag, textSearches[j].text);
            if (found) {
              console.log('Semantic Search: Found via text: ' + textSearches[j].text);
              found.click();
              if (found.href) { window.location.href = found.href; return true; }
              var parentLink = found.closest('a');
              if (parentLink && parentLink.href) {
                window.location.href = parentLink.href;
                return true;
              }
            }
          }

          try {
            var currentUrl = window.location.href;
            var decodedCurrentUrl = decodeURIComponent(currentUrl).replace(/\/$/, "");
            var allLinks = document.querySelectorAll('a[href]');
            var episodeLinks = [];
            for (var k = 0; k < allLinks.length; k++) {
              var href = allLinks[k].href;
              if (href && (href.indexOf('/watch/') !== -1 || href.indexOf('/episode/') !== -1 || href.indexOf('-episode-') !== -1 || href.indexOf('الحلقة-') !== -1 || href.indexOf('الحلقه-') !== -1)) {
                var decodedHref = decodeURIComponent(href).replace(/\/$/, "");
                if (episodeLinks.indexOf(decodedHref) === -1) {
                  episodeLinks.push(decodedHref);
                }
              }
            }
            if (episodeLinks.length > 1) {
              var currentIdx = -1;
              for (var k = 0; k < episodeLinks.length; k++) {
                if (episodeLinks[k] === decodedCurrentUrl || decodedCurrentUrl.indexOf(episodeLinks[k]) !== -1 || episodeLinks[k].indexOf(decodedCurrentUrl) !== -1) {
                  currentIdx = k;
                  break;
                }
              }
              if (currentIdx !== -1) {
                var getEpNum = function(url) {
                  var m = url.match(/(?:الحلق[ةه]|episode|ep|halka)[-_]?([0-9]+)/i) || url.match(/[-_]([0-9]+)[-_]/);
                  return m ? parseInt(m[1], 10) : null;
                };
                var currEpNum = getEpNum(decodedCurrentUrl);
                if (currEpNum !== null) {
                  if (currentIdx + 1 < episodeLinks.length) {
                    var nextEpNum = getEpNum(episodeLinks[currentIdx + 1]);
                    if (nextEpNum === currEpNum + 1) {
                      console.log('Semantic List Match (+1): ' + episodeLinks[currentIdx + 1]);
                      window.location.href = encodeURI(episodeLinks[currentIdx + 1]);
                      return true;
                    }
                  }
                  if (currentIdx - 1 >= 0) {
                    var prevEpNum = getEpNum(episodeLinks[currentIdx - 1]);
                    if (prevEpNum === currEpNum + 1) {
                      console.log('Semantic List Match (-1): ' + episodeLinks[currentIdx - 1]);
                      window.location.href = encodeURI(episodeLinks[currentIdx - 1]);
                      return true;
                    }
                  }
                }
              }
            }
          } catch(e) {}

          try {
            var decoded = decodeURIComponent(window.location.href);
            var arabicMatch = decoded.match(/(الحلق[ةه]-?)([0-9]+)/i);
            if (arabicMatch && arabicMatch[2]) {
              var num = parseInt(arabicMatch[2], 10);
              var nextNum = num + 1;
              var replaced = decoded.replace(arabicMatch[0], arabicMatch[1] + nextNum);
              console.log('Emergency Arabic URL Guessing: ' + replaced);
              window.location.href = encodeURI(replaced);
              return true;
            }
            var englishMatch = decoded.match(/((?:episode|ep|halka)[-_]?)([0-9]+)/i);
            if (englishMatch && englishMatch[2]) {
              var num = parseInt(englishMatch[2], 10);
              var nextNum = num + 1;
              var replaced = decoded.replace(englishMatch[0], englishMatch[1] + nextNum);
              console.log('Emergency English URL Guessing: ' + replaced);
              window.location.href = encodeURI(replaced);
              return true;
            }
          } catch(e) {}

          console.log('Semantic Search failed to find next episode.');
          return false;
        };

        function hookVideoEnded(doc) {
          if (!doc) return;
          try {
            var videos = doc.querySelectorAll('video');
            videos.forEach(function(video) {
              if (video.getAttribute('data-ended-hooked')) return;
              video.setAttribute('data-ended-hooked', 'true');
              console.log('Successfully hooked video ended and progress events!');
              
              video.addEventListener('ended', function() {
                console.log('Video ended event fired! Transitioning to next episode...');
                if (window.playNextEpisode && typeof window.playNextEpisode === 'function') {
                  window.playNextEpisode();
                } else if (window.top && window.top.playNextEpisode && typeof window.top.playNextEpisode === 'function') {
                  window.top.playNextEpisode();
                }
              });

              video.addEventListener('timeupdate', function() {
                if (video.duration && video.currentTime > 0) {
                  var remaining = video.duration - video.currentTime;
                  if (remaining <= 10) {
                    if (!video.getAttribute('data-autoplay-triggered')) {
                      video.setAttribute('data-autoplay-triggered', 'true');
                      console.log('Video is near end (under 10s)! Autoplaying next episode...');
                      if (window.playNextEpisode && typeof window.playNextEpisode === 'function') {
                        window.playNextEpisode();
                      } else if (window.top && window.top.playNextEpisode && typeof window.top.playNextEpisode === 'function') {
                        window.top.playNextEpisode();
                      }
                    }
                  }
                }
              });
            });
          } catch(e) {}
        }

        function isCaptchaOrVerificationElement(el) {
          if (!el) return false;
          try {
            var idAndClass = ((el.id || '') + ' ' + (el.className || '')).toLowerCase();
            if (idAndClass.indexOf('recaptcha') !== -1 ||
                idAndClass.indexOf('g-recaptcha') !== -1 ||
                idAndClass.indexOf('rc-') !== -1 ||
                idAndClass.indexOf('hcaptcha') !== -1 ||
                idAndClass.indexOf('cf-turnstile') !== -1 ||
                idAndClass.indexOf('captcha') !== -1 ||
                idAndClass.indexOf('challenge') !== -1) {
              return true;
            }
            if (el.querySelector && el.querySelector('iframe[src*="recaptcha"], iframe[src*="captcha"], iframe[src*="cloudflare"], iframe[title*="reCAPTCHA"], iframe[title*="challenge"], .g-recaptcha')) {
              return true;
            }
          } catch(e) {}
          return false;
        }

        function cleanseAds() {
          try {
            // Never run DOM ad stripping if the page is currently showing a CAPTCHA challenge
            if (document.querySelector('iframe[src*="recaptcha"], iframe[src*="hcaptcha"], iframe[src*="cloudflare"], .g-recaptcha, #captcha-form, #recaptcha')) {
              return;
            }
            window.open = function() { return null; };
            var ads = document.querySelectorAll('a[href*="luugy"], a[href*="adsterra"], a[href*="propeller"], div[id*="popunder"], div[class*="popup"], div[class*="ads-box"]');
            ads.forEach(function(el) {
              if (!isCaptchaOrVerificationElement(el) && !el.closest('#player') && !el.closest('#movie-player') && !el.closest('.movie-player-wrap') && !el.closest('.player--iframe') && !el.closest('iframe')) {
                el.remove();
              }
            });
            // Remove transparent overlay divs and invisible click-jacking anchor overlays (never touch elements containing iframes or CAPTCHAs)
            var overlays = document.querySelectorAll('div[style*="position: fixed"], div[style*="position:absolute"], a[target="_blank"]');
            overlays.forEach(function(el) {
              if (!isCaptchaOrVerificationElement(el) && !el.querySelector('iframe') && !el.closest('#player') && !el.closest('#movie-player') && !el.closest('.movie-player-wrap') && !el.closest('iframe') && !el.closest('video') && !el.closest('.servers')) {
                var rect = el.getBoundingClientRect();
                if (rect.width > 200 && rect.height > 200) {
                  el.style.pointerEvents = 'none';
                  el.style.display = 'none';
                }
              }
            });
          } catch(e) {}
        }

        function isExplicitControl(target) {
          if (!target) return false;
          return !!target.closest(
            '.jw-controls, .jw-controlbar, .vjs-control-bar, .art-bottom, .plyr__controls, ' +
            '.jw-icon-playback, .vjs-play-control, .art-icon-play, button, input, ' +
            '.jw-icon-settings, .art-icon-setting, .vjs-menu-button, .plyr__menu, ' +
            '.servers, .server-item, .server--item, .watch--servers--list, ' +
            '.btn-play, .vjs-big-play-button, .jw-display-icon-container, [role="button"], [role="menuitem"], ' +
            'a[href], select, option, .settings-menu, .vjs-menu'
          );
        }

        function setupVideoClickFilter(doc) {
          if (!doc) return;
          try {
            var root = doc.body || doc.documentElement;
            if (!root || root.getAttribute('data-tv-click-filter-ready')) return;
            root.setAttribute('data-tv-click-filter-ready', 'true');

            function filterPlayerClick(e) {
              var target = e.target;
              if (!target) return;

              // Explicit interactive controls are never filtered
              if (isExplicitControl(target)) {
                if (window.AndroidTvBridge && typeof window.AndroidTvBridge.onPlayerClicked === 'function') {
                  window.AndroidTvBridge.onPlayerClicked(target.tagName || 'CONTROL', false, false);
                }
                return;
              }

              // Locate video element or player container
              var video = target.tagName === 'VIDEO' ? target : (target.querySelector ? target.querySelector('video') : null);
              if (!video) {
                var playerWrap = target.closest('#player, #movie-player, .movie-player-wrap, .jwplayer, .video-js, .artplayer-app, .plyr, .player--iframe');
                if (playerWrap) {
                  video = playerWrap.querySelector('video');
                }
              }

              if (!video) return;

              var rect = (video.getBoundingClientRect && video.getBoundingClientRect()) || (target.getBoundingClientRect && target.getBoundingClientRect());
              if (!rect || rect.width <= 0 || rect.height <= 0) return;

              var clickX = e.clientX - rect.left;
              var clickY = e.clientY - rect.top;
              var xRatio = clickX / rect.width;
              var yRatio = clickY / rect.height;
              var isPlaying = !video.paused && !video.ended && video.readyState > 2;

              var shouldFilter = false;
              if (window.AndroidTvBridge && typeof window.AndroidTvBridge.filterCenterClick === 'function') {
                try {
                  shouldFilter = window.AndroidTvBridge.filterCenterClick(target.tagName || 'VIDEO', xRatio, yRatio, isPlaying);
                } catch(err) {
                  shouldFilter = isPlaying && (xRatio >= 0.12 && xRatio <= 0.88 && yRatio >= 0.12 && yRatio <= 0.85);
                }
              } else {
                shouldFilter = isPlaying && (xRatio >= 0.12 && xRatio <= 0.88 && yRatio >= 0.12 && yRatio <= 0.85);
              }

              if (shouldFilter) {
                // Intercept accidental pause/play click from TV remote center click
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();

                if (window.AndroidTvBridge && typeof window.AndroidTvBridge.onPlayerClicked === 'function') {
                  window.AndroidTvBridge.onPlayerClicked(target.tagName || 'VIDEO', true, true);
                }

                // Show player controls without toggling playback state
                var controlBar = doc.querySelector('.jw-controlbar, .vjs-control-bar, .art-bottom, .plyr__controls');
                if (controlBar) {
                  controlBar.style.opacity = '1';
                  controlBar.style.visibility = 'visible';
                  setTimeout(function() {
                    controlBar.style.opacity = '';
                    controlBar.style.visibility = '';
                  }, 3500);
                }
                return false;
              }
            }

            doc.addEventListener('click', filterPlayerClick, true);
          } catch(e) {}
        }

        function setupPlayers() {
          cleanseAds();
          try {
            // Force video controls and gear/settings menus visible on Android TV
            if (!document.getElementById('tv-quality-fix-style')) {
              var css = document.createElement('style');
              css.id = 'tv-quality-fix-style';
              css.innerHTML = '.jw-controlbar, .jw-icon-settings, .vjs-control-bar, .art-bottom, .plyr__controls, .jw-settings-menu, .jw-settings-content, .jw-settings-submenu, .vjs-menu, .vjs-menu-content, .art-setting-panel, .art-setting, .plyr__menu { display: flex !important; opacity: 1 !important; visibility: visible !important; pointer-events: auto !important; z-index: 999999 !important; }';
              (document.head || document.documentElement).appendChild(css);
            }

            var clickableItems = document.querySelectorAll(
              '.servers .server-item, .server--item, .watch--servers--list li, ' +
              '#movie-player, #player, .movie-player-wrap, .btn-play, ' +
              '.player--iframe, iframe'
            );
            clickableItems.forEach(function(el) {
              el.style.pointerEvents = 'auto';
            });

            setupVideoClickFilter(document);

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

            var playBtn = document.querySelector('.btn-play');
            if (playBtn && !playBtn.getAttribute('data-app-ready')) {
              playBtn.setAttribute('data-app-ready', 'true');
              playBtn.style.cursor = 'pointer';
              playBtn.style.pointerEvents = 'auto';
            }

            try {
              if (window.jwplayer && typeof window.jwplayer === 'function') {
                var p = window.jwplayer();
                if (p && p.getQualityLevels && !window.hasOptimizedQuality) {
                  window.hasOptimizedQuality = true;
                  var levels = p.getQualityLevels();
                  for (var i = 0; i < levels.length; i++) {
                    var l = (levels[i].label || '').toLowerCase();
                    if (l.indexOf('720') !== -1 || l.indexOf('hd') !== -1) {
                      p.setCurrentQuality(i);
                      break;
                    }
                  }
                }
              }
            } catch(e) {}

            hookVideoEnded(document);
            var iframes = document.querySelectorAll('iframe');
            iframes.forEach(function(ifr) {
              try {
                var doc = ifr.contentDocument || ifr.contentWindow.document;
                if (doc) {
                  hookVideoEnded(doc);
                  setupVideoClickFilter(doc);
                  if (!doc.getElementById('tv-quality-fix-style-iframe')) {
                    var icss = doc.createElement('style');
                    icss.id = 'tv-quality-fix-style-iframe';
                    icss.innerHTML = '.jw-controlbar, .jw-icon-settings, .vjs-control-bar, .art-bottom, .plyr__controls, .jw-settings-menu, .jw-settings-content, .jw-settings-submenu, .vjs-menu, .vjs-menu-content, .art-setting-panel, .art-setting, .plyr__menu { display: flex !important; opacity: 1 !important; visibility: visible !important; pointer-events: auto !important; z-index: 999999 !important; }';
                    (doc.head || doc.documentElement).appendChild(icss);
                  }
                }
              } catch(e) {}
            });

            if (!window.hasTvMutationObserver) {
              window.hasTvMutationObserver = true;
              var mutDebounceTimer = null;
              var observer = new MutationObserver(function() {
                if (mutDebounceTimer) return;
                mutDebounceTimer = setTimeout(function() {
                  mutDebounceTimer = null;
                  try {
                    hookVideoEnded(document);
                    setupVideoClickFilter(document);
                    var currentIfrs = document.querySelectorAll('iframe');
                    currentIfrs.forEach(function(ifr) {
                      try {
                        var doc = ifr.contentDocument || ifr.contentWindow.document;
                        if (doc) {
                          hookVideoEnded(doc);
                          setupVideoClickFilter(doc);
                        }
                      } catch(e) {}
                    });
                  } catch(e) {}
                }, 450);
              });
              var rootNode = document.body || document.documentElement;
              if (rootNode) {
                observer.observe(rootNode, { childList: true, subtree: true });
              }
            }
          } catch(e) {}
        }

        // Global quality switcher function for evaluateJavascript
        window.setCinemaVideoQuality = function(target) {
          var t = (target || '').toLowerCase();
          var found = false;

          function apply(w) {
            if (!w) return false;
            try {
              // A. JWPlayer
              if (w.jwplayer && typeof w.jwplayer === 'function') {
                var p = w.jwplayer();
                if (p && p.getQualityLevels) {
                  var levels = p.getQualityLevels();
                  if (levels && levels.length > 0) {
                    for (var i = 0; i < levels.length; i++) {
                      var l = (levels[i].label || '').toLowerCase();
                      if (t === 'auto' && (l.indexOf('auto') !== -1 || i === 0)) {
                        p.setCurrentQuality(i);
                        return true;
                      }
                      if (l.indexOf(t) !== -1) {
                        p.setCurrentQuality(i);
                        return true;
                      }
                    }
                  }
                }
              }

              // B. HLS.js
              if (w.hls && w.hls.levels) {
                if (t === 'auto') {
                  w.hls.currentLevel = -1;
                  return true;
                }
                for (var h = 0; h < w.hls.levels.length; h++) {
                  var hgt = String(w.hls.levels[h].height || '');
                  if (hgt.indexOf(t) !== -1) {
                    w.hls.currentLevel = h;
                    return true;
                  }
                }
              }

              // C. DOM click: First click settings gear/button to open quality menu, then click item
              var doc = w.document;
              var gearBtns = doc.querySelectorAll('.jw-icon-settings, .vjs-menu-button-popup, .art-icon-setting, button[data-plyr="settings"], .jw-settings-gear, [class*="gear"], [class*="setting"]');
              for (var g = 0; g < gearBtns.length; g++) {
                gearBtns[g].click();
              }

              setTimeout(function() {
                try {
                  var items = doc.querySelectorAll('.jw-settings-item, .vjs-menu-item, .art-setting-item, .plyr__menu__container button, li, div');
                  for (var k = 0; k < items.length; k++) {
                    var text = (items[k].innerText || items[k].textContent || '').toLowerCase();
                    if (text.length < 35) {
                      if (t === 'auto' && (text.indexOf('auto') !== -1 || text.indexOf('تلقائي') !== -1)) {
                        items[k].click();
                        break;
                      } else if (text.indexOf(t) !== -1) {
                        items[k].click();
                        break;
                      }
                    }
                  }
                } catch(e2) {}
              }, 250);

              return true;
            } catch(e) {}
            return false;
          }

          if (apply(window)) found = true;
          var ifrs = document.querySelectorAll('iframe');
          for (var j = 0; j < ifrs.length; j++) {
            try {
              if (apply(ifrs[j].contentWindow)) found = true;
            } catch(e) {}
          }
          return found ? 'APPLIED' : 'NOT_FOUND';
        };

        setupPlayers();
        setTimeout(setupPlayers, 800);
        setTimeout(setupPlayers, 2000);
        setTimeout(setupPlayers, 4500);
      })();
    """.trimIndent()
    try {
      view?.evaluateJavascript(enhancePlayerJs, null)
    } catch (_: Exception) {}

    onPageFinishedCallback(url)
  }
}

open class TvPlayerBridge(
  private val onVideoEnded: () -> Unit = {},
  private val onVideoTimeUpdate: (currentTime: Float, duration: Float) -> Unit = { _, _ -> },
  private val onCenterClickFiltered: (target: String, isPlaying: Boolean) -> Unit = { _, _ -> },
  private val onVideoDetected: (String) -> Unit = {},
  private val onQualitiesDetected: (List<Int>) -> Unit = {}
) {
  @android.webkit.JavascriptInterface
  open fun onVideoDetected(src: String) {
    if (!src.isNullOrBlank() && src.startsWith("http")) {
      this.onVideoDetected.invoke(src)
    }
  }

  @android.webkit.JavascriptInterface
  open fun onQualitiesDetected(csvHeights: String) {
    if (!csvHeights.isNullOrBlank()) {
      val heights = csvHeights.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in 144..2160 }
        .distinct()
        .sortedDescending()
      if (heights.isNotEmpty()) {
        this.onQualitiesDetected.invoke(heights)
      }
    }
  }

  @android.webkit.JavascriptInterface
  open fun onVideoFinished() {
    this.onVideoEnded.invoke()
  }

  @android.webkit.JavascriptInterface
  open fun onVideoTimeUpdate(currentTime: Float, duration: Float) {
    this.onVideoTimeUpdate.invoke(currentTime, duration)
  }

  @android.webkit.JavascriptInterface
  open fun filterCenterClick(target: String, xRatio: Float, yRatio: Float, isPlaying: Boolean): Boolean {
    // If video is playing on TV, filter accidental clicks in center dead-zone (12% to 88%)
    val isCenter = xRatio in 0.12f..0.88f && yRatio in 0.12f..0.85f
    val shouldFilter = isPlaying && isCenter
    if (shouldFilter) {
      this.onCenterClickFiltered.invoke(target, isPlaying)
    }
    return shouldFilter
  }

  @android.webkit.JavascriptInterface
  open fun onPlayerClicked(target: String, isCenter: Boolean, isBlocked: Boolean) {
    if (isBlocked) {
      this.onCenterClickFiltered.invoke(target, true)
    }
  }
}

typealias TvAutoPlayBridge = TvPlayerBridge
