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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

/**
 * Coordinates lazy WebView initialization state and OS low-memory trim signals
 * across 1GB RAM Android TV devices (e.g. Xiaomi MiTV-AESP0, SDK 28, 160MB heap).
 */
object AppRuntimeMemoryGuard {
  /**
   * True ONLY after the user has actually mounted a WebView in this process session.
   * Prevents CookieManager.getInstance() from prematurely booting Chromium (~180MB RAM)
   * during app startup or pure OkHttp -> Native ExoPlayer playback.
   */
  @Volatile
  var hasWebViewBeenCreated: Boolean = false

  private val _memoryTrimEvents = MutableSharedFlow<Int>(extraBufferCapacity = 4)
  val memoryTrimEvents: SharedFlow<Int> = _memoryTrimEvents.asSharedFlow()

  fun notifyMemoryPressure(level: Int) {
    try {
      _memoryTrimEvents.tryEmit(level)
    } catch (_: Throwable) {}
  }
}

class TvCinemaApplication : Application(), ImageLoaderFactory {

  companion object {
    init {
      try {
        // Silence noisy Mesa/EGL logs without altering emulator/GPU driver selection
        Os.setenv("MESA_LOG_LEVEL", "none", true)
        Os.setenv("EGL_LOG_LEVEL", "none", true)
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

    // Let Chromium manage its own WebView HTTP Cache & Code Cache directories cleanly.
    // Remove any previously misplaced nested "HTTP Cache/Code Cache" folder if present.
    try {
      val misplacedCodeCache = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache")
      if (misplacedCodeCache.exists()) {
        misplacedCodeCache.deleteRecursively()
      }
    } catch (_: Throwable) {}

    // Isolate WebView data directories across child processes without booting Chromium in main process
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
   * - Max 8% app heap memory cache on Android 9 (10% on newer devices)
   * - Small 12MB disk cache on Android 9 (16MB on newer devices)
   * - Hardware bitmaps disabled on Android 9 (SDK <= 28) to prevent GPU GL_INVALID_VALUE / buffer exhaustion
   * - RGB_565 bitmaps on Android 9 to halve decoded poster RAM usage
   */
  override fun newImageLoader(): ImageLoader {
    val isAndroid9OrLower = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
    return ImageLoader.Builder(this)
      .memoryCache {
        MemoryCache.Builder(this)
          .maxSizePercent(if (isAndroid9OrLower) 0.08 else 0.10)
          .strongReferencesEnabled(true)
          .weakReferencesEnabled(false)
          .build()
      }
      .diskCache {
        DiskCache.Builder()
          .directory(File(cacheDir, "image_cache"))
          .maxSizeBytes(if (isAndroid9OrLower) 12L * 1024L * 1024L else 16L * 1024L * 1024L)
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
      if (level >= TRIM_MEMORY_RUNNING_MODERATE || level == TRIM_MEMORY_UI_HIDDEN) {
        coil.Coil.imageLoader(this).memoryCache?.clear()
        AppRuntimeMemoryGuard.notifyMemoryPressure(level)
      }
    } catch (_: Throwable) {}
  }

  override fun onLowMemory() {
    super.onLowMemory()
    try {
      AppDiagnosticsLogger.recordBreadcrumb(
        context = applicationContext,
        stage = "LOW_MEMORY",
        detail = "System triggered onLowMemory() — clearing Coil cache & inactive WebViews"
      )
      coil.Coil.imageLoader(this).memoryCache?.clear()
      AppRuntimeMemoryGuard.notifyMemoryPressure(TRIM_MEMORY_RUNNING_CRITICAL)
    } catch (_: Throwable) {}
  }
}
