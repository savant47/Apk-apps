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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HistoryEntry
import com.example.model.StreamingPortal
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
  onSelectEntry: (HistoryEntry) -> Unit,
  onRemoveEntry: (String) -> Unit,
  onClearHistory: () -> Unit,
  onDismiss: () -> Unit
) {
  BasicAlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.widthIn(max = 680.dp)
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
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = Icons.Default.History,
              contentDescription = null,
              tint = CinemaAccent,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "السجل",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "${history.size} عنصر",
                color = Color.LightGray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          Spacer(modifier = Modifier.width(8.dp))

          Row(verticalAlignment = Alignment.CenterVertically) {
            if (history.isNotEmpty()) {
              Button(
                onClick = onClearHistory,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                  .heightIn(min = 44.dp)
                  .testTag("clear_history_btn")
              ) {
                Icon(
                  imageVector = Icons.Default.DeleteSweep,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text("مسح الكل", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
              }
              Spacer(modifier = Modifier.width(6.dp))
            }

            IconButton(
              onClick = onDismiss,
              modifier = Modifier
                .size(42.dp)
                .testTag("close_history_btn")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "إغلاق",
                tint = Color.LightGray
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (history.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(160.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = Color.Gray.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "لا يوجد سجل بعد",
                color = Color.LightGray,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .height(340.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(history, key = { it.id }) { entry ->
              HistoryItemRow(
                entry = entry,
                onClick = { onSelectEntry(entry) },
                onRemove = { onRemoveEntry(entry.id) }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun HistoryItemRow(
  entry: HistoryEntry,
  onClick: () -> Unit,
  onRemove: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  val portalColor = if (entry.portal == StreamingPortal.TOP_CINEMA) CinemaPrimary else CinemaSecondary

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .scale(if (isFocused) 1.02f else 1.0f)
      .clip(RoundedCornerShape(10.dp))
      .background(if (isFocused) CinemaFocusGlow.copy(alpha = 0.25f) else CinemaDarkBackground)
      .border(
        width = if (isFocused) 2.dp else 1.dp,
        color = if (isFocused) CinemaFocusGlow else CinemaSurfaceVariant,
        shape = RoundedCornerShape(10.dp)
      )
      .clickable(interactionSource = interactionSource, indication = null) { onClick() }
      .focusable(interactionSource = interactionSource)
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(portalColor.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (entry.portal == StreamingPortal.TOP_CINEMA) Icons.Default.Movie else Icons.Default.LiveTv,
          contentDescription = null,
          tint = portalColor,
          modifier = Modifier.size(20.dp)
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = entry.title,
          color = Color.White,
          fontSize = 15.5.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        val subtitleText = if (entry.isSeries && entry.seasonNumber > 0 && entry.episodeNumber > 0) {
          val code = "S${entry.seasonNumber.toString().padStart(2, '0')}E${entry.episodeNumber.toString().padStart(2, '0')}"
          "الموسم ${entry.seasonNumber} • الحلقة ${entry.episodeNumber} ($code) • ${entry.portal.displayNameArabic}"
        } else if (entry.isSeries && entry.seasonNumber > 0) {
          "الموسم ${entry.seasonNumber} • ${entry.portal.displayNameArabic}"
        } else {
          entry.portal.displayNameArabic
        }
        Text(
          text = subtitleText,
          color = if (entry.isSeries && entry.seasonNumber > 0) CinemaAccent else Color.LightGray,
          fontSize = 13.5.sp,
          fontWeight = if (entry.isSeries && entry.seasonNumber > 0) FontWeight.Bold else FontWeight.Normal,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
      ) {
        Icon(
          imageVector = Icons.Default.OpenInBrowser,
          contentDescription = "فتح",
          tint = CinemaAccent,
          modifier = Modifier.size(20.dp)
        )
      }

      IconButton(
        onClick = onRemove,
        modifier = Modifier.size(40.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = "حذف",
          tint = Color.Gray,
          modifier = Modifier.size(19.dp)
        )
      }
    }
  }
}
