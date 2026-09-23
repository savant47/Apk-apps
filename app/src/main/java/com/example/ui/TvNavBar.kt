package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaFocusGlow
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant

enum class TvNavDestination(val label: String, val icon: ImageVector) {
  HOME("الرئيسية", Icons.Default.Home),
  TOP_CINEMA("أفلام توب سينما", Icons.Default.Movie),
  WATCH_SERIES("مسلسلات ووتش سيريز", Icons.Default.LiveTv),
  SEARCH("البحث والتصنيف", Icons.Default.Search),
  DIRECT_WEB("المتصفح المباشر للموقعين", Icons.Default.Language)
}

@Composable
fun TvNavBar(
  currentDestination: TvNavDestination,
  onNavigate: (TvNavDestination) -> Unit,
  onOpenBookmarks: () -> Unit,
  onOpenHistory: () -> Unit,
  onOpenRemoteGuide: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(60.dp)
      .background(CinemaDarkBackground)
      .border(width = 1.dp, color = CinemaSurfaceVariant)
      .padding(horizontal = 24.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // App Brand & Navigation Tabs
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // App Logo Badge
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CinemaPrimary.copy(alpha = 0.25f))
            .border(1.dp, CinemaPrimary, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(text = "CINEMA", color = CinemaPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = "TV", color = CinemaAccent, fontWeight = FontWeight.Black, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Navigation Destinations
        TvNavDestination.entries.forEach { destination ->
          TvNavTabButton(
            destination = destination,
            isSelected = currentDestination == destination,
            onClick = { onNavigate(destination) }
          )
        }
      }

      // Quick Tools (Bookmarks, History, Remote Guide)
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        TvHeaderToolButton(
          icon = Icons.Default.Bookmark,
          label = "المفضلة",
          testTag = "nav_bookmarks_btn",
          onClick = onOpenBookmarks
        )

        TvHeaderToolButton(
          icon = Icons.Default.History,
          label = "السجل",
          testTag = "nav_history_btn",
          onClick = onOpenHistory
        )

        TvHeaderToolButton(
          icon = Icons.Default.SettingsRemote,
          label = "الريموت QS285",
          testTag = "nav_remote_guide_btn",
          activeColor = CinemaSecondary,
          onClick = onOpenRemoteGuide
        )
      }
    }
  }
}

@Composable
fun TvNavTabButton(
  destination: TvNavDestination,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Box(
    modifier = Modifier
      .testTag("nav_tab_${destination.name}")
      .scale(if (isFocused) 1.08f else 1.0f)
      .clip(RoundedCornerShape(8.dp))
      .background(
        if (isSelected) CinemaPrimary.copy(alpha = 0.35f)
        else if (isFocused) CinemaFocusGlow.copy(alpha = 0.3f)
        else Color.Transparent
      )
      .border(
        width = if (isSelected || isFocused) 1.5.dp else 0.dp,
        color = if (isFocused) CinemaFocusGlow else if (isSelected) CinemaPrimary else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
      )
      .focusable(interactionSource = interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 7.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = destination.icon,
        contentDescription = destination.label,
        tint = if (isFocused) CinemaFocusGlow else if (isSelected) CinemaAccent else Color.LightGray,
        modifier = Modifier.size(16.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = destination.label,
        color = if (isFocused) CinemaFocusGlow else if (isSelected) Color.White else Color(0xFFD1D5DB),
        fontSize = 12.sp,
        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
      )
    }
  }
}

@Composable
fun TvHeaderToolButton(
  icon: ImageVector,
  label: String,
  testTag: String,
  onClick: () -> Unit,
  activeColor: Color = CinemaAccent
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Box(
    modifier = Modifier
      .testTag(testTag)
      .scale(if (isFocused) 1.08f else 1.0f)
      .clip(RoundedCornerShape(8.dp))
      .background(if (isFocused) CinemaFocusGlow.copy(alpha = 0.25f) else CinemaSurface)
      .border(
        width = if (isFocused) 1.5.dp else 1.dp,
        color = if (isFocused) CinemaFocusGlow else CinemaBorder,
        shape = RoundedCornerShape(8.dp)
      )
      .focusable(interactionSource = interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isFocused) CinemaFocusGlow else activeColor,
        modifier = Modifier.size(15.dp)
      )
      Spacer(modifier = Modifier.width(5.dp))
      Text(
        text = label,
        color = if (isFocused) CinemaFocusGlow else Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold
      )
    }
  }
}
