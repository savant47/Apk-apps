package com.example.ui.player

import android.media.MediaPlayer
import android.net.Uri
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.MediaItem
import com.example.model.StreamServer
import com.example.model.StreamingPortal
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaFocusGlow
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun TvMediaPlayerScreen(
  mediaItem: MediaItem,
  initialPositionMs: Long = 0L,
  onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
  onClosePlayer: () -> Unit,
  onOpenInWebPortal: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
  var isPlaying by remember { mutableStateOf(false) }
  var isBuffering by remember { mutableStateOf(true) }
  var currentPositionMs by remember { mutableLongStateOf(initialPositionMs) }
  var totalDurationMs by remember { mutableLongStateOf(0L) }
  var showControls by remember { mutableStateOf(true) }
  var resumeNoticeText by remember { mutableStateOf<String?>(null) }

  // Player Settings State
  var selectedServer by remember { mutableStateOf(mediaItem.servers.first()) }
  var selectedQuality by remember { mutableStateOf(mediaItem.availableQualities.first()) }
  var selectedSubtitle by remember { mutableStateOf(mediaItem.subtitleTracks.first()) }
  var selectedAudio by remember { mutableStateOf(mediaItem.audioTracks.first()) }
  var selectedAspectRatio by remember { mutableStateOf("16:9 ملء الشاشة") }

  // Dialog Menus State
  var activeMenu by remember { mutableStateOf<PlayerMenuType?>(null) }

  // Auto-hide controls timer
  LaunchedEffect(showControls, activeMenu) {
    if (showControls && activeMenu == null) {
      delay(6000)
      showControls = false
    }
  }

  // Auto-hide resume notification
  LaunchedEffect(resumeNoticeText) {
    if (resumeNoticeText != null) {
      delay(4500)
      resumeNoticeText = null
    }
  }

  // Periodic position updater and progress saver
  LaunchedEffect(isPlaying) {
    while (isPlaying) {
      videoViewRef?.let { vv ->
        val pos = vv.currentPosition.toLong()
        val dur = vv.duration.toLong().coerceAtLeast(0L)
        currentPositionMs = pos
        totalDurationMs = dur
        if (dur > 0L) {
          onProgressUpdate(pos, dur)
        }
      }
      delay(2500)
    }
  }

  // Save progress on exit
  DisposableEffect(Unit) {
    onDispose {
      val pos = videoViewRef?.currentPosition?.toLong() ?: currentPositionMs
      val dur = videoViewRef?.duration?.toLong() ?: totalDurationMs
      if (pos > 0L) {
        onProgressUpdate(pos, dur)
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) {
        showControls = !showControls
      }
  ) {
    // 1. Native Video Playback Surface
    AndroidView(
      factory = { ctx ->
        VideoView(ctx).apply {
          layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.Gravity.CENTER
          )

          setVideoURI(Uri.parse(selectedServer.streamUrl))

          setOnPreparedListener { mp ->
            isBuffering = false
            totalDurationMs = mp.duration.toLong()
            if (initialPositionMs > 1000L && initialPositionMs < totalDurationMs - 10000L) {
              mp.seekTo(initialPositionMs.toInt())
              currentPositionMs = initialPositionMs
              resumeNoticeText = "تم استئناف العرض من الدقيقة ${formatTime(initialPositionMs)}"
            }
            mp.start()
            isPlaying = true

            // Set scaling mode
            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
          }

          setOnInfoListener { _, what, _ ->
            when (what) {
              MediaPlayer.MEDIA_INFO_BUFFERING_START -> isBuffering = true
              MediaPlayer.MEDIA_INFO_BUFFERING_END -> isBuffering = false
            }
            true
          }

          setOnCompletionListener {
            isPlaying = false
            showControls = true
          }

          setOnErrorListener { _, _, _ ->
            isBuffering = false
            // Auto switch to backup server if available
            val nextServer = mediaItem.servers.find { it.id != selectedServer.id } ?: selectedServer
            if (nextServer.id != selectedServer.id) {
              selectedServer = nextServer
              setVideoURI(Uri.parse(nextServer.streamUrl))
              start()
            }
            true
          }

          videoViewRef = this
        }
      },
      update = { vv ->
        // When server changes
      },
      modifier = Modifier.fillMaxSize()
    )

    // 2. Simulated TV Subtitle Track Display
    if (selectedSubtitle != "معطل" && isPlaying) {
      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = if (showControls) 110.dp else 40.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(Color.Black.copy(alpha = 0.65f))
          .padding(horizontal = 16.dp, vertical = 6.dp)
      ) {
        Text(
          text = if (selectedSubtitle.contains("العربية")) "« جاري بث المحتوى بالترجمة العربية المعتمدة بجودة ${selectedQuality} »"
                 else "[ Streaming with ${selectedSubtitle} on ${selectedServer.name} ]",
          color = Color(0xFFFFEB3B), // Classic yellow TV subtitle
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center
        )
      }
    }

    // 3. Buffering Indicator
    if (isBuffering) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CinemaDarkBackground.copy(alpha = 0.85f))
            .padding(24.dp)
        ) {
          CircularProgressIndicator(color = CinemaAccent, strokeWidth = 4.dp)
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "جاري تحميل وسائط البث من ${selectedServer.name}...",
            color = Color.White,
            fontSize = 14.sp
          )
        }
      }
    }

    // 3.1 Resume Notice Banner
    AnimatedVisibility(
      visible = resumeNoticeText != null,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 70.dp)
    ) {
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(10.dp))
          .background(CinemaDarkBackground.copy(alpha = 0.92f))
          .border(1.5.dp, CinemaAccent, RoundedCornerShape(10.dp))
          .padding(horizontal = 20.dp, vertical = 10.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = CinemaAccent,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = resumeNoticeText.orEmpty(),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    // 4. On-Screen TV Remote Controls (OSD)
    AnimatedVisibility(
      visible = showControls,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.fillMaxSize()
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Black.copy(alpha = 0.85f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.95f)
              )
            )
          )
      ) {
        // Top Header Bar in Player
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerIconButton(
              icon = Icons.AutoMirrored.Filled.ArrowBack,
              label = "خروج",
              testTag = "player_back_btn",
              onClick = onClosePlayer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
              Text(
                text = "${mediaItem.arabicTitle} (${mediaItem.title})",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "المصدر: ${if (mediaItem.portal == StreamingPortal.TOP_CINEMA) "توب سينما TopCinema" else "ووتش سيريز WatchSeries"} • ${selectedServer.name} • $selectedQuality",
                color = CinemaSecondary,
                fontSize = 12.sp
              )
            }
          }

          // Open in Web Portal button
          PlayerActionButton(
            icon = Icons.Default.OpenInBrowser,
            label = "فتح في المتصفح المباشر للموقع",
            testTag = "open_in_web_btn",
            onClick = { onOpenInWebPortal(mediaItem.portalUrl) }
          )
        }

        // Bottom Controls Container
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .padding(horizontal = 28.dp, vertical = 18.dp)
        ) {
          // Progress Scrubber Bar
          val progressRatio = if (totalDurationMs > 0) (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = formatTime(currentPositionMs),
              color = Color.White,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(12.dp))
            LinearProgressIndicator(
              progress = { progressRatio },
              modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
              color = CinemaPrimary,
              trackColor = CinemaSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = formatTime(totalDurationMs),
              color = Color.LightGray,
              fontSize = 13.sp
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Control Buttons Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Playback controls (Skip -10s, Play/Pause, Skip +10s)
            Row(
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              PlayerIconButton(
                icon = Icons.Default.FastRewind,
                label = "-10 ثوانٍ",
                testTag = "player_rewind_btn",
                onClick = {
                  val newPos = (currentPositionMs - 10000).coerceAtLeast(0L)
                  videoViewRef?.seekTo(newPos.toInt())
                  currentPositionMs = newPos
                }
              )

              // Main Play/Pause (Enlarged)
              val interactionSource = remember { MutableInteractionSource() }
              val isPlayFocused by interactionSource.collectIsFocusedAsState()
              Box(
                modifier = Modifier
                  .size(54.dp)
                  .testTag("player_play_pause_btn")
                  .scale(if (isPlayFocused) 1.15f else 1.0f)
                  .clip(CircleShape)
                  .background(if (isPlayFocused) CinemaFocusGlow else CinemaPrimary)
                  .border(2.dp, if (isPlayFocused) Color.White else CinemaAccent, CircleShape)
                  .focusable(interactionSource = interactionSource)
                  .clickable(interactionSource = interactionSource, indication = null) {
                    if (isPlaying) {
                      videoViewRef?.pause()
                      isPlaying = false
                    } else {
                      videoViewRef?.start()
                      isPlaying = true
                    }
                  },
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                  contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                  tint = Color.White,
                  modifier = Modifier.size(32.dp)
                )
              }

              PlayerIconButton(
                icon = Icons.Default.FastForward,
                label = "+10 ثوانٍ",
                testTag = "player_forward_btn",
                onClick = {
                  val newPos = (currentPositionMs + 10000).coerceAtMost(totalDurationMs)
                  videoViewRef?.seekTo(newPos.toInt())
                  currentPositionMs = newPos
                }
              )
            }

            // Stream Customization Tools (Quality, Subtitles, Audio, Servers, Aspect Ratio)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              // Quality
              PlayerActionButton(
                icon = Icons.Default.HighQuality,
                label = "الجودة: $selectedQuality",
                testTag = "player_quality_btn",
                onClick = { activeMenu = PlayerMenuType.QUALITY }
              )

              // Subtitles
              PlayerActionButton(
                icon = Icons.Default.ClosedCaption,
                label = "الترجمة: $selectedSubtitle",
                testTag = "player_subtitles_btn",
                onClick = { activeMenu = PlayerMenuType.SUBTITLES }
              )

              // Audio Tracks
              PlayerActionButton(
                icon = Icons.Default.Audiotrack,
                label = "الصوت: $selectedAudio",
                testTag = "player_audio_btn",
                onClick = { activeMenu = PlayerMenuType.AUDIO }
              )

              // Servers
              PlayerActionButton(
                icon = Icons.Default.Dns,
                label = "السيرفرات (${selectedServer.name.take(10)}...)",
                testTag = "player_servers_btn",
                onClick = { activeMenu = PlayerMenuType.SERVERS }
              )

              // Aspect Ratio
              PlayerActionButton(
                icon = Icons.Default.AspectRatio,
                label = selectedAspectRatio,
                testTag = "player_aspect_ratio_btn",
                onClick = {
                  selectedAspectRatio = if (selectedAspectRatio.contains("ملء")) "الأبعاد الأصلية (Fit)" else "16:9 ملء الشاشة"
                }
              )
            }
          }
        }
      }
    }

    // 5. Options Overlay Sheet (Quality / Subtitles / Audio / Servers)
    activeMenu?.let { menuType ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.75f))
          .clickable { activeMenu = null },
        contentAlignment = Alignment.Center
      ) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = CinemaSurface,
          border = androidx.compose.foundation.BorderStroke(1.5.dp, CinemaSurfaceVariant),
          modifier = Modifier
            .width(420.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            Text(
              text = when (menuType) {
                PlayerMenuType.QUALITY -> "اختيار جودة البث (Streaming Quality)"
                PlayerMenuType.SUBTITLES -> "خيارات الترجمة (Subtitles)"
                PlayerMenuType.AUDIO -> "مسارات الصوت (Audio Tracks)"
                PlayerMenuType.SERVERS -> "سيرفرات البث البديلة (Streaming Servers)"
              },
              color = Color.White,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))

            when (menuType) {
              PlayerMenuType.QUALITY -> {
                mediaItem.availableQualities.forEach { q ->
                  PlayerOptionItem(
                    label = q,
                    isSelected = q == selectedQuality,
                    onClick = {
                      selectedQuality = q
                      activeMenu = null
                    }
                  )
                }
              }
              PlayerMenuType.SUBTITLES -> {
                mediaItem.subtitleTracks.forEach { sub ->
                  PlayerOptionItem(
                    label = sub,
                    isSelected = sub == selectedSubtitle,
                    onClick = {
                      selectedSubtitle = sub
                      activeMenu = null
                    }
                  )
                }
              }
              PlayerMenuType.AUDIO -> {
                mediaItem.audioTracks.forEach { audio ->
                  PlayerOptionItem(
                    label = audio,
                    isSelected = audio == selectedAudio,
                    onClick = {
                      selectedAudio = audio
                      activeMenu = null
                    }
                  )
                }
              }
              PlayerMenuType.SERVERS -> {
                mediaItem.servers.forEach { srv ->
                  PlayerOptionItem(
                    label = "${srv.name} • ${srv.quality}",
                    isSelected = srv.id == selectedServer.id,
                    onClick = {
                      selectedServer = srv
                      isBuffering = true
                      videoViewRef?.setVideoURI(Uri.parse(srv.streamUrl))
                      videoViewRef?.start()
                      activeMenu = null
                    }
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

enum class PlayerMenuType {
  QUALITY,
  SUBTITLES,
  AUDIO,
  SERVERS
}

@Composable
fun PlayerIconButton(
  icon: ImageVector,
  label: String,
  testTag: String,
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Box(
    modifier = Modifier
      .size(44.dp)
      .testTag(testTag)
      .scale(if (isFocused) 1.15f else 1.0f)
      .clip(CircleShape)
      .background(if (isFocused) CinemaFocusGlow.copy(alpha = 0.35f) else CinemaSurfaceVariant)
      .border(if (isFocused) 2.dp else 1.dp, if (isFocused) CinemaFocusGlow else CinemaSurfaceVariant, CircleShape)
      .focusable(interactionSource = interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = Color.White,
      modifier = Modifier.size(22.dp)
    )
  }
}

@Composable
fun PlayerActionButton(
  icon: ImageVector,
  label: String,
  testTag: String,
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Box(
    modifier = Modifier
      .testTag(testTag)
      .scale(if (isFocused) 1.08f else 1.0f)
      .clip(RoundedCornerShape(8.dp))
      .background(if (isFocused) CinemaFocusGlow.copy(alpha = 0.3f) else CinemaDarkBackground.copy(alpha = 0.85f))
      .border(if (isFocused) 2.dp else 1.dp, if (isFocused) CinemaFocusGlow else CinemaSurfaceVariant, RoundedCornerShape(8.dp))
      .focusable(interactionSource = interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 8.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isFocused) CinemaFocusGlow else CinemaAccent,
        modifier = Modifier.size(16.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
      )
    }
  }
}

@Composable
fun PlayerOptionItem(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .scale(if (isFocused) 1.03f else 1.0f)
      .clip(RoundedCornerShape(8.dp))
      .background(if (isSelected) CinemaPrimary.copy(alpha = 0.25f) else if (isFocused) CinemaFocusGlow.copy(alpha = 0.25f) else CinemaDarkBackground)
      .border(if (isSelected) 1.5.dp else if (isFocused) 1.5.dp else 1.dp, if (isSelected) CinemaPrimary else if (isFocused) CinemaFocusGlow else CinemaSurfaceVariant, RoundedCornerShape(8.dp))
      .focusable(interactionSource = interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = label,
        color = if (isSelected) CinemaAccent else Color.White,
        fontSize = 13.sp,
        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
      )
      if (isSelected) {
        Text(text = "✓", color = CinemaAccent, fontWeight = FontWeight.Bold)
      }
    }
  }
}

private fun formatTime(millis: Long): String {
  val totalSeconds = (millis / 1000).coerceAtLeast(0)
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = totalSeconds % 60
  return if (hours > 0) {
    String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
  } else {
    String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
  }
}
