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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.model.SubtitleItem
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
  onFallbackToWebPlayer: () -> Unit = onDismiss
) {
  val context = LocalContext.current
  val activity = context as? Activity
  var retryAttempt by remember(videoUrl) { mutableIntStateOf(0) }
  var isControllerVisible by remember { mutableStateOf(true) }
  var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
  var isLandscapeForced by remember { mutableStateOf(false) }

  val httpDataSourceFactory = remember {
    DefaultHttpDataSource.Factory()
      .setAllowCrossProtocolRedirects(true)
      .setConnectTimeoutMs(12_000)
      .setReadTimeoutMs(15_000)
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

  // Dynamically apply the user's selected quality (Auto, 240p, 360p, 480p, 720p, 1080p)
  LaunchedEffect(selectedQuality) {
    val paramsBuilder = trackSelector.buildUponParameters()
    when (selectedQuality) {
      "240" -> paramsBuilder.setMaxVideoSize(426, 240).setMaxVideoBitrate(450_000)
      "360" -> paramsBuilder.setMaxVideoSize(640, 360).setMaxVideoBitrate(850_000)
      "480" -> paramsBuilder.setMaxVideoSize(854, 480).setMaxVideoBitrate(1_500_000)
      "720" -> paramsBuilder.setMaxVideoSize(1280, 720).setMaxVideoBitrate(3_200_000)
      "1080" -> paramsBuilder.setMaxVideoSize(1920, 1080).setMaxVideoBitrate(6_800_000)
      else -> paramsBuilder.clearVideoSizeConstraints().setMaxVideoBitrate(Int.MAX_VALUE)
    }
    trackSelector.setParameters(paramsBuilder)
  }

  // Handle 403 / Source Errors gracefully: retry with alternate Referer/UA combinations before fallback
  DisposableEffect(player) {
    val listener = object : Player.Listener {
      override fun onPlayerError(error: PlaybackException) {
        if (retryAttempt < 2) {
          retryAttempt += 1
        } else {
          onFallbackToWebPlayer()
        }
      }
    }
    player.addListener(listener)
    onDispose {
      player.removeListener(listener)
      player.release()
    }
  }

  LaunchedEffect(videoUrl, refererUrl, capturedHeaders, userAgent, retryAttempt, activeSubtitle) {
    if (videoUrl.isNotBlank()) {
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

      val desktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
      val effectiveUa = when (retryAttempt) {
        0 -> capturedHeaders["User-Agent"] ?: capturedHeaders["user-agent"] ?: desktopUa
        1 -> desktopUa
        else -> userAgent.ifBlank { desktopUa }
      }

      val cookieManager = try { CookieManager.getInstance() } catch (_: Exception) { null }
      val videoCookie = try { cookieManager?.getCookie(videoUrl) ?: "" } catch (_: Exception) { "" }
      val refererCookie = try { cookieManager?.getCookie(effectiveReferer) ?: "" } catch (_: Exception) { "" }
      val combinedCookie = listOf(videoCookie, refererCookie)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString("; ")

      val headersMap = mutableMapOf(
        "Referer" to effectiveReferer,
        "Origin" to effectiveOrigin,
        "Accept" to "*/*",
        "Accept-Language" to "ar,en-US;q=0.9,en;q=0.8"
      )
      if (combinedCookie.isNotBlank()) {
        headersMap["Cookie"] = combinedCookie
      }

      httpDataSourceFactory.setUserAgent(effectiveUa)
      httpDataSourceFactory.setDefaultRequestProperties(headersMap)

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

  val qualityDisplayLabel = when (selectedQuality) {
    "240" -> "240p توفير الباقة"
    "360" -> "360p متوسط"
    "480" -> "480p SD"
    "720" -> "720p HD"
    "1080" -> "1080p Full HD"
    else -> "تلقائي (Auto HD)"
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

          // Prominent Full-Screen Button (ملء الشاشة)
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
