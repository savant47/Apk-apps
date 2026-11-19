package com.example.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import android.os.Build
import androidx.compose.ui.platform.LocalContext
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
import coil.request.ImageRequest
import com.example.data.AppDisplayMode
import com.example.data.SearchAndSubtitlesService
import com.example.data.TvPreferencesRepository
import com.example.model.CatalogMediaItem
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
import com.example.ui.theme.ClipboxPillBackground
import com.example.ui.theme.ClipboxWhite
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ClipboxRailSection {
  SEARCH,
  HOME,
  MOVIES,
  TV_SHOWS
}

@Composable
fun HomeDashboardScreen(
  repository: TvPreferencesRepository,
  onLaunchUrl: (url: String, portal: StreamingPortal, title: String) -> Unit,
  onLaunchResultWithSources: (
    url: String,
    portal: StreamingPortal,
    title: String,
    searchQuery: String,
    portalResultsMap: Map<StreamingPortal, List<SearchResultItem>>
  ) -> Unit = { url, portal, title, _, _ -> onLaunchUrl(url, portal, title) },
  onOpenBookmarks: () -> Unit,
  onOpenHistory: () -> Unit,
  onOpenDisplayMode: () -> Unit,
  onOpenAdBlockSettings: () -> Unit = {},
  onOpenUpdate: () -> Unit = {},
  modifier: Modifier = Modifier,
  searchService: SearchAndSubtitlesService = remember { SearchAndSubtitlesService() }
) {
  val scope = rememberCoroutineScope()
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current
  val searchInputFocusRequester = remember { FocusRequester() }
  val listState = rememberLazyListState()

  var activeSection by remember { mutableStateOf(ClipboxRailSection.HOME) }
  var searchQuery by remember { mutableStateOf("") }
  var isSearching by remember { mutableStateOf(false) }
  var portalResultsMap by remember { mutableStateOf<Map<StreamingPortal, List<SearchResultItem>>>(emptyMap()) }
  var hasSearched by remember { mutableStateOf(false) }
  var showSourcesDialog by remember { mutableStateOf(false) }
  var spellingSuggestion by remember { mutableStateOf<String?>(null) }

  // Catalog State (Live Daily IMDb Trending, Top 10 Movies Today, Top 10 TV Series Today)
  var trendingItems by remember { mutableStateOf<List<CatalogMediaItem>>(emptyList()) }
  var top10Movies by remember { mutableStateOf<List<CatalogMediaItem>>(emptyList()) }
  var top10TvSeries by remember { mutableStateOf<List<CatalogMediaItem>>(emptyList()) }
  var isLoadingCatalog by remember { mutableStateOf(true) }
  var catalogRefreshTrigger by remember { mutableIntStateOf(0) }
  val todayDateLabel = remember {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
  }

  val history by repository.historyFlow.collectAsState()
  val displayMode by repository.displayModeFlow.collectAsState()

  // Load Live Daily IMDb Catalog on launch and whenever refreshed
  LaunchedEffect(catalogRefreshTrigger) {
    isLoadingCatalog = true
    val trendingDeferred = async { searchService.fetchTrendingCatalog() }
    val moviesDeferred = async { searchService.fetchTop10Movies() }
    val tvDeferred = async { searchService.fetchTop10TvSeries() }
    trendingItems = trendingDeferred.await()
    top10Movies = moviesDeferred.await()
    top10TvSeries = tvDeferred.await()
    isLoadingCatalog = false
  }

  val quickSuggestions = listOf(
    "Silo",
    "Squid Game",
    "الحشاشين",
    "The Last of Us",
    "Peaky Blinders",
    "Breaking Bad",
    "Dune: Part Two",
    "Shogun",
    "From",
    "Attack on Titan"
  )

  fun executeMultiSourceSearch(query: String) {
    val q = query.trim()
    if (q.isBlank()) return
    searchQuery = q
    keyboardController?.hide()
    focusManager.clearFocus()
    isSearching = true
    hasSearched = true
    spellingSuggestion = getSpellingSuggestion(q)

    scope.launch {
      val results = searchService.searchAllPortals(q)
      portalResultsMap = results
      isSearching = false
    }
  }

  Row(
    modifier = modifier
      .fillMaxSize()
      .background(CinemaDarkBackground)
  ) {
    // 1. Clipbox Floating Pill Navigation Rail on the side
    ClipboxNavigationRail(
      activeSection = activeSection,
      isCompactMobile = displayMode == AppDisplayMode.MOBILE,
      onSelectSection = { section ->
        activeSection = section
        if (section == ClipboxRailSection.SEARCH) {
          scope.launch {
            listState.animateScrollToItem(0)
            try {
              searchInputFocusRequester.requestFocus()
            } catch (_: Exception) {}
          }
        } else if (section == ClipboxRailSection.HOME) {
          hasSearched = false
          scope.launch { listState.animateScrollToItem(0) }
        }
      },
      onOpenBookmarks = onOpenBookmarks,
      onOpenHistory = onOpenHistory,
      onOpenSources = { showSourcesDialog = true },
      onOpenUpdate = onOpenUpdate
    )

    // 2. Main Clipbox Content Area
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .padding(horizontal = if (displayMode == AppDisplayMode.MOBILE) 10.dp else 16.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
      // Top Search Bar (Opens right at the top for searching any Arabic, English, Korean, Turkish, Asian movie or series)
      item {
        ClipboxTopSearchBar(
          searchQuery = searchQuery,
          onQueryChange = { searchQuery = it },
          onExecuteSearch = { executeMultiSourceSearch(searchQuery) },
          onClearSearch = {
            searchQuery = ""
            hasSearched = false
            portalResultsMap = emptyMap()
          },
          quickSuggestions = quickSuggestions,
          onSuggestionClick = { suggestion ->
            searchQuery = suggestion
            executeMultiSourceSearch(suggestion)
          },
          onOpenSources = { showSourcesDialog = true },
          focusRequester = searchInputFocusRequester
        )
      }

      // Multi-Source Search Results Section (Divided by Website: TopCinema, LaRoza, WatchSeries, EgyDead, Flixer)
      if (isSearching) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(190.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(CinemaSurface),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator(color = CinemaAccent, modifier = Modifier.size(36.dp))
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "جاري البحث عن \"$searchQuery\" في جميع المواقع المصادر...",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "توب سينما • لاروزا • ووتش سيريز • إيجي ديد • فليكسر",
                color = CinemaAccent,
                fontSize = 11.sp
              )
            }
          }
        }
      } else if (hasSearched) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "نتائج البحث في المصادر لـ \"$searchQuery\":",
              color = Color.White,
              fontSize = 15.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f)
            )
            Surface(
              onClick = { hasSearched = false },
              color = CinemaSurfaceVariant,
              shape = RoundedCornerShape(50),
              border = BorderStroke(1.dp, CinemaBorder)
            ) {
              Text(
                text = "إخفاء النتائج ✕",
                color = Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                maxLines = 1,
                softWrap = false
              )
            }
          }
        }

        // Render grouped results per streaming portal
        StreamingPortal.values().filter { it.isEnabled }.forEach { portal ->
          val results = portalResultsMap[portal] ?: emptyList()
          item(key = "portal_results_${portal.name}") {
            PortalResultsSection(
              portal = portal,
              query = searchQuery,
              results = results,
              onItemClick = { item ->
                onLaunchResultWithSources(
                  item.url,
                  portal,
                  item.title,
                  searchQuery,
                  portalResultsMap
                )
              },
              spellingSuggestion = spellingSuggestion,
              onSearchSuggestion = { corrected ->
                executeMultiSourceSearch(corrected)
              },
              onLaunchManualUrl = { manualUrl ->
                onLaunchResultWithSources(
                  manualUrl,
                  portal,
                  "${portal.displayNameArabic}: $searchQuery",
                  searchQuery,
                  portalResultsMap
                )
              }
            )
          }
        }
      }

      // Continue Watching Row (متابعة المشاهدة) with Clipbox Red Progress Bar
      if (history.isNotEmpty()) {
        item {
          ContinueWatchingSection(
            history = history,
            onResumeItem = { entry ->
              onLaunchResultWithSources(
                entry.url,
                entry.portal,
                entry.title,
                entry.title,
                portalResultsMap
              )
            },
            onDeleteItem = { entry ->
              repository.removeHistoryEntry(entry.id)
            },
            onClearAll = {
              repository.clearHistory()
            }
          )
        }
      }

      // Live Daily IMDb Status Strip & Manual Refresh
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CinemaSurface)
            .border(1.dp, CinemaBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
          ) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isLoadingCatalog) CinemaAccent else CinemaSuccess)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isLoadingCatalog) {
                "جاري سحب وتحديث قوائم IMDb اليومية المباشرة..."
              } else {
                "قوائم IMDb المباشرة • محدثة يومياً ($todayDateLabel)"
              },
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Surface(
            onClick = { catalogRefreshTrigger++ },
            color = CinemaDarkBackground,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.7f))
          ) {
            Text(
              text = "تحديث الآن ⟳",
              color = CinemaAccent,
              fontSize = 10.sp,
              fontWeight = FontWeight.ExtraBold,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              maxLines = 1,
              softWrap = false
            )
          }
        }
      }

      // Trending Now (IMDb الرائج اليوم)
      if (activeSection == ClipboxRailSection.HOME || activeSection == ClipboxRailSection.SEARCH) {
        item {
          ClipboxCatalogRow(
            title = "الرائج اليوم • Trending Today (IMDb)",
            subtitle = "الأعمال الأكثر رواجاً وبحثاً اليوم على IMDb — اضغط للبحث في جميع المصادر",
            items = trendingItems,
            showRankBadge = false,
            onItemClick = { media ->
              executeMultiSourceSearch(media.title)
            }
          )
        }
      }

      // Top 10 Movies Today (أفضل 10 أفلام اليوم على IMDb)
      if (activeSection == ClipboxRailSection.HOME || activeSection == ClipboxRailSection.MOVIES || activeSection == ClipboxRailSection.SEARCH) {
        item {
          ClipboxCatalogRow(
            title = "أفضل 10 أفلام اليوم • Top 10 Movies (IMDb)",
            subtitle = "ترتيب الأفلام الأكثر شعبية ومشاهدة اليوم على IMDb",
            items = top10Movies,
            showRankBadge = true,
            onItemClick = { media ->
              executeMultiSourceSearch(media.title)
            }
          )
        }
      }

      // Top 10 TV Series Today (أفضل 10 مسلسلات اليوم على IMDb)
      if (activeSection == ClipboxRailSection.HOME || activeSection == ClipboxRailSection.TV_SHOWS || activeSection == ClipboxRailSection.SEARCH) {
        item {
          ClipboxCatalogRow(
            title = "أفضل 10 مسلسلات اليوم • Top 10 TV Series (IMDb)",
            subtitle = "ترتيب المسلسلات الأكثر شعبية ومشاهدة اليوم على IMDb",
            items = top10TvSeries,
            showRankBadge = true,
            onItemClick = { media ->
              executeMultiSourceSearch(media.title)
            }
          )
        }
      }
    }
  }

  if (showSourcesDialog) {
    SourcesManagementDialog(
      repository = repository,
      onDismiss = { showSourcesDialog = false }
    )
  }
}

