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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AppDisplayMode
import com.example.data.SearchAndSubtitlesService
import com.example.data.TvPreferencesRepository
import com.example.model.HistoryEntry
import com.example.model.SearchResultItem
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeDashboardScreen(
  repository: TvPreferencesRepository,
  onLaunchUrl: (url: String, portal: StreamingPortal, title: String) -> Unit,
  onOpenBookmarks: () -> Unit,
  onOpenHistory: () -> Unit,
  onOpenDisplayMode: () -> Unit,
  onOpenAdBlockSettings: () -> Unit = {},
  modifier: Modifier = Modifier,
  searchService: SearchAndSubtitlesService = remember { SearchAndSubtitlesService() }
) {
  val scope = rememberCoroutineScope()
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current

  var searchQuery by remember { mutableStateOf("") }
  var isSearching by remember { mutableStateOf(false) }
  var topCinemaResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
  var watchSeriesResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
  var hasSearched by remember { mutableStateOf(false) }

  val history by repository.historyFlow.collectAsState()
  val displayMode by repository.displayModeFlow.collectAsState()

  val quickSuggestions = listOf(
    "Silo",
    "Z Nation",
    "The Walking Dead",
    "Peaky Blinders",
    "Breaking Bad",
    "Game of Thrones",
    "From",
    "الحشاشين"
  )

  fun executeDualSearch(query: String) {
    val q = query.trim()
    if (q.isBlank()) return
    searchQuery = q
    keyboardController?.hide()
    focusManager.clearFocus()
    isSearching = true
    hasSearched = true

    scope.launch {
      val (tc, ws) = searchService.searchDualPortals(q)
      topCinemaResults = tc
      watchSeriesResults = ws
      isSearching = false
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(CinemaDarkBackground)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp)
  ) {
    // 1. Top Header Bar (Optimized and responsive for Phone and TV)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // App Logo & Title
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f, fill = false)
        ) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(
                Brush.linearGradient(listOf(CinemaPrimary, CinemaSecondary))
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Tv,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(22.dp)
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = "Cinema TV",
              color = Color.White,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "سينما تي في",
              color = CinemaAccent,
              fontSize = 11.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        // Action Buttons: Mode Badge, Bookmarks, History
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // AdGuard Shield Button
          Surface(
            onClick = onOpenAdBlockSettings,
            color = CinemaSurface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSuccess.copy(alpha = 0.5f)),
            modifier = Modifier.testTag("btn_dashboard_adblock")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = CinemaSuccess,
                modifier = Modifier.size(14.dp)
              )
              Text(
                text = "AdGuard",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }

          // Display Mode Switcher
          Surface(
            onClick = onOpenDisplayMode,
            color = CinemaSurface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
            modifier = Modifier.testTag("btn_switch_display_mode")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = if (displayMode == AppDisplayMode.TV) Icons.Default.Tv else Icons.Default.Smartphone,
                contentDescription = null,
                tint = CinemaAccent,
                modifier = Modifier.size(14.dp)
              )
              Text(
                text = if (displayMode == AppDisplayMode.TV) "تلفاز" else "هاتف",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }

          IconButton(
            onClick = onOpenBookmarks,
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(CinemaSurface)
              .border(1.dp, CinemaBorder, CircleShape)
              .testTag("btn_dashboard_bookmarks")
          ) {
            Icon(
              Icons.Default.Bookmark,
              contentDescription = "المفضلة",
              tint = CinemaAccent,
              modifier = Modifier.size(18.dp)
            )
          }

          IconButton(
            onClick = onOpenHistory,
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(CinemaSurface)
              .border(1.dp, CinemaBorder, CircleShape)
              .testTag("btn_dashboard_history")
          ) {
            Icon(
              Icons.Default.History,
              contentDescription = "السجل",
              tint = Color.LightGray,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }
    }

    // TV Mode Special Indicator & Quick Guide Banner
    if (displayMode == AppDisplayMode.TV) {
      item {
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = CinemaSecondary.copy(alpha = 0.15f)),
          border = androidx.compose.foundation.BorderStroke(1.5.dp, CinemaSecondary.copy(alpha = 0.6f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                tint = CinemaSecondary,
                modifier = Modifier.size(24.dp)
              )
              Column {
                Text(
                  text = "وضع تلفاز أندرويد نشط (Android TV Mode)",
                  color = Color.White,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "شاشة عريضة 16:9 • تحكم كامل بأسهم الريموت D-Pad • اضغط OK للاختيار",
                  color = Color.LightGray,
                  fontSize = 11.sp
                )
              }
            }

            Surface(
              onClick = onOpenDisplayMode,
              color = CinemaSecondary.copy(alpha = 0.25f),
              shape = RoundedCornerShape(8.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSecondary)
            ) {
              Text(
                text = "تغيير الوضع ⚙️",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
        }
      }
    }

    // 2. Unified Search Bar with Dedicated Search Button
    item {
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(
            text = "🔍 ابحث في موقعي توب سينما وووتش سيريز معاً:",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              placeholder = {
                Text(
                  text = "اكتب اسم الفيلم أو المسلسل (مثل: Silo, Z Nation, Peaky Blinders...)",
                  color = Color.Gray,
                  fontSize = 12.sp
                )
              },
              leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = CinemaAccent)
              },
              trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                  IconButton(onClick = { searchQuery = "" }) {
                    Icon(Icons.Default.Clear, contentDescription = "مسح", tint = Color.LightGray)
                  }
                }
              },
              singleLine = true,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
              keyboardActions = KeyboardActions(
                onSearch = { executeDualSearch(searchQuery) }
              ),
              colors = TextFieldDefaults.colors(
                focusedContainerColor = CinemaDarkBackground,
                unfocusedContainerColor = CinemaDarkBackground,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = CinemaAccent,
                unfocusedIndicatorColor = CinemaBorder
              ),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("home_search_input")
            )

            // Dedicated Search Button
            Button(
              onClick = { executeDualSearch(searchQuery) },
              colors = ButtonDefaults.buttonColors(
                containerColor = CinemaAccent,
                contentColor = Color.Black
              ),
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
              modifier = Modifier.testTag("btn_dual_search_action")
            ) {
              Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "بحث",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Quick Suggestion Chips
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            items(quickSuggestions) { suggestion ->
              FilterChip(
                selected = searchQuery == suggestion,
                onClick = {
                  searchQuery = suggestion
                  executeDualSearch(suggestion)
                },
                label = { Text(suggestion, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = CinemaAccent,
                  selectedLabelColor = Color.Black,
                  containerColor = CinemaDarkBackground,
                  labelColor = Color.LightGray
                ),
                border = FilterChipDefaults.filterChipBorder(
                  enabled = true,
                  selected = searchQuery == suggestion,
                  borderColor = CinemaBorder,
                  selectedBorderColor = CinemaAccent
                )
              )
            }
          }
        }
      }
    }

    // 3. Search Results Section (Divided into two distinct portal categories)
    if (isSearching) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = CinemaAccent, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "جاري البحث في توب سينما وووتش سيريز واستحضار النتائج...",
              color = Color.LightGray,
              fontSize = 13.sp
            )
          }
        }
      }
    } else if (hasSearched) {
      // Results for TopCinema
      item {
        PortalResultsSection(
          portal = StreamingPortal.TOP_CINEMA,
          query = searchQuery,
          results = topCinemaResults,
          onItemClick = { item ->
            onLaunchUrl(item.url, StreamingPortal.TOP_CINEMA, item.title)
          }
        )
      }

      // Results for WatchSeries
      item {
        PortalResultsSection(
          portal = StreamingPortal.WATCH_SERIES,
          query = searchQuery,
          results = watchSeriesResults,
          onItemClick = { item ->
            onLaunchUrl(item.url, StreamingPortal.WATCH_SERIES, item.title)
          }
        )
      }
    }

    // 4. Continue Watching Section (متابعة المشاهدة)
    item {
      ContinueWatchingSection(
        history = history,
        onResumeItem = { entry ->
          onLaunchUrl(entry.url, entry.portal, entry.title)
        },
        onDeleteItem = { entry ->
          repository.removeHistoryEntry(entry.id)
        },
        onClearAll = {
          repository.clearHistory()
        }
      )
    }

    // 5. Direct Portal Shortcuts Cards
    item {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "🌐 تصفح بوابات المشاهدة مباشرة:",
          color = Color.White,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // TopCinema Card
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaPrimary.copy(alpha = 0.5f)),
            modifier = Modifier
              .weight(1f)
              .clickable {
                onLaunchUrl(StreamingPortal.TOP_CINEMA.homeUrl, StreamingPortal.TOP_CINEMA, "توب سينما")
              }
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(CinemaPrimary),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("توب سينما", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text("أفلام ومسلسلات مترجمة بسيرفرات سريعة وبدون إعلانات مزعجة", color = Color.Gray, fontSize = 10.sp, maxLines = 2)
            }
          }

          // WatchSeries Card
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSecondary.copy(alpha = 0.5f)),
            modifier = Modifier
              .weight(1f)
              .clickable {
                onLaunchUrl(StreamingPortal.WATCH_SERIES.homeUrl, StreamingPortal.WATCH_SERIES, "ووتش سيريز")
              }
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(CinemaSecondary),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("ووتش سيريز", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text("المسلسلات التلفزيونية العالمية كاملة بكافة المواسم والحلقات", color = Color.Gray, fontSize = 10.sp, maxLines = 2)
            }
          }
        }
      }
    }
  }
}

