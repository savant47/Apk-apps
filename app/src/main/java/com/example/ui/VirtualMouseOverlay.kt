package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ControlMode
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaSecondary
import kotlin.math.roundToInt

@Composable
fun VirtualMouseOverlay(
  controlMode: ControlMode,
  cursorX: Float,
  cursorY: Float,
  modifier: Modifier = Modifier
) {
  if (controlMode != ControlMode.VIRTUAL_MOUSE) return

  // Small pointer canvas only covering its own 36x36dp size
  Canvas(
    modifier = modifier
      .size(36.dp)
      .offset { IntOffset(cursorX.roundToInt(), cursorY.roundToInt()) }
  ) {
    // Glowing target halo around pointer
    drawCircle(
      color = Color(0x3338BDF8),
      radius = 16.dp.toPx(),
      center = Offset(4.dp.toPx(), 4.dp.toPx())
    )

    // Arrow cursor path
    val path = Path().apply {
      moveTo(0f, 0f)
      lineTo(22.dp.toPx(), 14.dp.toPx())
      lineTo(13.dp.toPx(), 14.dp.toPx())
      lineTo(18.dp.toPx(), 26.dp.toPx())
      lineTo(13.dp.toPx(), 28.dp.toPx())
      lineTo(8.dp.toPx(), 16.dp.toPx())
      lineTo(0f, 22.dp.toPx())
      close()
    }

    // Dark drop shadow
    drawPath(path = path, color = Color(0xCC000000), style = Stroke(width = 3.dp.toPx()))

    // Bright electric cyan pointer body
    drawPath(path = path, color = Color(0xFF38BDF8))

    // White inner highlight
    val innerPath = Path().apply {
      moveTo(2.dp.toPx(), 2.dp.toPx())
      lineTo(16.dp.toPx(), 11.dp.toPx())
      lineTo(10.dp.toPx(), 11.dp.toPx())
      lineTo(13.dp.toPx(), 19.dp.toPx())
      close()
    }
    drawPath(path = innerPath, color = Color.White)

    // Active center indicator
    drawCircle(
      color = Color(0xFFF43F5E),
      radius = 2.5.dp.toPx(),
      center = Offset(2.dp.toPx(), 2.dp.toPx())
    )
  }
}
