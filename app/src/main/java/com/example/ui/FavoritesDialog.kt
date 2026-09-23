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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.BasicAlertDialog
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
import com.example.model.Bookmark
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
fun FavoritesDialog(
  bookmarks: List<Bookmark>,
  onSelectBookmark: (Bookmark) -> Unit,
  onRemoveBookmark: (String) -> Unit,
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
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Bookmark,
              contentDescription = null,
              tint = CinemaAccent,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "قائمة المفضلة المحفوظة",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("close_favorites_dialog")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "إغلاق",
              tint = Color.LightGray
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (bookmarks.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "لم تقم بحفظ أي فيلم أو مسلسل في المفضلة بعد.\nاضغط على زر (حفظ) في الشريط العلوي أثناء المشاهدة.",
              color = Color.Gray,
              fontSize = 14.sp,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .height(280.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(bookmarks, key = { it.id }) { item ->
              BookmarkItemCard(
                bookmark = item,
                onClick = {
                  onSelectBookmark(item)
                  onDismiss()
                },
                onDelete = { onRemoveBookmark(item.id) }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun BookmarkItemCard(
  bookmark: Bookmark,
  onClick: () -> Unit,
  onDelete: () -> Unit
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
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f)
    ) {
      Icon(
        imageVector = if (bookmark.portal == StreamingPortal.TOP_CINEMA) Icons.Default.Movie else Icons.Default.Tv,
        contentDescription = null,
        tint = if (bookmark.portal == StreamingPortal.TOP_CINEMA) CinemaPrimary else CinemaSecondary,
        modifier = Modifier.size(20.dp)
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(
          text = bookmark.title,
          color = Color.White,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = if (bookmark.portal == StreamingPortal.TOP_CINEMA) "توب سينما" else "ووتش سيريز",
          color = Color.Gray,
          fontSize = 11.sp
        )
      }
    }

    IconButton(
      onClick = onDelete,
      modifier = Modifier.size(36.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Delete,
        contentDescription = "حذف",
        tint = Color(0xFFEF4444).copy(alpha = 0.8f),
        modifier = Modifier.size(18.dp)
      )
    }
  }
}
