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
        Os.setenv("MESA_DEBUG", "0", true)
        Os.setenv("MESA_NO_ERROR", "1", true)
        Os.setenv("GALLIUM_DRIVER", "softpipe", true)
      } catch (_: Throwable) {}
    }
  }

  override fun onCreate() {
    super.onCreate()
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
