package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CinemaDarkBackground
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
  onSplashFinished: () -> Unit,
  modifier: Modifier = Modifier
) {
  LaunchedEffect(Unit) {
    // Ultra-fast startup without heavy GPU frame animations for older TV boxes and phones
    delay(300)
    onSplashFinished()
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(CinemaDarkBackground),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Image(
        painter = painterResource(id = R.drawable.ic_assertv_logo),
        contentDescription = "Asser TV Logo",
        contentScale = ContentScale.Fit,
        modifier = Modifier
          .size(160.dp)
          .clip(RoundedCornerShape(24.dp))
      )

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = "Asser TV",
        color = Color(0xFF38BDF8),
        fontSize = 34.sp,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily(Font(R.font.bebas_neue)),
        letterSpacing = 2.sp
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "بوابة Asser TV الذكية ومتصفح الويب السريع",
        color = Color(0xFFB3B3B3),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
      )
    }
  }
}
