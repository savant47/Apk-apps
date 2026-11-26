package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TrackChanges
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
import androidx.compose.ui.graphics.graphicsLayer
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
  selectedMainTab: AppMainTab = AppMainTab.ASSER_TV,
  controlMode: ControlMode,
  currentDisplayMode: AppDisplayMode,
  onGoToBrowserHome: () -> Unit,
  onGoToAsserTvHome: () -> Unit = onGoToBrowserHome,
  onGoToTrakt: () -> Unit = {},
  onSelectPortal: (StreamingPortal) -> Unit,
  onOpenBookmarks: () -> Unit,
  onOpenHistory: () -> Unit,
  onToggleControlMode: () -> Unit,
  onOpenModeDialog: () -> Unit,
  accountDisplayName: String = "",
  onOpenAccountDialog: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 58.dp)
      .background(CinemaDarkBackground)
      .border(width = 1.dp, color = CinemaSurfaceVariant)
      .padding(vertical = 6.dp),
    contentAlignment = Alignment.CenterStart
  ) {
    // Scrollable single-line LazyRow with dedicated focusGroup for crisp TV D-Pad navigation
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .focusGroup(),
      contentPadding = PaddingValues(horizontal = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(7.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // 1. Asser Browser (Google Search) Button
      item {
        TvNavUtilityButton(
          label = "جوجل",
          icon = Icons.Default.Public,
          isActive = isHomeActive && selectedMainTab == AppMainTab.BROWSER,
          activeColor = Color(0xFF38BDF8),
          onClick = onGoToBrowserHome,
          testTag = "btn_nav_home"
        )
      }

      // 2. Asser TV Main Tab Button
      item {
        TvNavUtilityButton(
          label = "Asser TV",
          icon = Icons.Default.LiveTv,
          isActive = isHomeActive && selectedMainTab == AppMainTab.ASSER_TV,
          activeColor = CinemaPrimary,
          onClick = onGoToAsserTvHome,
          testTag = "btn_nav_asser_tv"
        )
      }

      // 2b. Trakt Tab Button
      item {
        TvNavUtilityButton(
          label = "Trakt",
          icon = Icons.Default.TrackChanges,
          isActive = isHomeActive && selectedMainTab == AppMainTab.TRAKT,
          activeColor = Color(0xFFED1C24),
          onClick = onGoToTrakt,
          testTag = "btn_nav_trakt"
        )
      }

      // 2c. Local Email/Password Account Button
      item {
        val isSignedIn = accountDisplayName.isNotBlank()
        TvNavUtilityButton(
          label = if (isSignedIn) accountDisplayName.take(12) else "حسابي",
          icon = Icons.Default.AccountCircle,
          isActive = isSignedIn,
          activeColor = Color(0xFF10B981),
          onClick = onOpenAccountDialog,
          testTag = "btn_nav_account"
        )
      }

      // 3. Direct Portal Switch Buttons
      items(StreamingPortal.values().filter { it.isEnabled }) { portal ->
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
          icon = icon,
          isSelected = !isHomeActive && activePortal == portal,
          activeColor = btnColor,
          onClick = { onSelectPortal(portal) },
          testTag = "btn_nav_${portal.id}"
        )
      }

      // 4. Secondary Utilities: Bookmarks, History, Virtual Mouse, Perspective Switcher
      item {
        TvNavUtilityButton(
          label = "المفضلة",
          icon = Icons.Default.Bookmark,
          onClick = onOpenBookmarks,
          testTag = "btn_nav_bookmarks"
        )
      }

      item {
        TvNavUtilityButton(
          label = "السجل",
          icon = Icons.Default.History,
          onClick = onOpenHistory,
          testTag = "btn_nav_history"
        )
      }

      item {
        TvNavUtilityButton(
          label = if (controlMode == ControlMode.VIRTUAL_MOUSE) "ماوس ✓" else "الماوس",
          icon = Icons.Default.Mouse,
          isActive = controlMode == ControlMode.VIRTUAL_MOUSE,
          activeColor = CinemaAccent,
          onClick = onToggleControlMode,
          testTag = "btn_nav_mouse"
        )
      }

      item {
        TvNavUtilityButton(
          label = if (currentDisplayMode == AppDisplayMode.TV) "شاشة" else "هاتف",
          icon = if (currentDisplayMode == AppDisplayMode.TV) Icons.Default.Tv else Icons.Default.Smartphone,
          isActive = true,
          activeColor = Color(0xFF38BDF8),
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
  icon: ImageVector,
  isSelected: Boolean,
  activeColor: Color,
  onClick: () -> Unit,
  testTag: String
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isFocused) 1.07f else 1f,
    animationSpec = tween(durationMillis = 100),
    label = "nav_action_scale"
  )

  Box(
    modifier = Modifier
      .heightIn(min = 44.dp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .clip(RoundedCornerShape(8.dp))
      .background(
        when {
          isFocused -> CinemaFocusGlow
          isSelected -> activeColor.copy(alpha = 0.25f)
          else -> Color.Transparent
        }
      )
      .border(
        width = if (isFocused) 2.5.dp else if (isSelected) 1.5.dp else 1.dp,
        color = if (isFocused) Color.White else if (isSelected) activeColor else CinemaSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
      )
      // Single focus target via clickable(interactionSource) — no duplicate .focusable() stop!
      .clickable(interactionSource = interactionSource, indication = null) { onClick() }
      .padding(horizontal = 12.dp, vertical = 7.dp)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = when {
          isFocused -> Color.Black
          isSelected -> activeColor
          else -> Color.LightGray
        },
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        color = when {
          isFocused -> Color.Black
          isSelected -> Color.White
          else -> Color.LightGray
        },
        fontSize = 14.sp,
        fontWeight = if (isFocused || isSelected) FontWeight.ExtraBold else FontWeight.Bold,
        maxLines = 1,
        softWrap = false
      )
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
  val scale by animateFloatAsState(
    targetValue = if (isFocused) 1.07f else 1f,
    animationSpec = tween(durationMillis = 100),
    label = "nav_utility_scale"
  )

  Box(
    modifier = Modifier
      .heightIn(min = 44.dp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .clip(RoundedCornerShape(8.dp))
      .background(
        when {
          isFocused -> CinemaFocusGlow
          isActive -> activeColor.copy(alpha = 0.25f)
          else -> Color.Transparent
        }
      )
      .border(
        width = if (isFocused) 2.5.dp else 1.dp,
        color = if (isFocused) Color.White else if (isActive) activeColor else CinemaSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
      )
      // Single focus target via clickable(interactionSource) — no duplicate .focusable() stop!
      .clickable(interactionSource = interactionSource, indication = null) { onClick() }
      .padding(horizontal = 12.dp, vertical = 7.dp)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = when {
          isFocused -> Color.Black
          isActive -> activeColor
          else -> Color.LightGray
        },
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(5.dp))
      Text(
        text = label,
        color = when {
          isFocused -> Color.Black
          isActive -> Color.White
          else -> Color.LightGray
        },
        fontSize = 14.sp,
        fontWeight = if (isFocused || isActive) FontWeight.ExtraBold else FontWeight.Bold,
        maxLines = 1,
        softWrap = false
      )
    }
  }
}
