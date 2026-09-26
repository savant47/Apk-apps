package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
  // Animatable states for elegant scaling and fading in/out
  val scale = remember { Animatable(0.8f) }
  val alpha = remember { Animatable(0f) }

  LaunchedEffect(key1 = true) {
    // Elegant parallel fade and scale animation
    scale.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 1000)
    )
  }

  LaunchedEffect(key1 = true) {
    alpha.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 800)
    )
    // Delay for 2.5 seconds total as requested
    delay(2500)
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
      // Display the beautiful logo with a rounded clip
      Image(
        painter = painterResource(id = R.drawable.ic_assertv_logo),
        contentDescription = "AsserTV Logo",
        contentScale = ContentScale.Fit,
        modifier = Modifier
          .size(240.dp)
          .scale(scale.value)
          .alpha(alpha.value)
          .clip(RoundedCornerShape(24.dp))
      )

      Spacer(modifier = Modifier.height(18.dp))

      // Elegant Subtitle / App name text below in Netflix style
      Text(
        text = "AsserTV",
        color = Color(0xFFE50914),
        fontSize = 36.sp,
        fontWeight = FontWeight.Black,
        fontFamily = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.bebas_neue)),
        letterSpacing = 2.sp,
        modifier = Modifier.alpha(alpha.value)
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "بوابة المشاهدة السينمائية الذكية",
        color = Color(0xFFB3B3B3),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.alpha(alpha.value)
      )
    }
  }
}
