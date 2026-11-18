package com.example.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.AppDiagnosticsLogger
import com.example.model.SubtitleItem
import com.example.model.VideoQualityOption
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.ClipboxWhite

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
  activeSubtitle: SubtitleItem? = null,
  isFullscreen: Boolean = false,
  onToggleFullscreen: () -> Unit = {},
  onOpenSubtitles: () -> Unit = {},
  onOpenDiagnostics: () -> Unit = {},
  onQualitiesDetected: (List<VideoQualityOption>) -> Unit = {},
  onFallbackToWebPlayer: () -> Unit = onDismiss
) {
  val context = LocalContext.current
  val activity = context as? Activity
  var retryAttempt by remember(videoUrl) { mutableIntStateOf(0) }
  var isControllerVisible by remember { mutableStateOf(true) }
  var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
  var isLandscapeForced by remember { mutableStateOf(false) }
  var actualVideoResolution by remember(videoUrl) { mutableStateOf("") }

  val httpDataSourceFactory = remember {
    DefaultHttpDataSource.Factory()
      .setAllowCrossProtocolRedirects(true)
      .setConnectTimeoutMs(15_000)
      .setReadTimeoutMs(18_000)
  }

  val trackSelector = remember {
    DefaultTrackSelector(context)
  }

  val player = remember {
    ExoPlayer.Builder(context)
      .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
      .setTrackSelector(trackSelector)
      .build().apply {
        playWhenReady = true
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

      if (!appliedExplicitOverride) {
        when (targetHeight) {
          240 -> paramsBuilder.setMaxVideoSize(480, 260).setMinVideoSize(0, 0)
          360 -> paramsBuilder.setMaxVideoSize(680, 380).setMinVideoSize(0, 300)
          480 -> paramsBuilder.setMaxVideoSize(900, 500).setMinVideoSize(0, 400)
          720 -> paramsBuilder.setMaxVideoSize(1366, 760).setMinVideoSize(0, 640)
          1080 -> paramsBuilder.setMaxVideoSize(1980, 1120).setMinVideoSize(0, 960)
          else -> paramsBuilder.setMaxVideoSize(Int.MAX_VALUE, targetHeight + 60)
        }
      } else {
        paramsBuilder.clearVideoSizeConstraints()
      }
      AppDiagnosticsLogger.log("ExoPlayerQuality", "Applied target quality ${targetHeight}p (explicitOverride=$appliedExplicitOverride)")
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
              if (fmt.height in 144..2160) {
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
              h >= 1080 -> "${h}p FHD"
              h >= 720 -> "${h}p HD"
              h >= 480 -> "${h}p SD"
              else -> "${h}p توفير"
            }
            options.add(VideoQualityOption(id = h.toString(), label = badge, height = h))
          }
          AppDiagnosticsLogger.detectedQualitiesSummary = options.joinToString { it.label }
          AppDiagnosticsLogger.log("ExoPlayerTracks", "Detected real video tracks: ${sorted.joinToString { "${it}p" }}")
          onQualitiesDetected(options)
          if (selectedQuality != "auto") {
            applyQualityToTrackSelector(selectedQuality)
          }
        }
      }

      override fun onVideoSizeChanged(videoSize: VideoSize) {
        if (videoSize.width > 0 && videoSize.height > 0) {
          actualVideoResolution = "${videoSize.height}p"
          AppDiagnosticsLogger.actualPlayingResolution = "${videoSize.width}x${videoSize.height} (${videoSize.height}p)"
          AppDiagnosticsLogger.log("ExoPlayerVideo", "Playing resolution: ${videoSize.width}x${videoSize.height}")
        }
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
          AppDiagnosticsLogger.activePlayerMode = "Native ExoPlayer (يعمل بنجاح ✅)"
          AppDiagnosticsLogger.log("ExoPlayer", "Playback STATE_READY for $videoUrl")
        }
      }

      override fun onPlayerError(error: PlaybackException) {
        val cause = error.cause
        val httpCode = if (cause is HttpDataSource.InvalidResponseCodeException) {
          "HTTP ${cause.responseCode}"
        } else {
          error.errorCodeName
        }
        AppDiagnosticsLogger.log(
          "ExoPlayerError",
          "Attempt $retryAttempt failed ($httpCode): ${cause?.message ?: error.message} | URL=$videoUrl",
          isError = true
        )
        if (retryAttempt < 2) {
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

  DisposableEffect(Unit) {
    onDispose {
      player.release()
    }
  }

  LaunchedEffect(videoUrl, refererUrl, capturedHeaders, userAgent, retryAttempt, activeSubtitle) {
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

      val effectiveOrigin = try {
        val rUri = Uri.parse(effectiveReferer)
        if (!rUri.scheme.isNullOrBlank() && !rUri.host.isNullOrBlank()) {
          "${rUri.scheme}://${rUri.host}"
        } else {
          videoHostOrigin
        }
      } catch (_: Exception) {
        videoHostOrigin
      }

      val standardDesktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
      val effectiveUa = when (retryAttempt) {
        0 -> capturedHeaders["User-Agent"] ?: capturedHeaders["user-agent"] ?: userAgent.ifBlank { standardDesktopUa }
        1 -> standardDesktopUa
        else -> userAgent.ifBlank { standardDesktopUa }
      }

      val cookieManager = try { CookieManager.getInstance() } catch (_: Exception) { null }
      val videoCookie = try { cookieManager?.getCookie(videoUrl) ?: "" } catch (_: Exception) { "" }
      val refererCookie = try { cookieManager?.getCookie(effectiveReferer) ?: "" } catch (_: Exception) { "" }
      val combinedCookie = listOf(videoCookie, refererCookie)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString("; ")

      val headersMap = mutableMapOf<String, String>()
      // Preserve relevant headers captured from WebView
      for ((k, v) in capturedHeaders) {
        val kl = k.lowercase()
        if (kl != "host" && kl != "connection" && kl != "range" && kl != "accept-encoding" && v.isNotBlank()) {
          headersMap[k] = v
        }
      }
      headersMap["Referer"] = effectiveReferer
      headersMap["Accept"] = "*/*"
      headersMap["Accept-Language"] = "ar,en-US;q=0.9,en;q=0.8"

      // Only send Origin if it was originally in capturedHeaders or on retryAttempt == 1
      if (retryAttempt == 1 && !headersMap.containsKey("Origin") && !headersMap.containsKey("origin")) {
        headersMap["Origin"] = effectiveOrigin
      } else if (retryAttempt == 2) {
        headersMap.remove("Origin")
        headersMap.remove("origin")
      }

      if (combinedCookie.isNotBlank()) {
        headersMap["Cookie"] = combinedCookie
      }

      httpDataSourceFactory.setUserAgent(effectiveUa)
      httpDataSourceFactory.setDefaultRequestProperties(headersMap)

      AppDiagnosticsLogger.log(
        "ExoPlayerInit",
        "Loading stream (attempt $retryAttempt): $videoUrl | Referer=$effectiveReferer | UA=${effectiveUa.take(42)}..."
      )

      val currentPos = if (retryAttempt == 0) player.currentPosition.coerceAtLeast(0L) else 0L
      val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl)
      if (activeSubtitle != null) {
        val subUriStr = activeSubtitle.rawVttDataUri.ifBlank { activeSubtitle.downloadUrl }
        if (subUriStr.isNotBlank()) {
          val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUriStr))
            .setMimeType(
              if (activeSubtitle.format.equals("srt", ignoreCase = true) && !subUriStr.startsWith("data:text/vtt")) {
                MimeTypes.APPLICATION_SUBRIP
              } else {
                MimeTypes.TEXT_VTT
              }
            )
            .setLanguage(activeSubtitle.languageCode)
            .setLabel(activeSubtitle.languageName)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
          mediaItemBuilder.setSubtitleConfigurations(listOf(subConfig))
        }
      }
      player.setMediaItem(mediaItemBuilder.build(), currentPos)
      player.prepare()
      player.playWhenReady = true
    }
  }

  BackHandler {
    if (isFullscreen) {
      if (isLandscapeForced) {
        isLandscapeForced = false
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      }
      onToggleFullscreen()
    } else {
      onDismiss()
    }
  }

  val qualityDisplayLabel = buildString {
    append(
      when (selectedQuality) {
        "240" -> "240p توفير"
        "360" -> "360p متوسط"
        "480" -> "480p SD"
        "720" -> "720p HD"
        "1080" -> "1080p FHD"
        else -> "Auto تلقائي"
      }
    )
    if (actualVideoResolution.isNotBlank()) {
      append(" ($actualVideoResolution)")
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
      .onPreviewKeyEvent { keyEvent ->
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
              if (isFullscreen) {
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
          PlayerView(ctx).apply {
            this.player = player
            useController = true
            this.resizeMode = resizeMode
            controllerAutoShow = true
            controllerShowTimeoutMs = 3500
            layoutParams = FrameLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Enable the official Media3 Fullscreen button next to the Settings gear icon in the bottom-right!
            setFullscreenButtonClickListener { _ ->
              onToggleFullscreen()
            }
            setControllerVisibilityListener(
              PlayerView.ControllerVisibilityListener { visibility ->
                isControllerVisible = (visibility == View.VISIBLE)
              }
            )
          }
        },
        update = { view ->
          if (view.player != player) {
            view.player = player
          }
          if (view.resizeMode != resizeMode) {
            view.resizeMode = resizeMode
          }
        },
        modifier = Modifier.fillMaxSize()
      )
    } else {
      // Primary Player Extraction State (shown while the scraper resolves the direct .m3u8/.mp4 stream)
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        CircularProgressIndicator(
          color = CinemaPrimary,
          strokeWidth = 3.dp,
          modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
          text = "جاري سحب البث المباشر إلى المشغل الأساسي...",
          color = Color.White,
          fontSize = 14.sp,
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
        Spacer(modifier = Modifier.height(16.dp))
        Button(
          onClick = onFallbackToWebPlayer,
          colors = ButtonDefaults.buttonColors(
            containerColor = CinemaSurface,
            contentColor = Color.White
          ),
          border = BorderStroke(1.dp, CinemaBorder),
          shape = RoundedCornerShape(50),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Web,
            contentDescription = null,
            tint = CinemaAccent,
            modifier = Modifier.size(16.dp)
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

    // Top Overlay Bar (auto-hides with PlayerView controller so it never blocks the video!)
    AnimatedVisibility(
      visible = isControllerVisible || videoUrl.isBlank(),
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.TopCenter)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 8.dp)
          .background(Color(0xD9070A0E), RoundedCornerShape(12.dp))
          .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(CinemaSuccess)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = title.ifBlank { "المشغل الأساسي (Asser Player)" },
              color = Color.White,
              fontSize = 12.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
          ) {
            Icon(
              imageVector = Icons.Default.HighQuality,
              contentDescription = null,
              tint = CinemaAccent,
              modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "المشغل الأساسي • $qualityDisplayLabel",
              color = CinemaAccent,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Row(
          horizontalArrangement = Arrangement.spacedBy(5.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Aspect Ratio Fit / Fill / Zoom Toggle
          IconButton(
            onClick = {
              resizeMode = when (resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
              }
            },
            modifier = Modifier
              .size(34.dp)
              .clip(CircleShape)
              .background(CinemaSurface)
              .testTag("exo_aspect_ratio_button")
          ) {
            Icon(
              imageVector = Icons.Default.AspectRatio,
              contentDescription = "تغيير أبعاد الصورة",
              tint = Color.White,
              modifier = Modifier.size(16.dp)
            )
          }

          // Rotate Screen (Landscape / Portrait)
          IconButton(
            onClick = {
              isLandscapeForced = !isLandscapeForced
              activity?.requestedOrientation = if (isLandscapeForced) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
              } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
              }
            },
            modifier = Modifier
              .size(34.dp)
              .clip(CircleShape)
              .background(if (isLandscapeForced) ClipboxWhite else CinemaSurface)
              .testTag("exo_rotate_screen_button")
          ) {
            Icon(
              imageVector = Icons.Default.ScreenRotation,
              contentDescription = "تدوير الشاشة",
              tint = if (isLandscapeForced) Color.Black else Color.White,
              modifier = Modifier.size(16.dp)
            )
          }

          // Subtitles CC Button
          IconButton(
            onClick = onOpenSubtitles,
            modifier = Modifier
              .size(34.dp)
              .clip(CircleShape)
              .background(CinemaSurface)
              .testTag("exo_subtitles_button")
          ) {
            Icon(
              imageVector = Icons.Default.Subtitles,
              contentDescription = "الترجمة",
              tint = CinemaAccent,
              modifier = Modifier.size(16.dp)
            )
          }

          // Diagnostics & Log Button inside player
          IconButton(
            onClick = onOpenDiagnostics,
            modifier = Modifier
              .size(34.dp)
              .clip(CircleShape)
              .background(CinemaSurface)
              .testTag("exo_diagnostics_button")
          ) {
            Icon(
              imageVector = Icons.Default.BugReport,
              contentDescription = "لوج الأعطال",
              tint = CinemaAccent,
              modifier = Modifier.size(16.dp)
            )
          }

          // Prominent Full-Screen Button (ملء الشاشة للمشغل نفسه)
          Surface(
            onClick = onToggleFullscreen,
            color = if (isFullscreen) ClipboxWhite else CinemaPrimary,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .heightIn(min = 34.dp)
              .testTag("exo_fullscreen_button")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = if (isFullscreen) "تصغير الشاشة" else "ملء الشاشة",
                tint = if (isFullscreen) Color.Black else Color.White,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (isFullscreen) "تصغير" else "ملء الشاشة",
                color = if (isFullscreen) Color.Black else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
              )
            }
          }

          // Switch to Web Player if needed
          IconButton(
            onClick = onFallbackToWebPlayer,
            modifier = Modifier
              .size(34.dp)
              .clip(CircleShape)
              .background(CinemaSurface)
              .testTag("close_exo_player_button")
          ) {
            Icon(
              imageVector = Icons.Default.Web,
              contentDescription = "مشغل السيرفر البديل",
              tint = Color.LightGray,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }
    }
  }
}
