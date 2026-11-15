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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDisplayMode
import com.example.model.SmartOmniboxResolver
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
  isGeneralBrowserMode: Boolean = false,
  currentDisplayMode: AppDisplayMode = AppDisplayMode.MOBILE,
  isBarVisible: Boolean,
  loadingProgress: Int,
  pageTitle: String,
  isBookmarked: Boolean,
  onGoToHome: () -> Unit,
  onOpenSubtitles: () -> Unit,
  onSearchDirect: (portal: StreamingPortal, query: String) -> Unit,
  onOmniboxNavigate: (url: String, title: String) -> Unit = { _, _ -> },
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
  onOpenUpdate: () -> Unit = {},
  onToggleBarVisibility: () -> Unit,
  modifier: Modifier = Modifier
) {
  var searchQuery by remember { mutableStateOf("") }

  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current
  val configuration = LocalConfiguration.current
  val isMobileLayout = currentDisplayMode == AppDisplayMode.MOBILE || configuration.screenWidthDp < 600

  val quickSuggestions = listOf(
    "google.com",
    "youtube.com",
    "Silo",
    "Z Nation",
    "Breaking Bad",
    "Game of Thrones",
    "From",
    "الحشاشين"
  )

  fun executeGoogleOrUrlOmnibox() {
    val q = searchQuery.trim()
    keyboardController?.hide()
    focusManager.clearFocus()
    val resolvedUrl = SmartOmniboxResolver.resolveInputToUrl(q)
    val title = if (q.isBlank()) "Google" else "بحث: $q"
    onOmniboxNavigate(resolvedUrl, title)
  }

  fun executePortalSearch(portal: StreamingPortal) {
    val q = searchQuery.trim().ifBlank { "Silo" }
    keyboardController?.hide()
    focusManager.clearFocus()
    onSearchDirect(portal, q)
  }

  Column(modifier = modifier.fillMaxWidth()) {
    if (isBarVisible) {
      Surface(
        color = CinemaDarkBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceVariant)
      ) {
        if (isMobileLayout) {
          // MOBILE PERSPECTIVE TOP BAR:
          // Row 1: [Home] [Back] [Refresh] [Wide Visible Omnibox Input] [Go] [Bookmark]
          // Row 2: Scrollable LazyRow of Portals & Tools (AdGuard, Subtitles, Quality 480p, Fullscreen)
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              TvTopBarIconButton(
                icon = Icons.Default.Public,
                contentDescription = "الرئيسية",
                tint = Color(0xFF38BDF8),
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
                icon = Icons.Default.Refresh,
                contentDescription = "تحديث",
                onClick = onRefreshClick,
                testTag = "btn_topbar_refresh"
              )

              Box(
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 40.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(CinemaSurface)
                  .border(1.dp, CinemaBorder, RoundedCornerShape(8.dp))
                  .padding(horizontal = 10.dp, vertical = 6.dp)
                  .testTag("topbar_search_input"),
                contentAlignment = Alignment.CenterStart
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                  ) {
                    if (searchQuery.isEmpty()) {
                      Text(
                        text = pageTitle.ifBlank { "ابحث في جوجل أو اكتب رابطاً..." },
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                    BasicTextField(
                      value = searchQuery,
                      onValueChange = { searchQuery = it },
                      singleLine = true,
                      textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 13.sp
                      ),
                      cursorBrush = SolidColor(Color(0xFF38BDF8)),
                      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                      keyboardActions = KeyboardActions(
                        onSearch = {
                          if (isGeneralBrowserMode) executeGoogleOrUrlOmnibox()
                          else executePortalSearch(activePortal)
                        }
                      ),
                      modifier = Modifier.fillMaxWidth()
                    )
                  }
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
                }
              }

              Button(
                onClick = {
                  if (isGeneralBrowserMode) executeGoogleOrUrlOmnibox()
                  else executePortalSearch(activePortal)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                  .heightIn(min = 38.dp)
                  .testTag("topbar_btn_search_google")
              ) {
                Icon(Icons.Default.Search, contentDescription = "بحث", modifier = Modifier.size(16.dp), tint = Color.White)
              }

              TvTopBarIconButton(
                icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = "إضافة للمفضلة",
                tint = if (isBookmarked) CinemaAccent else Color.LightGray,
                onClick = onBookmarkClick,
                testTag = "btn_topbar_bookmark"
              )
            }

            // Row 2: Scrollable Tools & Quick Portal Search Strip
            LazyRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              item {
                MobileToolPill(
                  label = "بحث جوجل",
                  icon = Icons.Default.Public,
                  color = Color(0xFF0284C7),
                  onClick = { executeGoogleOrUrlOmnibox() }
                )
              }

              items(StreamingPortal.values().filter { it.isEnabled }) { portal ->
                val btnColor = when (portal) {
                  StreamingPortal.TOP_CINEMA -> CinemaPrimary
                  StreamingPortal.WATCH_SERIES -> CinemaSecondary
                  StreamingPortal.LAROZA -> CinemaAccent
                  StreamingPortal.EGY_DEAD -> Color(0xFF4CAF50)
                  else -> CinemaSurfaceVariant
                }
                MobileToolPill(
                  label = "بحث ${portal.displayNameArabic}",
                  icon = Icons.Default.LiveTv,
                  color = btnColor,
                  onClick = { executePortalSearch(portal) },
                  testTag = "topbar_btn_search_${portal.id}"
                )
              }

              item {
                MobileToolPill(
                  label = "حماية AdGuard",
                  icon = Icons.Default.Shield,
                  color = CinemaSuccess,
                  onClick = onOpenAdBlockSettings,
                  testTag = "btn_topbar_adblock"
                )
              }
              item {
                MobileToolPill(
                  label = "الترجمة",
                  icon = Icons.Default.Subtitles,
                  color = CinemaAccent,
                  onClick = onOpenSubtitles,
                  testTag = "btn_topbar_subtitles"
                )
              }
              item {
                MobileToolPill(
                  label = "الجودة 480p",
                  icon = Icons.Default.HighQuality,
                  color = Color(0xFF38BDF8),
                  onClick = onOpenQuality,
                  testTag = "btn_topbar_quality"
                )
              }
              item {
                MobileToolPill(
                  label = "ملء الشاشة",
                  icon = Icons.Default.Fullscreen,
                  color = Color.LightGray,
                  onClick = onToggleFullscreen,
                  testTag = "btn_topbar_fullscreen"
                )
              }
            }
          }
        } else {
          // TV / TABLET WIDESCREEN PERSPECTIVE TOP BAR
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                TvTopBarIconButton(
                  icon = Icons.Default.Public,
                  contentDescription = "الواجهة الرئيسية",
                  tint = Color(0xFF38BDF8),
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

              Row(
                modifier = Modifier
                  .weight(1f)
                  .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CinemaSurface)
                    .border(1.dp, CinemaBorder, RoundedCornerShape(8.dp))
                    .clickable { keyboardController?.show() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("topbar_search_input"),
                  contentAlignment = Alignment.CenterStart
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = Icons.Default.Search,
                      contentDescription = "بحث",
                      tint = Color(0xFF38BDF8),
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                      modifier = Modifier.weight(1f),
                      contentAlignment = Alignment.CenterStart
                    ) {
                      if (searchQuery.isEmpty()) {
                        Text(
                          text = "ابحث في جوجل، اكتب رابط موقع، أو اسم فيلم...",
                          color = Color.Gray,
                          fontSize = 12.sp,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                      }
                      BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                          color = Color.White,
                          fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(Color(0xFF38BDF8)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                          onSearch = {
                            if (isGeneralBrowserMode) executeGoogleOrUrlOmnibox()
                            else executePortalSearch(activePortal)
                          }
                        ),
                        modifier = Modifier.fillMaxWidth()
                      )
                    }
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
                  }
                }

                Button(
                  onClick = { executeGoogleOrUrlOmnibox() },
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                  shape = RoundedCornerShape(6.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                  modifier = Modifier
                    .heightIn(min = 38.dp)
                    .testTag("topbar_btn_search_google")
                ) {
                  Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("جوجل / رابط", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                }

                StreamingPortal.values().filter { it.isEnabled }.take(2).forEach { portal ->
                  val btnColor = when (portal) {
                    StreamingPortal.TOP_CINEMA -> CinemaPrimary
                    StreamingPortal.WATCH_SERIES -> CinemaSecondary
                    else -> CinemaSurfaceVariant
                  }
                  val icon = if (portal == StreamingPortal.TOP_CINEMA) Icons.Default.Movie else Icons.Default.LiveTv

                  Button(
                    onClick = { executePortalSearch(portal) },
                    colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                      .heightIn(min = 38.dp)
                      .testTag("topbar_btn_search_${portal.id}")
                  ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(portal.displayNameArabic, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                  }
                }
              }

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
                  contentDescription = "اختيار جودة الفيديو",
                  tint = Color(0xFF38BDF8),
                  onClick = onOpenQuality,
                  testTag = "btn_topbar_quality"
                )
                TvTopBarIconButton(
                  icon = Icons.Default.SystemUpdate,
                  contentDescription = "تحديث التطبيق المباشر",
                  tint = CinemaSuccess,
                  onClick = onOpenUpdate,
                  testTag = "btn_topbar_update"
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

            Spacer(modifier = Modifier.height(4.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(0.45f)
              ) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                      if (isGeneralBrowserMode) Color(0xFF0284C7).copy(alpha = 0.3f)
                      else if (activePortal == StreamingPortal.TOP_CINEMA) CinemaPrimary.copy(alpha = 0.3f)
                      else CinemaSecondary.copy(alpha = 0.3f)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = if (isGeneralBrowserMode) "Asser Browser" else activePortal.displayNameArabic,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
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
                        if (suggestion.contains(".")) executeGoogleOrUrlOmnibox()
                        else executePortalSearch(activePortal)
                      }
                      .focusable(interactionSource = interactionSource)
                      .padding(horizontal = 6.dp, vertical = 2.dp)
                  ) {
                    Text(
                      text = suggestion,
                      color = if (isFocused) CinemaFocusGlow else Color.White,
                      fontSize = 10.sp,
                      maxLines = 1,
                      softWrap = false
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    if (loadingProgress in 1..99) {
      LinearProgressIndicator(
        progress = { loadingProgress / 100f },
        modifier = Modifier
          .fillMaxWidth()
          .height(3.dp),
        color = Color(0xFF38BDF8),
        trackColor = Color.Transparent
      )
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(end = 16.dp),
      contentAlignment = Alignment.TopEnd
    ) {
      Surface(
        onClick = onToggleBarVisibility,
        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
        color = CinemaSurface,
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
private fun MobileToolPill(
  label: String,
  icon: ImageVector,
  color: Color,
  onClick: () -> Unit,
  testTag: String = ""
) {
  Surface(
    onClick = onClick,
    color = color.copy(alpha = 0.18f),
    shape = RoundedCornerShape(8.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.6f)),
    modifier = if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(13.dp)
      )
      Text(
        text = label,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false
      )
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