@Composable
fun ClipboxNavigationRail(
  activeSection: ClipboxRailSection,
  isCompactMobile: Boolean,
  onSelectSection: (ClipboxRailSection) -> Unit,
  onOpenBookmarks: () -> Unit,
  onOpenHistory: () -> Unit,
  onOpenSources: () -> Unit,
  onOpenUpdate: () -> Unit
) {
  val railWidth = if (isCompactMobile) 46.dp else 56.dp
  val iconBoxSize = if (isCompactMobile) 34.dp else 40.dp

  Box(
    modifier = Modifier
      .fillMaxHeight()
      .padding(start = if (isCompactMobile) 6.dp else 10.dp, top = 10.dp, bottom = 10.dp),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      color = ClipboxPillBackground,
      shape = RoundedCornerShape(50),
      border = BorderStroke(1.dp, CinemaBorder),
      modifier = Modifier
        .width(railWidth)
        .padding(vertical = 4.dp)
    ) {
      Column(
        modifier = Modifier
          .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Search Icon
        ClipboxRailIcon(
          icon = Icons.Default.Search,
          contentDescription = "بحث",
          isActive = activeSection == ClipboxRailSection.SEARCH,
          size = iconBoxSize,
          onClick = { onSelectSection(ClipboxRailSection.SEARCH) }
        )

        Box(
          modifier = Modifier
            .width(20.dp)
            .height(1.dp)
            .background(CinemaBorder)
        )

        // Home Icon (White Circle when active, matching Clipbox screenshot)
        ClipboxRailIcon(
          icon = Icons.Default.Home,
          contentDescription = "الرئيسية",
          isActive = activeSection == ClipboxRailSection.HOME,
          size = iconBoxSize,
          onClick = { onSelectSection(ClipboxRailSection.HOME) }
        )

        // Movies Icon
        ClipboxRailIcon(
          icon = Icons.Default.Movie,
          contentDescription = "الأفلام",
          isActive = activeSection == ClipboxRailSection.MOVIES,
          size = iconBoxSize,
          onClick = { onSelectSection(ClipboxRailSection.MOVIES) }
        )

        // TV Shows Icon
        ClipboxRailIcon(
          icon = Icons.Default.Tv,
          contentDescription = "المسلسلات",
          isActive = activeSection == ClipboxRailSection.TV_SHOWS,
          size = iconBoxSize,
          onClick = { onSelectSection(ClipboxRailSection.TV_SHOWS) }
        )

        // Bookmarks Icon
        ClipboxRailIcon(
          icon = Icons.Default.Bookmark,
          contentDescription = "المفضلة",
          isActive = false,
          size = iconBoxSize,
          onClick = onOpenBookmarks
        )

        // History Icon
        ClipboxRailIcon(
          icon = Icons.Default.History,
          contentDescription = "السجل",
          isActive = false,
          size = iconBoxSize,
          onClick = onOpenHistory
        )

        Box(
          modifier = Modifier
            .width(20.dp)
            .height(1.dp)
            .background(CinemaBorder)
        )

        // Sources Settings Icon
        ClipboxRailIcon(
          icon = Icons.Default.Settings,
          contentDescription = "إدارة المصادر",
          isActive = false,
          size = iconBoxSize,
          onClick = onOpenSources
        )

        // App Update Icon
        ClipboxRailIcon(
          icon = Icons.Default.SystemUpdate,
          contentDescription = "التحديثات",
          isActive = false,
          size = iconBoxSize,
          onClick = onOpenUpdate
        )
      }
    }
  }
}

