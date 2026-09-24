package com.example.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDisplayMode
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaFocusGlow
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayModeSelectionDialog(
  currentMode: AppDisplayMode,
  onModeSelected: (AppDisplayMode) -> Unit,
  onDismiss: () -> Unit
) {
  var selected by remember { mutableStateOf(currentMode) }

  val configuration = androidx.compose.ui.platform.LocalConfiguration.current
  val isNarrow = configuration.screenWidthDp < 500

  BasicAlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier
      .widthIn(max = 520.dp)
      .fillMaxWidth(0.94f)
      .padding(8.dp)
  ) {
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = CinemaSurface,
      border = BorderStroke(2.dp, CinemaBorder)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Dialog Title
        Text(
          text = "🎯 اختر طريقة العرض والتحكم المفضلة",
          color = Color.White,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = "التطبيق يدعم اللمس والماوس وأزرار الريموت في كلا الوضعين معاً، اختر الواجهة الأنسب لشاشتك:",
          color = Color.LightGray,
          fontSize = 12.sp,
          textAlign = TextAlign.Center,
          lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isNarrow) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            ModeOptionCard(
              title = "📱 وضع الهاتف المحمول",
              subtitle = "تحكم كامل وسلس باللمس، يدعم الوضعين الرأسي والأفقي مع شريط تنقل سفلي مناسب للهواتف والتابلت",
              icon = Icons.Default.PhoneAndroid,
              isCurrent = currentMode == AppDisplayMode.MOBILE,
              isSelected = selected == AppDisplayMode.MOBILE,
              activeColor = CinemaPrimary,
              modifier = Modifier.fillMaxWidth(),
              onSelect = { selected = AppDisplayMode.MOBILE },
              onApplyImmediate = {
                onModeSelected(AppDisplayMode.MOBILE)
                onDismiss()
              }
            )

            ModeOptionCard(
              title = "📺 وضع التلفاز والرسيفر",
              subtitle = "واجهة سينمائية أفقية 16:9، حدود تركيز بارزة لأزرار الريموت D-Pad، وضع الفأرة الافتراضية لشاشات التلفاز",
              icon = Icons.Default.Tv,
              isCurrent = currentMode == AppDisplayMode.TV,
              isSelected = selected == AppDisplayMode.TV,
              activeColor = CinemaSecondary,
              modifier = Modifier.fillMaxWidth(),
              onSelect = { selected = AppDisplayMode.TV },
              onApplyImmediate = {
                onModeSelected(AppDisplayMode.TV)
                onDismiss()
              }
            )
          }
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            ModeOptionCard(
              title = "📱 وضع الهاتف المحمول",
              subtitle = "تحكم كامل وسلس باللمس، يدعم الوضعين الرأسي والأفقي مع شريط تنقل سفلي مناسب للهواتف والتابلت",
              icon = Icons.Default.PhoneAndroid,
              isCurrent = currentMode == AppDisplayMode.MOBILE,
              isSelected = selected == AppDisplayMode.MOBILE,
              activeColor = CinemaPrimary,
              modifier = Modifier.weight(1f),
              onSelect = { selected = AppDisplayMode.MOBILE },
              onApplyImmediate = {
                onModeSelected(AppDisplayMode.MOBILE)
                onDismiss()
              }
            )

            ModeOptionCard(
              title = "📺 وضع التلفاز والرسيفر",
              subtitle = "واجهة سينمائية أفقية 16:9، حدود تركيز بارزة لأزرار الريموت D-Pad، وضع الفأرة الافتراضية لشاشات التلفاز",
              icon = Icons.Default.Tv,
              isCurrent = currentMode == AppDisplayMode.TV,
              isSelected = selected == AppDisplayMode.TV,
              activeColor = CinemaSecondary,
              modifier = Modifier.weight(1f),
              onSelect = { selected = AppDisplayMode.TV },
              onApplyImmediate = {
                onModeSelected(AppDisplayMode.TV)
                onDismiss()
              }
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Bottom Confirmation Button
        Button(
          onClick = {
            onModeSelected(selected)
            onDismiss()
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = CinemaAccent,
            contentColor = Color.Black
          ),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("confirm_display_mode_btn")
        ) {
          Text(
            text = "تأكيد واستخدام ${selected.arabicTitle}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

@Composable
private fun ModeOptionCard(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  isCurrent: Boolean,
  isSelected: Boolean,
  activeColor: Color,
  modifier: Modifier = Modifier,
  onSelect: () -> Unit,
  onApplyImmediate: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Box(
    modifier = modifier
      .scale(if (isFocused) 1.03f else 1.0f)
      .clip(RoundedCornerShape(12.dp))
      .background(if (isSelected) activeColor.copy(alpha = 0.18f) else CinemaSurfaceVariant)
      .border(
        width = if (isSelected) 2.5.dp else if (isFocused) 2.dp else 1.dp,
        color = if (isSelected) activeColor else if (isFocused) CinemaFocusGlow else CinemaBorder,
        shape = RoundedCornerShape(12.dp)
      )
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onApplyImmediate
      )
      .focusable(interactionSource = interactionSource)
      .padding(14.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (isSelected) activeColor else Color.LightGray,
          modifier = Modifier.size(28.dp)
        )

        if (isCurrent) {
          Surface(
            color = activeColor.copy(alpha = 0.25f),
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = "الوضع الحالي",
              color = activeColor,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        } else if (isSelected) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "تم الاختيار",
            tint = activeColor,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = title,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = subtitle,
        color = Color.LightGray,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        lineHeight = 15.sp
      )

      Spacer(modifier = Modifier.height(10.dp))

      Button(
        onClick = onApplyImmediate,
        colors = ButtonDefaults.buttonColors(
          containerColor = if (isSelected) activeColor else CinemaSurface,
          contentColor = if (isSelected) Color.White else Color.LightGray
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(36.dp)
      ) {
        Text(
          text = if (isCurrent) "مفعّل حالياً ✓" else "تفعيل هذا الوضع الآن ⚡",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}
