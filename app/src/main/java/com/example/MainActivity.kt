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
      // Silence Mesa driver logs and avoid searching for non-existent DRM rendernodes
      try {
        android.system.Os.setenv("MESA_LOG_LEVEL", "none", true)
        android.system.Os.setenv("EGL_LOG_LEVEL", "none", true)
        android.system.Os.setenv("MESA_NO_ERROR", "1", true)
        android.system.Os.setenv("MESA_LOG_FILE", "/dev/null", true)
        android.system.Os.unsetenv("MESA_LOADER_DRIVER_OVERRIDE")
        android.system.Os.unsetenv("GALLIUM_DRIVER")
        android.system.Os.unsetenv("MESA_DEBUG")
        android.system.Os.unsetenv("LIBGL_DEBUG")
      } catch (_: Throwable) {}
    }
  }

  private var keyEventCallback: ActivityKeyEventCallback? = null
  private lateinit var repository: TvPreferencesRepository
  private val exitHandler = Handler(Looper.getMainLooper())
  private var isCleaningUpForExit = false
  private var hasCompletedCleanExit = false

  override fun onCreate(savedInstanceState: Bundle?) {
    // Disable hardware acceleration at the Window level on emulators to prevent libhwui from initializing EGL/Mesa drivers, 
    // entirely eliminating 'Failed to open rendernode' warnings in cloud environments.
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

    if (isEmulator) {
      window.clearFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
    }

    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    // Ensure WebView multi-process data directory isolation to prevent MESA/Chromium process lock errors
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      try {
        val processName = android.app.Application.getProcessName()
        if (packageName != processName) {
          android.webkit.WebView.setDataDirectorySuffix(processName)
        }
      } catch (_: Exception) {}
    }

    repository = TvPreferencesRepository(applicationContext)

    // Clean up potentially corrupted index and pre-create WebView Code Cache directories to prevent opendir and index reconstruction errors
    try {
      val httpCacheDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache")
      val indexFile = java.io.File(httpCacheDir, "index")
      val jsCacheDir = java.io.File(httpCacheDir, "Code Cache/js")
      val wasmCacheDir = java.io.File(httpCacheDir, "Code Cache/wasm")
      
      if (indexFile.exists() && (!jsCacheDir.exists() || !wasmCacheDir.exists())) {
        indexFile.delete()
      }
      
      if (!jsCacheDir.exists()) jsCacheDir.mkdirs()
      if (!wasmCacheDir.exists()) wasmCacheDir.mkdirs()
    } catch (_: Exception) {}

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
  }

  override fun onResume() {
    super.onResume()
    if (::repository.isInitialized) {
      applyDisplayMode(repository.getDisplayMode())
    }
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
        wv.onPause()
        wv.pauseTimers()
        wv.clearHistory()
        wv.clearCache(true)
        wv.loadUrl("about:blank")
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