@Composable
private fun ClipboxRailIcon(
  icon: ImageVector,
  contentDescription: String,
  isActive: Boolean,
  size: androidx.compose.ui.unit.Dp,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .size(size)
      .clip(CircleShape)
      .background(if (isActive) ClipboxWhite else Color.Transparent)
      .clickable { onClick() },
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = if (isActive) Color.Black else Color.LightGray,
      modifier = Modifier.size(20.dp)
    )
  }
}

@Composable
fun ClipboxTopSearchBar(
  searchQuery: String,
  onQueryChange: (String) -> Unit,
  onExecuteSearch: () -> Unit,
  onClearSearch: () -> Unit,
  quickSuggestions: List<String>,
  onSuggestionClick: (String) -> Unit,
  onOpenSources: () -> Unit,
  focusRequester: FocusRequester
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
    border = BorderStroke(1.dp, CinemaBorder),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
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
            imageVector = Icons.Default.LiveTv,
            contentDescription = null,
            tint = CinemaPrimary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Asser TV • محرك بحث الأفلام والمسلسلات الشامل",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Surface(
          onClick = onOpenSources,
          color = CinemaSurfaceVariant,
          shape = RoundedCornerShape(50),
          border = BorderStroke(1.dp, CinemaBorder),
          modifier = Modifier.testTag("btn_dashboard_sources")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = "المصادر",
              tint = CinemaAccent,
              modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "المصادر (5)",
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onQueryChange,
          placeholder = {
            Text(
              text = "ابحث عن أي فيلم أو مسلسل (عربي، أجنبي، كوري، تركي، أنمي)...",
              color = Color.Gray,
              fontSize = 12.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          },
          leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = ClipboxWhite)
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = onClearSearch) {
                Icon(Icons.Default.Clear, contentDescription = "مسح", tint = Color.LightGray)
              }
            }
          },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { onExecuteSearch() }),
          colors = TextFieldDefaults.colors(
            focusedContainerColor = CinemaDarkBackground,
            unfocusedContainerColor = CinemaDarkBackground,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedIndicatorColor = ClipboxWhite,
            unfocusedIndicatorColor = CinemaBorder
          ),
          shape = RoundedCornerShape(50),
          modifier = Modifier
            .weight(1f)
            .focusRequester(focusRequester)
            .testTag("home_search_input")
        )

        Button(
          onClick = onExecuteSearch,
          colors = ButtonDefaults.buttonColors(
            containerColor = ClipboxWhite,
            contentColor = Color.Black
          ),
          shape = RoundedCornerShape(50),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("btn_dual_search_action")
        ) {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(17.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "بحث شامل",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Quick Suggestions Row
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(quickSuggestions) { suggestion ->
          val selected = searchQuery.equals(suggestion.trim(), ignoreCase = true)
          FilterChip(
            selected = selected,
            onClick = { onSuggestionClick(suggestion.trim()) },
            label = {
              Text(
                text = suggestion.trim(),
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = ClipboxWhite,
              selectedLabelColor = Color.Black,
              containerColor = CinemaDarkBackground,
              labelColor = Color.LightGray
            ),
            border = FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = selected,
              borderColor = CinemaBorder,
              selectedBorderColor = ClipboxWhite
            ),
            modifier = Modifier.heightIn(min = 30.dp)
          )
        }
      }
    }
  }
}

