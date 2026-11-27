package com.example

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.AppDisplayMode
import com.example.data.TvPreferencesRepository
import com.example.ui.ActivityKeyEventCallback
import com.example.ui.KeyEventProvider
import com.example.ui.MainScreen
import com.example.ui.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), KeyEventProvider {

  companion object {
    init {
      // Silence noisy Mesa/EGL logs without altering emulator/GPU driver overrides
      try {
        android.system.Os.setenv("MESA_LOG_LEVEL", "none", true)
        android.system.Os.setenv("EGL_LOG_LEVEL", "none", true)
      } catch (_: Throwable) {}
    }
  }

  private var keyEventCallback: ActivityKeyEventCallback? = null
  private lateinit var repository: TvPreferencesRepository
  private val exitHandler = Handler(Looper.getMainLooper())
  private var isCleaningUpForExit = false
  private var hasCompletedCleanExit = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes = window.attributes.apply {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      }
    }
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    repository = TvPreferencesRepository(applicationContext)

    lifecycleScope.launch {
      repository.displayModeFlow.collect { mode ->
        applyDisplayMode(mode)
      }
    }

    setContent {
      MyApplicationTheme {
        var showSplash by remember { mutableStateOf(true) }

        if (showSplash) {
          SplashScreen(
            onSplashFinished = { showSplash = false },
            modifier = Modifier.fillMaxSize()
          )
        } else {
          MainScreen(
            repository = repository,
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }
    com.example.ui.installAndroid7AccessibilityCrashShield(window?.decorView)
  }

  override fun onResume() {
    super.onResume()
    com.example.ui.installAndroid7AccessibilityCrashShield(window?.decorView)
    if (::repository.isInitialized) {
      applyDisplayMode(repository.getDisplayMode())
    }
  }

  override fun onStop() {
    super.onStop()
    try {
      if (isFinishing) {
        releaseAllActiveGraphicsAndWebViews(window?.decorView)
      } else {
        AppRuntimeMemoryGuard.notifyMemoryPressure(TRIM_MEMORY_UI_HIDDEN)
      }
    } catch (_: Throwable) {}
  }

  fun applyDisplayMode(mode: AppDisplayMode) {
    try {
      val insetsController = WindowCompat.getInsetsController(window, window.decorView)
      // Gracefully dismiss keyboard before changing screen orientation or insets
      insetsController.hide(WindowInsetsCompat.Type.ime())

      if (mode == AppDisplayMode.TV) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideSystemBarsForTv()
      } else {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        showSystemBarsForMobile()
      }
    } catch (_: Exception) {}
  }

  private fun hideSystemBarsForTv() {
    try {
      val controller = WindowCompat.getInsetsController(window, window.decorView)
      controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      controller.hide(WindowInsetsCompat.Type.systemBars())
    } catch (_: Exception) {}
  }

  private fun showSystemBarsForMobile() {
    try {
      val controller = WindowCompat.getInsetsController(window, window.decorView)
      controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
      controller.show(WindowInsetsCompat.Type.systemBars())
    } catch (_: Exception) {}
  }

  override fun setKeyEventCallback(callback: ActivityKeyEventCallback?) {
    this.keyEventCallback = callback
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    // Intercept remote keys first with custom handler
    if (keyEventCallback?.onDispatchKeyEvent(event) == true) {
      return true
    }
    return super.dispatchKeyEvent(event)
  }

  override fun finish() {
    if (hasCompletedCleanExit) {
      super.finish()
      return
    }
    performCleanExitWithFlush()
  }

  override fun finishAndRemoveTask() {
    if (hasCompletedCleanExit) {
      super.finishAndRemoveTask()
      return
    }
    performCleanExitWithFlush()
  }

  private fun performCleanExitWithFlush() {
    if (isCleaningUpForExit) return
    isCleaningUpForExit = true

    try {
      window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      releaseAllActiveGraphicsAndWebViews(window?.decorView)
      System.gc()
    } catch (_: Throwable) {}

    // Yield 150ms on the main looper so Android WindowManager / SurfaceFlinger
    // cleanly flushes detached SurfaceView hardware overlays before returning to the TV launcher.
    exitHandler.postDelayed({
      hasCompletedCleanExit = true
      try {
        super.finishAndRemoveTask()
      } catch (_: Throwable) {
        try {
          super.finish()
        } catch (_: Throwable) {}
      }
    }, 150L)
  }

  private fun releaseAllActiveGraphicsAndWebViews(root: View?) {
    if (root == null) return
    val webViews = mutableListOf<WebView>()
    val playerViews = mutableListOf<PlayerView>()
    val surfaceViews = mutableListOf<SurfaceView>()

    fun collect(view: View) {
      when (view) {
        is WebView -> webViews.add(view)
        is PlayerView -> playerViews.add(view)
        is SurfaceView -> surfaceViews.add(view)
      }
      if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
          val child = view.getChildAt(i)
          if (child != null) collect(child)
        }
      }
    }

    try {
      collect(root)
    } catch (_: Throwable) {}

    for (pv in playerViews) {
      try {
        pv.videoSurfaceView?.visibility = View.GONE
        pv.visibility = View.GONE
        val attachedPlayer = pv.player
        pv.player = null
        attachedPlayer?.let { p ->
          try { p.playWhenReady = false } catch (_: Throwable) {}
          try { p.stop() } catch (_: Throwable) {}
          try { p.clearVideoSurface() } catch (_: Throwable) {}
          try { p.release() } catch (_: Throwable) {}
        }
      } catch (_: Throwable) {}
    }

    for (sv in surfaceViews) {
      try {
        sv.visibility = View.GONE
      } catch (_: Throwable) {}
    }

    for (wv in webViews) {
      try {
        wv.stopLoading()
        wv.webChromeClient = null
        wv.webViewClient = android.webkit.WebViewClient()
        wv.removeJavascriptInterface("AndroidTvBridge")
        wv.onPause()
        wv.visibility = View.GONE
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.removeAllViews()
        wv.destroy()
      } catch (_: Throwable) {}
    }
  }

  override fun onDestroy() {
    exitHandler.removeCallbacksAndMessages(null)
    keyEventCallback = null
    try {
      releaseAllActiveGraphicsAndWebViews(window?.decorView)
      System.gc()
    } catch (_: Throwable) {}
    super.onDestroy()
  }
}
