package com.example.tv

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView
import com.example.model.ControlMode
import kotlin.math.max
import kotlin.math.min

class TvKeyHandler(
  private val getWebView: () -> WebView?,
  private val getControlMode: () -> ControlMode,
  private val onToggleControlMode: () -> Unit,
  private val onToggleTopBar: () -> Unit,
  private val onToggleFullscreen: () -> Unit,
  private val onCursorMoved: (Float, Float) -> Unit,
  private val onBackHandled: () -> Boolean
) {

  var cursorX: Float = 960f
  var cursorY: Float = 540f
  private var screenWidth: Float = 1920f
  private var screenHeight: Float = 1080f

  private var speedMultiplier = 1f
  private var lastRepeatCount = 0

  fun updateScreenBounds(width: Float, height: Float) {
    if (width > 0 && height > 0) {
      screenWidth = width
      screenHeight = height
      // Center cursor initially if at default
      if (cursorX == 960f && cursorY == 540f) {
        cursorX = width / 2f
        cursorY = height / 2f
        onCursorMoved(cursorX, cursorY)
      }
    }
  }

  fun handleKeyEvent(event: KeyEvent): Boolean {
    val keyCode = event.keyCode
    val isActionDown = event.action == KeyEvent.ACTION_DOWN

    // Handle Menu key to toggle top navigation bar on TV
    if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO || keyCode == KeyEvent.KEYCODE_SETTINGS) {
      if (isActionDown) {
        onToggleTopBar()
      }
      return true
    }

    // Media keys for play/pause control on TV
    if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
      keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
      keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
      if (isActionDown) {
        toggleHtmlVideoPlayback()
      }
      return true
    }

    // Color keys for quick actions (BeoutQ QS285 remote often has Red/Green/Yellow/Blue keys)
    if (keyCode == KeyEvent.KEYCODE_PROG_YELLOW || keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
      if (isActionDown) {
        onToggleControlMode()
      }
      return true
    }

    if (keyCode == KeyEvent.KEYCODE_PROG_BLUE || keyCode == KeyEvent.KEYCODE_BUTTON_X) {
      if (isActionDown) {
        onToggleFullscreen()
      }
      return true
    }

    // Back key handling
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      if (isActionDown) {
        return onBackHandled()
      }
      return true
    }

    // If Virtual Mouse Mode is active
    if (getControlMode() == ControlMode.VIRTUAL_MOUSE) {
      return handleVirtualMouseKeys(event)
    } else {
      // D-Pad navigation mode
      return handleDpadNavKeys(event)
    }
  }

  private fun handleVirtualMouseKeys(event: KeyEvent): Boolean {
    val isDown = event.action == KeyEvent.ACTION_DOWN
    val keyCode = event.keyCode
    val repeat = event.repeatCount

    // Adjust step speed for fluid movement on TV
    val baseStep = 28f
    val speed = if (repeat > 5) baseStep * 2.2f else if (repeat > 1) baseStep * 1.5f else baseStep

    when (keyCode) {
      KeyEvent.KEYCODE_DPAD_UP -> {
        if (isDown) {
          cursorY = max(20f, cursorY - speed)
          onCursorMoved(cursorX, cursorY)
        }
        return true
      }
      KeyEvent.KEYCODE_DPAD_DOWN -> {
        if (isDown) {
          cursorY = min(screenHeight - 20f, cursorY + speed)
          onCursorMoved(cursorX, cursorY)
        }
        return true
      }
      KeyEvent.KEYCODE_DPAD_LEFT -> {
        if (isDown) {
          cursorX = max(20f, cursorX - speed)
          onCursorMoved(cursorX, cursorY)
        }
        return true
      }
      KeyEvent.KEYCODE_DPAD_RIGHT -> {
        if (isDown) {
          cursorX = min(screenWidth - 20f, cursorX + speed)
          onCursorMoved(cursorX, cursorY)
        }
        return true
      }
      KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A -> {
        if (isDown) {
          simulateClickAt(cursorX, cursorY)
        }
        return true
      }
      KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
        val webView = getWebView()
        if (webView != null && webView.hasFocus()) {
          if (isDown) {
            simulateClickAt(cursorX, cursorY)
          }
          return true
        }
        return false
      }
    }
    return false
  }

  private fun handleDpadNavKeys(event: KeyEvent): Boolean {
    val webView = getWebView() ?: return false
    // Only scroll WebView with D-Pad if WebView itself currently holds focus;
    // never hijack Compose navigation on Home Dashboard, Top Bar, Side Panel, or Dialogs!
    if (!webView.hasFocus()) return false
    val isDown = event.action == KeyEvent.ACTION_DOWN
    val keyCode = event.keyCode

    if (!isDown) return false

    val scrollStep = 180

    when (keyCode) {
      KeyEvent.KEYCODE_DPAD_UP -> {
        webView.scrollBy(0, -scrollStep)
        return true
      }
      KeyEvent.KEYCODE_DPAD_DOWN -> {
        webView.scrollBy(0, scrollStep)
        return true
      }
      KeyEvent.KEYCODE_DPAD_LEFT -> {
        webView.scrollBy(-scrollStep, 0)
        return true
      }
      KeyEvent.KEYCODE_DPAD_RIGHT -> {
        webView.scrollBy(scrollStep, 0)
        return true
      }
      KeyEvent.KEYCODE_PAGE_UP -> {
        webView.pageUp(false)
        return true
      }
      KeyEvent.KEYCODE_PAGE_DOWN -> {
        webView.pageDown(false)
        return true
      }
    }
    return false
  }

  private fun simulateClickAt(x: Float, y: Float) {
    val webView = getWebView() ?: return
    val downTime = SystemClock.uptimeMillis()
    val eventTime = SystemClock.uptimeMillis()

    val downEvent = MotionEvent.obtain(
      downTime,
      eventTime,
      MotionEvent.ACTION_DOWN,
      x,
      y,
      0
    )
    val upEvent = MotionEvent.obtain(
      downTime,
      eventTime + 50,
      MotionEvent.ACTION_UP,
      x,
      y,
      0
    )

    webView.dispatchTouchEvent(downEvent)
    webView.dispatchTouchEvent(upEvent)

    downEvent.recycle()
    upEvent.recycle()
  }

  fun toggleHtmlVideoPlayback() {
    val webView = getWebView() ?: return
    val js = """
      (function() {
        var videos = document.querySelectorAll('video');
        if (videos.length > 0) {
          for (var i = 0; i < videos.length; i++) {
            if (videos[i].paused) {
              videos[i].play();
            } else {
              videos[i].pause();
            }
          }
        } else {
          // Check for iframe video players
          var iframes = document.querySelectorAll('iframe');
          for (var j = 0; j < iframes.length; j++) {
            try {
              iframes[j].contentWindow.postMessage('{"event":"command","func":"togglePlay"}', '*');
            } catch(e) {}
          }
        }
      })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
  }
}
