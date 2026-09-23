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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.HistoryEntry
import com.example.model.StreamingPortal
import com.example.model.WatchProgress
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaFocusGlow
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDialog(
  history: List<HistoryEntry>,
  recentlyWatched: List<WatchProgress> = emptyList(),
  onResumeMedia: ((WatchProgress) -> Unit)? = null,
  onRemoveRecent: ((String) -> Unit)? = null,
  onClearRecents: (() -> Unit)? = null,
  onSelectEntry: (HistoryEntry) -> Unit,
  onClearHistory: () -> Unit,
  onDismiss: () -> Unit
) {
  var selectedTab by remember { mutableIntStateOf(if (recentlyWatched.isNotEmpty()) 0 else 1) }

  BasicAlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.widthIn(max = 740.dp)
  ) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = CinemaSurface,
      border = androidx.compose.foundation.BorderStroke(1.5.dp, CinemaSurfaceVariant)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.History,
              contentDescription = null,
              tint = CinemaSecondary,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "سجل المشاهدة والتصفح",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectedTab == 0 && recentlyWatched.isNotEmpty() && onClearRecents != null) {
              OutlinedButton(
                onClick = onClearRecents,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                modifier = Modifier.padding(end = 8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.DeleteSweep,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "مسح سجل المشاهدة", fontSize = 12.sp)
              }
            } else if (selectedTab == 1 && history.isNotEmpty()) {
              OutlinedButton(
                onClick = onClearHistory,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                modifier = Modifier.padding(end = 8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.DeleteSweep,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "مسح الروابط", fontSize = 12.sp)
              }
            }

            IconButton(
              onClick = onDismiss,
              modifier = Modifier.testTag("close_history_dialog")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "إغلاق",
                tint = Color.LightGray
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tabs
        TabRow(
          selectedTabIndex = selectedTab,
          containerColor = CinemaDarkBackground,
          contentColor = Color.White,
          indicator = { tabPositions ->
            if (selectedTab < tabPositions.size) {
              TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                color = CinemaPrimary,
                height = 3.dp
              )
            }
          }
        ) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = {
              Text(
                text = "⏱️ متابعة المشاهدة (${recentlyWatched.size})",
                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedTab == 0) CinemaAccent else Color.LightGray,
                fontSize = 13.sp
              )
            }
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = {
              Text(
                text = "🌐 سجل المواقع والصفحات (${history.size})",
                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedTab == 1) CinemaAccent else Color.LightGray,
                fontSize = 13.sp
              )
            }
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tab Content
        if (selectedTab == 0) {
          // Recently Watched Tab
          if (recentlyWatched.isEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "لا توجد أفلام أو مسلسلات قيد المتابعة حالياً.",
                color = Color.Gray,
                fontSize = 14.sp
              )
            }
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              items(recentlyWatched, key = { it.mediaId }) { item ->
                RecentWatchedRowCard(
                  item = item,
                  onResume = {
                    onResumeMedia?.invoke(item)
                    onDismiss()
                  },
                  onRemove = {
                    onRemoveRecent?.invoke(item.mediaId)
                  }
                )
              }
            }
          }
        } else {
          // Browsing History Tab
          if (history.isEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "سجل التصفح فارغ حالياً.",
                color = Color.Gray,
                fontSize = 14.sp
              )
            }
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              items(history, key = { it.id }) { item ->
                HistoryItemCard(
                  entry = item,
                  onClick = {
                    onSelectEntry(item)
                    onDismiss()
                  }
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun RecentWatchedRowCard(
  item: WatchProgress,
  onResume: () -> Unit,
  onRemove: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .scale(if (isFocused) 1.02f else 1.0f)
      .clip(RoundedCornerShape(10.dp))
      .background(if (isFocused) CinemaFocusGlow.copy(alpha = 0.22f) else CinemaDarkBackground)
      .border(
        width = if (isFocused) 1.5.dp else 1.dp,
        color = if (isFocused) CinemaFocusGlow else CinemaSurfaceVariant,
        shape = RoundedCornerShape(10.dp)
      )
      .focusable(interactionSource = interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onResume)
      .padding(10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Thumbnail
    Box(
      modifier = Modifier
        .width(60.dp)
        .height(80.dp)
        .clip(RoundedCornerShape(6.dp))
    ) {
      AsyncImage(
        model = item.posterUrl,
        contentDescription = item.arabicTitle,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    }

    Spacer(modifier = Modifier.width(12.dp))

    // Info
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = item.arabicTitle,
          color = Color.White,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (item.portal == StreamingPortal.TOP_CINEMA) CinemaPrimary else CinemaSecondary)
            .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
          Text(
            text = if (item.portal == StreamingPortal.TOP_CINEMA) "توب سينما" else "ووتش سيريز",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Text(
        text = item.title,
        color = Color.LightGray,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(6.dp))

      // Progress bar
      LinearProgressIndicator(
        progress = { item.progressFraction },
        modifier = Modifier
          .fillMaxWidth()
          .height(4.dp)
          .clip(RoundedCornerShape(2.dp)),
        color = CinemaPrimary,
        trackColor = CinemaSurfaceVariant
      )

      Spacer(modifier = Modifier.height(4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "استئناف من: ${formatTimeInRow(item.positionMs)}",
          color = CinemaAccent,
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = "${item.progressPercent}% (${formatRemainingInRow(item.remainingMs)})",
          color = Color.Gray,
          fontSize = 10.sp
        )
      }
    }

    Spacer(modifier = Modifier.width(10.dp))

    // Actions
    Row(verticalAlignment = Alignment.CenterVertically) {
      Button(
        onClick = onResume,
        colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(end = 4.dp)
      ) {
        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "استئناف", fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }

      IconButton(onClick = onRemove) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = "حذف من السجل",
          tint = Color(0xFFEF4444),
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

@Composable
fun HistoryItemCard(
  entry: HistoryEntry,
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .scale(if (isFocused) 1.02f else 1.0f)
      .clip(RoundedCornerShape(10.dp))
      .background(if (isFocused) CinemaFocusGlow.copy(alpha = 0.25f) else CinemaDarkBackground)
      .border(
        width = if (isFocused) 1.5.dp else 1.dp,
        color = if (isFocused) CinemaFocusGlow else CinemaSurfaceVariant,
        shape = RoundedCornerShape(10.dp)
      )
      .focusable(interactionSource = interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = if (entry.portal == StreamingPortal.TOP_CINEMA) Icons.Default.Movie else Icons.Default.Tv,
      contentDescription = null,
      tint = if (entry.portal == StreamingPortal.TOP_CINEMA) CinemaPrimary else CinemaSecondary,
      modifier = Modifier.size(20.dp)
    )
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = entry.title,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = entry.url,
        color = Color.Gray,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

private fun formatTimeInRow(millis: Long): String {
  val totalSeconds = (millis / 1000).coerceAtLeast(0)
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = totalSeconds % 60
  return if (hours > 0) {
    String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
  } else {
    String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
  }
}

private fun formatRemainingInRow(remainingMs: Long): String {
  val totalMinutes = (remainingMs / 60000).toInt()
  if (totalMinutes <= 0) return "مكتمل"
  val hours = totalMinutes / 60
  val minutes = totalMinutes % 60
  return if (hours > 0) "متبقي ${hours}س ${minutes}د" else "متبقي $minutes د"
}