@Composable
fun ClipboxHeroBanner(
  item: CatalogMediaItem,
  onPlayClick: () -> Unit,
  onNextHero: () -> Unit,
  onAddBookmark: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
    border = BorderStroke(1.dp, CinemaBorder),
    modifier = Modifier
      .fillMaxWidth()
      .height(220.dp)
      .clickable { onPlayClick() }
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // Backdrop Artwork (downsampled & software bitmap on Android 9 for 1GB RAM TV boxes)
      val heroCtx = LocalContext.current
      AsyncImage(
        model = remember(item.backdropUrl, item.posterUrl) {
          ImageRequest.Builder(heroCtx)
            .data(item.backdropUrl.ifBlank { item.posterUrl })
            .size(450, 300)
            .allowHardware(Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
            .crossfade(false)
            .build()
        },
        contentDescription = item.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )

      // Dark Clipbox Gradient Overlay (bottom & side fade)
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Black.copy(alpha = 0.25f),
                Color(0xCC070A0E),
                Color(0xF5070A0E)
              )
            )
          )
      )

      // Content Overlay
      Column(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .fillMaxWidth()
          .padding(16.dp)
      ) {
        Text(
          text = item.title,
          color = Color.White,
          fontSize = 22.sp,
          fontWeight = FontWeight.ExtraBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "${item.year} • ${item.genre}",
            color = Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Star,
              contentDescription = null,
              tint = CinemaAccent,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = item.rating,
              color = CinemaAccent,
              fontSize = 12.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              softWrap = false
            )
          }
        }

        if (item.overview.isNotBlank()) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = item.overview,
            color = Color.LightGray.copy(alpha = 0.9f),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Clipbox Action Buttons Row: White Pill [ ▶ Play ] + Dark Slate [ + | i ] Pill
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Button(
            onClick = onPlayClick,
            colors = ButtonDefaults.buttonColors(
              containerColor = ClipboxWhite,
              contentColor = Color.Black
            ),
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            modifier = Modifier
              .heightIn(min = 40.dp)
              .testTag("hero_play_button")
          ) {
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Play • بحث ومشاهدة",
              fontSize = 12.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              softWrap = false
            )
          }

          // Secondary Clipbox Pill [ + | ⟳ ]
          Surface(
            color = CinemaSurfaceVariant.copy(alpha = 0.9f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, CinemaBorder)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.heightIn(min = 40.dp)
            ) {
              IconButton(
                onClick = onAddBookmark,
                modifier = Modifier.size(40.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Add,
                  contentDescription = "إضافة للمفضلة",
                  tint = Color.White,
                  modifier = Modifier.size(18.dp)
                )
              }
              Box(
                modifier = Modifier
                  .width(1.dp)
                  .height(18.dp)
                  .background(CinemaBorder)
              )
              IconButton(
                onClick = onNextHero,
                modifier = Modifier.size(40.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Info,
                  contentDescription = "عرض عمل آخر",
                  tint = Color.White,
                  modifier = Modifier.size(17.dp)
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
fun ClipboxCatalogRow(
  title: String,
  subtitle: String,
  items: List<CatalogMediaItem>,
  showRankBadge: Boolean,
  onItemClick: (CatalogMediaItem) -> Unit
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = title,
      color = Color.White,
      fontSize = 16.sp,
      fontWeight = FontWeight.ExtraBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    Text(
      text = subtitle,
      color = Color.Gray,
      fontSize = 11.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )

    Spacer(modifier = Modifier.height(10.dp))

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      itemsIndexed(items, key = { _, it -> it.id }) { index, media ->
        ClipboxPosterCard(
          item = media,
          rankNumber = if (showRankBadge) index + 1 else null,
          onClick = { onItemClick(media) }
        )
      }
    }
  }
}

@Composable
fun ClipboxPosterCard(
  item: CatalogMediaItem,
  rankNumber: Int? = null,
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Column(
    modifier = Modifier
      .width(138.dp)
      .focusable(interactionSource = interactionSource)
      .clickable { onClick() }
      .testTag("catalog_card_${item.id}")
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(196.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(CinemaSurface)
        .border(
          width = if (isFocused) 2.dp else 1.dp,
          color = if (isFocused) ClipboxWhite else CinemaBorder,
          shape = RoundedCornerShape(14.dp)
        )
    ) {
      val posterCtx = LocalContext.current
      AsyncImage(
        model = remember(item.posterUrl) {
          ImageRequest.Builder(posterCtx)
            .data(item.posterUrl)
            .size(300, 450)
            .allowHardware(Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
            .crossfade(false)
            .build()
        },
        contentDescription = item.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )

      // Top-Left Rank Badge for Top 10 Movies & Top 10 TV Series
      if (rankNumber != null) {
        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(CinemaPrimary)
            .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
          Text(
            text = "#$rankNumber",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
          )
        }
      }

      // Bottom Dark Gradient Bar with ★ Rating on Left and Year on Right (Exact Clipbox Card Style)
      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Transparent,
                Color(0xD9070A0E),
                Color(0xF5070A0E)
              )
            )
          )
          .padding(horizontal = 8.dp, vertical = 6.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "★",
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = item.rating,
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              softWrap = false
            )
          }

          Text(
            text = item.year,
            color = Color.LightGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = item.title,
      color = Color.White,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesManagementDialog(
  repository: TvPreferencesRepository,
  onDismiss: () -> Unit
) {
  var selectedPortalForEdit by remember { mutableStateOf<StreamingPortal?>(null) }

  var editNameAr by remember { mutableStateOf("") }
  var editNameEn by remember { mutableStateOf("") }
  var editHomeUrl by remember { mutableStateOf("") }
  var editSearchUrl by remember { mutableStateOf("") }
  var editEnabled by remember { mutableStateOf(true) }

  BasicAlertDialog(
    onDismissRequest = {
      if (selectedPortalForEdit != null) {
        selectedPortalForEdit = null
      } else {
        onDismiss()
      }
    }
  ) {
    Card(
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = CinemaSurface),
      border = BorderStroke(1.5.dp, CinemaBorder),
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(16.dp)
      ) {
        if (selectedPortalForEdit == null) {
          Text(
            text = "⚙️ إدارة وبوابات مصادر الفيديو",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "جميع المواقع الـ 5 المعتمدة للبحث والمشاهدة المباشرة داخل المشغل:",
            color = Color.LightGray,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
          )
          Spacer(modifier = Modifier.height(12.dp))

          Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            StreamingPortal.values().forEach { portal ->
              val isCustom = portal == StreamingPortal.PORTAL_5
              Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CinemaDarkBackground),
                border = BorderStroke(1.dp, if (portal.isEnabled) CinemaBorder else Color.DarkGray),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.weight(1f)
                    ) {
                      Box(
                        modifier = Modifier
                          .size(9.dp)
                          .clip(CircleShape)
                          .background(if (portal.isEnabled) CinemaAccent else Color.Gray)
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = "${portal.displayNameArabic} (${portal.displayNameEnglish})",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                      onClick = {
                        selectedPortalForEdit = portal
                        editNameAr = portal.displayNameArabic
                        editNameEn = portal.displayNameEnglish
                        editHomeUrl = portal.homeUrl
                        editSearchUrl = portal.searchBaseUrl
                        editEnabled = portal.isEnabled
                      },
                      colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
                      shape = RoundedCornerShape(8.dp),
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                      modifier = Modifier.heightIn(min = 34.dp)
                    ) {
                      Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("تعديل", fontSize = 11.sp, color = Color.White, maxLines = 1, softWrap = false)
                    }
                  }

                  Spacer(modifier = Modifier.height(4.dp))

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(
                      text = if (portal.homeUrl.isNotBlank()) portal.homeUrl else "غير مهيأ بعد (مغلق)",
                      color = if (portal.isEnabled) CinemaAccent else Color.Gray,
                      fontSize = 11.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f)
                    )

                    if (isCustom) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                          checked = portal.isEnabled,
                          onCheckedChange = { checked ->
                            repository.savePortal(
                              portal = portal,
                              nameAr = portal.displayNameArabic,
                              nameEn = portal.displayNameEnglish,
                              homeUrl = portal.homeUrl,
                              searchUrl = portal.searchBaseUrl,
                              enabled = checked
                            )
                          },
                          colors = CheckboxDefaults.colors(
                            checkedColor = CinemaAccent,
                            uncheckedColor = Color.Gray,
                            checkmarkColor = Color.Black
                          )
                        )
                        Text(
                          text = if (portal.isEnabled) "مفعل" else "مغلق",
                          color = Color.LightGray,
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

          Spacer(modifier = Modifier.height(14.dp))
          Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(
              containerColor = ClipboxWhite,
              contentColor = Color.Black
            ),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 46.dp)
          ) {
            Text(
              text = "إغلاق",
              fontSize = 14.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              softWrap = false
            )
          }
        } else {
          val portal = selectedPortalForEdit!!
          val isCustom = portal == StreamingPortal.PORTAL_5

          Text(
            text = "✏️ تعديل مصدر: ${portal.displayNameArabic}",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
          )
          Spacer(modifier = Modifier.height(14.dp))

          if (isCustom) {
            Text("اسم الموقع بالعربية:", color = Color.LightGray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
              value = editNameAr,
              onValueChange = { editNameAr = it },
              singleLine = true,
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = CinemaAccent
              ),
              modifier = Modifier.fillMaxWidth(),
              placeholder = { Text("مثال: أكوام", fontSize = 11.sp, color = Color.Gray) }
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text("اسم الموقع بالإنجليزية:", color = Color.LightGray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
              value = editNameEn,
              onValueChange = { editNameEn = it },
              singleLine = true,
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = CinemaAccent
              ),
              modifier = Modifier.fillMaxWidth(),
              placeholder = { Text("Example: Akoam", fontSize = 11.sp, color = Color.Gray) }
            )
            Spacer(modifier = Modifier.height(10.dp))
          }

          Text("الرابط الرئيسي (Home URL):", color = Color.LightGray, fontSize = 11.sp)
          Spacer(modifier = Modifier.height(4.dp))
          OutlinedTextField(
            value = editHomeUrl,
            onValueChange = { editHomeUrl = it },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedBorderColor = CinemaAccent
            ),
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://example.com/", fontSize = 11.sp, color = Color.Gray) }
          )
          Spacer(modifier = Modifier.height(10.dp))

          Text("رابط البحث (Search URL):", color = Color.LightGray, fontSize = 11.sp)
          Spacer(modifier = Modifier.height(4.dp))
          OutlinedTextField(
            value = editSearchUrl,
            onValueChange = { editSearchUrl = it },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedBorderColor = CinemaAccent
            ),
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://example.com/search/%s", fontSize = 11.sp, color = Color.Gray) }
          )
          Spacer(modifier = Modifier.height(14.dp))

          if (isCustom) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.clickable { editEnabled = !editEnabled }
            ) {
              Checkbox(
                checked = editEnabled,
                onCheckedChange = { editEnabled = it },
                colors = CheckboxDefaults.colors(
                  checkedColor = CinemaAccent,
                  uncheckedColor = Color.Gray,
                  checkmarkColor = Color.Black
                )
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("تفعيل هذا المصدر المخصص وعرضه في القوائم والبحث", color = Color.White, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = { selectedPortalForEdit = null },
              colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
            ) {
              Text("إلغاء", fontSize = 13.sp, color = Color.White, maxLines = 1, softWrap = false)
            }

            Button(
              onClick = {
                if (editHomeUrl.isNotBlank()) {
                  repository.savePortal(
                    portal = portal,
                    nameAr = editNameAr,
                    nameEn = editNameEn,
                    homeUrl = editHomeUrl,
                    searchUrl = editSearchUrl,
                    enabled = if (isCustom) editEnabled else true
                  )
                  selectedPortalForEdit = null
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = CinemaAccent),
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
            ) {
              Text("حفظ 💾", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
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
  onItemClick: (SearchResultItem) -> Unit,
  spellingSuggestion: String? = null,
  onSearchSuggestion: ((String) -> Unit)? = null,
  onLaunchManualUrl: ((String) -> Unit)? = null
) {
  val badgeColor = when (portal) {
    StreamingPortal.TOP_CINEMA -> CinemaPrimary
    StreamingPortal.WATCH_SERIES -> CinemaSecondary
    StreamingPortal.LAROZA -> CinemaAccent
    StreamingPortal.EGY_DEAD -> Color(0xFF10B981)
    StreamingPortal.FLIXER -> Color(0xFF8B5CF6)
    else -> CinemaSurfaceVariant
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        Box(
          modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(badgeColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "موقع ${portal.displayNameArabic} (${portal.displayNameEnglish})",
          color = Color.White,
          fontSize = 14.sp,
          fontWeight = FontWeight.ExtraBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Text(
        text = "${results.size} نتيجة",
        color = CinemaAccent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (results.isEmpty()) {
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "لا توجد نتائج مفهرسة حالياً في ${portal.displayNameArabic} لـ \"$query\"",
            color = Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(6.dp))

          if (spellingSuggestion != null && onSearchSuggestion != null) {
            Button(
              onClick = { onSearchSuggestion(spellingSuggestion) },
              colors = ButtonDefaults.buttonColors(containerColor = CinemaAccent, contentColor = Color.Black),
              shape = RoundedCornerShape(50),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
              modifier = Modifier.heightIn(min = 36.dp)
            ) {
              Text(text = "هل تقصد: $spellingSuggestion ؟", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(modifier = Modifier.height(8.dp))
          }

          if (onLaunchManualUrl != null) {
            Button(
              onClick = { onLaunchManualUrl(portal.buildSearchUrl(query)) },
              colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
              shape = RoundedCornerShape(50),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
              modifier = Modifier.heightIn(min = 36.dp)
            ) {
              Text("فتح بحث ${portal.displayNameArabic} مباشرة في المشغل 🔍", fontSize = 11.sp, color = Color.White, maxLines = 1)
            }
          }
        }
      }
    } else {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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

  Column(
    modifier = Modifier
      .width(142.dp)
      .focusable(interactionSource = interactionSource)
      .clickable { onClick() }
      .testTag("result_card_${item.id}")
  ) {
    // Clipbox Vertical Poster Card
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(190.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(CinemaSurface)
        .border(
          width = if (isFocused) 2.dp else 1.dp,
          color = if (isFocused) ClipboxWhite else CinemaBorder,
          shape = RoundedCornerShape(14.dp)
        )
    ) {
      if (item.posterUrl.isNotBlank()) {
        val searchPosterCtx = LocalContext.current
        AsyncImage(
          model = remember(item.posterUrl) {
            ImageRequest.Builder(searchPosterCtx)
              .data(item.posterUrl)
              .size(300, 450)
              .allowHardware(Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
              .crossfade(false)
              .build()
          },
          contentDescription = item.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Icon(Icons.Default.Movie, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(38.dp))
        }
      }

      // Top-Right Quality Badge
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(6.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(Color.Black.copy(alpha = 0.78f))
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text(
          text = item.quality,
          color = CinemaAccent,
          fontSize = 9.sp,
          fontWeight = FontWeight.ExtraBold,
          maxLines = 1,
          softWrap = false
        )
      }

      // Top-Left Portal/Type Badge
      Box(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(6.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(badgeColor.copy(alpha = 0.9f))
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text(
          text = item.type,
          color = Color.White,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          softWrap = false
        )
      }

      // Bottom Clipbox Overlay (Rating + Year)
      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Transparent,
                Color(0xD9070A0E),
                Color(0xF5070A0E)
              )
            )
          )
          .padding(horizontal = 8.dp, vertical = 6.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "★ ${item.rating}",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
          )
          Text(
            text = item.year.ifBlank { "HD" },
            color = Color.LightGray,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = item.title,
      color = Color.White,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
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
        border = BorderStroke(1.dp, CinemaBorder)
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
            text = "هل تريد حذف جميع عناصر متابعة المشاهدة والبدء من جديد؟",
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
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text("إلغاء", color = Color.White, maxLines = 1, softWrap = false)
            }
            Button(
              onClick = {
                showConfirmClearDialog = false
                onClearAll()
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text("نعم، مسح", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
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
      Text(
        text = "Continue Watching • متابعة المشاهدة",
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f)
      )

      if (history.isNotEmpty()) {
        Surface(
          onClick = { showConfirmClearDialog = true },
          color = CinemaSurface,
          shape = RoundedCornerShape(50),
          border = BorderStroke(1.dp, CinemaBorder),
          modifier = Modifier.testTag("btn_clear_continue_watching")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.DeleteSweep,
              contentDescription = "مسح السجل",
              tint = Color(0xFFFF5252),
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "مسح السجل",
              color = Color.LightGray,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
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

@Composable
fun ContinueWatchingCard(
  entry: HistoryEntry,
  onResume: () -> Unit,
  onDelete: () -> Unit
) {
  val formattedDate = remember(entry.timestamp) {
    try {
      val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
      sdf.format(Date(entry.timestamp))
    } catch (_: Exception) {
      ""
    }
  }

  Column(
    modifier = Modifier
      .width(165.dp)
      .clickable { onResume() }
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(105.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(CinemaSurface)
        .border(1.dp, CinemaBorder, RoundedCornerShape(14.dp))
    ) {
      // Top row: Portal badge + delete button
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CinemaSurfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(
            text = entry.portal.displayNameArabic,
            color = CinemaAccent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
          )
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier.size(24.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "حذف",
            tint = Color.LightGray,
            modifier = Modifier.size(14.dp)
          )
        }
      }

      // Center Play Circle
      Box(
        modifier = Modifier
          .align(Alignment.Center)
          .size(34.dp)
          .clip(CircleShape)
          .background(ClipboxWhite),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription = null,
          tint = Color.Black,
          modifier = Modifier.size(20.dp)
        )
      }

      // Bottom Meta + Clipbox Red Progress Line
      Column(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "استكمال",
            color = Color.LightGray,
            fontSize = 9.sp,
            maxLines = 1
          )
          Text(
            text = formattedDate,
            color = Color.Gray,
            fontSize = 9.sp,
            maxLines = 1
          )
        }
        // Clipbox signature red progress bar at the bottom edge
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color.DarkGray)
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth(0.65f)
              .height(3.dp)
              .background(CinemaPrimary)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = entry.title,
      color = Color.White,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

fun getSpellingSuggestion(query: String): String? {
  val clean = query.trim().lowercase()
  if (clean.isBlank()) return null

  val popularShows = listOf(
    "Futurama", "Silo", "Z Nation", "The Walking Dead", "Peaky Blinders",
    "Breaking Bad", "Game of Thrones", "From", "الحشاشين", "Severance",
    "The Last of Us", "Succession", "Friends", "Stranger Things", "Dark",
    "The Boys", "Rick and Morty", "Sherlock", "Fargo", "True Detective",
    "Dexter", "The Office", "Black Mirror", "Better Call Saul", "Vikings",
    "House of the Dragon", "Narcos", "Squid Game", "Attack on Titan", "Death Note",
    "Simpsons", "Family Guy", "South Park", "Bojack Horseman", "Batman", "Spider-Man",
    "Lost", "Prison Break", "Suits", "Lucifer", "The Crown", "Westworld", "The Witcher",
    "The Mandalorian", "The Bear", "Shogun", "Fallout", "The White Lotus", "Euphoria"
  )

  var bestMatch: String? = null
  var minDistance = 3

  for (show in popularShows) {
    val distance = calculateLevenshteinDistance(clean, show.lowercase())
    if (distance < minDistance) {
      minDistance = distance
      bestMatch = show
    }
  }

  return bestMatch
}

private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
  val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
  for (i in 0..s1.length) {
    dp[i][0] = i
  }
  for (j in 0..s2.length) {
    dp[0][j] = j
  }
  for (i in 1..s1.length) {
    for (j in 1..s2.length) {
      val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
      dp[i][j] = minOf(
        dp[i - 1][j] + 1,
        dp[i][j - 1] + 1,
        dp[i - 1][j - 1] + cost
      )
    }
  }
  return dp[s1.length][s2.length]
}
