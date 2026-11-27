package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TrackChanges
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs
import coil.request.ImageRequest
import com.example.data.AppDisplayMode
import com.example.data.SearchAndSubtitlesService
import com.example.data.TvPreferencesRepository
import com.example.model.CatalogMediaItem
import com.example.model.HistoryEntry
import com.example.model.SearchResultItem
import com.example.model.StreamingPortal
import com.example.model.TraktCalendarItem
import com.example.model.TraktWatchlistItem
import com.example.model.UpNextItem
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
  onOpenSettings: () -> Unit = {},
  onOpenTrakt: () -> Unit = {},
  onPlayUpNext: (UpNextItem) -> Unit = {},
  onMarkUpNextWatched: ((UpNextItem) -> Unit)? = null,
  onPlayTraktCalendarItem: (TraktCalendarItem) -> Unit = {},
  onPlayTraktWatchlistItem: (TraktWatchlistItem) -> Unit = {},
  onSyncTraktFromHome: () -> Unit = {},
  onResumeHistory: ((HistoryEntry) -> Unit)? = null,
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
  var spellingSuggestion by remember { mutableStateOf<String?>(null) }

  // Catalog State (Live Daily IMDb Trending, Top 10 Movies Today, Top 10 TV Series Today)
  var trendingItems by remember { mutableStateOf<List<CatalogMediaItem>>(emptyList()) }
  var top10Movies by remember { mutableStateOf<List<CatalogMediaItem>>(emptyList()) }
  var top10TvSeries by remember { mutableStateOf<List<CatalogMediaItem>>(emptyList()) }
  var isLoadingCatalog by remember { mutableStateOf(true) }
  var catalogRefreshTrigger by remember { mutableIntStateOf(0) }
  val todayDateLabel = remember {
    SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
  }
  val railFocusRequester = remember { FocusRequester() }
  val contentFocusRequester = remember { FocusRequester() }

  val history by repository.historyFlow.collectAsState()
  val upNextQueue by repository.upNextFlow.collectAsState()
  val traktConnected by repository.traktConnectedFlow.collectAsState()
  val traktUsername by repository.traktUsernameFlow.collectAsState()
  val traktLastSyncError by repository.traktLastSyncErrorFlow.collectAsState()
  val traktCalendar by repository.traktCalendarFlow.collectAsState()
  val traktWatchlist by repository.traktWatchlistFlow.collectAsState()
  val displayMode by repository.displayModeFlow.collectAsState()

  val displayedUpNext = remember(upNextQueue, traktConnected) {
    upNextQueue.filter { item ->
      item.fromTrakt &&
        item.seasonNumber > 0 &&
        item.episodeNumber > 0 &&
        item.firstAiredIso.isNotBlank() &&
        item.watchedCount > 0 &&
        item.totalCount > item.watchedCount
    }
  }

  // Load Live Daily Catalog via single lightweight pass after first frame paints
  LaunchedEffect(catalogRefreshTrigger) {
    isLoadingCatalog = true
    if (catalogRefreshTrigger == 0) {
      kotlinx.coroutines.delay(180L)
    }
    val bundle = searchService.fetchHomeCatalogBundle(forceRefresh = catalogRefreshTrigger > 0)
    trendingItems = bundle.trending
    top10Movies = bundle.top10Movies
    top10TvSeries = bundle.top10TvSeries
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

  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    BoxWithConstraints(
      modifier = modifier
        .fillMaxSize()
        .background(CinemaDarkBackground)
    ) {
      val isCompactMobile = displayMode == AppDisplayMode.MOBILE || maxWidth < 720.dp

      Row(modifier = Modifier.fillMaxSize()) {
        // 1. Clipbox Floating Pill Navigation Rail (Hidden completely on mobile/portrait phones to reclaim full width)
        if (!isCompactMobile) {
          ClipboxNavigationRail(
            activeSection = activeSection,
            isCompactMobile = false,
            railFocusRequester = railFocusRequester,
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
            onOpenTrakt = onOpenTrakt,
            onOpenSources = onOpenSettings,
            onOpenUpdate = onOpenUpdate
          )
        }

        // 2. Main Clipbox Content Area (Dedicated FocusGroup for instant D-Pad traversal without losing focus)
        val mainDragSlopPx = with(LocalDensity.current) { 8.dp.toPx() }
        LazyColumn(
          state = listState,
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .focusRequester(contentFocusRequester)
            .focusGroup()
            .mouseVerticalDragScroll(
              listState = listState,
              scope = scope,
              dragSlopPx = mainDragSlopPx
            )
            .padding(horizontal = if (isCompactMobile) 12.dp else 16.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          // Top Search Bar (with compact Trakt link button when disconnected)
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
              onOpenSources = onOpenSettings,
              traktConnected = traktConnected,
              onOpenTrakt = onOpenTrakt,
              focusRequester = searchInputFocusRequester,
              isCompactMobile = isCompactMobile
            )
          }

      // Multi-Source Search Results Section
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
              CircularProgressIndicator(color = CinemaAccent, modifier = Modifier.size(38.dp))
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "جاري البحث عن \"$searchQuery\"...",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
              )
              Spacer(modifier = Modifier.height(5.dp))
              Text(
                text = "توب سينما • لاروزا • ووتش سيريز • إيجي ديد • فليكسر",
                color = CinemaAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
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
              text = "نتائج \"$searchQuery\"",
              color = Color.White,
              fontSize = 18.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f)
            )
            Surface(
              onClick = { hasSearched = false },
              color = CinemaSurfaceVariant,
              shape = RoundedCornerShape(50),
              border = BorderStroke(1.dp, CinemaBorder),
              modifier = Modifier.heightIn(min = 38.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = "إغلاق ✕",
                  color = Color.LightGray,
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                  maxLines = 1,
                  softWrap = false
                )
              }
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

      val showUpNextSection = traktConnected || displayedUpNext.isNotEmpty() || !traktLastSyncError.isNullOrBlank()
      val showContinueFirst = displayedUpNext.isEmpty() && history.isNotEmpty() && traktLastSyncError.isNullOrBlank()

      // First content slot: Continue Watching (متابعة المشاهدة) or Up Next (التالي للمشاهدة)
      if (showContinueFirst) {
        item {
          ContinueWatchingSection(
            history = history,
            repository = repository,
            onResumeItem = { entry ->
              if (onResumeHistory != null) {
                onResumeHistory(entry)
              } else {
                onLaunchResultWithSources(
                  entry.url,
                  entry.portal,
                  entry.title,
                  entry.title,
                  portalResultsMap
                )
              }
            },
            onDeleteItem = { entry ->
              repository.removeHistoryEntry(entry.id)
            },
            onClearAll = {
              repository.clearHistory()
            }
          )
        }
        if (showUpNextSection) {
          item {
            UpNextQueueSection(
              items = displayedUpNext,
              onPlayItem = onPlayUpNext,
              onMarkWatched = { item ->
                if (onMarkUpNextWatched != null) {
                  onMarkUpNextWatched(item)
                }
              },
              onOpenTrakt = onOpenTrakt,
              onSyncTrakt = if (traktConnected) onSyncTraktFromHome else null,
              traktUsername = if (traktConnected) traktUsername else "",
              syncErrorMessage = traktLastSyncError,
              onDeleteItem = { item ->
                repository.removeUpNextItem(item.id)
              },
              onClearAll = {
                repository.clearUpNext()
              }
            )
          }
        }
      } else {
        if (showUpNextSection) {
          item {
            UpNextQueueSection(
              items = displayedUpNext,
              onPlayItem = onPlayUpNext,
              onMarkWatched = { item ->
                if (onMarkUpNextWatched != null) {
                  onMarkUpNextWatched(item)
                }
              },
              onOpenTrakt = onOpenTrakt,
              onSyncTrakt = if (traktConnected) onSyncTraktFromHome else null,
              traktUsername = if (traktConnected) traktUsername else "",
              syncErrorMessage = traktLastSyncError,
              onDeleteItem = { item ->
                repository.removeUpNextItem(item.id)
              },
              onClearAll = {
                repository.clearUpNext()
              }
            )
          }
        }

        if (history.isNotEmpty()) {
          item {
            ContinueWatchingSection(
              history = history,
              repository = repository,
              onResumeItem = { entry ->
                if (onResumeHistory != null) {
                  onResumeHistory(entry)
                } else {
                  onLaunchResultWithSources(
                    entry.url,
                    entry.portal,
                    entry.title,
                    entry.title,
                    portalResultsMap
                  )
                }
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
      }

      // Live Daily IMDb Status Strip & Manual Refresh (Clean BiDi-safe 2-line layout with isolated LTR date)
      item {
        val refreshInteraction = remember { MutableInteractionSource() }
        val isRefreshFocused by refreshInteraction.collectIsFocusedAsState()
        val refreshScale by animateFloatAsState(
          targetValue = if (isRefreshFocused) 1.06f else 1f,
          animationSpec = tween(durationMillis = 100),
          label = "imdb_refresh_scale"
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CinemaSurface)
            .border(1.dp, CinemaBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(9.dp)
                  .clip(CircleShape)
                  .background(if (isLoadingCatalog) CinemaAccent else CinemaSuccess)
              )
              Surface(
                color = CinemaAccent,
                shape = RoundedCornerShape(6.dp)
              ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                  Text(
                    text = "IMDb",
                    color = Color.Black,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    maxLines = 1,
                    softWrap = false
                  )
                }
              }
              Text(
                text = if (isLoadingCatalog) "جاري التحديث..." else "قوائم اليوم",
                color = Color.White,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (!isLoadingCatalog) {
                Surface(
                  color = CinemaDarkBackground,
                  shape = RoundedCornerShape(6.dp),
                  border = BorderStroke(1.dp, CinemaBorder)
                ) {
                  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                      text = todayDateLabel,
                      color = CinemaAccent,
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.ExtraBold,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                      maxLines = 1,
                      softWrap = false
                    )
                  }
                }
              }
            }
          }

          Spacer(modifier = Modifier.width(10.dp))

          Surface(
            onClick = { catalogRefreshTrigger++ },
            interactionSource = refreshInteraction,
            color = if (isRefreshFocused) CinemaFocusGlow else CinemaDarkBackground,
            shape = RoundedCornerShape(50),
            border = BorderStroke(
              width = if (isRefreshFocused) 2.dp else 1.dp,
              color = if (isRefreshFocused) ClipboxWhite else CinemaAccent.copy(alpha = 0.75f)
            ),
            modifier = Modifier
              .graphicsLayer {
                scaleX = refreshScale
                scaleY = refreshScale
              }
              .heightIn(min = 40.dp)
              .testTag("btn_refresh_imdb_catalog")
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
              Text(
                text = "تحديث ⟳",
                color = if (isRefreshFocused) Color.Black else CinemaAccent,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
              )
            }
          }
        }
      }

      // Trending Now (IMDb الرائج اليوم)
      if (activeSection == ClipboxRailSection.HOME || activeSection == ClipboxRailSection.SEARCH) {
        item {
          ClipboxCatalogRow(
            title = "الرائج اليوم",
            badgeLabel = "IMDb",
            subtitle = "",
            items = trendingItems,
            showRankBadge = false,
            showMouseScrollArrows = !isCompactMobile,
            onItemClick = { media ->
              executeMultiSourceSearch(media.title)
            }
          )
        }
      }

      // Top 10 Movies Today (أفضل 10 أفلام اليوم)
      if (activeSection == ClipboxRailSection.HOME || activeSection == ClipboxRailSection.MOVIES || activeSection == ClipboxRailSection.SEARCH) {
        item {
          ClipboxCatalogRow(
            title = "أفضل 10 أفلام",
            badgeLabel = "Top 10",
            subtitle = "",
            items = top10Movies,
            showRankBadge = true,
            showMouseScrollArrows = !isCompactMobile,
            onItemClick = { media ->
              executeMultiSourceSearch(media.title)
            }
          )
        }
      }

      // Top 10 TV Series Today (أفضل 10 مسلسلات اليوم)
      if (activeSection == ClipboxRailSection.HOME || activeSection == ClipboxRailSection.TV_SHOWS || activeSection == ClipboxRailSection.SEARCH) {
        item {
          ClipboxCatalogRow(
            title = "أفضل 10 مسلسلات",
            badgeLabel = "Top 10",
            subtitle = "",
            items = top10TvSeries,
            showRankBadge = true,
            showMouseScrollArrows = !isCompactMobile,
            onItemClick = { media ->
              executeMultiSourceSearch(media.title)
            }
          )
        }
      }
        }
      }
    }
  }
}

