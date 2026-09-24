package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TvBadge(
  text: String,
  color: Color,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(color.copy(alpha = 0.2f))
      .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(
      text = text,
      color = color,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold
    )
  }
}
