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
    view?.let { webView ->
      try {
        val parent = webView.parent as? ViewGroup
        parent?.removeView(webView)
        webView.post {
          try {
            webView.destroy()
          } catch (_: Exception) {}
        }
      } catch (_: Exception) {}
    }
    onRenderProcessGoneCallback?.invoke()
    return true
  }

  override fun shouldInterceptRequest(
    view: WebView?,
    request: WebResourceRequest?
  ): WebResourceResponse? {
    val uri = request?.url ?: return null
    val urlStr = uri.toString()
    val host = uri.host?.lowercase() ?: ""
    val method = request.method ?: "GET"

    // 1. Core Ad Blocker Integration
    if (isAdBlockEnabled()) {
      var isAd = false
      for (ad in adHostKeywords) {
        if (host.contains(ad)) {
          isAd = true
          break
        }
      }
      if (isAd && !isStreamingOrCdnHost(host)) {
        return WebResourceResponse(
          "text/plain",
          "UTF-8",
          ByteArrayInputStream("".toByteArray())
        )
      }
    }

    // 2. High-precision cross-origin iframe HTML interceptor for 100% reliable postMessage autoplay events!
    if (method == "GET" && 
        (urlStr.contains("/embed") || urlStr.contains("/iframe") || urlStr.contains("player") || urlStr.contains("play")) &&
        !urlStr.endsWith(".js") && !urlStr.endsWith(".css") && !urlStr.endsWith(".mp4") && 
        !urlStr.endsWith(".m3u8") && !urlStr.endsWith(".ts") && !urlStr.endsWith(".png") && 
        !urlStr.endsWith(".jpg") && !urlStr.endsWith(".gif") && !urlStr.endsWith(".webp")
    ) {
      try {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.requestMethod = "GET"
        
        // Forward all headers to maintain cookies/credentials/User-Agent of WebView
        request.requestHeaders.forEach { (key, value) ->
          conn.setRequestProperty(key, value)
        }
        
        val responseCode = conn.responseCode
        if (responseCode == 200) {
          val contentType = conn.contentType ?: "text/html"
          if (contentType.lowercase().contains("text/html")) {
            val encoding = conn.contentEncoding ?: "UTF-8"
            val reader = BufferedReader(InputStreamReader(conn.inputStream, encoding))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
              sb.append(line).append("\n")
            }
            reader.close()
            var html = sb.toString()
            
            // Injected styles and script inside the same-origin of the iframe
            val scriptToInject = """
              <style>
              /* TV Optimized Video Player & Quality Menu for BeoutQ / Android TV */
              .jw-settings-menu, .jw-settings-submenu, .vjs-menu, .plyr__menu__container, .art-settings,
              [class*="settings-menu"], [class*="settings-submenu"], [class*="quality-menu"] {
                min-width: 220px !important;
                background: #0B132B !important;
                background-color: #0B132B !important;
                border: 2px solid #38bdf8 !important;
                border-radius: 10px !important;
                box-shadow: 0 4px 16px rgba(0,0,0,0.85) !important;
                z-index: 2147483647 !important;
                opacity: 1 !important;
                visibility: visible !important;
              }
              .jw-settings-item, .vjs-menu-item, .plyr__menu__container [role="menuitem"], .art-setting-item,
              .jw-settings-content li, .jw-reset.jw-settings-item, [class*="settings-item"], [class*="quality-item"],
              .jw-settings-submenu-item, .jw-text, .jw-reset {
                font-size: 16px !important;
                line-height: 24px !important;
                padding: 10px 16px !important;
                min-height: 44px !important;
                color: #ffffff !important;
                background: #0B132B !important;
                background-color: #0B132B !important;
                cursor: pointer !important;
                pointer-events: auto !important;
                display: flex !important;
                align-items: center !important;
                justify-content: space-between !important;
                text-shadow: none !important;
                opacity: 1 !important;
                visibility: visible !important;
              }
              .jw-settings-item:hover, .jw-settings-item:focus, .jw-settings-item[aria-checked="true"],
              .vjs-menu-item:hover, .vjs-menu-item:focus, .vjs-selected, .art-setting-item:hover,
              [class*="settings-item"]:hover, [class*="quality-item"]:hover {
                background: #0284c7 !important;
                background-color: #0284c7 !important;
                color: #ffffff !important;
                font-weight: bold !important;
              }
              .jw-settings-submenu-quality, [class*="submenu-quality"] {
                display: block !important;
                opacity: 1 !important;
                visibility: visible !important;
                background: #0B132B !important;
              }
              </style>
              <script>
              (function() {
                console.log("TvAutoPlay: Frame loaded!");
                function hookSettings() {
                  var buttons = document.querySelectorAll(
                    '.jw-icon-settings, button[aria-label*="Settings"], button[aria-label*="إعدادات"], ' +
                    '.vjs-menu-button-popup, .plyr__controls [data-plyr="settings"], .art-control-setting, ' +
                    '[class*="settings-btn"], [class*="gear"]'
                  );
                  buttons.forEach(function(btn) {
                    if (btn.getAttribute('data-tv-hooked')) return;
                    btn.setAttribute('data-tv-hooked', 'true');
                    btn.addEventListener('click', function() {
                      setTimeout(function() {
                        try {
                          var qualityItem = document.querySelector('.jw-settings-item[aria-controls*="quality"], .jw-settings-item[aria-label*="Quality"], [data-jw-submenu-name="quality"]');
                          if (qualityItem) {
                            qualityItem.click();
                          } else {
                            var qSubmenu = document.querySelector('.jw-settings-submenu[data-jw-submenu-name="quality"], .jw-settings-submenu-quality');
                            var mainSubmenu = document.querySelector('.jw-settings-submenu[data-jw-submenu-name="main"]');
                            if (qSubmenu) {
                              if (mainSubmenu) mainSubmenu.classList.remove('jw-settings-submenu-active');
                              qSubmenu.classList.add('jw-settings-submenu-active');
                              qSubmenu.style.display = 'block';
                              qSubmenu.style.visibility = 'visible';
                            }
                          }
                        } catch(e) {}
                      }, 100);
                    });
                  });
                }

                function checkVideo() {
                  var videos = document.querySelectorAll('video');
                  videos.forEach(function(video) {
                    if (video.getAttribute('data-ended-hooked-cross')) return;
                    video.setAttribute('data-ended-hooked-cross', 'true');
                    console.log('TvAutoPlay: Hooked video inside iframe successfully!');
                    
                    video.addEventListener('ended', function() {
                      console.log('TvAutoPlay: Video ended! Sending postMessage...');
                      window.parent.postMessage('video_ended', '*');
                    });
                    
                    video.addEventListener('timeupdate', function() {
                      if (video.duration && video.currentTime > 0) {
                        var remaining = video.duration - video.currentTime;
                        if (remaining <= 8) {
                          if (!video.getAttribute('data-autoplay-triggered-cross')) {
                            video.setAttribute('data-autoplay-triggered-cross', 'true');
                            console.log('TvAutoPlay: Video near end! Sending postMessage...');
                            window.parent.postMessage('video_ended', '*');
                          }
                        }
                      }
                    });
                  });
                }

                hookSettings();
                checkVideo();
                setTimeout(function() { hookSettings(); checkVideo(); }, 500);
                setTimeout(function() { hookSettings(); checkVideo(); }, 1500);
                setTimeout(function() { hookSettings(); checkVideo(); }, 3000);
              })();
              </script>
            """.trimIndent()
            
            html = if (html.contains("</body>")) {
              html.replace("</body>", "$scriptToInject</body>")
            } else if (html.contains("</html>")) {
              html.replace("</html>", "$scriptToInject</html>")
            } else {
              html + scriptToInject
            }
            
            return WebResourceResponse(
              "text/html",
              encoding,
              ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
            )
          }
        }
      } catch (e: Exception) {
        Log.e("TvWebViewClient", "CORS proxy injection failed: ${e.message}")
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
        // Listen to postMessage from cross-origin iframes
        window.addEventListener('message', function(event) {
          if (event.data === 'video_ended') {
            console.log('Parent received video_ended from iframe! Navigating to next episode...');
            window.playNextEpisode();
          }
        });

        function injectQualityStyles(doc) {
          try {
            if (!doc || doc.getElementById('cinema-tv-quality-style')) return;
            var style = doc.createElement('style');
            style.id = 'cinema-tv-quality-style';
            style.innerHTML = `
              /* Ultra-compatible Android TV / BeoutQ Settings & Quality Menu styling */
              .jw-settings-menu, .jw-settings-submenu, .vjs-menu, .plyr__menu__container, .art-settings {
                min-width: 220px !important;
                background: #0B132B !important;
                border: 2px solid #38bdf8 !important;
                border-radius: 12px !important;
                box-shadow: 0 10px 30px rgba(0,0,0,0.95) !important;
                z-index: 2147483647 !important;
                opacity: 1 !important;
                visibility: visible !important;
              }
              .jw-settings-item, .vjs-menu-item, .plyr__menu__container [role="menuitem"], .art-setting-item, .jw-text, .jw-reset {
                font-size: 16px !important;
                line-height: 24px !important;
                padding: 12px 18px !important;
                min-height: 46px !important;
                color: #ffffff !important;
                background: #0B132B !important;
                cursor: pointer !important;
                pointer-events: auto !important;
                display: flex !important;
                align-items: center !important;
                justify-content: space-between !important;
                text-shadow: none !important;
              }
              .jw-settings-item:hover, .jw-settings-item:focus, .jw-settings-item[aria-checked="true"],
              .vjs-menu-item:hover, .vjs-menu-item:focus, .vjs-selected, .art-setting-item:hover {
                background: #0284c7 !important;
                color: #ffffff !important;
                font-weight: bold !important;
              }
              .jw-settings-submenu-quality {
                display: block !important;
                opacity: 1 !important;
                background: #0B132B !important;
              }
              /* Ensure player iframe container has enough height on TV */
              @media screen and (min-width: 600px) {
                .player--iframe {
                  min-height: 460px !important;
                  height: 52vh !important;
                }
              }
            `;
            (doc.head || doc.body).appendChild(style);
          } catch(e) {}
        }

        function hookPlayerSettings(doc) {
          try {
            if (!doc) return;
            injectQualityStyles(doc);

            // Hook settings gear button to auto-reveal quality options directly
            var settingsButtons = doc.querySelectorAll(
              '.jw-icon-settings, button[aria-label*="Settings"], button[aria-label*="إعدادات"], ' +
              '.vjs-menu-button-popup, .plyr__controls [data-plyr="settings"], .art-control-setting'
            );

            settingsButtons.forEach(function(btn) {
              if (btn.getAttribute('data-tv-hooked')) return;
              btn.setAttribute('data-tv-hooked', 'true');

              btn.addEventListener('click', function() {
                setTimeout(function() {
                  try {
                    // 1. In JWPlayer: Auto-activate quality submenu if present
                    var qSubmenu = doc.querySelector('.jw-settings-submenu[data-jw-submenu-name="quality"], .jw-settings-submenu-quality');
                    var mainSubmenu = doc.querySelector('.jw-settings-submenu[data-jw-submenu-name="main"]');
                    var qualityItem = doc.querySelector('.jw-settings-item[aria-controls*="quality"], .jw-settings-item[aria-label*="Quality"], [data-jw-submenu-name="quality"]');

                    if (qualityItem) {
                      qualityItem.click();
                    } else if (qSubmenu) {
                      if (mainSubmenu) mainSubmenu.classList.remove('jw-settings-submenu-active');
                      qSubmenu.classList.add('jw-settings-submenu-active');
                      qSubmenu.style.display = 'block';
                      qSubmenu.style.visibility = 'visible';
                    }

                    // 2. Video.js: Auto-expand quality menu
                    var vjsQuality = doc.querySelector('.vjs-quality-menu-button, .vjs-menu-button-popup');
                    if (vjsQuality) {
                      var vjsMenu = vjsQuality.querySelector('.vjs-menu');
                      if (vjsMenu) vjsMenu.classList.add('vjs-lock-showing');
                    }
                  } catch(e) {}
                }, 80);
              });
            });
          } catch(e) {}
        }

        window.playNextEpisode = function() {
          console.log('Semantic Search: Starting next episode retrieval...');
          
          var nextSelectors = [
            'a.next', 'a.next-episode', '.next-episode a', 'a[href*="/next"]', 'a[title*="التالية"]', 'a[title*="Next"]',
            'button.next', 'button.next-episode', '.btn-next', '.plyr__controls [data-plyr="next"]',
            'a.next_episode', '.episode-nav .next', 'a[href*="/watch/"]:contains("التالية")', 'a:contains("الحلقة التالية")', 'a:contains("التالي")'
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

        function cleanseDOM() {
          try {
            var els = document.querySelectorAll('div, a, iframe, span');
            els.forEach(function(el) {
              if (el.closest('#player') || el.closest('#movie-player') || el.closest('.movie-player-wrap') || el.closest('.player--iframe') || el.closest('.jwplayer') || el.closest('.vjs-tech') || el.closest('video')) {
                return;
              }
              var style = window.getComputedStyle(el);
              var zIndex = parseInt(style.zIndex, 10);
              if (zIndex > 99 && (style.position === 'fixed' || style.position === 'absolute')) {
                var width = el.offsetWidth;
                var height = el.offsetHeight;
                var winW = window.innerWidth;
                var winH = window.innerHeight;
                if (width > winW * 0.5 && height > winH * 0.5 && !el.innerText.trim()) {
                  console.log('DOM Cleanser removed overlay:', el);
                  el.remove();
                }
              }
              if (el.tagName === 'A' && el.href) {
                var href = el.href.toLowerCase();
                if (href.indexOf('luugy') !== -1 || href.indexOf('adsterra') !== -1 || href.indexOf('propeller') !== -1 || href.indexOf('onclick') !== -1 || href.indexOf('doubleclick') !== -1) {
                  el.remove();
                }
              }
            });
          } catch(e) {}
        }

        function setupPlayers() {
          cleanseDOM();
          
          try {
            var ads = document.querySelectorAll('a[href*="luugy"], a[href*="adsterra"], a[href*="propeller"], div[id*="popunder"]');
            ads.forEach(function(el) {
              if (!el.closest('#player') && !el.closest('#movie-player') && !el.closest('.movie-player-wrap') && !el.closest('.player--iframe')) {
                el.remove();
              }
            });
          } catch(e) {}

          try {
            var clickableItems = document.querySelectorAll(
              '.servers .server-item, .server--item, .watch--servers--list li, ' +
              '#movie-player, #player, .movie-player-wrap, .btn-play, ' +
              '.player--iframe, iframe'
            );
            clickableItems.forEach(function(el) {
              el.style.pointerEvents = 'auto';
            });

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

            hookPlayerSettings(document);
            hookVideoEnded(document);
            
            var iframes = document.querySelectorAll('iframe');
            iframes.forEach(function(ifr) {
              try {
                var doc = ifr.contentDocument || ifr.contentWindow.document;
                if (doc) {
                  hookPlayerSettings(doc);
                  hookVideoEnded(doc);
                }
              } catch(e) {}
            });

            if (!window.hasTvMutationObserver) {
              window.hasTvMutationObserver = true;
              var observer = new MutationObserver(function(mutations) {
                var needsSetup = false;
                mutations.forEach(function(mut) {
                  if (mut.addedNodes) {
                    mut.addedNodes.forEach(function(node) {
                      if (node.nodeType === 1) {
                        if (node.tagName === 'VIDEO' || node.tagName === 'IFRAME') {
                          needsSetup = true;
                        } else if (node.querySelector && (node.querySelector('video') || node.querySelector('iframe'))) {
                          needsSetup = true;
                        }
                      }
                    });
                  }
                });
                if (needsSetup) {
                  hookVideoEnded(document);
                  var currentIfrs = document.querySelectorAll('iframe');
                  currentIfrs.forEach(function(ifr) {
                    try {
                      var doc = ifr.contentDocument || ifr.contentWindow.document;
                      if (doc) {
                        hookPlayerSettings(doc);
                        hookVideoEnded(doc);
                      }
                    } catch(e) {}
                  });
                }
              });
              observer.observe(document.body || document.documentElement, { childList: true, subtree: true });
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

              // C. DOM click
              var items = w.document.querySelectorAll('.jw-settings-item, .vjs-menu-item, [data-plyr="quality"] button, .art-setting-item');
              for (var k = 0; k < items.length; k++) {
                var text = (items[k].innerText || items[k].textContent || '').toLowerCase();
                if (t === 'auto' && text.indexOf('auto') !== -1) {
                  items[k].click();
                  return true;
                }
                if (text.indexOf(t) !== -1) {
                  items[k].click();
                  return true;
                }
              }
            } catch(e) {}
            return false;
          }

          if (apply(window)) return 'APPLIED_ROOT';
          var ifrs = document.querySelectorAll('iframe');
          for (var j = 0; j < ifrs.length; j++) {
            try {
              if (apply(ifrs[j].contentWindow)) return 'APPLIED_IFRAME';
            } catch(e) {}
          }
          return 'NOT_FOUND';
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

class TvAutoPlayBridge(
  private val onVideoEnded: () -> Unit,
  private val onVideoTimeUpdate: (currentTime: Float, duration: Float) -> Unit
) {
  @android.webkit.JavascriptInterface
  fun onVideoFinished() {
    onVideoEnded()
  }

  @android.webkit.JavascriptInterface
  fun onVideoTimeUpdate(currentTime: Float, duration: Float) {
    onVideoTimeUpdate(currentTime, duration)
  }
}