@Composable
fun PortalResultsSection(
  portal: StreamingPortal,
  query: String,
  results: List<SearchResultItem>,
  onItemClick: (SearchResultItem) -> Unit
) {
  val isTopCinema = portal == StreamingPortal.TOP_CINEMA
  val badgeColor = if (isTopCinema) CinemaPrimary else CinemaSecondary

  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(badgeColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = if (isTopCinema) "🎬 نتائج توب سينما (TopCinema)" else "📺 نتائج ووتش سيريز (WatchSeries)",
          color = Color.White,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold
        )
      }
      Text(
        text = "${results.size} نتائج",
        color = Color.Gray,
        fontSize = 11.sp
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (results.isEmpty()) {
      Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = "لا توجد نتائج مباشرة في ${portal.displayNameArabic} لـ \"$query\"، يمكنك البحث بالاسم الإنجليزي أو تصفح الموقع مباشرة.",
          color = Color.Gray,
          fontSize = 12.sp,
          modifier = Modifier.padding(12.dp)
        )
      }
    } else {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(results, key = { it.id }) { item ->
          SearchResultCard(
            item = item,
            badgeColor = badgeColor,
            onClick = { onItemClick(item) }
          )
        }
      }
    }
  }
}

@Composable
fun SearchResultCard(
  item: SearchResultItem,
  badgeColor: Color,
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Card(
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
    border = androidx.compose.foundation.BorderStroke(
      width = if (isFocused) 2.dp else 1.dp,
      color = if (isFocused) CinemaFocusGlow else CinemaBorder
    ),
    modifier = Modifier
      .width(170.dp)
      .scale(if (isFocused) 1.03f else 1f)
      .focusable(interactionSource = interactionSource)
      .clickable { onClick() }
      .testTag("result_card_${item.id}")
  ) {
    Column {
      // Poster Image
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(130.dp)
          .background(Color.Black)
      ) {
        if (item.posterUrl.isNotBlank()) {
          AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Movie, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(36.dp))
          }
        }

        // Quality Badge
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
          Text(item.quality, color = CinemaAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }

        // Type Badge
        Box(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(badgeColor.copy(alpha = 0.85f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(item.type, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
      }

      // Title & Play Action
      Column(modifier = Modifier.padding(8.dp)) {
        Text(
          text = item.title,
          color = Color.White,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))

        Button(
          onClick = onClick,
          colors = ButtonDefaults.buttonColors(containerColor = badgeColor),
          shape = RoundedCornerShape(6.dp),
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
        ) {
          Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
          Spacer(modifier = Modifier.width(3.dp))
          Text("مشاهدة الآن", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinueWatchingSection(
  history: List<HistoryEntry>,
  onResumeItem: (HistoryEntry) -> Unit,
  onDeleteItem: (HistoryEntry) -> Unit,
  onClearAll: () -> Unit
) {
  var showConfirmClearDialog by remember { mutableStateOf(false) }

  if (showConfirmClearDialog) {
    BasicAlertDialog(
      onDismissRequest = { showConfirmClearDialog = false }
    ) {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = CinemaSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Icon(
            imageVector = Icons.Default.DeleteSweep,
            contentDescription = null,
            tint = Color(0xFFFF5252),
            modifier = Modifier.size(36.dp)
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "مسح سجل المشاهدة بالكامل",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "هل تريد حذف جميع عناصر استكمال المشاهدة والبدء من جديد؟",
            color = Color.LightGray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(18.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Button(
              onClick = { showConfirmClearDialog = false },
              colors = ButtonDefaults.buttonColors(containerColor = CinemaDarkBackground),
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text("إلغاء", color = Color.White)
            }
            Button(
              onClick = {
                showConfirmClearDialog = false
                onClearAll()
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text("نعم، مسح السجل", color = Color.White, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription = null,
          tint = CinemaAccent,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "متابعة المشاهدة (Continue Watching):",
          color = Color.White,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold
        )
      }
      if (history.isNotEmpty()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "${history.size} عنصر",
            color = Color.Gray,
            fontSize = 11.sp
          )

          // Button to Clear All History
          Surface(
            onClick = { showConfirmClearDialog = true },
            color = Color(0xFFD32F2F).copy(alpha = 0.15f),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.5f)),
            modifier = Modifier.testTag("btn_clear_continue_watching")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = "مسح السجل بالكامل",
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(14.dp)
              )
              Text(
                text = "مسح السجل",
                color = Color(0xFFFF8A80),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (history.isEmpty()) {
      Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "لا توجد مشاهدات سابقة مسجلة بعد",
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "ابدأ بالبحث أعلاه أو اضغط على أحد مقترحات المسلسلات لمشاهدتها وستظهر تلقائياً هنا لمتابعتها.",
            color = Color.Gray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
          )
        }
      }
    } else {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(history.take(15), key = { it.id }) { entry ->
          ContinueWatchingCard(
            entry = entry,
            onResume = { onResumeItem(entry) },
            onDelete = { onDeleteItem(entry) }
          )
        }
      }
    }
  }
}

@Composable
fun ContinueWatchingCard(
  entry: HistoryEntry,
  onResume: () -> Unit,
  onDelete: () -> Unit
) {
  val isTopCinema = entry.portal == StreamingPortal.TOP_CINEMA
  val portalColor = if (isTopCinema) CinemaPrimary else CinemaSecondary
  val formattedDate = remember(entry.timestamp) {
    try {
      val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
      sdf.format(Date(entry.timestamp))
    } catch (_: Exception) {
      ""
    }
  }

  Card(
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
    modifier = Modifier
      .width(200.dp)
      .clickable { onResume() }
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(portalColor.copy(alpha = 0.25f))
            .border(1.dp, portalColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(entry.portal.displayNameArabic, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier.size(20.dp)
        ) {
          Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Gray, modifier = Modifier.size(14.dp))
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = entry.title,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )

      if (formattedDate.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(formattedDate, color = Color.Gray, fontSize = 9.sp)
      }

      Spacer(modifier = Modifier.height(8.dp))

      Button(
        onClick = onResume,
        colors = ButtonDefaults.buttonColors(containerColor = CinemaAccent, contentColor = Color.Black),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(28.dp)
      ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("متابعة المشاهدة", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
      }
    }
  }
}