@Composable
fun ClipboxNavigationRail(
  activeSection: ClipboxRailSection,
  isCompactMobile: Boolean,
  railFocusRequester: FocusRequester = remember { FocusRequester() },
  onSelectSection: (ClipboxRailSection) -> Unit,
  onOpenBookmarks: () -> Unit,
  onOpenHistory: () -> Unit,
  onOpenTrakt: () -> Unit = {},
  onOpenSources: () -> Unit,
  onOpenUpdate: () -> Unit
) {
  val railWidth = if (isCompactMobile) 48.dp else 60.dp
  val iconBoxSize = if (isCompactMobile) 36.dp else 44.dp

  Box(
    modifier = Modifier
      .fillMaxHeight()
      .focusRequester(railFocusRequester)
      .focusGroup()
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

        // Trakt Tracker Icon
        ClipboxRailIcon(
          icon = Icons.Default.TrackChanges,
          contentDescription = "Trakt",
          isActive = false,
          size = iconBoxSize,
          onClick = onOpenTrakt
        )

        Box(
          modifier = Modifier
            .width(20.dp)
            .height(1.dp)
            .background(CinemaBorder)
        )

        // Unified Settings Gear Icon
        ClipboxRailIcon(
          icon = Icons.Default.Settings,
          contentDescription = "الإعدادات",
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
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isFocused) 1.14f else 1f,
    animationSpec = tween(durationMillis = 100),
    label = "rail_icon_scale"
  )

  Box(
    modifier = Modifier
      .size(size)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .clip(CircleShape)
      .background(
        when {
          isFocused -> CinemaFocusGlow
          isActive -> ClipboxWhite
          else -> Color.Transparent
        }
      )
      .border(
        width = if (isFocused) 2.5.dp else 0.dp,
        color = if (isFocused) ClipboxWhite else Color.Transparent,
        shape = CircleShape
      )
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
      ),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = if (isFocused || isActive) Color.Black else Color.LightGray,
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
  traktConnected: Boolean = true,
  onOpenTrakt: () -> Unit = {},
  focusRequester: FocusRequester,
  isCompactMobile: Boolean = false
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
            modifier = Modifier.size(22.dp)
          )
          Spacer(modifier = Modifier.width(7.dp))
          Text(
            text = "بحث Asser TV",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (!traktConnected) {
            val traktBtnInteraction = remember { MutableInteractionSource() }
            val isTraktBtnFocused by traktBtnInteraction.collectIsFocusedAsState()
            Surface(
              onClick = onOpenTrakt,
              interactionSource = traktBtnInteraction,
              color = if (isTraktBtnFocused) CinemaFocusGlow else Color(0xFFED1C24),
              shape = RoundedCornerShape(50),
              border = if (isTraktBtnFocused) BorderStroke(2.dp, ClipboxWhite) else null,
              modifier = Modifier
                .heightIn(min = 38.dp)
                .testTag("btn_home_connect_trakt_code")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Key,
                  contentDescription = "ربط Trakt",
                  tint = if (isTraktBtnFocused) Color.Black else Color.White,
                  modifier = Modifier.size(14.dp)
                )
                Text(
                  text = "ربط Trakt",
                  color = if (isTraktBtnFocused) Color.Black else Color.White,
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.ExtraBold,
                  maxLines = 1,
                  softWrap = false
                )
              }
            }
          }

          val settingsInteraction = remember { MutableInteractionSource() }
          val isSettingsFocused by settingsInteraction.collectIsFocusedAsState()
          Surface(
            onClick = onOpenSources,
            interactionSource = settingsInteraction,
            color = if (isSettingsFocused) CinemaFocusGlow else CinemaSurfaceVariant,
            shape = RoundedCornerShape(50),
            border = BorderStroke(
              width = if (isSettingsFocused) 2.dp else 1.dp,
              color = if (isSettingsFocused) ClipboxWhite else CinemaBorder
            ),
            modifier = Modifier
              .size(40.dp)
              .testTag("btn_dashboard_settings_gear")
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "الإعدادات",
                tint = if (isSettingsFocused) Color.Black else CinemaAccent,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onQueryChange,
          textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium),
          placeholder = {
            Text(
              text = "ابحث عن فيلم أو مسلسل...",
              color = Color.Gray,
              fontSize = 14.5.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          },
          leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = ClipboxWhite, modifier = Modifier.size(22.dp))
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

        val searchBtnInteraction = remember { MutableInteractionSource() }
        val isSearchBtnFocused by searchBtnInteraction.collectIsFocusedAsState()
        Button(
          onClick = onExecuteSearch,
          interactionSource = searchBtnInteraction,
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isSearchBtnFocused) CinemaFocusGlow else ClipboxWhite,
            contentColor = Color.Black
          ),
          border = if (isSearchBtnFocused) BorderStroke(2.5.dp, ClipboxWhite) else null,
          shape = RoundedCornerShape(50),
          contentPadding = PaddingValues(horizontal = if (isCompactMobile) 16.dp else 20.dp, vertical = 12.dp),
          modifier = Modifier
            .heightIn(min = 52.dp)
            .testTag("btn_dual_search_action")
        ) {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(5.dp))
          Text(
            text = "بحث",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            maxLines = 1,
            softWrap = false
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Quick Suggestions Row (supports mouse drag left/right + touch + crisp D-Pad focus)
      val suggestionsListState = rememberLazyListState()
      val suggestionsScope = rememberCoroutineScope()
      val suggestionsIsRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
      val suggestionsDragSlopPx = with(LocalDensity.current) { 8.dp.toPx() }
      LazyRow(
        state = suggestionsListState,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .focusGroup()
          .mouseHorizontalDragScroll(
            listState = suggestionsListState,
            scope = suggestionsScope,
            isRtl = suggestionsIsRtl,
            dragSlopPx = suggestionsDragSlopPx
          )
      ) {
        itemsIndexed(quickSuggestions) { idx, suggestion ->
          val selected = searchQuery.equals(suggestion.trim(), ignoreCase = true)
          val chipInteraction = remember { MutableInteractionSource() }
          val isChipFocused by chipInteraction.collectIsFocusedAsState()
          FilterChip(
            selected = selected,
            onClick = { onSuggestionClick(suggestion.trim()) },
            interactionSource = chipInteraction,
            label = {
              Text(
                text = suggestion.trim(),
                fontSize = 13.5.sp,
                fontWeight = if (isChipFocused || selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = if (isChipFocused) CinemaFocusGlow else ClipboxWhite,
              selectedLabelColor = Color.Black,
              containerColor = if (isChipFocused) CinemaFocusGlow else CinemaDarkBackground,
              labelColor = if (isChipFocused) Color.Black else Color.LightGray
            ),
            border = FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = selected || isChipFocused,
              borderColor = CinemaBorder,
              selectedBorderColor = ClipboxWhite,
              selectedBorderWidth = if (isChipFocused) 2.dp else 1.dp
            ),
            modifier = Modifier
              .heightIn(min = 38.dp)
              .onFocusChanged { fs ->
                if (fs.isFocused) {
                  suggestionsScope.launch {
                    try {
                      suggestionsListState.animateScrollToItem((idx - 1).coerceAtLeast(0))
                    } catch (_: Throwable) {}
                  }
                }
              }
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
  badgeLabel: String = "",
  showMouseScrollArrows: Boolean = true,
  onItemClick: (CatalogMediaItem) -> Unit
) {
  val rowListState = rememberLazyListState()
  val rowScope = rememberCoroutineScope()
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val dragSlopPx = with(LocalDensity.current) { 8.dp.toPx() }
  val scrollStepPx = with(LocalDensity.current) { 420.dp.toPx() }

  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          if (badgeLabel.isNotBlank()) {
            Surface(
              color = CinemaAccent.copy(alpha = 0.16f),
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.65f))
            ) {
              CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                  text = badgeLabel,
                  color = CinemaAccent,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.ExtraBold,
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                  maxLines = 1,
                  softWrap = false
                )
              }
            }
          }
        }
        if (subtitle.isNotBlank()) {
          Text(
            text = subtitle,
            color = Color.Gray,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      if (showMouseScrollArrows && items.size > 2) {
        Spacer(modifier = Modifier.width(8.dp))
        CarouselMouseScrollArrows(
          listState = rowListState,
          scope = rowScope,
          isRtl = isRtl,
          scrollStepPx = scrollStepPx
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    LazyRow(
      state = rowListState,
      contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .focusGroup()
        .mouseHorizontalDragScroll(
          listState = rowListState,
          scope = rowScope,
          isRtl = isRtl,
          dragSlopPx = dragSlopPx
        )
    ) {
      itemsIndexed(items, key = { _, it -> it.id }) { index, media ->
        ClipboxPosterCard(
          item = media,
          rankNumber = if (showRankBadge) index + 1 else null,
          onFocused = {
            rowScope.launch {
              try {
                rowListState.animateScrollToItem((index - 1).coerceAtLeast(0))
              } catch (_: Throwable) {}
            }
          },
          onClick = { onItemClick(media) }
        )
      }
    }
  }
}

/**
 * Enables smooth left/right click-and-drag scrolling AND horizontal scroll-wheel support for
 * USB/Bluetooth/Air mice on LazyRow (Jetpack Compose's built-in scrollable ignores mouse drags
 * due to `down.type != PointerType.Mouse`). Touch gestures are completely untouched.
 */
private fun Modifier.mouseHorizontalDragScroll(
  listState: LazyListState,
  scope: CoroutineScope,
  isRtl: Boolean,
  dragSlopPx: Float
): Modifier = this
  .pointerInput(listState, isRtl) {
    awaitPointerEventScope {
      while (true) {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.type == PointerType.Touch) {
          continue
        }
        val pointerId = down.id
        var prevX = down.position.x
        var prevY = down.position.y
        var totalDx = 0f
        var totalDy = 0f
        var isDraggingHorizontally = false
        var abortedForVertical = false
        var lastDeltaX = 0f

        while (true) {
          val event = awaitPointerEvent(pass = PointerEventPass.Initial)
          val change = event.changes.firstOrNull { it.id == pointerId } ?: break
          if (!change.pressed) {
            if (isDraggingHorizontally) {
              // Consume release so card clickable does NOT fire after dragging with mouse
              change.consume()
              val momentum = (if (isRtl) lastDeltaX else -lastDeltaX) * 4.5f
              if (abs(momentum) > 12f) {
                scope.launch {
                  try {
                    listState.animateScrollBy(momentum.coerceIn(-900f, 900f))
                  } catch (_: Throwable) {}
                }
              }
            }
            break
          }

          if (change.isConsumed && !isDraggingHorizontally) {
            abortedForVertical = true
            break
          }

          val dx = change.position.x - prevX
          val dy = change.position.y - prevY
          prevX = change.position.x
          prevY = change.position.y
          totalDx += dx
          totalDy += dy

          if (!isDraggingHorizontally && !abortedForVertical) {
            val absX = abs(totalDx)
            val absY = abs(totalDy)
            if (absX > dragSlopPx && absX >= absY * 1.1f) {
              isDraggingHorizontally = true
              val initialDelta = if (isRtl) totalDx else -totalDx
              listState.dispatchRawDelta(initialDelta)
              lastDeltaX = dx
              change.consume()
            } else if (absY > dragSlopPx && absY > absX * 1.15f) {
              abortedForVertical = true
              break
            }
          } else if (isDraggingHorizontally) {
            if (dx != 0f) {
              val scrollDelta = if (isRtl) dx else -dx
              listState.dispatchRawDelta(scrollDelta)
              lastDeltaX = dx
            }
            change.consume()
          }
        }
      }
    }
  }
  .pointerInput(listState, isRtl) {
    awaitPointerEventScope {
      while (true) {
        val event = awaitPointerEvent(pass = PointerEventPass.Main)
        if (event.type == PointerEventType.Scroll) {
          val scrollX = event.changes.firstOrNull()?.scrollDelta?.x ?: 0f
          if (scrollX != 0f) {
            val stepPx = (if (isRtl) -scrollX else scrollX) * 90f
            listState.dispatchRawDelta(stepPx)
            event.changes.forEach { it.consume() }
          }
        }
      }
    }
  }

/**
 * Enables smooth up/down mouse click-and-drag scrolling on LazyColumn without conflicting with
 * horizontal carousel drags or touch scrolling.
 */
private fun Modifier.mouseVerticalDragScroll(
  listState: LazyListState,
  scope: CoroutineScope,
  dragSlopPx: Float
): Modifier = this.pointerInput(listState) {
  awaitPointerEventScope {
    while (true) {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      if (down.type == PointerType.Touch) {
        continue
      }
      val pointerId = down.id
      var prevX = down.position.x
      var prevY = down.position.y
      var totalDx = 0f
      var totalDy = 0f
      var isDraggingVertically = false
      var abortedForHorizontal = false
      var lastDeltaY = 0f

      while (true) {
        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
        if (!change.pressed) {
          if (isDraggingVertically) {
            change.consume()
            val momentum = -lastDeltaY * 4.5f
            if (abs(momentum) > 12f) {
              scope.launch {
                try {
                  listState.animateScrollBy(momentum.coerceIn(-900f, 900f))
                } catch (_: Throwable) {}
              }
            }
          }
          break
        }

        val dx = change.position.x - prevX
        val dy = change.position.y - prevY
        prevX = change.position.x
        prevY = change.position.y
        totalDx += dx
        totalDy += dy

        if (!isDraggingVertically && !abortedForHorizontal) {
          val absX = abs(totalDx)
          val absY = abs(totalDy)
          if (absY > dragSlopPx && absY > absX * 1.25f) {
            isDraggingVertically = true
            listState.dispatchRawDelta(-totalDy)
            lastDeltaY = dy
            change.consume()
          } else if (absX > dragSlopPx && absX >= absY) {
            abortedForHorizontal = true
            break
          }
        } else if (isDraggingVertically) {
          if (dy != 0f) {
            listState.dispatchRawDelta(-dy)
            lastDeltaY = dy
          }
          change.consume()
        }
      }
    }
  }
}

@Composable
private fun CarouselMouseScrollArrows(
  listState: LazyListState,
  scope: CoroutineScope,
  isRtl: Boolean,
  scrollStepPx: Float
) {
  val canScrollBackward by remember(listState) {
    derivedStateOf { listState.canScrollBackward }
  }
  val canScrollForward by remember(listState) {
    derivedStateOf { listState.canScrollForward }
  }

  Row(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
    // Skip mouse-only scroll arrows during TV remote D-Pad traversal for faster row-to-row jumping
    modifier = Modifier.focusProperties { canFocus = false }
  ) {
    // Physical Start/Backward arrow
    Surface(
      onClick = {
        scope.launch {
          try {
            listState.animateScrollBy(-scrollStepPx)
          } catch (_: Throwable) {}
        }
      },
      enabled = canScrollBackward,
      color = if (canScrollBackward) CinemaSurfaceVariant else CinemaSurface.copy(alpha = 0.45f),
      shape = CircleShape,
      border = BorderStroke(
        1.dp,
        if (canScrollBackward) CinemaAccent.copy(alpha = 0.7f) else CinemaBorder.copy(alpha = 0.35f)
      ),
      modifier = Modifier
        .size(32.dp)
        .focusProperties { canFocus = false }
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = if (isRtl) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
          contentDescription = "التمرير للخلف",
          tint = if (canScrollBackward) Color.White else Color.Gray.copy(alpha = 0.5f),
          modifier = Modifier.size(18.dp)
        )
      }
    }

    // Physical End/Forward arrow
    Surface(
      onClick = {
        scope.launch {
          try {
            listState.animateScrollBy(scrollStepPx)
          } catch (_: Throwable) {}
        }
      },
      enabled = canScrollForward,
      color = if (canScrollForward) CinemaSurfaceVariant else CinemaSurface.copy(alpha = 0.45f),
      shape = CircleShape,
      border = BorderStroke(
        1.dp,
        if (canScrollForward) CinemaAccent.copy(alpha = 0.7f) else CinemaBorder.copy(alpha = 0.35f)
      ),
      modifier = Modifier
        .size(32.dp)
        .focusProperties { canFocus = false }
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = if (isRtl) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
          contentDescription = "التمرير للأمام",
          tint = if (canScrollForward) Color.White else Color.Gray.copy(alpha = 0.5f),
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

@Composable
fun ClipboxPosterCard(
  item: CatalogMediaItem,
  rankNumber: Int? = null,
  onFocused: () -> Unit = {},
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isFocused) 1.06f else 1f,
    animationSpec = tween(durationMillis = 100),
    label = "poster_card_scale"
  )

  Column(
    modifier = Modifier
      .width(152.dp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .onFocusChanged { fs ->
        if (fs.isFocused) onFocused()
      }
      // Single shared interactionSource (clickable is already focusable — zero double D-Pad stops!)
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
      )
      .testTag("catalog_card_${item.id}")
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(214.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(CinemaSurface)
        .border(
          width = if (isFocused) 3.dp else 1.dp,
          color = if (isFocused) CinemaFocusGlow else CinemaBorder,
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
            .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Text(
            text = "#$rankNumber",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
          )
        }
      }

      // Bottom Dark Gradient Bar with ★ Rating on Left and Year on Right
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
              color = CinemaAccent,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = item.rating,
              color = Color.White,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              softWrap = false
            )
          }

          Text(
            text = item.year,
            color = Color.LightGray,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(7.dp))

    Text(
      text = item.title,
      color = if (isFocused) CinemaFocusGlow else Color.White,
      fontSize = 14.5.sp,
      fontWeight = if (isFocused) FontWeight.ExtraBold else FontWeight.Bold,
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
            text = "إدارة البوابات والروابط",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
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
                          .size(10.dp)
                          .clip(CircleShape)
                          .background(if (portal.isEnabled) CinemaAccent else Color.Gray)
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = portal.displayNameArabic,
                        color = Color.White,
                        fontSize = 15.5.sp,
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
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                      modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                      Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                      Spacer(modifier = Modifier.width(5.dp))
                      Text("تعديل", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                    }
                  }

                  Spacer(modifier = Modifier.height(5.dp))

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(
                      text = if (portal.homeUrl.isNotBlank()) portal.homeUrl else "غير مفعل",
                      color = if (portal.isEnabled) CinemaAccent else Color.Gray,
                      fontSize = 13.sp,
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
                          fontSize = 13.sp,
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
              .heightIn(min = 48.dp)
          ) {
            Text(
              text = "إغلاق",
              fontSize = 15.5.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              softWrap = false
            )
          }
        } else {
          val portal = selectedPortalForEdit!!
          val isCustom = portal == StreamingPortal.PORTAL_5

          Text(
            text = "تعديل ${portal.displayNameArabic}",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
          )
          Spacer(modifier = Modifier.height(14.dp))

          if (isCustom) {
            Text("الاسم بالعربية:", color = Color.LightGray, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
              value = editNameAr,
              onValueChange = { editNameAr = it },
              singleLine = true,
              textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = CinemaAccent
              ),
              modifier = Modifier.fillMaxWidth(),
              placeholder = { Text("مثال: أكوام", fontSize = 14.sp, color = Color.Gray) }
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text("الاسم بالإنجليزية:", color = Color.LightGray, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
              value = editNameEn,
              onValueChange = { editNameEn = it },
              singleLine = true,
              textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = CinemaAccent
              ),
              modifier = Modifier.fillMaxWidth(),
              placeholder = { Text("Akoam", fontSize = 14.sp, color = Color.Gray) }
            )
            Spacer(modifier = Modifier.height(10.dp))
          }

          Text("الرابط الرئيسي:", color = Color.LightGray, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
          Spacer(modifier = Modifier.height(4.dp))
          OutlinedTextField(
            value = editHomeUrl,
            onValueChange = { editHomeUrl = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedBorderColor = CinemaAccent
            ),
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://example.com/", fontSize = 14.sp, color = Color.Gray) }
          )
          Spacer(modifier = Modifier.height(10.dp))

          Text("رابط البحث:", color = Color.LightGray, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
          Spacer(modifier = Modifier.height(4.dp))
          OutlinedTextField(
            value = editSearchUrl,
            onValueChange = { editSearchUrl = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedBorderColor = CinemaAccent
            ),
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://example.com/search/%s", fontSize = 14.sp, color = Color.Gray) }
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
              Text("تفعيل البوابة", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
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
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
            ) {
              Text("إلغاء", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
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
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
            ) {
              Text("حفظ", fontSize = 15.sp, color = Color.Black, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
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
          text = portal.displayNameArabic,
          color = Color.White,
          fontSize = 17.sp,
          fontWeight = FontWeight.ExtraBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Text(
        text = "${results.size} نتيجة",
        color = CinemaAccent,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.ExtraBold,
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
            text = "لا توجد نتائج في ${portal.displayNameArabic}",
            color = Color.LightGray,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(8.dp))

          if (spellingSuggestion != null && onSearchSuggestion != null) {
            Button(
              onClick = { onSearchSuggestion(spellingSuggestion) },
              colors = ButtonDefaults.buttonColors(containerColor = CinemaAccent, contentColor = Color.Black),
              shape = RoundedCornerShape(50),
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
              modifier = Modifier.heightIn(min = 42.dp)
            ) {
              Text(text = "بحث عن: $spellingSuggestion", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
            Spacer(modifier = Modifier.height(8.dp))
          }

          if (onLaunchManualUrl != null) {
            Button(
              onClick = { onLaunchManualUrl(portal.buildSearchUrl(query)) },
              colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
              shape = RoundedCornerShape(50),
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
              modifier = Modifier.heightIn(min = 42.dp)
            ) {
              Text("فتح ${portal.displayNameArabic} 🔍", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            }
          }
        }
      }
    } else {
      val portalRowState = rememberLazyListState()
      val portalRowScope = rememberCoroutineScope()
      val portalIsRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
      val portalDragSlopPx = with(LocalDensity.current) { 8.dp.toPx() }
      LazyRow(
        state = portalRowState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .mouseHorizontalDragScroll(
            listState = portalRowState,
            scope = portalRowScope,
            isRtl = portalIsRtl,
            dragSlopPx = portalDragSlopPx
          )
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
  val scale by animateFloatAsState(
    targetValue = if (isFocused) 1.06f else 1f,
    animationSpec = tween(durationMillis = 100),
    label = "search_result_card_scale"
  )

  Column(
    modifier = Modifier
      .width(154.dp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
      )
      .testTag("result_card_${item.id}")
  ) {
    // Clipbox Vertical Poster Card
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(206.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(CinemaSurface)
        .border(
          width = if (isFocused) 3.dp else 1.dp,
          color = if (isFocused) CinemaFocusGlow else CinemaBorder,
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
          Icon(Icons.Default.Movie, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
        }
      }

      // Top-Right Quality Badge
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(6.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(Color.Black.copy(alpha = 0.78f))
          .padding(horizontal = 7.dp, vertical = 2.5.dp)
      ) {
        Text(
          text = item.quality,
          color = CinemaAccent,
          fontSize = 11.5.sp,
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
          .padding(horizontal = 7.dp, vertical = 2.5.dp)
      ) {
        Text(
          text = item.type,
          color = Color.White,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          softWrap = false
        )
      }

      // Bottom Overlay (Rating + Year)
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
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
          )
          Text(
            text = item.year.ifBlank { "HD" },
            color = Color.LightGray,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(7.dp))

    Text(
      text = item.title,
      color = Color.White,
      fontSize = 14.5.sp,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinueWatchingSection(
  history: List<HistoryEntry>,
  repository: TvPreferencesRepository? = null,
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
            text = "مسح سجل المشاهدة؟",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "سيتم حذف جميع العناصر.",
            color = Color.LightGray,
            fontSize = 14.5.sp,
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
                .heightIn(min = 48.dp),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text("إلغاء", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
            Button(
              onClick = {
                showConfirmClearDialog = false
                onClearAll()
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text("مسح", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
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
        text = "متابعة المشاهدة",
        color = Color.White,
        fontSize = 19.sp,
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
          modifier = Modifier
            .heightIn(min = 38.dp)
            .testTag("btn_clear_continue_watching")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
          ) {
            Icon(
              imageVector = Icons.Default.DeleteSweep,
              contentDescription = "مسح",
              tint = Color(0xFFFF5252),
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "مسح",
              color = Color.LightGray,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              softWrap = false
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    val historyRowState = rememberLazyListState()
    val historyRowScope = rememberCoroutineScope()
    val historyIsRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val historyDragSlopPx = with(LocalDensity.current) { 8.dp.toPx() }
    LazyRow(
      state = historyRowState,
      contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .focusGroup()
        .mouseHorizontalDragScroll(
          listState = historyRowState,
          scope = historyRowScope,
          isRtl = historyIsRtl,
          dragSlopPx = historyDragSlopPx
        )
    ) {
      itemsIndexed(history.take(15), key = { _, it -> it.id }) { idx, entry ->
        ContinueWatchingCard(
          entry = entry,
          repository = repository,
          onFocused = {
            historyRowScope.launch {
              try {
                historyRowState.animateScrollToItem((idx - 1).coerceAtLeast(0))
              } catch (_: Throwable) {}
            }
          },
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
  repository: TvPreferencesRepository? = null,
  onFocused: () -> Unit = {},
  onResume: () -> Unit,
  onDelete: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isFocused) 1.05f else 1f,
    animationSpec = tween(durationMillis = 100),
    label = "continue_watching_scale"
  )
  val formattedDate = remember(entry.timestamp) {
    try {
      val sdf = SimpleDateFormat("dd MMM", Locale.US)
      sdf.format(Date(entry.timestamp))
    } catch (_: Exception) {
      ""
    }
  }

  val resolvedMedia = remember(entry.id, entry.title, entry.url, entry.seasonNumber, entry.episodeNumber, entry.isSeries) {
    val parsed = SearchAndSubtitlesService.formatStandardizedEpisodeInfo(
      seriesTitle = SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(entry.title),
      pageTitle = entry.title,
      lastSearchQuery = "",
      watchPageUrl = entry.url,
      embedUrl = "",
      serverName = "",
      isSeriesHint = entry.isSeries || entry.seasonNumber > 0 || entry.episodeNumber > 0,
      selectedSeasonHint = entry.seasonNumber,
      currentEpisodeHint = entry.episodeNumber
    )
    val isSeries = entry.isSeries || parsed.isSeries || entry.seasonNumber > 0 || entry.episodeNumber > 0
    val seasonNum = when {
      entry.seasonNumber > 0 -> entry.seasonNumber
      isSeries && (parsed.seasonNumber ?: 0) > 0 -> parsed.seasonNumber ?: 1
      else -> 0
    }
    val episodeNum = when {
      entry.episodeNumber > 0 -> entry.episodeNumber
      isSeries && (parsed.episodeNumber ?: 0) > 0 -> parsed.episodeNumber ?: 1
      else -> 0
    }
    val cleanShowName = if (parsed.cleanTitle.isNotBlank() && !parsed.cleanTitle.equals("Asser TV", ignoreCase = true)) {
      parsed.cleanTitle
    } else {
      SearchAndSubtitlesService.stripEpisodeAndSeasonSuffix(entry.title).ifBlank { entry.title }
    }
    val codeTag = if (isSeries && seasonNum > 0 && episodeNum > 0) {
      "S${seasonNum.toString().padStart(2, '0')}E${episodeNum.toString().padStart(2, '0')}"
    } else ""
    val arabicStoppedLabel = if (isSeries && seasonNum > 0 && episodeNum > 0) {
      "توقفت عند: الموسم $seasonNum • الحلقة $episodeNum"
    } else if (isSeries && seasonNum > 0) {
      "توقفت عند: الموسم $seasonNum"
    } else {
      "فيلم • استكمال المشاهدة"
    }
    Triple(cleanShowName, codeTag, arabicStoppedLabel)
  }
  val cleanShowName = resolvedMedia.first
  val shortEpisodeCode = resolvedMedia.second
  val arabicStoppedLabel = resolvedMedia.third

  val progressRecord = remember(entry.id, cleanShowName, entry.seasonNumber, entry.episodeNumber, shortEpisodeCode) {
    val contentKey = TvPreferencesRepository.buildNormalizedContentKey(
      rawTitle = cleanShowName,
      seasonNumber = entry.seasonNumber.takeIf { it > 0 },
      episodeNumber = entry.episodeNumber.takeIf { it > 0 },
      isSeries = shortEpisodeCode.isNotBlank()
    )
    if (contentKey.isNotBlank()) repository?.getPlaybackProgress(contentKey) else null
  }

  val progressFraction = remember(progressRecord) {
    if (progressRecord != null && progressRecord.durationMs > 0L) {
      (progressRecord.positionMs.toFloat() / progressRecord.durationMs.toFloat()).coerceIn(0.08f, 0.98f)
    } else {
      0.35f
    }
  }

  val stoppedTimeText = remember(progressRecord) {
    if (progressRecord != null && progressRecord.positionMs > 0L) {
      val posLabel = formatCompactDurationMs(progressRecord.positionMs)
      if (progressRecord.durationMs > 0L) {
        "$posLabel / ${formatCompactDurationMs(progressRecord.durationMs)}"
      } else {
        posLabel
      }
    } else {
      ""
    }
  }

  Column(
    modifier = Modifier
      .width(210.dp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .onFocusChanged { fs ->
        if (fs.isFocused) onFocused()
      }
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onResume
      )
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(120.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(CinemaSurface)
        .border(
          width = if (isFocused) 3.dp else 1.dp,
          color = if (isFocused) CinemaFocusGlow else CinemaBorder,
          shape = RoundedCornerShape(14.dp)
        )
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
            .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Text(
            text = entry.portal.displayNameArabic,
            color = CinemaAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
          )
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier
            .size(28.dp)
            .focusProperties { canFocus = false }
        ) {
          Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "حذف",
            tint = Color.LightGray,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      // Center Play Circle
      Box(
        modifier = Modifier
          .align(Alignment.Center)
          .size(38.dp)
          .clip(CircleShape)
          .background(ClipboxWhite),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription = null,
          tint = Color.Black,
          modifier = Modifier.size(22.dp)
        )
      }

      // Bottom Meta (Episode Pill SxxExx + Date) + Red Progress Line
      Column(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 5.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (shortEpisodeCode.isNotBlank()) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(CinemaPrimary.copy(alpha = 0.92f))
                .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
              CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                  text = shortEpisodeCode,
                  color = Color.White,
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.ExtraBold,
                  maxLines = 1,
                  softWrap = false
                )
              }
            }
          } else {
            Text(
              text = "استكمال",
              color = Color.LightGray,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1
            )
          }

          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
              text = formattedDate,
              color = Color.Gray,
              fontSize = 11.5.sp,
              maxLines = 1,
              softWrap = false
            )
          }
        }
        // Signature red progress bar at the bottom edge
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(3.5.dp)
            .background(Color.DarkGray)
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth(progressFraction)
              .height(3.5.dp)
              .background(CinemaPrimary)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(7.dp))

    // Unified Yellow Title + SxxExx (e.g., "Dexter Resurrection S02E04")
    val unifiedYellowTitle = if (shortEpisodeCode.isNotBlank()) {
      "$cleanShowName $shortEpisodeCode"
    } else {
      cleanShowName
    }
    Text(
      text = unifiedYellowTitle,
      color = if (isFocused) CinemaFocusGlow else CinemaAccent,
      fontSize = 15.sp,
      fontWeight = FontWeight.ExtraBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("continue_watching_title_${entry.id}")
    )

    Spacer(modifier = Modifier.height(3.dp))

    // Prominent Arabic Stopped-At Line ("توقفت عند: الموسم X • الحلقة Y")
    Text(
      text = arabicStoppedLabel,
      color = Color.White,
      fontSize = 13.5.sp,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.fillMaxWidth()
    )

    if (stoppedTimeText.isNotBlank()) {
      Spacer(modifier = Modifier.height(2.dp))
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
          text = "⏱ $stoppedTimeText",
          color = Color.LightGray,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }
}

private fun formatCompactDurationMs(ms: Long): String {
  val totalSec = (ms / 1000L).coerceAtLeast(0L)
  val hours = totalSec / 3600L
  val mins = (totalSec % 3600L) / 60L
  val secs = totalSec % 60L
  return if (hours > 0L) {
    "%d:%02d:%02d".format(Locale.US, hours, mins, secs)
  } else {
    "%02d:%02d".format(Locale.US, mins, secs)
  }
}

@Composable
fun UpNextQueueSection(
  items: List<UpNextItem>,
  onPlayItem: (UpNextItem) -> Unit,
  onMarkWatched: (UpNextItem) -> Unit,
  onOpenTrakt: () -> Unit = {},
  onSyncTrakt: (() -> Unit)? = null,
  traktUsername: String = "",
  syncErrorMessage: String? = null,
  onDeleteItem: (UpNextItem) -> Unit,
  onClearAll: () -> Unit
) {
  val context = LocalContext.current
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("up_next_queue_section")
  ) {
    // Clean, balanced top header row: [Icon + Title + Count] on right, [تحديث + Trakt] on left — zero clipping or overlap
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 2.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.QueuePlayNext,
          contentDescription = null,
          tint = CinemaSuccess,
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = "التالي للمشاهدة",
          color = Color.White,
          fontSize = 16.5.sp,
          fontWeight = FontWeight.ExtraBold,
          maxLines = 1,
          softWrap = false
        )
        Surface(
          color = CinemaSuccess.copy(alpha = 0.18f),
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, CinemaSuccess.copy(alpha = 0.75f))
        ) {
          Text(
            text = "${items.size}",
            color = CinemaSuccess,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1,
            softWrap = false
          )
        }
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (onSyncTrakt != null) {
          Surface(
            onClick = onSyncTrakt,
            color = CinemaSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, CinemaSuccess.copy(alpha = 0.75f)),
            modifier = Modifier
              .heightIn(min = 36.dp)
              .testTag("btn_sync_trakt_from_home")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "تحديث",
                tint = CinemaSuccess,
                modifier = Modifier.size(15.dp)
              )
              Text(
                text = "تحديث الآن • Sync now",
                color = CinemaSuccess,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
              )
            }
          }
        }

        Surface(
          onClick = onOpenTrakt,
          color = CinemaSurfaceVariant,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, CinemaPrimary.copy(alpha = 0.75f)),
          modifier = Modifier
            .heightIn(min = 36.dp)
            .testTag("btn_open_trakt_table_from_home")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
          ) {
            Icon(
              imageVector = Icons.Default.TrackChanges,
              contentDescription = null,
              tint = CinemaPrimary,
              modifier = Modifier.size(15.dp)
            )
            Text(
              text = "Trakt",
              color = Color.White,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              softWrap = false
            )
          }
        }

        if (onSyncTrakt == null) {
          Surface(
            onClick = onClearAll,
            color = CinemaSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, CinemaBorder),
            modifier = Modifier
              .heightIn(min = 36.dp)
              .testTag("btn_clear_up_next")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(15.dp))
              Text("مسح", color = Color.LightGray, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
          }
        }
      }
    }

    if (!syncErrorMessage.isNullOrBlank()) {
      Spacer(modifier = Modifier.height(6.dp))
      Surface(
        color = Color(0x33EF4444),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFEF4444)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = syncErrorMessage,
            color = Color(0xFFFCA5A5),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
          )
          if (onSyncTrakt != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
              onClick = onSyncTrakt,
              color = Color(0xFFED1C24),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text(
                text = "إعادة المحاولة",
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                maxLines = 1,
                softWrap = false
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    val rowState = rememberLazyListState()
    LazyRow(
      state = rowState,
      contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .focusGroup()
    ) {
      items(items.take(100), key = { it.id }) { item ->
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()
        val sPad = item.seasonNumber.toString().padStart(2, '0')
        val ePad = item.episodeNumber.toString().padStart(2, '0')
        val epTag = "S.$sPad E.$ePad"
        val hasProgress = item.totalCount > 0
        val leftCount = if (hasProgress) (item.totalCount - item.watchedCount).coerceAtLeast(0) else 0
        val progressFraction = if (hasProgress) (item.watchedCount.toFloat() / item.totalCount.toFloat()).coerceIn(0f, 1f) else 0f

        Surface(
          onClick = { onPlayItem(item) },
          interactionSource = interactionSource,
          color = CinemaSurface,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(
            width = if (isFocused) 3.dp else 1.5.dp,
            color = if (isFocused) CinemaFocusGlow else if (item.fromTrakt) CinemaPrimary.copy(alpha = 0.7f) else CinemaSuccess.copy(alpha = 0.65f)
          ),
          modifier = Modifier
            .width(if (item.posterUrl.isNotBlank()) 285.dp else 235.dp)
            .testTag("up_next_card_${item.id}")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (item.posterUrl.isNotBlank()) {
              Box(
                modifier = Modifier
                  .width(62.dp)
                  .height(92.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(CinemaDarkBackground)
                  .border(1.dp, CinemaBorder, RoundedCornerShape(8.dp))
              ) {
                AsyncImage(
                  model = remember(item.posterUrl) {
                    ImageRequest.Builder(context)
                      .data(item.posterUrl)
                      .size(160, 240)
                      .allowHardware(Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                      .crossfade(false)
                      .build()
                  },
                  contentDescription = item.showTitle,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
              }
            }

            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  horizontalArrangement = Arrangement.spacedBy(5.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Surface(
                    color = CinemaSurfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                  ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                      Text(
                        text = epTag,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1,
                        softWrap = false
                      )
                    }
                  }
                  if (item.fromTrakt) {
                    Surface(
                      color = CinemaPrimary.copy(alpha = 0.2f),
                      shape = RoundedCornerShape(5.dp)
                    ) {
                      Text(
                        text = "Trakt",
                        color = CinemaPrimary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        maxLines = 1,
                        softWrap = false
                      )
                    }
                  }
                }

                IconButton(
                  onClick = { onDeleteItem(item) },
                  modifier = Modifier
                    .size(26.dp)
                    .focusProperties { canFocus = false }
                ) {
                  Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "حذف",
                    tint = Color.Gray,
                    modifier = Modifier.size(15.dp)
                  )
                }
              }

              Text(
                text = item.showTitle,
                color = if (isFocused) CinemaFocusGlow else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )

              if (item.episodeTitle.isNotBlank()) {
                Text(
                  text = item.episodeTitle,
                  color = Color(0xFFD1D5DB),
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.Medium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }

              if (hasProgress) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Text(
                      text = "${item.watchedCount}/${item.totalCount} ($leftCount)",
                      color = Color(0xFF9CA3AF),
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.SemiBold,
                      maxLines = 1,
                      softWrap = false
                    )
                    Box(
                      modifier = Modifier
                        .weight(1f)
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF2D333B))
                    ) {
                      Box(
                        modifier = Modifier
                          .fillMaxWidth(progressFraction.coerceAtLeast(0.04f))
                          .fillMaxHeight()
                          .clip(RoundedCornerShape(50))
                          .background(CinemaPrimary)
                      )
                    }
                  }
                }
              }

              if (item.lastWatchedAtIso.isNotBlank()) {
                val shortDate = item.lastWatchedAtIso.take(10)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                  Text(
                    text = "Last watched: $shortDate",
                    color = Color(0xFF9CA3AF),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Surface(
                  color = CinemaPrimary,
                  shape = RoundedCornerShape(50)
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("تشغيل", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                  }
                }

                // Watched ✓ button (marks watched + syncs to Trakt + advances to next episode)
                Surface(
                  onClick = { onMarkWatched(item) },
                  color = Color.Transparent,
                  shape = RoundedCornerShape(50),
                  border = BorderStroke(1.3.dp, CinemaSuccess),
                  modifier = Modifier
                    .heightIn(min = 30.dp)
                    .focusProperties { canFocus = false }
                    .testTag("btn_home_up_next_watched_${item.id}")
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.CheckCircle,
                      contentDescription = "تمت المشاهدة",
                      tint = CinemaSuccess,
                      modifier = Modifier.size(14.dp)
                    )
                    Text(
                      text = "تمت المشاهدة",
                      color = Color.White,
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Bold,
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
  }
}

@Composable
private fun HomeTraktCalendarSection(
  items: List<TraktCalendarItem>,
  onPlayItem: (TraktCalendarItem) -> Unit,
  onOpenTrakt: () -> Unit
) {
  val context = LocalContext.current
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("home_trakt_calendar_section")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = CinemaAccent, modifier = Modifier.size(21.dp))
        Text("جدول الحلقات القادمة", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
      }
      Surface(
        onClick = onOpenTrakt,
        color = CinemaSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, CinemaBorder)
      ) {
        Text(
          text = "الكل (${items.size})",
          color = CinemaAccent,
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
      items(items.take(15), key = { it.id }) { cal ->
        val sPad = cal.seasonNumber.toString().padStart(2, '0')
        val ePad = cal.episodeNumber.toString().padStart(2, '0')
        Surface(
          onClick = { onPlayItem(cal) },
          color = CinemaSurface,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, CinemaBorder),
          modifier = Modifier.width(230.dp)
        ) {
          Row(
            modifier = Modifier.padding(9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (cal.posterUrl.isNotBlank()) {
              AsyncImage(
                model = remember(cal.posterUrl) {
                  ImageRequest.Builder(context)
                    .data(cal.posterUrl)
                    .size(140, 200)
                    .allowHardware(Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                    .crossfade(false)
                    .build()
                },
                contentDescription = cal.showTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .width(50.dp)
                  .height(74.dp)
                  .clip(RoundedCornerShape(8.dp))
              )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = cal.firstAiredIso.take(10),
                color = CinemaAccent,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold
              )
              Text(
                text = cal.showTitle,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                  text = "S.$sPad E.$ePad",
                  color = Color.LightGray,
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.Bold
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
private fun HomeTraktWatchlistSection(
  items: List<TraktWatchlistItem>,
  onPlayItem: (TraktWatchlistItem) -> Unit,
  onOpenTrakt: () -> Unit
) {
  val context = LocalContext.current
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("home_trakt_watchlist_section")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(21.dp))
        Text("قائمتي في Trakt", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
      }
      Surface(
        onClick = onOpenTrakt,
        color = CinemaSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, CinemaBorder)
      ) {
        Text(
          text = "الكل (${items.size})",
          color = Color(0xFF38BDF8),
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
      items(items.take(15), key = { it.id }) { wl ->
        val isMovie = wl.mediaType == "movie"
        Surface(
          onClick = { onPlayItem(wl) },
          color = CinemaSurface,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, CinemaBorder),
          modifier = Modifier.width(195.dp)
        ) {
          Row(
            modifier = Modifier.padding(9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (wl.posterUrl.isNotBlank()) {
              AsyncImage(
                model = remember(wl.posterUrl) {
                  ImageRequest.Builder(context)
                    .data(wl.posterUrl)
                    .size(140, 200)
                    .allowHardware(Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                    .crossfade(false)
                    .build()
                },
                contentDescription = wl.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .width(48.dp)
                  .height(70.dp)
                  .clip(RoundedCornerShape(8.dp))
              )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = if (isMovie) "فيلم • ${wl.year}" else "مسلسل • ${wl.year}",
                color = if (isMovie) CinemaPrimary else Color(0xFF38BDF8),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
              )
              Text(
                text = wl.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    }
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
