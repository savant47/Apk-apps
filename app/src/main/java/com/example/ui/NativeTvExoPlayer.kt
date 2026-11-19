package com.example.ui

import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.AppDiagnosticsLogger
import com.example.data.SearchAndSubtitlesService
import com.example.model.SubtitleItem
import com.example.model.SubtitleStyleConfig
import com.example.model.VideoQualityOption
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.ClipboxWhite
import kotlinx.coroutines.delay
import okhttp3.ConnectionPool
import okhttp3.Protocol
import java.io.IOException
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@Composable
fun NativeTvExoPlayer(
  videoUrl: String,
  title: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  refererUrl: String = "https://topcinema.io",
  capturedHeaders: Map<String, String> = emptyMap(),
  userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
  selectedQuality: String = "auto",
  availableQualities: List<VideoQualityOption> = emptyList(),
  onSelectQualityOption: (VideoQualityOption) -> Unit = {},
  activeSubtitle: SubtitleItem? = null,
  subtitleStyle: SubtitleStyleConfig = SubtitleStyleConfig(),
  onUpdateSubtitleStyle: (SubtitleStyleConfig) -> Unit = {},
  nextEpisodeTitle: String? = null,
  isAutoPlayNextEnabled: Boolean = true,
  onToggleAutoPlayNext: (Boolean) -> Unit = {},
  onPlayNextEpisode: () -> Unit = {},
  contentKey: String = "",
  savedResumePositionMs: Long = 0L,
  onSavePlaybackProgress: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
  onClearPlaybackProgress: () -> Unit = {},
  isFullscreen: Boolean = false,
  onToggleFullscreen: () -> Unit = {},
  onOpenSubtitles: () -> Unit = {},
  onOpenDiagnostics: () -> Unit = {},
  onQualitiesDetected: (List<VideoQualityOption>) -> Unit = {},
  onFallbackToWebPlayer: () -> Unit = onDismiss
) {
  val context = LocalContext.current
  // Ensure permissive SSL socket factory is installed before ExoPlayer opens any HTTPS connection
  remember {
    SearchAndSubtitlesService.installPermissiveSslForStreaming()
    true
  }

  val latestSelectedQuality by rememberUpdatedState(selectedQuality)
  val latestNextEpisodeTitle by rememberUpdatedState(nextEpisodeTitle)
  val latestIsAutoPlayNextEnabled by rememberUpdatedState(isAutoPlayNextEnabled)
  val latestOnPlayNextEpisode by rememberUpdatedState(onPlayNextEpisode)
  val latestOnSavePlaybackProgress by rememberUpdatedState(onSavePlaybackProgress)
  val latestOnClearPlaybackProgress by rememberUpdatedState(onClearPlaybackProgress)

  var retryAttempt by remember(videoUrl) { mutableIntStateOf(0) }
  var isControllerVisible by remember { mutableStateOf(true) }
  var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
  var actualVideoResolution by remember(videoUrl) { mutableStateOf("") }
  var showGearSettingsMenu by remember { mutableStateOf(false) }
  var currentPlaybackSpeed by remember { mutableFloatStateOf(1.0f) }
  var currentPlaybackPosMs by remember(videoUrl) { mutableLongStateOf(0L) }
  var lastKnownGoodPositionMs by remember(videoUrl) { mutableLongStateOf(0L) }
  var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
  var isPlayerReleased by remember { mutableStateOf(false) }

  // Cross-Source Resume Playback State
  var showResumePrompt by remember(contentKey, savedResumePositionMs) {
    mutableStateOf(contentKey.isNotBlank() && savedResumePositionMs > 15_000L)
  }
  var pendingResumeSeekMs by remember(contentKey) { mutableLongStateOf(0L) }
  var lastProgressSaveWallMs by remember(contentKey) { mutableLongStateOf(0L) }
  val resumeButtonFocusRequester = remember { FocusRequester() }

  // Auto-Play Next Episode Countdown State
  var showNextEpisodeCountdown by remember(videoUrl) { mutableStateOf(false) }
  var nextEpisodeCountdownSec by remember(videoUrl) { mutableIntStateOf(5) }
  var userCancelledNextEpisode by remember(videoUrl) { mutableStateOf(false) }
  var hasTriggeredAutoNext by remember(videoUrl) { mutableStateOf(false) }

  // Dedicated OkHttpClient tuned for Xiaomi TV Stick & TopCinema CDNs:
  // - retryOnConnectionFailure(true)
  // - HTTP/1.1 protocol avoids abrupt HTTP/2 unexpected-end-of-stream EOF on CDN edge servers
  // - Clean short-lived connection pool prevents stale keep-alive sockets
  val streamingOkHttpClient = remember {
    SearchAndSubtitlesService.buildSslTolerantHttpClient()
      .newBuilder()
      .retryOnConnectionFailure(true)
      .protocols(listOf(Protocol.HTTP_1_1))
      .connectionPool(ConnectionPool(2, 15, TimeUnit.SECONDS))
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .build()
  }

  val httpDataSourceFactory = remember(streamingOkHttpClient) {
    OkHttpDataSource.Factory(streamingOkHttpClient)
  }

  // Automatic retry policy for IOException / unexpected end of stream so video doesn't freeze
  val resilientLoadErrorPolicy = remember {
    object : DefaultLoadErrorHandlingPolicy(6) {
      override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val ex = loadErrorInfo.exception
        val msg = (ex.message ?: ex.cause?.message ?: "").lowercase()
        val isTransientStreamDrop = ex is IOException && (
          msg.contains("unexpected end of stream") ||
          msg.contains("eof") ||
          msg.contains("reset") ||
          msg.contains("closed") ||
          msg.contains("timeout")
        )
        if (isTransientStreamDrop && loadErrorInfo.errorCount <= 6) {
          return (loadErrorInfo.errorCount * 800L).coerceAtMost(3500L)
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
      }
    }
  }

  // Balanced Low-RAM LoadControl (15-35s buffer + 24MB memory cap for Xiaomi TV Stick)
  val balancedLowRamLoadControl = remember {
    DefaultLoadControl.Builder()
      .setBufferDurationsMs(
        15_000, // minBufferMs (15s smooth cushion)
        35_000, // maxBufferMs (35s max prevents OOM on low-RAM TV Stick)
        2_500,  // bufferForPlaybackMs (fast start)
        5_000   // bufferForPlaybackAfterRebufferMs (prevents freeze/skip loops)
      )
      .setTargetBufferBytes(24 * 1024 * 1024)
      .setPrioritizeTimeOverSizeThresholds(false)
      .build()
  }

  val trackSelector = remember {
    DefaultTrackSelector(context)
  }

  val player = remember {
    val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)
      .setLoadErrorHandlingPolicy(resilientLoadErrorPolicy)
    ExoPlayer.Builder(context)
      .setMediaSourceFactory(mediaSourceFactory)
      .setTrackSelector(trackSelector)
      .setLoadControl(balancedLowRamLoadControl)
      .build().apply {
        playWhenReady = true
      }
  }

  fun persistPlaybackProgressNow() {
    if (isPlayerReleased || showResumePrompt) return
    try {
      val pos = maxOf(lastKnownGoodPositionMs, player.currentPosition.coerceAtLeast(0L))
      val dur = player.duration.coerceAtLeast(0L)
      if (pos > 15_000L || (dur > 0L && pos >= (dur * 0.95).toLong())) {
        latestOnSavePlaybackProgress(pos, dur)
      }
    } catch (_: Throwable) {}
  }

  // Poll playback position every 200ms for subtitle sync, 10s progress persistence, and near-end Auto-Next check
  LaunchedEffect(player, videoUrl, contentKey) {
    while (true) {
      try {
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)
        currentPlaybackPosMs = pos
        if (pos > 1500L && !showResumePrompt) {
          lastKnownGoodPositionMs = pos
        }

        // Persist cross-source playback progress every 10 seconds during playback
        val nowWall = System.currentTimeMillis()
        if (!showResumePrompt && player.isPlaying && pos > 15_000L && (nowWall - lastProgressSaveWallMs >= 10_000L)) {
          lastProgressSaveWallMs = nowWall
          latestOnSavePlaybackProgress(pos, dur)
        }

        if (latestNextEpisodeTitle != null &&
            latestIsAutoPlayNextEnabled &&
            !userCancelledNextEpisode &&
            !showNextEpisodeCountdown &&
            !hasTriggeredAutoNext &&
            dur > 60_000L &&
            pos > 30_000L
        ) {
          val remainingMs = dur - pos
          if (remainingMs in 1L..10_000L) {
            nextEpisodeCountdownSec = 5
            showNextEpisodeCountdown = true
          }
        }
      } catch (_: Exception) {
      }
      delay(200L)
    }
  }

  // Run 5-second Auto-Play Next Episode countdown when banner is visible
  LaunchedEffect(showNextEpisodeCountdown, videoUrl) {
    if (showNextEpisodeCountdown && !hasTriggeredAutoNext && !userCancelledNextEpisode) {
      var remaining = nextEpisodeCountdownSec
      while (remaining > 0 && showNextEpisodeCountdown && !userCancelledNextEpisode) {
        delay(1000L)
        remaining -= 1
        nextEpisodeCountdownSec = remaining
      }
      if (remaining <= 0 && showNextEpisodeCountdown && !userCancelledNextEpisode && !hasTriggeredAutoNext) {
        hasTriggeredAutoNext = true
        showNextEpisodeCountdown = false
        AppDiagnosticsLogger.log("AutoNext", "تشغيل الحلقة التالية تلقائياً: ${latestNextEpisodeTitle ?: ""}")
        latestOnPlayNextEpisode()
      }
    }
  }

  fun applyQualityToTrackSelector(targetQualityId: String) {
    val paramsBuilder = trackSelector.buildUponParameters()
    paramsBuilder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
    val targetHeight = targetQualityId.toIntOrNull()

    if (targetHeight != null && targetHeight > 0) {
      var appliedExplicitOverride = false
      val currentTracks = player.currentTracks
      for (group in currentTracks.groups) {
        if (group.type == C.TRACK_TYPE_VIDEO && group.length > 0) {
          val matchingIndices = mutableListOf<Int>()
          for (i in 0 until group.length) {
            val fmt = group.getTrackFormat(i)
            if (fmt.height == targetHeight || (fmt.height in (targetHeight - 60)..(targetHeight + 60))) {
              matchingIndices.add(i)
            }
          }
          if (matchingIndices.isNotEmpty()) {
            paramsBuilder.setOverrideForType(
              TrackSelectionOverride(group.mediaTrackGroup, matchingIndices)
            )
            appliedExplicitOverride = true
            break
          }
        }
      }

      // Always enforce maxVideoSize constraint alongside TrackSelectionOverride so adaptive HLS
      // never jumps back to 1080p/1040p across track refreshes!
      paramsBuilder
        .setMaxVideoSize(Int.MAX_VALUE, targetHeight + 45)
        .setMinVideoSize(0, 0)

      AppDiagnosticsLogger.log(
        "ExoPlayerQuality",
        "Applied target quality ${targetHeight}p (explicitOverride=$appliedExplicitOverride)"
      )
    } else {
      paramsBuilder.clearVideoSizeConstraints().setMaxVideoBitrate(Int.MAX_VALUE)
      AppDiagnosticsLogger.log("ExoPlayerQuality", "Applied Auto quality selection")
    }
    trackSelector.setParameters(paramsBuilder)
  }

  // Dynamically apply the user's selected quality whenever selectedQuality changes
  LaunchedEffect(selectedQuality) {
    AppDiagnosticsLogger.selectedQualityId = selectedQuality
    applyQualityToTrackSelector(selectedQuality)
  }

  // Listen to tracks, actual resolution, and playback errors
  DisposableEffect(player, videoUrl) {
    val listener = object : Player.Listener {
      override fun onTracksChanged(tracks: Tracks) {
        val heights = mutableSetOf<Int>()
        for (group in tracks.groups) {
          if (group.type == C.TRACK_TYPE_VIDEO) {
            for (i in 0 until group.length) {
              val fmt = group.getTrackFormat(i)
              if (fmt.height in 140..2160) {
                heights.add(fmt.height)
              }
            }
          }
        }
        if (heights.isNotEmpty()) {
          val sorted = heights.sortedDescending()
          val options = mutableListOf(
            VideoQualityOption(
              id = "auto",
              label = "تلقائي Auto (${sorted.first()}p)",
              height = 0,
              isAuto = true
            )
          )
          for (h in sorted) {
            val badge = when {
              h >= 1000 -> "${h}p FHD"
              h >= 680 -> "${h}p HD"
              h >= 440 -> "${h}p SD"
              else -> "${h}p توفير"
            }
            options.add(VideoQualityOption(id = h.toString(), label = badge, height = h))
          }
          AppDiagnosticsLogger.log(
            "ExoPlayerTracks",
            "Detected real video tracks: ${sorted.joinToString { "${it}p" }}"
          )
          onQualitiesDetected(options)
          if (latestSelectedQuality != "auto") {
            applyQualityToTrackSelector(latestSelectedQuality)
          }
        }
      }

      override fun onVideoSizeChanged(videoSize: VideoSize) {
        if (videoSize.width > 0 && videoSize.height > 0) {
          actualVideoResolution = "${videoSize.height}p"
          AppDiagnosticsLogger.actualPlayingResolution =
            "${videoSize.width}x${videoSize.height} (${videoSize.height}p)"
          AppDiagnosticsLogger.log(
            "ExoPlayerVideo",
            "Playing resolution: ${videoSize.width}x${videoSize.height}"
          )
        }
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
          AppDiagnosticsLogger.activePlayerMode = "Native ExoPlayer (يعمل بنجاح ✅)"
          AppDiagnosticsLogger.log("ExoPlayer", "Playback STATE_READY for $videoUrl")
          if (pendingResumeSeekMs > 0L) {
            val targetMs = pendingResumeSeekMs
            pendingResumeSeekMs = 0L
            try {
              player.seekTo(targetMs)
              lastKnownGoodPositionMs = targetMs
              player.playWhenReady = true
              AppDiagnosticsLogger.log("Resume", "Seeked to saved resume position ${targetMs}ms on STATE_READY")
            } catch (_: Throwable) {}
          }
        } else if (playbackState == Player.STATE_ENDED) {
          // Mark episode/movie as completed (clears saved resume position)
          try {
            val dur = player.duration.coerceAtLeast(1L)
            latestOnSavePlaybackProgress(dur, dur)
          } catch (_: Throwable) {}
          if (latestNextEpisodeTitle != null && latestIsAutoPlayNextEnabled && !userCancelledNextEpisode && !hasTriggeredAutoNext) {
            nextEpisodeCountdownSec = 4
            showNextEpisodeCountdown = true
          }
        }
      }

      override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying && !showResumePrompt) {
          persistPlaybackProgressNow()
        }
      }

      override fun onPlayerError(error: PlaybackException) {
        val cause = error.cause
        val causeMsg = (cause?.message ?: error.message ?: "").lowercase()
        val isUnexpectedEof = causeMsg.contains("unexpected end of stream") ||
            causeMsg.contains("eof") ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

        val httpCode = if (cause is HttpDataSource.InvalidResponseCodeException) {
          "HTTP ${cause.responseCode}"
        } else {
          error.errorCodeName
        }

        val currentPosSnapshot = try {
          player.currentPosition.coerceAtLeast(0L)
        } catch (_: Exception) { 0L }
        if (currentPosSnapshot > 1500L) {
          lastKnownGoodPositionMs = currentPosSnapshot
        }

        AppDiagnosticsLogger.log(
          "ExoPlayerError",
          "Attempt $retryAttempt failed ($httpCode, eof=$isUnexpectedEof, pos=${lastKnownGoodPositionMs}ms): ${cause?.message ?: error.message} | URL=$videoUrl",
          isError = true
        )
        val maxRetries = if (isUnexpectedEof) 4 else 2
        if (retryAttempt < maxRetries) {
          retryAttempt += 1
        } else {
          AppDiagnosticsLogger.activePlayerMode = "WebView Fallback (بسبب $httpCode)"
          onFallbackToWebPlayer()
        }
      }
    }
    player.addListener(listener)
    onDispose {
      player.removeListener(listener)
    }
  }

  fun hideSurfaceViewsRecursively(view: View?) {
    if (view == null) return
    if (view is SurfaceView) {
      view.visibility = View.GONE
    }
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        hideSurfaceViewsRecursively(view.getChildAt(i))
      }
    }
  }

  fun teardownPlayerAndSurface() {
    persistPlaybackProgressNow()
    val pv = playerViewRef
    if (pv != null) {
      try {
        pv.videoSurfaceView?.visibility = View.GONE
        hideSurfaceViewsRecursively(pv)
        pv.visibility = View.GONE
        pv.player = null
      } catch (_: Throwable) {}
      playerViewRef = null
    }
    if (!isPlayerReleased) {
      isPlayerReleased = true
      try {
        player.playWhenReady = false
        player.stop()
      } catch (_: Throwable) {}
      try {
        player.clearVideoSurface()
      } catch (_: Throwable) {}
      try {
        player.release()
      } catch (_: Throwable) {}
      try {
        streamingOkHttpClient.connectionPool.evictAll()
      } catch (_: Throwable) {}
    }
  }

  // Also release hardware SurfaceView overlay immediately when Activity stops/destroys (e.g. Home/Back on TV Box)
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, player) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> {
          if (!isPlayerReleased) {
            persistPlaybackProgressNow()
            try {
              player.pause()
              player.clearVideoSurface()
            } catch (_: Throwable) {}
            try {
              playerViewRef?.videoSurfaceView?.visibility = View.GONE
              hideSurfaceViewsRecursively(playerViewRef)
            } catch (_: Throwable) {}
          }
        }
        Lifecycle.Event.ON_START -> {
          if (!isPlayerReleased) {
            try {
              playerViewRef?.visibility = View.VISIBLE
              playerViewRef?.videoSurfaceView?.visibility = View.VISIBLE
              playerViewRef?.player = null
              playerViewRef?.player = player
            } catch (_: Throwable) {}
          }
        }
        Lifecycle.Event.ON_DESTROY -> {
          teardownPlayerAndSurface()
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      teardownPlayerAndSurface()
    }
  }

  // Note: activeSubtitle is intentionally NOT a key here so selecting/changing subtitles
  // or changing subtitle colors/sizes NEVER reloads or re-buffers the video stream!
  LaunchedEffect(videoUrl, refererUrl, capturedHeaders, userAgent, retryAttempt) {
    if (videoUrl.isNotBlank()) {
      AppDiagnosticsLogger.extractedStreamUrl = videoUrl
      val videoHostOrigin = try {
        val vUri = Uri.parse(videoUrl)
        "${vUri.scheme}://${vUri.host}"
      } catch (_: Exception) {
        "https://topcinema.io"
      }

      val capturedReferer = capturedHeaders["Referer"]
        ?: capturedHeaders["referer"]
        ?: refererUrl

      val capturedRefererOrigin = try {
        val rUri = Uri.parse(capturedReferer)
        if (!rUri.scheme.isNullOrBlank() && !rUri.host.isNullOrBlank()) {
          "${rUri.scheme}://${rUri.host}/"
        } else {
          "$videoHostOrigin/"
        }
      } catch (_: Exception) {
        "$videoHostOrigin/"
      }

      val effectiveReferer = when (retryAttempt) {
        0 -> capturedReferer.ifBlank { capturedRefererOrigin }
        1 -> capturedRefererOrigin
        else -> "$videoHostOrigin/"
      }
      AppDiagnosticsLogger.streamReferer = effectiveReferer

      val effectiveOrigin = capturedHeaders["Origin"]
        ?: capturedHeaders["origin"]
        ?: try {
          val rUri = Uri.parse(effectiveReferer)
          if (!rUri.scheme.isNullOrBlank() && !rUri.host.isNullOrBlank()) {
            "${rUri.scheme}://${rUri.host}"
          } else {
            videoHostOrigin
          }
        } catch (_: Exception) {
          videoHostOrigin
        }

      val standardDesktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
      val effectiveUa = when (retryAttempt) {
        0 -> capturedHeaders["User-Agent"]
          ?: capturedHeaders["user-agent"]
          ?: userAgent.ifBlank { standardDesktopUa }
        1 -> standardDesktopUa
        else -> userAgent.ifBlank { standardDesktopUa }
      }

      val cookieManager = try {
        CookieManager.getInstance()
      } catch (_: Exception) {
        null
      }
      val videoCookie = try {
        cookieManager?.getCookie(videoUrl) ?: ""
      } catch (_: Exception) {
        ""
      }
      val refererCookie = try {
        cookieManager?.getCookie(effectiveReferer) ?: ""
      } catch (_: Exception) {
        ""
      }
      val combinedCookie = listOf(videoCookie, refererCookie)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString("; ")

      val headersMap = mutableMapOf<String, String>()
      for ((k, v) in capturedHeaders) {
        val kl = k.lowercase()
        if (kl != "host" && kl != "connection" && kl != "range" && kl != "accept-encoding" && v.isNotBlank()) {
          headersMap[k] = v
        }
      }
      headersMap["Referer"] = effectiveReferer
      headersMap["Accept"] = "*/*"
      headersMap["Accept-Language"] = "ar,en-US;q=0.9,en;q=0.8"
      // Force clean socket termination on TopCinema / CDN streams to prevent OkHttp "unexpected end of stream" EOF freezes
      headersMap["Connection"] = "close"

      if (retryAttempt <= 1 && effectiveOrigin.isNotBlank()) {
        headersMap["Origin"] = effectiveOrigin
      } else if (retryAttempt >= 2) {
        headersMap.remove("Origin")
        headersMap.remove("origin")
      }

      if (combinedCookie.isNotBlank()) {
        headersMap["Cookie"] = combinedCookie
      }

      if (retryAttempt > 0) {
        try {
          streamingOkHttpClient.connectionPool.evictAll()
        } catch (_: Exception) {}
        delay(650L)
      }

      httpDataSourceFactory.setUserAgent(effectiveUa)
      httpDataSourceFactory.setDefaultRequestProperties(headersMap)

      // Resume from last known playback position across reconnect retries so video never jumps back to 0:00!
      val resumePositionMs = maxOf(
        lastKnownGoodPositionMs,
        try { player.currentPosition.coerceAtLeast(0L) } catch (_: Exception) { 0L }
      )

      AppDiagnosticsLogger.log(
        "ExoPlayerInit",
        "Loading stream via OkHttp (attempt $retryAttempt, resume=${resumePositionMs}ms): $videoUrl | Referer=$effectiveReferer | Origin=${headersMap["Origin"] ?: "none"}"
      )

      try {
        val lowerUrl = videoUrl.lowercase()
        val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl)
        when {
          lowerUrl.contains(".mpd") || lowerUrl.contains("/dash/") -> {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
          }
          lowerUrl.contains(".m3u8") -> {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
          }
          lowerUrl.contains(".ism/manifest") || lowerUrl.contains(".isml/manifest") -> {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_SS)
          }
        }
        val mediaItem = mediaItemBuilder.build()
        player.setMediaItem(mediaItem, resumePositionMs)
        player.prepare()
        player.playWhenReady = !showResumePrompt
      } catch (t: Throwable) {
        val errMsg = "${t.javaClass.simpleName}: ${t.message ?: "Unsupported stream format"}"
        AppDiagnosticsLogger.log(
          "ExoPlayerInit",
          "Failed to initialize MediaItem ($errMsg) for $videoUrl - falling back to WebView player",
          isError = true
        )
        onFallbackToWebPlayer()
      }
    }
  }

  BackHandler {
    if (showGearSettingsMenu) {
      showGearSettingsMenu = false
    } else if (isFullscreen) {
      onToggleFullscreen()
    } else {
      onDismiss()
    }
  }

  val effectiveQualityList = remember(availableQualities, actualVideoResolution) {
    if (availableQualities.isNotEmpty()) {
      availableQualities
    } else {
      val autoSuffix = if (actualVideoResolution.isNotBlank()) " ($actualVideoResolution)" else ""
      listOf(
        VideoQualityOption(
          id = "auto",
          label = "تلقائي Auto$autoSuffix",
          height = 0,
          isAuto = true
        )
      )
    }
  }

  val activeQualityShortBadge = remember(selectedQuality, actualVideoResolution) {
    if (selectedQuality == "auto") {
      if (actualVideoResolution.isNotBlank()) "Auto ($actualVideoResolution)" else "Auto"
    } else {
      "${selectedQuality}p"
    }
  }

  fun hookPlayerViewGearButton(playerView: PlayerView) {
    try {
      val settingsBtn = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_settings)
      settingsBtn?.let { btn ->
        btn.visibility = View.VISIBLE
        btn.isEnabled = true
        btn.isClickable = true
        btn.setOnClickListener {
          showGearSettingsMenu = !showGearSettingsMenu
        }
      }
    } catch (_: Exception) {
    }
  }

  fun applyCaptionStyleToPlayerView(playerView: PlayerView, style: SubtitleStyleConfig) {
    try {
      playerView.subtitleView?.let { subView ->
        // Hide Media3's internal subtitleView if we are rendering custom parsedCues via Compose overlay
        if (activeSubtitle != null && activeSubtitle.parsedCues.isNotEmpty()) {
          subView.visibility = View.GONE
        } else {
          subView.visibility = View.VISIBLE
          val effectiveFontSizeSp = style.fontSizeSp.coerceIn(12f, 60f)
          val bgArgb = Color(style.backgroundColorArgb).toArgb()
          val isTransparentBg = android.graphics.Color.alpha(bgArgb) == 0
          subView.setApplyEmbeddedStyles(false)
          subView.setApplyEmbeddedFontSizes(false)
          // Generous bottom padding fraction prevents large 48sp-60sp Arabic text from clipping at screen bottom
          subView.setBottomPaddingFraction(if (effectiveFontSizeSp >= 40f) 0.10f else 0.08f)
          subView.setStyle(
            CaptionStyleCompat(
              Color(style.textColorArgb).toArgb(),
              if (isTransparentBg) android.graphics.Color.TRANSPARENT else bgArgb,
              android.graphics.Color.TRANSPARENT,
              if (isTransparentBg) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
              android.graphics.Color.BLACK,
              Typeface.DEFAULT_BOLD
            )
          )
          subView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveFontSizeSp)
        }
      }
    } catch (_: Exception) {
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
      .onPreviewKeyEvent { keyEvent ->
        // Let the D-pad navigate freely inside the Resume Playback or Gear Settings overlays
        if (showResumePrompt || showGearSettingsMenu) {
          return@onPreviewKeyEvent false
        }
        when (keyEvent.key) {
          Key.DirectionCenter, Key.Enter -> {
            if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
              if (player.isPlaying) player.pause() else player.play()
              true
            } else false
          }
          Key.DirectionLeft -> {
            if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
              player.seekTo(maxOf(0, player.currentPosition - 10000))
              true
            } else false
          }
          Key.DirectionRight -> {
            if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
              player.seekTo(minOf(player.duration, player.currentPosition + 10000))
              true
            } else false
          }
          Key.Back -> {
            if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
              if (showGearSettingsMenu) {
                showGearSettingsMenu = false
              } else if (isFullscreen) {
                onToggleFullscreen()
              } else {
                onDismiss()
              }
              true
            } else false
          }
          else -> false
        }
      }
      .focusable()
  ) {
    if (videoUrl.isNotBlank()) {
      AndroidView(
        factory = { ctx ->
          // Inflate explicit app:surface_type="surface_view" layout (NEVER texture_view)
          // for zero-drop hardware video decoding on Xiaomi TV Stick & low-RAM Android TV devices.
          val inflated = LayoutInflater.from(ctx)
            .inflate(R.layout.exo_player_surface_view, null, false) as PlayerView
          playerViewRef = inflated
          inflated.apply {
            this.player = if (!isPlayerReleased) player else null
            useController = true
            this.resizeMode = resizeMode
            controllerAutoShow = true
            controllerShowTimeoutMs = 3500
            layoutParams = FrameLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT
            )
            setFullscreenButtonClickListener { _ ->
              onToggleFullscreen()
            }
            setControllerVisibilityListener(
              PlayerView.ControllerVisibilityListener { visibility ->
                isControllerVisible = (visibility == View.VISIBLE)
                if (visibility == View.VISIBLE) {
                  hookPlayerViewGearButton(this)
                }
              }
            )
            applyCaptionStyleToPlayerView(this, subtitleStyle)
            post { hookPlayerViewGearButton(this) }
          }
        },
        update = { view ->
          if (!isPlayerReleased) {
            playerViewRef = view
            if (view.player != player) {
              view.player = player
            }
            if (view.resizeMode != resizeMode) {
              view.resizeMode = resizeMode
            }
            applyCaptionStyleToPlayerView(view, subtitleStyle)
            hookPlayerViewGearButton(view)
          }
        },
        onRelease = { view ->
          try {
            view.videoSurfaceView?.visibility = View.GONE
            hideSurfaceViewsRecursively(view)
            view.visibility = View.GONE
            view.player = null
          } catch (_: Throwable) {}
          if (playerViewRef === view) {
            playerViewRef = null
          }
        },
        modifier = Modifier.fillMaxSize()
      )

      // Synchronized Live Custom Subtitle Overlay (renders activeSubtitle.parsedCues with user's exact Color, Background, and Font Size!)
      ActiveSubtitleCueOverlay(
        activeSubtitle = activeSubtitle,
        currentPositionMs = currentPlaybackPosMs,
        subtitleStyle = subtitleStyle,
        isControllerVisible = isControllerVisible,
        modifier = Modifier.align(Alignment.BottomCenter)
      )

      // =========================================================================
      // AUTO-PLAY NEXT EPISODE COUNTDOWN BANNER (Bottom-End, D-Pad & Touch Ready)
      // =========================================================================
      AnimatedVisibility(
        visible = showNextEpisodeCountdown && !nextEpisodeTitle.isNullOrBlank() && !showGearSettingsMenu,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(horizontal = 14.dp, vertical = if (isControllerVisible) 66.dp else 20.dp)
      ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          Surface(
            color = CinemaDarkBackground.copy(alpha = 0.96f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, CinemaAccent),
            shadowElevation = 18.dp,
            modifier = Modifier
              .widthIn(min = 240.dp, max = 330.dp)
              .testTag("exo_next_episode_countdown_card")
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.SkipNext,
                  contentDescription = null,
                  tint = CinemaAccent,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = "الحلقة التالية تلقائياً خلال ${nextEpisodeCountdownSec}ث",
                  color = CinemaAccent,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.ExtraBold
                )
              }

              Text(
                text = nextEpisodeTitle ?: "الحلقة التالية",
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )

              LinearProgressIndicator(
                progress = { ((5 - nextEpisodeCountdownSec).coerceIn(0, 5)) / 5f },
                color = CinemaPrimary,
                trackColor = CinemaSurfaceVariant,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(4.dp)
                  .clip(RoundedCornerShape(4.dp))
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Button(
                  onClick = {
                    hasTriggeredAutoNext = true
                    showNextEpisodeCountdown = false
                    onPlayNextEpisode()
                  },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = CinemaPrimary,
                    contentColor = Color.White
                  ),
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .testTag("btn_play_next_episode_now")
                ) {
                  Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("تشغيل الآن", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }

                Surface(
                  onClick = {
                    userCancelledNextEpisode = true
                    showNextEpisodeCountdown = false
                  },
                  color = CinemaSurface,
                  shape = RoundedCornerShape(8.dp),
                  border = BorderStroke(1.dp, CinemaBorder),
                  modifier = Modifier
                    .heightIn(min = 38.dp)
                    .testTag("btn_cancel_next_episode_countdown")
                ) {
                  Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Text("إلغاء", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }
        }
      }
    } else {
      // Primary Player Extraction State
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        CircularProgressIndicator(
          color = CinemaPrimary,
          strokeWidth = 3.dp,
          modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "جاري سحب البث المباشر إلى المشغل الأساسي...",
          color = Color.White,
          fontSize = 13.sp,
          fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = title.ifBlank { "مشغل Asser TV الأساسي" },
          color = CinemaAccent,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
          onClick = onFallbackToWebPlayer,
          colors = ButtonDefaults.buttonColors(
            containerColor = CinemaSurface,
            contentColor = Color.White
          ),
          border = BorderStroke(1.dp, CinemaBorder),
          shape = RoundedCornerShape(50),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Web,
            contentDescription = null,
            tint = CinemaAccent,
            modifier = Modifier.size(15.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "التبديل لمشغل السيرفر المدمج",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    // Clean, Well-Proportioned Top Overlay inside Player (auto-hides with PlayerView controller)
    AnimatedVisibility(
      visible = (isControllerVisible || videoUrl.isBlank()) && !showGearSettingsMenu,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.TopCenter)
    ) {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .background(Color(0xCC070A0E), RoundedCornerShape(10.dp))
            .border(1.dp, CinemaBorder.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Right side: Live green dot + Current Quality Badge
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
          ) {
            Box(
              modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(CinemaSuccess)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = "المشغل الأساسي • $activeQualityShortBadge",
              color = CinemaAccent,
              fontSize = 10.5.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          Spacer(modifier = Modifier.width(6.dp))

          // Left side: Clean, non-overlapping compact pills (Gear Quality, Subtitles, Aspect Ratio, Web Fallback, Fullscreen)
          Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // 1. Gear Quality & Settings Button
            Surface(
              onClick = { showGearSettingsMenu = true },
              color = CinemaSurface,
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.8f)),
              modifier = Modifier
                .heightIn(min = 28.dp)
                .testTag("exo_quality_gear_button")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Settings,
                  contentDescription = "إعدادات الجودة والمشغل",
                  tint = CinemaAccent,
                  modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = activeQualityShortBadge,
                  color = Color.White,
                  fontSize = 9.5.sp,
                  fontWeight = FontWeight.ExtraBold,
                  maxLines = 1,
                  softWrap = false
                )
              }
            }

            // 1.5 Quick Next Episode Button (shown for series when a next episode is available)
            if (!nextEpisodeTitle.isNullOrBlank()) {
              Surface(
                onClick = {
                  hasTriggeredAutoNext = true
                  showNextEpisodeCountdown = false
                  onPlayNextEpisode()
                },
                color = CinemaSuccess.copy(alpha = 0.22f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CinemaSuccess),
                modifier = Modifier
                  .heightIn(min = 28.dp)
                  .testTag("exo_top_next_episode_button")
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "تشغيل الحلقة التالية",
                    tint = CinemaSuccess,
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(modifier = Modifier.width(2.dp))
                  Text(
                    text = "التالية",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                  )
                }
              }
            }

            // 2. Subtitles CC Button
            PlayerCompactSquareBtn(
              icon = Icons.Default.Subtitles,
              tint = if (activeSubtitle != null) CinemaSuccess else CinemaAccent,
              contentDescription = "الترجمة",
              onClick = onOpenSubtitles,
              testTag = "exo_subtitles_button"
            )

            // 3. Aspect Ratio Toggle
            PlayerCompactSquareBtn(
              icon = Icons.Default.AspectRatio,
              tint = Color.White,
              contentDescription = "أبعاد الفيديو",
              onClick = {
                resizeMode = when (resizeMode) {
                  AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                  AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                  else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
              },
              testTag = "exo_aspect_ratio_button"
            )

            // 4. Web Player Fallback Button
            PlayerCompactSquareBtn(
              icon = Icons.Default.Web,
              tint = Color.LightGray,
              contentDescription = "مشغل السيرفر المدمج",
              onClick = onFallbackToWebPlayer,
              testTag = "close_exo_player_button"
            )

            // 5. Fullscreen Button
            Surface(
              onClick = onToggleFullscreen,
              color = if (isFullscreen) ClipboxWhite else CinemaPrimary,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier
                .heightIn(min = 28.dp)
                .testTag("exo_fullscreen_button")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                  contentDescription = if (isFullscreen) "تصغير" else "ملء الشاشة",
                  tint = if (isFullscreen) Color.Black else Color.White,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = if (isFullscreen) "تصغير" else "تكبير",
                  color = if (isFullscreen) Color.Black else Color.White,
                  fontSize = 9.5.sp,
                  fontWeight = FontWeight.ExtraBold,
                  maxLines = 1,
                  softWrap = false
                )
              }
            }
          }
        }
      }
    }

    // =========================================================================
    // IN-PLAYER GEAR SETTINGS & EXTRACTED VIDEO QUALITIES MENU (ترس المشغل ⚙️)
    // =========================================================================
    if (showGearSettingsMenu) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.75f))
          .clickable { showGearSettingsMenu = false },
        contentAlignment = Alignment.Center
      ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          Surface(
            color = CinemaDarkBackground.copy(alpha = 0.98f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, CinemaAccent.copy(alpha = 0.7f)),
            shadowElevation = 18.dp,
            modifier = Modifier
              .fillMaxWidth(0.92f)
              .widthIn(max = 430.dp)
              .padding(8.dp)
              .clickable(enabled = false) {}
              .testTag("exo_gear_settings_sheet")
          ) {
            Column(
              modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              // Sheet Header
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = CinemaAccent,
                    modifier = Modifier.size(18.dp)
                  )
                  Column {
                    Text(
                      text = "إعدادات المشغل والجودة والترجمة",
                      color = Color.White,
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.ExtraBold
                    )
                    if (actualVideoResolution.isNotBlank()) {
                      Text(
                        text = "الأبعاد الفعلية للبث الآن: ${AppDiagnosticsLogger.actualPlayingResolution}",
                        color = CinemaSuccess,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }
                }

                PlayerCompactSquareBtn(
                  icon = Icons.Default.Close,
                  tint = Color.LightGray,
                  contentDescription = "إغلاق",
                  onClick = { showGearSettingsMenu = false },
                  testTag = "btn_close_exo_gear_menu"
                )
              }

              // 1. Extracted Video Qualities Section
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.HighQuality,
                  contentDescription = null,
                  tint = CinemaAccent,
                  modifier = Modifier.size(15.dp)
                )
                Text(
                  text = "جودة الفيديو المستخرجة من السيرفر (${effectiveQualityList.size}):",
                  color = CinemaAccent,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.ExtraBold
                )
              }

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                effectiveQualityList.forEach { qOpt ->
                  val isSelected = selectedQuality == qOpt.id ||
                      (selectedQuality == "auto" && qOpt.isAuto)
                  Surface(
                    onClick = {
                      applyQualityToTrackSelector(qOpt.id)
                      onSelectQualityOption(qOpt)
                      showGearSettingsMenu = false
                    },
                    color = if (isSelected) ClipboxWhite else CinemaSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                      width = 1.dp,
                      color = if (isSelected) ClipboxWhite else CinemaSuccess.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier
                      .heightIn(min = 32.dp)
                      .testTag("exo_gear_quality_${qOpt.id}")
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      if (isSelected) {
                        Icon(
                          imageVector = Icons.Default.Check,
                          contentDescription = null,
                          tint = Color.Black,
                          modifier = Modifier.size(13.dp)
                        )
                      }
                      Text(
                        text = qOpt.label,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                      )
                    }
                  }
                }
              }

              // 2. Quick Subtitle Style Controls (Color, Background, Font Size)
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Palette,
                  contentDescription = null,
                  tint = CinemaAccent,
                  modifier = Modifier.size(15.dp)
                )
                Text(
                  text = "مظهر الترجمة السريع (اللون • الحجم • الخلفية):",
                  color = CinemaAccent,
                  fontSize = 10.5.sp,
                  fontWeight = FontWeight.ExtraBold
                )
              }

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                val quickColors = listOf(
                  0xFFFFD700L to "أصفر",
                  0xFFFFFFFFL to "أبيض",
                  0xFF00E5FFL to "سماوي",
                  0xFF4ADE80L to "أخضر"
                )
                quickColors.forEach { (argb, label) ->
                  val isSelected = subtitleStyle.textColorArgb == argb
                  Surface(
                    onClick = { onUpdateSubtitleStyle(subtitleStyle.copy(textColorArgb = argb)) },
                    color = if (isSelected) CinemaPrimary.copy(alpha = 0.3f) else CinemaSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isSelected) Color(argb) else CinemaBorder),
                    modifier = Modifier.heightIn(min = 28.dp)
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      Box(
                        modifier = Modifier
                          .size(11.dp)
                          .clip(CircleShape)
                          .background(Color(argb))
                      )
                      Text(
                        text = label,
                        color = Color(argb),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }
                }

                val quickSizes = listOf(
                  20f to "20sp",
                  28f to "28sp",
                  36f to "36sp",
                  48f to "48sp",
                  60f to "60sp"
                )
                quickSizes.forEach { (sz, lbl) ->
                  val isSelected = subtitleStyle.fontSizeSp.toInt() == sz.toInt()
                  Surface(
                    onClick = { onUpdateSubtitleStyle(subtitleStyle.copy(fontSizeSp = sz)) },
                    color = if (isSelected) ClipboxWhite else CinemaSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isSelected) ClipboxWhite else CinemaBorder),
                    modifier = Modifier
                      .heightIn(min = 28.dp)
                      .testTag("exo_gear_sub_size_${sz.toInt()}")
                  ) {
                    Text(
                      text = lbl,
                      color = if (isSelected) Color.Black else Color.White,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                  }
                }
              }

              // 2.5 Auto-Play Next Episode Toggle (inside Gear Settings)
              Surface(
                onClick = { onToggleAutoPlayNext(!isAutoPlayNextEnabled) },
                color = if (isAutoPlayNextEnabled) CinemaSuccess.copy(alpha = 0.16f) else CinemaSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                  1.dp,
                  if (isAutoPlayNextEnabled) CinemaSuccess else CinemaBorder
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("exo_gear_toggle_auto_next")
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Autorenew,
                      contentDescription = null,
                      tint = if (isAutoPlayNextEnabled) CinemaSuccess else Color.Gray,
                      modifier = Modifier.size(16.dp)
                    )
                    Column {
                      Text(
                        text = "تشغيل الحلقة التالية تلقائياً (مسلسلات)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                      )
                      Text(
                        text = if (!nextEpisodeTitle.isNullOrBlank()) {
                          "الحلقة القادمة: $nextEpisodeTitle"
                        } else {
                          "يسحب روابط الحلقة التالية ويشغلها أوتوماتيكياً عند النهاية"
                        },
                        color = if (isAutoPlayNextEnabled) CinemaSuccess else Color.Gray,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                  }
                  Surface(
                    color = if (isAutoPlayNextEnabled) CinemaSuccess else CinemaSurfaceVariant,
                    shape = RoundedCornerShape(50)
                  ) {
                    Text(
                      text = if (isAutoPlayNextEnabled) "مُفعّل" else "متوقف",
                      color = if (isAutoPlayNextEnabled) Color.Black else Color.LightGray,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.ExtraBold,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                  }
                }
              }

              // 3. Playback Speed Section
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Speed,
                  contentDescription = null,
                  tint = Color.LightGray,
                  modifier = Modifier.size(15.dp)
                )
                Text(
                  text = "سرعة التشغيل (Speed):",
                  color = Color.LightGray,
                  fontSize = 10.5.sp,
                  fontWeight = FontWeight.Bold
                )
              }

              val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                speeds.forEach { spd ->
                  val isSpdSelected = currentPlaybackSpeed == spd
                  val spdLabel = if (spd == 1.0f) "1.0x (عادي)" else "${spd}x"
                  Surface(
                    onClick = {
                      currentPlaybackSpeed = spd
                      player.setPlaybackSpeed(spd)
                      showGearSettingsMenu = false
                    },
                    color = if (isSpdSelected) CinemaPrimary else CinemaSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                      1.dp,
                      if (isSpdSelected) ClipboxWhite else CinemaBorder
                    ),
                    modifier = Modifier.heightIn(min = 28.dp)
                  ) {
                    Text(
                      text = spdLabel,
                      color = Color.White,
                      fontSize = 10.sp,
                      fontWeight = if (isSpdSelected) FontWeight.ExtraBold else FontWeight.Medium,
                      modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                      maxLines = 1,
                      softWrap = false
                    )
                  }
                }
              }

              // 4. Quick Subtitles Dialog & Aspect Ratio Row
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Surface(
                  onClick = {
                    showGearSettingsMenu = false
                    onOpenSubtitles()
                  },
                  color = CinemaSurfaceVariant,
                  shape = RoundedCornerShape(8.dp),
                  border = BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.6f)),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 32.dp)
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = Icons.Default.Subtitles,
                      contentDescription = null,
                      tint = CinemaAccent,
                      modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                      text = "بحث وإعدادات الترجمة (CC)",
                      color = Color.White,
                      fontSize = 10.5.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }

                Surface(
                  onClick = {
                    resizeMode = when (resizeMode) {
                      AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                      AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                      else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    showGearSettingsMenu = false
                  },
                  color = CinemaSurfaceVariant,
                  shape = RoundedCornerShape(8.dp),
                  border = BorderStroke(1.dp, CinemaBorder),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 32.dp)
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = Icons.Default.AspectRatio,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                      text = "تغيير أبعاد الشاشة",
                      color = Color.White,
                      fontSize = 10.5.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
    // =========================================================================
    // CROSS-SOURCE RESUME PLAYBACK OVERLAY (D-Pad / TV Remote Friendly)
    // =========================================================================
    if (showResumePrompt && savedResumePositionMs > 15_000L) {
      LaunchedEffect(showResumePrompt) {
        delay(120L)
        try {
          resumeButtonFocusRequester.requestFocus()
        } catch (_: Throwable) {}
      }

      val formattedTime = remember(savedResumePositionMs) {
        val totalSec = (savedResumePositionMs / 1000L).coerceAtLeast(0L)
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        if (hours > 0L) {
          String.format("%02d:%02d:%02d", hours, mins, secs)
        } else {
          String.format("%02d:%02d", mins, secs)
        }
      }

      val resumeInteraction = remember { MutableInteractionSource() }
      val isResumeFocused by resumeInteraction.collectIsFocusedAsState()
      val startOverInteraction = remember { MutableInteractionSource() }
      val isStartOverFocused by startOverInteraction.collectIsFocusedAsState()

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
      ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          Surface(
            color = CinemaDarkBackground.copy(alpha = 0.98f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, CinemaAccent),
            shadowElevation = 22.dp,
            modifier = Modifier
              .fillMaxWidth(0.90f)
              .widthIn(max = 460.dp)
              .padding(12.dp)
              .testTag("resume_playback_prompt_card")
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.History,
                  contentDescription = null,
                  tint = CinemaAccent,
                  modifier = Modifier.size(22.dp)
                )
                Text(
                  text = "استكمال المشاهدة",
                  color = CinemaAccent,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.ExtraBold
                )
              }

              Text(
                text = "تم التوقف عند الدقيقة [$formattedTime] — هل تريد الاستكمال من حيث توقفت أم البدء من البداية؟",
                color = Color.White,
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                // Auto-focused primary Resume button
                Surface(
                  onClick = {
                    showResumePrompt = false
                    pendingResumeSeekMs = savedResumePositionMs
                    lastKnownGoodPositionMs = savedResumePositionMs
                    try {
                      if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                        player.seekTo(savedResumePositionMs)
                        if (player.playbackState == Player.STATE_READY) {
                          pendingResumeSeekMs = 0L
                        }
                      }
                      player.playWhenReady = true
                      player.play()
                    } catch (_: Throwable) {}
                    AppDiagnosticsLogger.log("Resume", "User resumed playback at $formattedTime ($contentKey)")
                  },
                  interactionSource = resumeInteraction,
                  color = if (isResumeFocused) CinemaAccent else CinemaSuccess,
                  shape = RoundedCornerShape(10.dp),
                  border = BorderStroke(
                    width = if (isResumeFocused) 2.5.dp else 1.dp,
                    color = if (isResumeFocused) Color.White else CinemaSuccess
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .focusRequester(resumeButtonFocusRequester)
                    .focusable(interactionSource = resumeInteraction)
                    .testTag("btn_resume_playback")
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = Icons.Default.PlayArrow,
                      contentDescription = "استكمال المشاهدة",
                      tint = Color.Black,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                      text = "استكمال المشاهدة",
                      color = Color.Black,
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.ExtraBold,
                      maxLines = 1,
                      softWrap = false
                    )
                  }
                }

                // Secondary Start Over button
                Surface(
                  onClick = {
                    showResumePrompt = false
                    pendingResumeSeekMs = 0L
                    lastKnownGoodPositionMs = 0L
                    latestOnClearPlaybackProgress()
                    try {
                      player.seekTo(0L)
                      player.playWhenReady = true
                      player.play()
                    } catch (_: Throwable) {}
                    AppDiagnosticsLogger.log("Resume", "User chose Start Over ($contentKey)")
                  },
                  interactionSource = startOverInteraction,
                  color = if (isStartOverFocused) ClipboxWhite else CinemaSurfaceVariant,
                  shape = RoundedCornerShape(10.dp),
                  border = BorderStroke(
                    width = if (isStartOverFocused) 2.5.dp else 1.dp,
                    color = if (isStartOverFocused) CinemaAccent else CinemaBorder
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .focusable(interactionSource = startOverInteraction)
                    .testTag("btn_start_over_playback")
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = Icons.Default.Replay,
                      contentDescription = "البدء من البداية",
                      tint = if (isStartOverFocused) Color.Black else Color.White,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                      text = "البدء من البداية",
                      color = if (isStartOverFocused) Color.Black else Color.White,
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      softWrap = false
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Live Synchronized Subtitle Cue Overlay that renders active `ParsedSubtitleCue` items
 * on top of either `NativeTvExoPlayer` or `WebViewPlayerBox` using the user's chosen
 * Text Color, Background Color/Opacity, Font Size (up to 60sp for TV), and Timing Sync Offset.
 */
@Composable
fun ActiveSubtitleCueOverlay(
  activeSubtitle: SubtitleItem?,
  currentPositionMs: Long,
  subtitleStyle: SubtitleStyleConfig,
  isControllerVisible: Boolean = false,
  modifier: Modifier = Modifier
) {
  if (activeSubtitle == null || activeSubtitle.parsedCues.isEmpty()) return

  val effectiveTimeMs = (currentPositionMs - subtitleStyle.syncOffsetMs).coerceAtLeast(0L)
  val matchingCueText = remember(activeSubtitle, effectiveTimeMs / 150L) {
    activeSubtitle.parsedCues
      .filter { effectiveTimeMs in it.startMs..it.endMs }
      .joinToString("\n") { it.text }
  }

  if (matchingCueText.isNotBlank()) {
    val safeFontSizeSp = subtitleStyle.fontSizeSp.coerceIn(12f, 60f)
    val extraBottomSafeMargin = if (safeFontSizeSp >= 40f) 14 else 6
    val bottomPad = if (isControllerVisible) {
      (subtitleStyle.bottomOffsetDp + 48 + extraBottomSafeMargin).dp
    } else {
      (subtitleStyle.bottomOffsetDp + extraBottomSafeMargin).dp
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      Box(
        modifier = modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(bottom = bottomPad),
        contentAlignment = Alignment.BottomCenter
      ) {
        Box(
          modifier = Modifier
            .background(
              color = Color(subtitleStyle.backgroundColorArgb),
              shape = RoundedCornerShape(10.dp)
            )
            .padding(
              horizontal = if (safeFontSizeSp >= 36f) 18.dp else 14.dp,
              vertical = if (safeFontSizeSp >= 36f) 8.dp else 6.dp
            )
        ) {
          Text(
            text = matchingCueText,
            color = Color(subtitleStyle.textColorArgb),
            fontSize = safeFontSizeSp.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            lineHeight = (safeFontSizeSp * 1.32f).sp,
            style = TextStyle(
              shadow = Shadow(
                color = Color.Black,
                offset = Offset(2.5f, 2.5f),
                blurRadius = 8f
              )
            )
          )
        }
      }
    }
  }
}

@Composable
private fun PlayerCompactSquareBtn(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  tint: Color,
  contentDescription: String,
  onClick: () -> Unit,
  testTag: String
) {
  Surface(
    onClick = onClick,
    color = CinemaSurface,
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(1.dp, CinemaBorder),
    modifier = Modifier
      .size(28.dp)
      .testTag(testTag)
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(14.dp)
      )
    }
  }
}
