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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StreamingPortal
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
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
  currentUrl: String,
  isBarVisible: Boolean,
  loadingProgress: Int,
  pageTitle: String,
  isBookmarked: Boolean,
  onGoToHome: () -> Unit,
  onOpenSubtitles: () -> Unit,
  onSearchDirect: (portal: StreamingPortal, query: String) -> Unit,
  onPortalSelected: (StreamingPortal) -> Unit,
  onHomeClick: () -> Unit,
  onBackClick: () -> Unit,
  onForwardClick: () -> Unit,
  onRefreshClick: () -> Unit,
  onBookmarkClick: () -> Unit,
  onToggleFullscreen: () -> Unit,
  onOpenGuide: () -> Unit,
  onOpenAdBlockSettings: () -> Unit = {},
  onOpenQuality: () -> Unit = {},
  onToggleBarVisibility: () -> Unit,
  modifier: Modifier = Modifier
) {
  var searchQuery by remember { mutableStateOf("") }
  var isSearchExpanded by remember { mutableStateOf(false) }

  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current

  val quickSuggestions = listOf(
    "Silo",
    "Z Nation",
    "The Walking Dead",
    "Breaking Bad",
    "Game of Thrones",
    "Peaky Blinders",
    "From",
    "Severance",
    "الحشاشين"
  )

  fun executeSearch(portal: StreamingPortal) {
    val q = searchQuery.trim().ifBlank { "Silo" }
    keyboardController?.hide()
    focusManager.clearFocus()
    onSearchDirect(portal, q)
  }

  Column(modifier = modifier.fillMaxWidth()) {
    AnimatedVisibility(
      visible = isBarVisible,
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut()
    ) {
      Surface(
        color = CinemaDarkBackground.copy(alpha = 0.95f),
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceVariant)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
          // Row 1: Browser Controls, Live Search Bar, and Portal Tabs
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Left: Home Dashboard, Back, Forward, Reload, Home Portal
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              TvTopBarIconButton(
                icon = Icons.Default.Tv,
                contentDescription = "الواجهة الرئيسية (بحث ومتابعة)",
                tint = CinemaAccent,
                onClick = onGoToHome,
                testTag = "btn_topbar_home_dashboard"
              )
              TvTopBarIconButton(
                icon = Icons.Default.ArrowBack,
                contentDescription = "للخلف",
                onClick = onBackClick,
                testTag = "btn_topbar_back"
              )
              TvTopBarIconButton(
                icon = Icons.Default.ArrowForward,
                contentDescription = "للأمام",
                onClick = onForwardClick,
                testTag = "btn_topbar_forward"
              )
              TvTopBarIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = "تحديث",
                onClick = onRefreshClick,
                testTag = "btn_topbar_refresh"
              )
            }

            // Center: Live Search Field
            Row(
              modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                  Text(
                    text = "ابحث عن أي مسلسل أو فيلم (مثل Silo أو Z Nation)...",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1
                  )
                },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "بحث",
                    tint = CinemaAccent,
                    modifier = Modifier.size(18.dp)
                  )
                },
                trailingIcon = {
                  if (searchQuery.isNotEmpty()) {
                    IconButton(
                      onClick = { searchQuery = "" },
                      modifier = Modifier.size(22.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "مسح",
                        tint = Color.LightGray,
                        modifier = Modifier.size(14.dp)
                      )
                    }
                  }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                  onSearch = { executeSearch(activePortal) }
                ),
                colors = TextFieldDefaults.colors(
                  focusedContainerColor = CinemaSurface,
                  unfocusedContainerColor = CinemaSurface,
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White,
                  focusedIndicatorColor = CinemaAccent,
                  unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                  .weight(1f)
                  .height(44.dp)
                  .border(1.dp, CinemaBorder, RoundedCornerShape(8.dp))
                  .testTag("topbar_search_input")
                  .clickable { keyboardController?.show() }
              )

              // Render direct search buttons dynamically for all enabled portals!
              StreamingPortal.values().filter { it.isEnabled }.forEach { portal ->
                val btnColor = when (portal) {
                  StreamingPortal.TOP_CINEMA -> CinemaPrimary
                  StreamingPortal.WATCH_SERIES -> CinemaSecondary
                  StreamingPortal.LAROZA -> CinemaAccent
                  StreamingPortal.EGY_DEAD -> Color(0xFF4CAF50)
                  else -> CinemaSurfaceVariant
                }
                val textColor = if (portal == StreamingPortal.LAROZA) Color.Black else Color.White
                val icon = when (portal) {
                  StreamingPortal.TOP_CINEMA -> Icons.Default.Movie
                  StreamingPortal.WATCH_SERIES -> Icons.Default.LiveTv
                  StreamingPortal.EGY_DEAD -> Icons.Default.Tv
                  else -> Icons.Default.HighQuality
                }

                Button(
                  onClick = { executeSearch(portal) },
                  colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                  shape = RoundedCornerShape(6.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                  modifier = Modifier
                    .height(38.dp)
                    .testTag("topbar_btn_search_${portal.id}")
                ) {
                  Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = textColor)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(portal.displayNameArabic, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                }
              }
            }

            // Right: AdBlock Shield, Subtitles, Bookmark, Guide, Fullscreen
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              TvTopBarIconButton(
                icon = Icons.Default.Shield,
                contentDescription = "مانع الإعلانات و AdGuard DNS",
                tint = CinemaSuccess,
                onClick = onOpenAdBlockSettings,
                testTag = "btn_topbar_adblock"
              )
              TvTopBarIconButton(
                icon = Icons.Default.Subtitles,
                contentDescription = "الترجمة والكابشنز OpenSubtitles",
                tint = CinemaAccent,
                onClick = onOpenSubtitles,
                testTag = "btn_topbar_subtitles"
              )
              TvTopBarIconButton(
                icon = Icons.Default.HighQuality,
                contentDescription = "اختيار جودة الفيديو (Quality)",
                tint = Color(0xFF38BDF8),
                onClick = onOpenQuality,
                testTag = "btn_topbar_quality"
              )

              TvTopBarIconButton(
                icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = "إضافة للمفضلة",
                tint = if (isBookmarked) CinemaAccent else Color.LightGray,
                onClick = onBookmarkClick,
                testTag = "btn_topbar_bookmark"
              )
              TvTopBarIconButton(
                icon = Icons.Default.HelpOutline,
                contentDescription = "دليل الاستخدام",
                onClick = onOpenGuide,
                testTag = "btn_topbar_guide"
              )
              TvTopBarIconButton(
                icon = Icons.Default.Fullscreen,
                contentDescription = "ملء الشاشة",
                onClick = onToggleFullscreen,
                testTag = "btn_topbar_fullscreen"
              )
            }
          }

          // Row 2: Page Title / URL badge and Quick Search Suggestions
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // URL / Page Title Indicator
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(0.45f)
            ) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (activePortal == StreamingPortal.TOP_CINEMA) CinemaPrimary.copy(alpha = 0.3f) else CinemaSecondary.copy(alpha = 0.3f))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(
                  text = activePortal.displayNameArabic,
                  color = Color.White,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold
                )
              }
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = pageTitle.ifBlank { currentUrl },
                color = Color.LightGray,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            // Quick suggestion chips
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.weight(0.55f)
            ) {
              items(quickSuggestions) { suggestion ->
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()

                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isFocused) CinemaFocusGlow.copy(alpha = 0.3f) else CinemaSurfaceVariant)
                    .border(1.dp, if (isFocused) CinemaFocusGlow else Color.Transparent, RoundedCornerShape(4.dp))
                    .clickable(interactionSource = interactionSource, indication = null) {
                      searchQuery = suggestion
                      executeSearch(activePortal)
                    }
                    .focusable(interactionSource = interactionSource)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = suggestion,
                    color = if (isFocused) CinemaFocusGlow else Color.White,
                    fontSize = 10.sp
                  )
                }
              }
            }
          }
        }
      }
    }

    // Loading Progress Indicator
    if (loadingProgress in 1..99) {
      LinearProgressIndicator(
        progress = { loadingProgress / 100f },
        modifier = Modifier
          .fillMaxWidth()
          .height(3.dp),
        color = CinemaAccent,
        trackColor = Color.Transparent
      )
    }

    // Small Show/Hide Toggle Tab
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(end = 16.dp),
      contentAlignment = Alignment.TopEnd
    ) {
      Surface(
        onClick = onToggleBarVisibility,
        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
        color = CinemaSurface.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
      ) {
        Icon(
          imageVector = if (isBarVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
          contentDescription = if (isBarVisible) "إخفاء الشريط العلوي" else "إظهار الشريط العلوي",
          tint = Color.LightGray,
          modifier = Modifier
            .size(24.dp)
            .padding(2.dp)
        )
      }
    }
  }
}

@Composable
private fun TvTopBarIconButton(
  icon: ImageVector,
  contentDescription: String,
  tint: Color = Color.White,
  onClick: () -> Unit,
  testTag: String
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Box(
    modifier = Modifier
      .size(34.dp)
      .scale(if (isFocused) 1.15f else 1.0f)
      .clip(RoundedCornerShape(6.dp))
      .background(if (isFocused) CinemaFocusGlow.copy(alpha = 0.3f) else CinemaSurfaceVariant)
      .border(
        width = if (isFocused) 2.dp else 1.dp,
        color = if (isFocused) CinemaFocusGlow else Color.Transparent,
        shape = RoundedCornerShape(6.dp)
      )
      .clickable(interactionSource = interactionSource, indication = null) { onClick() }
      .focusable(interactionSource = interactionSource)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = if (isFocused) CinemaFocusGlow else tint,
      modifier = Modifier.size(18.dp)
    )
  }
}
