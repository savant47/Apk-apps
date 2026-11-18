package com.example.ui

import android.app.Activity
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
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
  availableQualities: List<VideoQualityOption> = emptyList(),
  onSelectQualityOption: (VideoQualityOption) -> Unit = {},
  activeSubtitle: SubtitleItem? = null,
  isFullscreen: Boolean = false,
  onToggleFullscreen: () -> Unit = {},
  onOpenSubtitles: () -> Unit = {},
  onOpenDiagnostics: () -> Unit = {},
  onQualitiesDetected: (List<VideoQualityOption>) -> Unit = {},
  onFallbackToWebPlayer: () -> Unit = onDismiss
) {
  val context = LocalContext.current
  var retryAttempt by remember(videoUrl) { mutableIntStateOf(0) }
  var isControllerVisible by remember { mutableStateOf(true) }
  var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
  var actualVideoResolution by remember(videoUrl) { mutableStateOf("") }
  var showGearSettingsMenu by remember { mutableStateOf(false) }
  var currentPlaybackSpeed by remember { mutableFloatStateOf(1.0f) }

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
        paramsBuilder
          .setMaxVideoSize(Int.MAX_VALUE, targetHeight + 50)
          .setMinVideoSize(0, (targetHeight - 60).coerceAtLeast(0))
      } else {
        paramsBuilder.clearVideoSizeConstraints()
      }
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
              h >= 1080 -> "${h}p FHD"
              h >= 720 -> "${h}p HD"
              h >= 480 -> "${h}p SD"
              else -> "${h}p توفير"
            }
            options.add(VideoQualityOption(id = h.toString(), label = badge, height = h))
          }
          AppDiagnosticsLogger.log(
            "ExoPlayerTracks",
            "Detected real video tracks: ${sorted.joinToString { "${it}p" }}"
          )
          onQualitiesDetected(options)
          if (selectedQuality != "auto") {
            applyQualityToTrackSelector(selectedQuality)
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

  // Helper to hook Media3's built-in bottom-right Settings Gear button (`exo_settings`)
  // so tapping the gear icon opens our real extracted Video Qualities & Speed menu!
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
            post { hookPlayerViewGearButton(this) }
          }
        },
        update = { view ->
          if (view.player != player) {
            view.player = player
          }
          if (view.resizeMode != resizeMode) {
            view.resizeMode = resizeMode
          }
          hookPlayerViewGearButton(view)
        },
        modifier = Modifier.fillMaxSize()
      )
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

            // 2. Subtitles CC Button
            PlayerCompactSquareBtn(
              icon = Icons.Default.Subtitles,
              tint = CinemaAccent,
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
          .background(Color.Black.copy(alpha = 0.72f))
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
              .widthIn(max = 420.dp)
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
                      text = "إعدادات المشغل والجودة المستخرجة",
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

              // 2. Playback Speed Section
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

              // 3. Quick Subtitles & Aspect Ratio Row
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
                      text = "الترجمة (CC)",
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
