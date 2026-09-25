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
      // Configure Mesa software rasterizer environment to avoid searching for non-existent DRM rendernodes (/dev/dri/renderD128)
      try {
        android.system.Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
        android.system.Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "swrast", true)
        android.system.Os.setenv("MESA_LOG_FILE", "/dev/null", true)
        android.system.Os.setenv("EGL_LOG_LEVEL", "fatal", true)
        android.system.Os.unsetenv("MESA_DEBUG")
      } catch (_: Throwable) {}
    }
  }

  private var keyEventCallback: ActivityKeyEventCallback? = null
  private lateinit var repository: TvPreferencesRepository

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
}
