package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ControlMode
import com.example.model.StreamingPortal
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaFocusGlow
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant

@Composable
fun TvTopBar(
  activePortal: StreamingPortal,
  controlMode: ControlMode,
  isBarVisible: Boolean,
  loadingProgress: Int,
  pageTitle: String,
  isBookmarked: Boolean,
  onPortalSelected: (StreamingPortal) -> Unit,
  onHomeClick: () -> Unit,
  onBackClick: () -> Unit,
  onForwardClick: () -> Unit,
  onRefreshClick: () -> Unit,
  onToggleControlMode: () -> Unit,
  onBookmarkClick: () -> Unit,
  onOpenBookmarks: () -> Unit,
  onOpenHistory: () -> Unit,
  onZoomIn: () -> Unit,
  onZoomOut: () -> Unit,
  onToggleFullscreen: () -> Unit,
  onOpenGuide: () -> Unit,
  onToggleBarVisibility: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            CinemaDarkBackground.copy(alpha = 0.98f),
            CinemaDarkBackground.copy(alpha = 0.85f),
            Color.Transparent
          )
        )
      )
  ) {
    // Mini bar toggle handle when collapsed or expanded
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (activePortal == StreamingPortal.TOP_CINEMA) CinemaPrimary else CinemaSecondary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (activePortal == StreamingPortal.TOP_CINEMA) "توب سينما • TopCinema" else "ووتش سيريز • WatchSeries",
          color = Color.White,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold
        )
        if (pageTitle.isNotBlank()) {
          Text(
            text = " — $pageTitle",
            color = Color.LightGray,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp)
          )
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        // Quick control mode indicator
        TvBadge(
          text = if (controlMode == ControlMode.VIRTUAL_MOUSE) "وضع الماوس" else "وضع الأسهم",
          color = if (controlMode == ControlMode.VIRTUAL_MOUSE) CinemaAccent else CinemaSecondary
        )
        Spacer(modifier = Modifier.width(8.dp))
        // Toggle menu button
        TvIconButton(
          icon = if (isBarVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
          label = if (isBarVisible) "إخفاء القائمة (Menu)" else "إظهار القائمة (Menu)",
          testTag = "toggle_top_bar",
          onClick = onToggleBarVisibility,
          modifier = Modifier.size(34.dp)
        )
      }
    }

    // Loading progress bar
    if (loadingProgress in 1..99) {
      LinearProgressIndicator(
        progress = { loadingProgress / 100f },
        modifier = Modifier
          .fillMaxWidth()
          .height(3.dp),
        color = CinemaAccent,
        trackColor = CinemaSurfaceVariant
      )
    }

    // Expanded TV Control Dashboard
    AnimatedVisibility(
      visible = isBarVisible,
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut()
    ) {
      Surface(
        color = CinemaSurface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 6.dp)
          .border(1.dp, CinemaSurfaceVariant, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
      ) {
        Column(modifier = Modifier.padding(10.dp)) {
          // Row 1: The Two Main Portals
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            TvPortalTab(
              portal = StreamingPortal.TOP_CINEMA,
              isSelected = activePortal == StreamingPortal.TOP_CINEMA,
              onClick = { onPortalSelected(StreamingPortal.TOP_CINEMA) },
              modifier = Modifier.weight(1f),
              testTag = "portal_topcinema_tab"
            )

            TvPortalTab(
              portal = StreamingPortal.WATCH_SERIES,
              isSelected = activePortal == StreamingPortal.WATCH_SERIES,
              onClick = { onPortalSelected(StreamingPortal.WATCH_SERIES) },
              modifier = Modifier.weight(1f),
              testTag = "portal_watchseries_tab"
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Row 2: Navigation & Player Tools
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Web navigation group
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TvActionButton(
                icon = Icons.Default.Home,
                label = "الرئيسية",
                testTag = "nav_home_button",
                onClick = onHomeClick
              )
              TvActionButton(
                icon = Icons.Default.ArrowBack,
                label = "رجوع",
                testTag = "nav_back_button",
                onClick = onBackClick
              )
              TvActionButton(
                icon = Icons.Default.ArrowForward,
                label = "تقدم",
                testTag = "nav_forward_button",
                onClick = onForwardClick
              )
              TvActionButton(
                icon = Icons.Default.Refresh,
                label = "تحديث",
                testTag = "nav_refresh_button",
                onClick = onRefreshClick
              )
            }

            // TV controls group
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              // Toggle Control Mode (D-Pad vs Virtual Mouse)
              TvActionButton(
                icon = Icons.Default.Mouse,
                label = if (controlMode == ControlMode.VIRTUAL_MOUSE) "وضع الماوس (مفعّل)" else "وضع الماوس",
                testTag = "toggle_mouse_mode",
                highlighted = controlMode == ControlMode.VIRTUAL_MOUSE,
                highlightColor = CinemaAccent,
                onClick = onToggleControlMode
              )

              // Bookmark this page
              TvActionButton(
                icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                label = if (isBookmarked) "محفوظ" else "حفظ",
                testTag = "bookmark_current_button",
                highlighted = isBookmarked,
                highlightColor = CinemaAccent,
                onClick = onBookmarkClick
              )

              // Open Saved Favorites
              TvActionButton(
                icon = Icons.Default.Bookmark,
                label = "المفضلة",
                testTag = "open_favorites_button",
                onClick = onOpenBookmarks
              )

              // Open History
              TvActionButton(
                icon = Icons.Default.History,
                label = "السجل",
                testTag = "open_history_button",
                onClick = onOpenHistory
              )

              // Zoom controls
              TvActionButton(
                icon = Icons.Default.ZoomIn,
                label = "+",
                testTag = "zoom_in_button",
                onClick = onZoomIn
              )
              TvActionButton(
                icon = Icons.Default.ZoomOut,
                label = "-",
                testTag = "zoom_out_button",
                onClick = onZoomOut
              )

              // Fullscreen Toggle
              TvActionButton(
                icon = Icons.Default.Fullscreen,
                label = "شاشة كاملة",
                testTag = "fullscreen_button",
                onClick = onToggleFullscreen
              )

              // QS285 Remote Guide
              TvActionButton(
                icon = Icons.Default.HelpOutline,
                label = "الريموت",
                testTag = "remote_guide_button",
                onClick = onOpenGuide
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun TvPortalTab(
  portal: StreamingPortal,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  testTag: String
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  val borderColor = when {
    isFocused -> CinemaFocusGlow
    isSelected -> if (portal == StreamingPortal.TOP_CINEMA) CinemaPrimary else CinemaSecondary
    else -> CinemaSurfaceVariant
  }

  val backgroundColor = when {
    isSelected -> (if (portal == StreamingPortal.TOP_CINEMA) CinemaPrimary else CinemaSecondary).copy(alpha = 0.22f)
    isFocused -> CinemaSurfaceVariant
    else -> CinemaDarkBackground.copy(alpha = 0.7f)
  }

  Box(
    modifier = modifier
      .testTag(testTag)
      .clip(RoundedCornerShape(12.dp))
      .background(backgroundColor)
      .border(
        width = if (isFocused || isSelected) 2.dp else 1.dp,
        color = borderColor,
        shape = RoundedCornerShape(12.dp)
      )
      .focusable(interactionSource = interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 10.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Start
    ) {
      Icon(
        imageVector = if (portal == StreamingPortal.TOP_CINEMA) Icons.Default.Movie else Icons.Default.Tv,
        contentDescription = portal.displayNameArabic,
        tint = if (isSelected) (if (portal == StreamingPortal.TOP_CINEMA) CinemaPrimary else CinemaSecondary) else Color.Gray,
        modifier = Modifier.size(24.dp)
      )
      Spacer(modifier = Modifier.width(10.dp))
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = portal.displayNameArabic,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "(${portal.displayNameEnglish})",
            color = Color.LightGray,
            fontSize = 12.sp
          )
        }
        Text(
          text = portal.description,
          color = Color.Gray,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
fun TvActionButton(
  icon: ImageVector,
  label: String,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  highlighted: Boolean = false,
  highlightColor: Color = CinemaPrimary
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  val bg = when {
    highlighted -> highlightColor.copy(alpha = 0.25f)
    isFocused -> CinemaFocusGlow.copy(alpha = 0.3f)
    else -> CinemaDarkBackground.copy(alpha = 0.7f)
  }

  val border = when {
    isFocused -> CinemaFocusGlow
    highlighted -> highlightColor
    else -> CinemaSurfaceVariant
  }

  Box(
    modifier = modifier
      .testTag(testTag)
      .scale(if (isFocused) 1.05f else 1.0f)
      .clip(RoundedCornerShape(8.dp))
      .background(bg)
      .border(if (isFocused || highlighted) 1.5.dp else 1.dp, border, RoundedCornerShape(8.dp))
      .focusable(interactionSource = interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (highlighted) highlightColor else if (isFocused) CinemaFocusGlow else Color.White,
        modifier = Modifier.size(16.dp)
      )
      if (label.isNotBlank()) {
        Text(
          text = label,
          color = Color.White,
          fontSize = 12.sp,
          fontWeight = if (isFocused || highlighted) FontWeight.Bold else FontWeight.Normal
        )
      }
    }
  }
}

@Composable
fun TvIconButton(
  icon: ImageVector,
  label: String,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Box(
    modifier = modifier
      .testTag(testTag)
      .clip(CircleShape)
      .background(if (isFocused) CinemaFocusGlow.copy(alpha = 0.3f) else CinemaSurfaceVariant)
      .border(if (isFocused) 1.5.dp else 0.dp, CinemaFocusGlow, CircleShape)
      .focusable(interactionSource = interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = Color.White,
      modifier = Modifier.size(18.dp)
    )
  }
}

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
      .padding(horizontal = 8.dp, vertical = 3.dp)
  ) {
    Text(
      text = text,
      color = color,
      fontSize = 11.sp,
      fontWeight = FontWeight.Medium
    )
  }
}
