package com.example

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.system.Os
import android.webkit.WebView
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.AppDiagnosticsLogger
import java.io.File

class TvCinemaApplication : Application(), ImageLoaderFactory {

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
    // on 1GB RAM Android TV devices (e.g. Xiaomi MiTV-AESP0) are saved to last_crash_log.txt.
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        AppDiagnosticsLogger.recordBreadcrumb(
          context = applicationContext,
          stage = "UNCAUGHT_CRASH",
          detail = "${thread.name}: ${throwable.javaClass.simpleName}: ${throwable.message?.take(120) ?: ""}"
        )
        AppDiagnosticsLogger.saveCrashLog(
          context = applicationContext,
          threadName = thread.name,
          throwable = throwable
        )
      } catch (_: Throwable) {
      } finally {
        previousHandler?.uncaughtException(thread, throwable)
      }
    }

    // Record startup breadcrumb so OS-level SIGKILL events leave a clean boundary
    AppDiagnosticsLogger.recordBreadcrumb(
      context = applicationContext,
      stage = "APP_START",
      detail = "v${BuildConfig.VERSION_NAME} (SDK ${Build.VERSION.SDK_INT})"
    )

    // Clean up potentially corrupted index and pre-create WebView Code Cache directories
    try {
      val httpCacheDir = File(cacheDir, "WebView/Default/HTTP Cache")
      val indexFile = File(httpCacheDir, "index")
      val jsCacheDir = File(httpCacheDir, "Code Cache/js")
      val wasmCacheDir = File(httpCacheDir, "Code Cache/wasm")

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

  /**
   * Ultra-lightweight Coil ImageLoader capped strictly for 1GB RAM Android TV 9 devices:
   * - Max 10% app heap memory cache
   * - Small 16MB disk cache
   * - Hardware bitmaps disabled on Android 9 (SDK <= 28) to prevent GPU GL_INVALID_VALUE / buffer exhaustion
   * - RGB_565 bitmaps on Android 9 to halve decoded poster RAM usage
   */
  override fun newImageLoader(): ImageLoader {
    val isAndroid9OrLower = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
    return ImageLoader.Builder(this)
      .memoryCache {
        MemoryCache.Builder(this)
          .maxSizePercent(0.10)
          .strongReferencesEnabled(true)
          .weakReferencesEnabled(false)
          .build()
      }
      .diskCache {
        DiskCache.Builder()
          .directory(File(cacheDir, "image_cache"))
          .maxSizeBytes(16L * 1024L * 1024L)
          .build()
      }
      .allowHardware(!isAndroid9OrLower)
      .bitmapConfig(if (isAndroid9OrLower) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)
      .allowRgb565(true)
      .crossfade(false)
      .respectCacheHeaders(false)
      .build()
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    try {
      if (level >= TRIM_MEMORY_RUNNING_MODERATE) {
        coil.Coil.imageLoader(this).memoryCache?.clear()
      }
    } catch (_: Throwable) {}
  }

  override fun onLowMemory() {
    super.onLowMemory()
    try {
      AppDiagnosticsLogger.recordBreadcrumb(
        context = applicationContext,
        stage = "LOW_MEMORY",
        detail = "System triggered onLowMemory() — clearing Coil cache"
      )
      coil.Coil.imageLoader(this).memoryCache?.clear()
    } catch (_: Throwable) {}
  }
}
