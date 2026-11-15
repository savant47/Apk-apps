package com.example.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDisplayMode
import com.example.model.AppMainTab
import com.example.model.ControlMode
import com.example.model.StreamingPortal
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaFocusGlow
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSurfaceVariant

@Composable
fun TvNavBar(
  activePortal: StreamingPortal,
  isHomeActive: Boolean = false,
  selectedMainTab: AppMainTab = AppMainTab.BROWSER,
  controlMode: ControlMode,
  currentDisplayMode: AppDisplayMode,
  onGoToBrowserHome: () -> Unit,
  onGoToAsserTvHome: () -> Unit = onGoToBrowserHome,
  onSelectPortal: (StreamingPortal) -> Unit,
  onOpenBookmarks: () -> Unit,
  onOpenHistory: () -> Unit,
  onToggleControlMode: () -> Unit,
  onOpenModeDialog: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(54.dp)
      .background(CinemaDarkBackground)
      .border(width = 1.dp, color = CinemaSurfaceVariant)
      .padding(horizontal = 8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Main Tab Switchers (Asser Browser & Asser TV) + Direct Portal Switchers
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // 1. Asser Browser (Google Search) Button
        TvNavUtilityButton(
          label = "بحث جوجل",
          icon = Icons.Default.Public,
          isActive = isHomeActive && selectedMainTab == AppMainTab.BROWSER,
          activeColor = Color(0xFF38BDF8),
          onClick = onGoToBrowserHome,
          testTag = "btn_nav_home"
        )

        // 2. Asser TV Main Tab Button
        TvNavUtilityButton(
          label = "Asser TV",
          icon = Icons.Default.LiveTv,
          isActive = isHomeActive && selectedMainTab == AppMainTab.ASSER_TV,
          activeColor = CinemaPrimary,
          onClick = onGoToAsserTvHome,
          testTag = "btn_nav_asser_tv"
        )

        // 3. Direct Portal Switch Buttons
        StreamingPortal.values().filter { it.isEnabled }.forEach { portal ->
          val btnColor = when (portal) {
            StreamingPortal.TOP_CINEMA -> CinemaPrimary
            StreamingPortal.WATCH_SERIES -> CinemaSecondary
            StreamingPortal.LAROZA -> CinemaAccent
            StreamingPortal.EGY_DEAD -> Color(0xFF4CAF50)
            StreamingPortal.FLIXER -> Color(0xFF9C27B0)
            else -> CinemaSurfaceVariant
          }
          val icon = when (portal) {
            StreamingPortal.TOP_CINEMA -> Icons.Default.Movie
            StreamingPortal.WATCH_SERIES -> Icons.Default.LiveTv
            StreamingPortal.EGY_DEAD -> Icons.Default.Tv
            else -> Icons.Default.Tv
          }

          TvNavActionButton(
            label = portal.displayNameArabic,
            subLabel = portal.displayNameEnglish,
            icon = icon,
            isSelected = !isHomeActive && activePortal == portal,
            activeColor = btnColor,
            onClick = { onSelectPortal(portal) },
            testTag = "btn_nav_${portal.id}"
          )
        }
      }

      // Secondary Utilities: Bookmarks, History, Virtual Mouse, Mode
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        TvNavUtilityButton(
          label = "المفضلة",
          icon = Icons.Default.Bookmark,
          onClick = onOpenBookmarks,
          testTag = "btn_nav_bookmarks"
        )

        TvNavUtilityButton(
          label = "السجل",
          icon = Icons.Default.History,
          onClick = onOpenHistory,
          testTag = "btn_nav_history"
        )

        TvNavUtilityButton(
          label = if (controlMode == ControlMode.VIRTUAL_MOUSE) "ماوس: نشط" else "الماوس",
          icon = Icons.Default.Mouse,
          isActive = controlMode == ControlMode.VIRTUAL_MOUSE,
          activeColor = CinemaAccent,
          onClick = onToggleControlMode,
          testTag = "btn_nav_mouse"
        )

        TvNavUtilityButton(
          label = if (currentDisplayMode == AppDisplayMode.TV) "وضع TV" else "وضع هاتف",
          icon = Icons.Default.Tv,
          onClick = onOpenModeDialog,
          testTag = "btn_nav_mode"
        )
      }
    }
  }
}

@Composable
private fun TvNavActionButton(
  label: String,
  subLabel: String,
  icon: ImageVector,
  isSelected: Boolean,
  activeColor: Color,
  onClick: () -> Unit,
  testTag: String
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(
        when {
          isSelected -> activeColor.copy(alpha = 0.25f)
          isFocused -> CinemaFocusGlow.copy(alpha = 0.2f)
          else -> Color.Transparent
        }
      )
      .border(
        width = if (isFocused) 2.dp else if (isSelected) 1.5.dp else 1.dp,
        color = if (isFocused) CinemaFocusGlow else if (isSelected) activeColor else CinemaSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
      )
      .clickable(interactionSource = interactionSource, indication = null) { onClick() }
      .focusable(interactionSource = interactionSource)
      .padding(horizontal = 10.dp, vertical = 6.dp)
      .testTag(testTag)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isSelected) activeColor else Color.LightGray,
        modifier = Modifier.size(16.dp)
      )
      Spacer(modifier = Modifier.width(5.dp))
      Column {
        Text(
          text = label,
          color = if (isSelected) Color.White else Color.LightGray,
          fontSize = 11.sp,
          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
      }
    }
  }
}

@Composable
private fun TvNavUtilityButton(
  label: String,
  icon: ImageVector,
  isActive: Boolean = false,
  activeColor: Color = CinemaAccent,
  onClick: () -> Unit,
  testTag: String
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(
        if (isActive) activeColor.copy(alpha = 0.25f)
        else if (isFocused) CinemaFocusGlow.copy(alpha = 0.2f)
        else Color.Transparent
      )
      .border(
        width = if (isFocused) 2.dp else 1.dp,
        color = if (isFocused) CinemaFocusGlow else if (isActive) activeColor else CinemaSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
      )
      .clickable(interactionSource = interactionSource, indication = null) { onClick() }
      .focusable(interactionSource = interactionSource)
      .padding(horizontal = 8.dp, vertical = 6.dp)
      .testTag(testTag)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isActive) activeColor else if (isFocused) CinemaFocusGlow else Color.LightGray,
        modifier = Modifier.size(16.dp)
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = label,
        color = if (isActive) activeColor else if (isFocused) CinemaFocusGlow else Color.LightGray,
        fontSize = 11.sp,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
      )
    }
  }
}
