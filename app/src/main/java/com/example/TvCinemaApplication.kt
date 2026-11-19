package com.example

import android.app.Application
import android.os.Build
import android.system.Os
import android.webkit.WebView

class TvCinemaApplication : Application() {

  companion object {
    init {
      try {
        // Completely silence Mesa error logging and avoid triggering DRI rendernode lookups
        Os.setenv("MESA_LOG_LEVEL", "none", true)
        Os.setenv("EGL_LOG_LEVEL", "none", true)
        Os.setenv("MESA_NO_ERROR", "1", true)
        Os.setenv("MESA_LOG_FILE", "/dev/null", true)
        Os.unsetenv("MESA_LOADER_DRIVER_OVERRIDE")
        Os.unsetenv("GALLIUM_DRIVER")
        Os.unsetenv("MESA_DEBUG")
        Os.unsetenv("LIBGL_DEBUG")
      } catch (_: Throwable) {}
    }
  }

  override fun onCreate() {
    super.onCreate()

    // Install global persistent crash handler BEFORE anything else so fatal crashes
    // on low-RAM Android TV devices (e.g. Xiaomi TV Stick) are saved to last_crash_log.txt.
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        com.example.data.AppDiagnosticsLogger.saveCrashLog(
          context = applicationContext,
          threadName = thread.name,
          throwable = throwable
        )
      } catch (_: Throwable) {
      } finally {
        previousHandler?.uncaughtException(thread, throwable)
      }
    }

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
    } catch (_: Throwable) {}

    // Isolate WebView data directories across child processes
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      try {
        val processName = getProcessName()
        if (packageName != processName) {
          WebView.setDataDirectorySuffix(processName)
        }
      } catch (_: Throwable) {}
    }
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    try {
      if (level >= TRIM_MEMORY_MODERATE) {
        // Clean up memory cache on low-memory 2GB RAM TV boxes
        coil.Coil.imageLoader(this).memoryCache?.clear()
      }
    } catch (_: Throwable) {}
  }

  override fun onLowMemory() {
    super.onLowMemory()
    try {
      coil.Coil.imageLoader(this).memoryCache?.clear()
    } catch (_: Throwable) {}
  }
}
