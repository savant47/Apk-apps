package com.example

import android.app.Application
import android.os.Build
import android.system.Os
import android.webkit.WebView

class TvCinemaApplication : Application() {

  companion object {
    init {
      try {
        // Direct Mesa to use software rasterizer and mute Mesa error logging
        Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
        Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "swrast", true)
        Os.setenv("MESA_NO_ERROR", "1", true)
        Os.setenv("MESA_LOG_FILE", "/dev/null", true)
        Os.setenv("EGL_LOG_LEVEL", "fatal", true)
        Os.unsetenv("MESA_DEBUG")
        Os.unsetenv("LIBGL_DEBUG")
        Os.setenv("GALLIUM_DRIVER", "softpipe", true)
      } catch (_: Throwable) {}
    }
  }

  override fun onCreate() {
    super.onCreate()
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
}
