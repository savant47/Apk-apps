package com.example

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDisplayMode
import com.example.data.TvPreferencesRepository
import com.example.ui.ActivityKeyEventCallback
import com.example.ui.KeyEventProvider
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), KeyEventProvider {

  companion object {
    init {
      // Configure Mesa software rasterizer environment to avoid searching for non-existent DRM rendernodes (/dev/dri/renderD128)
      try {
        android.system.Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
        android.system.Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "swrast", true)
        android.system.Os.setenv("MESA_DEBUG", "0", true)
      } catch (_: Throwable) {}
    }
  }

  private var keyEventCallback: ActivityKeyEventCallback? = null
  private lateinit var repository: TvPreferencesRepository

  override fun onCreate(savedInstanceState: Bundle?) {
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

    // Pre-create Chromium WebView Code Cache directories to prevent opendir and index reconstruction errors
    try {
      val jsCache = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
      if (!jsCache.exists()) jsCache.mkdirs()
      val wasmCache = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
      if (!wasmCache.exists()) wasmCache.mkdirs()
    } catch (_: Exception) {}

    lifecycleScope.launch {
      repository.displayModeFlow.collect { mode ->
        applyDisplayMode(mode)
      }
    }

    setContent {
      MyApplicationTheme {
        MainScreen(
          repository = repository,
          modifier = Modifier.fillMaxSize()
        )
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
}
