package com.example.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaSecondary

@Composable
fun NativeTvExoPlayer(
  videoUrl: String,
  title: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  refererUrl: String = "https://topcinema.io"
) {
  val context = LocalContext.current

  // 1. Configure ExoPlayer with strict 480p Max Quality & 1Mbps Bitrate parameters to save internet quota
  // Plus browser Headers to completely bypass anti-hotlinking CDN 403 blocks
  val player = remember(videoUrl, refererUrl) {
    val originUrl = try {
      val uri = android.net.Uri.parse(refererUrl)
      "${uri.scheme}://${uri.host}"
    } catch (e: Exception) {
      "https://topcinema.io"
    }

    // Set standard browser headers so CDNs treat us exactly like a normal Web browser
    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
      .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
      .setDefaultRequestProperties(
        mapOf(
          "Referer" to refererUrl,
          "Origin" to originUrl,
          "Accept" to "*/*",
          "Accept-Language" to "ar,en-US;q=0.9,en;q=0.8"
        )
      )

    val trackSelector = DefaultTrackSelector(context).apply {
      setParameters(
        buildUponParameters()
          .setMaxVideoSize(854, 480) // Restrict resolution to 480p maximum
          .setMaxVideoBitrate(1_000_000) // 1 Mbps max bitrate limit to save package quota
      )
    }

    ExoPlayer.Builder(context)
      .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
      .setTrackSelector(trackSelector)
      .build().apply {
        playWhenReady = true
      }
  }

  // 2. Load the video stream URL
  LaunchedEffect(videoUrl) {
    val mediaItem = MediaItem.fromUri(videoUrl)
    player.setMediaItem(mediaItem)
    player.prepare()
  }

  // Dispose player properly when leaving
  DisposableEffect(Unit) {
    onDispose {
      player.release()
    }
  }

  // Catch back button to close the player
  BackHandler {
    onDismiss()
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
      .onPreviewKeyEvent { keyEvent ->
        // Handle TV remote keys for pausing, resuming and seeking
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
              onDismiss()
              true
            } else false
          }
          else -> false
        }
      }
      .focusable()
  ) {
    // 3. Render Android Media3 PlayerView
    AndroidView(
      factory = { ctx ->
        PlayerView(ctx).apply {
          this.player = player
          useController = true
          layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
        }
      },
      modifier = Modifier.fillMaxSize()
    )

    // 4. Data-Saver Overlay Header Badge (Arabic indicators)
    Column(
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(16.dp)
        .background(Color(0xCC000000), RoundedCornerShape(8.dp))
        .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
      Text(
        text = title.ifBlank { "جاري تشغيل الفيديو..." },
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
      ) {
        Icon(
          imageVector = Icons.Default.NetworkCheck,
          contentDescription = "Data Saver",
          tint = CinemaAccent,
          modifier = Modifier.padding(end = 4.dp)
        )
        Text(
          text = "نمط توفير الباقة: تم تقييد الجودة لـ 480p كحد أقصى تلقائياً",
          color = CinemaAccent,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium
        )
      }
    }

    // 5. Close Button
    Button(
      onClick = onDismiss,
      colors = ButtonDefaults.buttonColors(containerColor = Color(0x99FF0000)),
      shape = RoundedCornerShape(8.dp),
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(16.dp)
        .testTag("close_exo_player_button")
    ) {
      Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "إغلاق المشغل",
        tint = Color.White
      )
      Text(
        text = "إغلاق",
        color = Color.White,
        modifier = Modifier.padding(start = 4.dp)
      )
    }
  }
}
