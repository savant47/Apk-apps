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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

  val configuration = LocalConfiguration.current
  val isNarrow = configuration.screenWidthDp < 560

  BasicAlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier
      .widthIn(max = 520.dp)
      .fillMaxWidth(0.95f)
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
          .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = "وضع العرض",
          color = Color.White,
          fontSize = 19.sp,
          fontWeight = FontWeight.ExtraBold,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (isNarrow) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            ModeOptionCard(
              title = "📱 الهاتف",
              subtitle = "واجهة رأسية للمس",
              icon = Icons.Default.PhoneAndroid,
              isCurrent = currentMode == AppDisplayMode.MOBILE,
              isSelected = selected == AppDisplayMode.MOBILE,
              activeColor = Color(0xFF0284C7),
              modifier = Modifier.fillMaxWidth(),
              onSelect = { selected = AppDisplayMode.MOBILE },
              onApplyImmediate = {
                onModeSelected(AppDisplayMode.MOBILE)
                onDismiss()
              }
            )

            ModeOptionCard(
              title = "📺 الشاشة والتابلت",
              subtitle = "واجهة عريضة للريموت",
              icon = Icons.Default.Tv,
              isCurrent = currentMode == AppDisplayMode.TV,
              isSelected = selected == AppDisplayMode.TV,
              activeColor = CinemaPrimary,
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            ModeOptionCard(
              title = "📱 الهاتف",
              subtitle = "واجهة رأسية للمس",
              icon = Icons.Default.PhoneAndroid,
              isCurrent = currentMode == AppDisplayMode.MOBILE,
              isSelected = selected == AppDisplayMode.MOBILE,
              activeColor = Color(0xFF0284C7),
              modifier = Modifier.weight(1f),
              onSelect = { selected = AppDisplayMode.MOBILE },
              onApplyImmediate = {
                onModeSelected(AppDisplayMode.MOBILE)
                onDismiss()
              }
            )

            ModeOptionCard(
              title = "📺 الشاشة والتابلت",
              subtitle = "واجهة عريضة للريموت",
              icon = Icons.Default.Tv,
              isCurrent = currentMode == AppDisplayMode.TV,
              isSelected = selected == AppDisplayMode.TV,
              activeColor = CinemaPrimary,
              modifier = Modifier.weight(1f),
              onSelect = { selected = AppDisplayMode.TV },
              onApplyImmediate = {
                onModeSelected(AppDisplayMode.TV)
                onDismiss()
              }
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .testTag("confirm_display_mode_btn")
        ) {
          Text(
            text = "تشغيل ${selected.arabicTitle}",
            fontSize = 15.5.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
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
          modifier = Modifier.size(26.dp)
        )

        if (isCurrent) {
          Surface(
            color = activeColor.copy(alpha = 0.25f),
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = "الحالي",
              color = Color.White,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
          }
        } else if (isSelected) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "محدد",
            tint = activeColor,
            modifier = Modifier.size(22.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = title,
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(5.dp))

      Text(
        text = subtitle,
        color = Color.LightGray,
        fontSize = 13.5.sp,
        textAlign = TextAlign.Center,
        lineHeight = 19.sp
      )

      Spacer(modifier = Modifier.height(12.dp))

      Button(
        onClick = onApplyImmediate,
        colors = ButtonDefaults.buttonColors(
          containerColor = if (isSelected) activeColor else CinemaSurface,
          contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 44.dp)
      ) {
        Text(
          text = if (isCurrent) "مفعّل ✓" else "تفعيل",
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
