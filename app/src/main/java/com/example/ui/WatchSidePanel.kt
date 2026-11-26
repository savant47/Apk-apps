package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.TvPreferencesRepository
import com.example.model.ScrapedEpisodeItem
import com.example.model.ScrapedSeasonItem
import com.example.model.ScrapedSeriesStructure
import com.example.model.ScrapedWatchServer
import com.example.model.SearchResultItem
import com.example.model.StreamingPortal
import com.example.model.VideoQualityOption
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaFocusGlow
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.ClipboxWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchSidePanel(
  activePortal: StreamingPortal,
  currentUrl: String,
  activeWatchPageUrl: String = currentUrl,
  activeServerName: String = "",
  searchQuery: String,
  portalResultsMap: Map<StreamingPortal, List<SearchResultItem>>,
  seriesStructure: ScrapedSeriesStructure,
  isScrapingSeries: Boolean,
  currentQuality: String,
  detectedQualities: List<VideoQualityOption> = emptyList(),
  isNativePlayerActive: Boolean = false,
  hasDetectedDirectStream: Boolean = false,
  onSwitchToNativePlayer: () -> Unit = {},
  onToggleVideoFullscreen: () -> Unit = {},
  onOpenDiagnostics: () -> Unit = {},
  onOpenUpdate: () -> Unit = {},
  onSelectWatchServer: (ScrapedWatchServer) -> Unit = {},
  onSelectSearchResult: (SearchResultItem, StreamingPortal) -> Unit,
  onSwitchPortalDirect: (StreamingPortal) -> Unit,
  onSelectSeason: (ScrapedSeasonItem) -> Unit,
  onSelectEpisode: (ScrapedEpisodeItem) -> Unit,
  onRescrapePage: () -> Unit,
  onSelectQuality: (qualityOption: VideoQualityOption) -> Unit,
  onOpenSubtitles: () -> Unit,
  onClosePanel: () -> Unit,
  watchedEpisodes: Set<String> = emptySet(),
  seriesTitleForWatched: String = "",
  isCurrentEpisodeWatched: Boolean = false,
  onToggleCurrentEpisodeWatched: () -> Unit = {},
  isCompactMobileSheet: Boolean = false,
  modifier: Modifier = Modifier
) {
  var selectedPortalTab by remember(activePortal) { mutableStateOf(activePortal) }
  // 0 = Seasons & Episodes (if series) or Sources; 1 = Sources
  var activeSubTab by remember(seriesStructure.episodes.isNotEmpty(), seriesStructure.seasons.isNotEmpty()) {
    mutableIntStateOf(if (seriesStructure.episodes.isNotEmpty() || seriesStructure.seasons.isNotEmpty()) 0 else 1)
  }
  var showServerSelectorModal by remember { mutableStateOf(false) }

  val currentActiveServer = remember(seriesStructure.watchServers, currentUrl, activeServerName) {
    seriesStructure.watchServers.firstOrNull {
      (activeServerName.isNotBlank() && it.name == activeServerName) ||
        (currentUrl.isNotBlank() && it.embedUrl == currentUrl)
    } ?: seriesStructure.watchServers.firstOrNull()
  }

  val displayedServerLabel = remember(currentActiveServer, activeServerName, isScrapingSeries, seriesStructure.watchServers) {
    when {
      activeServerName.isNotBlank() && activeServerName != "تلقائي" -> activeServerName
      currentActiveServer != null -> currentActiveServer.name
      isScrapingSeries -> "جاري جلب السيرفرات..."
      else -> "اختر السيرفر"
    }
  }

  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Surface(
      color = CinemaDarkBackground.copy(alpha = 0.99f),
      border = BorderStroke(1.dp, CinemaBorder),
      shape = if (isCompactMobileSheet) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
      } else {
        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
      },
      modifier = modifier
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .fillMaxHeight()
          .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // 1. Sleek Well-Proportioned Header (All controls D-Pad focusable with clear focus borders)
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
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isNativePlayerActive) CinemaSuccess else CinemaAccent)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (searchQuery.isNotBlank()) searchQuery else activePortal.displayNameArabic,
              color = Color.White,
              fontSize = 15.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            if (isScrapingSeries) {
              Spacer(modifier = Modifier.width(6.dp))
              CircularProgressIndicator(
                color = CinemaAccent,
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp
              )
            }
          }

          Spacer(modifier = Modifier.width(6.dp))

          Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Switch to Primary Native Player (when in WebView fallback mode)
            if (hasDetectedDirectStream && !isNativePlayerActive) {
              SidePanelFocusPill(
                label = "الأساسي",
                icon = Icons.Default.PlayArrow,
                containerColor = CinemaSuccess,
                contentColor = Color.Black,
                borderColor = CinemaSuccess,
                onClick = onSwitchToNativePlayer,
                testTag = "play_native_video_button"
              )
            }

            // Video Fullscreen button
            SidePanelFocusPill(
              label = "تكبير",
              icon = Icons.Default.Fullscreen,
              containerColor = CinemaPrimary,
              contentColor = Color.White,
              borderColor = CinemaPrimary,
              onClick = onToggleVideoFullscreen,
              testTag = "side_panel_fullscreen_button"
            )

            // Subtitles CC Button
            SidePanelFocusPill(
              label = "CC",
              icon = Icons.Default.Subtitles,
              containerColor = CinemaSurfaceVariant,
              contentColor = Color.White,
              iconTint = CinemaAccent,
              borderColor = CinemaAccent.copy(alpha = 0.8f),
              onClick = onOpenSubtitles,
              testTag = "side_panel_subtitles_button"
            )

            // Refresh Servers & Episodes Button
            SidePanelSquareIconBtn(
              icon = Icons.Default.Refresh,
              tint = CinemaAccent,
              contentDescription = "تحديث السيرفرات والحلقات",
              onClick = onRescrapePage,
              testTag = "btn_rescrape_seasons_episodes"
            )

            // Close Panel Button
            SidePanelSquareIconBtn(
              icon = Icons.Default.Close,
              tint = Color.LightGray,
              contentDescription = "إخفاء اللوحة",
              onClick = onClosePanel,
              testTag = "btn_close_watch_side_panel"
            )
          }
        }

        // 2. Full-Width Active Server Selector Button (Opens Centered TV Modal Horizontal D-Pad Carousel)
        val serverSelectorInteraction = remember { MutableInteractionSource() }
        val isServerSelectorFocused by serverSelectorInteraction.collectIsFocusedAsState()
        val serverCount = seriesStructure.watchServers.size

        Surface(
          onClick = {
            if (serverCount > 0) {
              showServerSelectorModal = true
            } else {
              onRescrapePage()
            }
          },
          interactionSource = serverSelectorInteraction,
          color = if (isServerSelectorFocused) CinemaSurfaceVariant else CinemaSurface,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(
            width = if (isServerSelectorFocused) 2.5.dp else 1.dp,
            color = if (isServerSelectorFocused) CinemaFocusGlow else CinemaSuccess.copy(alpha = 0.7f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .scale(if (isServerSelectorFocused) 1.015f else 1.0f)
            .focusable(interactionSource = serverSelectorInteraction)
            .testTag("btn_open_server_selector_modal")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isServerSelectorFocused) CinemaFocusGlow else CinemaSuccess.copy(alpha = 0.18f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = "اختيار السيرفر",
                tint = if (isServerSelectorFocused) Color.Black else CinemaSuccess,
                modifier = Modifier.size(19.dp)
              )
            }

            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              Text(
                text = "السيرفر ($serverCount)",
                color = if (isServerSelectorFocused) CinemaFocusGlow else Color.LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = displayedServerLabel,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            Surface(
              color = if (isServerSelectorFocused) CinemaFocusGlow else CinemaDarkBackground,
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(
                1.dp,
                if (isServerSelectorFocused) Color.White else CinemaBorder
              )
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = if (serverCount > 0) "تبديل" else "تحديث",
                  color = if (isServerSelectorFocused) Color.Black else CinemaAccent,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.ExtraBold,
                  maxLines = 1,
                  softWrap = false
                )
                Icon(
                  imageVector = Icons.Default.KeyboardArrowDown,
                  contentDescription = null,
                  tint = if (isServerSelectorFocused) Color.Black else CinemaAccent,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }

        // 3. Auto-Detected Real Video Qualities Strip (FlowRow wrap on narrow mobile sheets so labels never clip)
        if (isCompactMobileSheet && detectedQualities.isNotEmpty()) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(CinemaSurface)
              .border(1.dp, CinemaBorder, RoundedCornerShape(8.dp))
              .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.HighQuality,
                contentDescription = null,
                tint = CinemaSuccess,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = "الجودة:",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
              )
            }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              detectedQualities.forEach { qOption ->
                val selected = currentQuality == qOption.id
                val qInteraction = remember { MutableInteractionSource() }
                val isFocused by qInteraction.collectIsFocusedAsState()
                Surface(
                  onClick = { onSelectQuality(qOption) },
                  interactionSource = qInteraction,
                  color = when {
                    isFocused -> CinemaFocusGlow
                    selected -> ClipboxWhite
                    else -> CinemaDarkBackground
                  },
                  shape = RoundedCornerShape(7.dp),
                  border = BorderStroke(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = when {
                      isFocused -> Color.White
                      selected -> ClipboxWhite
                      else -> CinemaSuccess.copy(alpha = 0.6f)
                    }
                  ),
                  modifier = Modifier
                    .heightIn(min = 38.dp)
                    .focusable(interactionSource = qInteraction)
                    .testTag("quality_chip_${qOption.id}")
                ) {
                  Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                  ) {
                    Text(
                      text = qOption.label,
                      color = if (selected || isFocused) Color.Black else Color.White,
                      fontSize = 13.sp,
                      fontWeight = if (selected || isFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }
              }
            }
          }
        } else {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(CinemaSurface)
              .border(1.dp, CinemaBorder, RoundedCornerShape(8.dp))
              .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.HighQuality,
              contentDescription = null,
              tint = if (detectedQualities.isNotEmpty()) CinemaSuccess else CinemaSecondary,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = "الجودة:",
              color = Color.White,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false
            )
            Spacer(modifier = Modifier.width(6.dp))
            if (detectedQualities.isNotEmpty()) {
              val qualityScope = rememberCoroutineScope()
              LazyRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
              ) {
                items(detectedQualities, key = { it.id }) { qOption ->
                  val selected = currentQuality == qOption.id
                  val qInteraction = remember { MutableInteractionSource() }
                  val isFocused by qInteraction.collectIsFocusedAsState()
                  val bringIntoViewRequester = remember { BringIntoViewRequester() }
                  Surface(
                    onClick = { onSelectQuality(qOption) },
                    interactionSource = qInteraction,
                    color = when {
                      isFocused -> CinemaFocusGlow
                      selected -> ClipboxWhite
                      else -> CinemaDarkBackground
                    },
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(
                      width = if (isFocused) 2.dp else 1.dp,
                      color = when {
                        isFocused -> Color.White
                        selected -> ClipboxWhite
                        else -> CinemaSuccess.copy(alpha = 0.6f)
                      }
                    ),
                    modifier = Modifier
                      .heightIn(min = 38.dp)
                      .bringIntoViewRequester(bringIntoViewRequester)
                      .onFocusChanged { state ->
                        if (state.isFocused) {
                          qualityScope.launch { bringIntoViewRequester.bringIntoView() }
                        }
                      }
                      .focusable(interactionSource = qInteraction)
                      .testTag("quality_chip_${qOption.id}")
                  ) {
                    Box(
                      contentAlignment = Alignment.Center,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                      Text(
                        text = qOption.label,
                        color = if (selected || isFocused) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (selected || isFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                      )
                    }
                  }
                }
              }
            } else {
              val savedLabel = when (currentQuality.lowercase()) {
                "lowest" -> "توفير الباقة • جاري الفحص..."
                "auto" -> "تلقائي • جاري الفحص..."
                else -> "${currentQuality}p • جاري الفحص..."
              }
              Text(
                text = savedLabel,
                color = CinemaAccent,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
            }
          }
        }

        // 4. Mode Switcher Tabs: [📺 الحلقات] vs [🌐 البوابات]
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          val tab0Interaction = remember { MutableInteractionSource() }
          val isTab0Focused by tab0Interaction.collectIsFocusedAsState()
          Surface(
            onClick = { activeSubTab = 0 },
            interactionSource = tab0Interaction,
            color = when {
              isTab0Focused -> CinemaFocusGlow
              activeSubTab == 0 -> ClipboxWhite
              else -> CinemaSurface
            },
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(
              width = if (isTab0Focused) 2.dp else 1.dp,
              color = when {
                isTab0Focused -> Color.White
                activeSubTab == 0 -> ClipboxWhite
                else -> CinemaBorder
              }
            ),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 44.dp)
              .focusable(interactionSource = tab0Interaction)
              .testTag("tab_seasons_episodes")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                tint = if (activeSubTab == 0 || isTab0Focused) Color.Black else CinemaAccent,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "الحلقات (${seriesStructure.episodes.size})",
                color = if (activeSubTab == 0 || isTab0Focused) Color.Black else Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
              )
            }
          }

          val tab1Interaction = remember { MutableInteractionSource() }
          val isTab1Focused by tab1Interaction.collectIsFocusedAsState()
          Surface(
            onClick = { activeSubTab = 1 },
            interactionSource = tab1Interaction,
            color = when {
              isTab1Focused -> CinemaFocusGlow
              activeSubTab == 1 -> ClipboxWhite
              else -> CinemaSurface
            },
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(
              width = if (isTab1Focused) 2.dp else 1.dp,
              color = when {
                isTab1Focused -> Color.White
                activeSubTab == 1 -> ClipboxWhite
                else -> CinemaBorder
              }
            ),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 44.dp)
              .focusable(interactionSource = tab1Interaction)
              .testTag("tab_alternative_sources")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = null,
                tint = if (activeSubTab == 1 || isTab1Focused) Color.Black else CinemaPrimary,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "البوابات",
                color = if (activeSubTab == 1 || isTab1Focused) Color.Black else Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
              )
            }
          }
        }

        // 5. Large, Expanded Content Area
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(CinemaSurface)
            .border(1.dp, CinemaBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
          if (activeSubTab == 0) {
            val contentScope = rememberCoroutineScope()
            Column(
              modifier = Modifier.fillMaxSize(),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              // Spacious Seasons Strip
              if (seriesStructure.seasons.isNotEmpty()) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = "المواسم (${seriesStructure.seasons.size})",
                    color = CinemaAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                  )
                  Text(
                    text = "الموسم ${seriesStructure.selectedSeasonNumber}",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                  )
                }

                val seasonsListState = rememberLazyListState()
                LaunchedEffect(seriesStructure.selectedSeasonNumber, seriesStructure.seasons.size) {
                  val activeIdx = seriesStructure.seasons.indexOfFirst {
                    it.seasonNumber == seriesStructure.selectedSeasonNumber
                  }
                  if (activeIdx >= 0) {
                    try {
                      seasonsListState.animateScrollToItem((activeIdx - 1).coerceAtLeast(0))
                    } catch (_: Throwable) {}
                  }
                }
                LazyRow(
                  state = seasonsListState,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  items(seriesStructure.seasons, key = { "${it.seasonNumber}_${it.url}" }) { season ->
                    val isSelected = season.seasonNumber == seriesStructure.selectedSeasonNumber
                    val sInteraction = remember { MutableInteractionSource() }
                    val isFocused by sInteraction.collectIsFocusedAsState()
                    val bringIntoView = remember { BringIntoViewRequester() }
                    Surface(
                      onClick = { onSelectSeason(season) },
                      interactionSource = sInteraction,
                      color = when {
                        isFocused -> CinemaFocusGlow
                        isSelected -> CinemaPrimary
                        else -> CinemaDarkBackground
                      },
                      shape = RoundedCornerShape(10.dp),
                      border = BorderStroke(
                        width = if (isFocused) 2.5.dp else 1.5.dp,
                        color = when {
                          isFocused -> Color.White
                          isSelected -> ClipboxWhite
                          else -> CinemaBorder
                        }
                      ),
                      modifier = Modifier
                        .heightIn(min = 46.dp)
                        .bringIntoViewRequester(bringIntoView)
                        .onFocusChanged { if (it.isFocused) contentScope.launch { bringIntoView.bringIntoView() } }
                        .focusable(interactionSource = sInteraction)
                        .testTag("season_chip_${season.seasonNumber}")
                    ) {
                      Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                      ) {
                        Text(
                          text = season.title,
                          color = if (isFocused) Color.Black else Color.White,
                          fontSize = 14.5.sp,
                          fontWeight = if (isSelected || isFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
                          maxLines = 1,
                          softWrap = false
                        )
                      }
                    }
                  }
                }
              }

              // Expanded Multi-Row Episodes Grid (50dp touch/focus targets + crisp D-Pad ring + Watched ✓ Badge)
              if (seriesStructure.episodes.isNotEmpty()) {
                val showNameForWatched = seriesTitleForWatched.ifBlank {
                  seriesStructure.seriesTitle.ifBlank { searchQuery }
                }
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = "الحلقات (${seriesStructure.episodes.size})",
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold
                  )
                  SidePanelFocusPill(
                    label = if (isCurrentEpisodeWatched) "تمت المشاهدة ✓" else "تمت المشاهدة",
                    icon = Icons.Default.CheckCircle,
                    containerColor = if (isCurrentEpisodeWatched) CinemaSuccess else CinemaSurfaceVariant,
                    contentColor = Color.White,
                    iconTint = if (isCurrentEpisodeWatched) Color.White else CinemaSuccess,
                    borderColor = CinemaSuccess,
                    onClick = onToggleCurrentEpisodeWatched,
                    testTag = "btn_side_panel_mark_watched"
                  )
                }

                LazyVerticalGrid(
                  columns = GridCells.Adaptive(minSize = 116.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                  contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                  items(seriesStructure.episodes, key = { "${it.episodeNumber}_${it.url}" }) { ep ->
                    val isCurrentEp = activeWatchPageUrl == ep.url || currentUrl == ep.url
                    val epWatchedKey = TvPreferencesRepository.buildEpisodeWatchedKey(
                      showTitle = showNameForWatched,
                      seasonNumber = ep.seasonNumber.coerceAtLeast(seriesStructure.selectedSeasonNumber.coerceAtLeast(1)),
                      episodeNumber = ep.episodeNumber
                    )
                    val isWatchedEp = epWatchedKey.isNotBlank() && watchedEpisodes.contains(epWatchedKey)
                    val epInteraction = remember { MutableInteractionSource() }
                    val isFocused by epInteraction.collectIsFocusedAsState()
                    val bringIntoView = remember { BringIntoViewRequester() }
                    Surface(
                      onClick = { onSelectEpisode(ep) },
                      interactionSource = epInteraction,
                      color = when {
                        isFocused -> CinemaFocusGlow
                        isCurrentEp -> CinemaSuccess
                        isWatchedEp -> CinemaSuccess.copy(alpha = 0.22f)
                        else -> CinemaDarkBackground
                      },
                      shape = RoundedCornerShape(10.dp),
                      border = BorderStroke(
                        width = if (isFocused) 2.5.dp else 1.5.dp,
                        color = when {
                          isFocused -> Color.White
                          isCurrentEp -> ClipboxWhite
                          isWatchedEp -> CinemaSuccess
                          else -> CinemaBorder
                        }
                      ),
                      modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .scale(if (isFocused) 1.03f else 1.0f)
                        .bringIntoViewRequester(bringIntoView)
                        .onFocusChanged { if (it.isFocused) contentScope.launch { bringIntoView.bringIntoView() } }
                        .focusable(interactionSource = epInteraction)
                        .testTag("episode_card_${ep.episodeNumber}")
                    ) {
                      Row(
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                      ) {
                        Icon(
                          imageVector = if (isWatchedEp && !isCurrentEp) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                          contentDescription = if (isWatchedEp) "تمت المشاهدة" else null,
                          tint = when {
                            isFocused -> Color.Black
                            isCurrentEp -> Color.White
                            isWatchedEp -> CinemaSuccess
                            else -> CinemaAccent
                          },
                          modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                          text = "حلقة ${ep.episodeNumber}",
                          color = if (isFocused) Color.Black else Color.White,
                          fontSize = 14.5.sp,
                          fontWeight = FontWeight.ExtraBold,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          textAlign = TextAlign.Center
                        )
                      }
                    }
                  }
                }
              } else {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = if (isScrapingSeries) {
                      "جاري تحميل الحلقات..."
                    } else {
                      "هذا فيلم، أو اختر من تبويب البوابات"
                    },
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                  )
                }
              }
            }
          } else {
            // Tab 1: Alternative Sources & Search Results
            val sourceScope = rememberCoroutineScope()
            Column(
              modifier = Modifier.fillMaxSize(),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              val enabledPortals = StreamingPortal.values().filter { it.isEnabled }
              LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                items(enabledPortals, key = { it.id }) { portal ->
                  val count = portalResultsMap[portal]?.size ?: 0
                  val isSelected = selectedPortalTab == portal
                  val pInteraction = remember { MutableInteractionSource() }
                  val isFocused by pInteraction.collectIsFocusedAsState()
                  val bringIntoView = remember { BringIntoViewRequester() }
                  Surface(
                    onClick = { selectedPortalTab = portal },
                    interactionSource = pInteraction,
                    color = when {
                      isFocused -> CinemaFocusGlow
                      isSelected -> ClipboxWhite
                      else -> CinemaDarkBackground
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                      width = if (isFocused) 2.dp else 1.dp,
                      color = when {
                        isFocused -> Color.White
                        isSelected -> ClipboxWhite
                        else -> CinemaBorder
                      }
                    ),
                    modifier = Modifier
                      .heightIn(min = 42.dp)
                      .bringIntoViewRequester(bringIntoView)
                      .onFocusChanged { if (it.isFocused) sourceScope.launch { bringIntoView.bringIntoView() } }
                      .focusable(interactionSource = pInteraction)
                      .testTag("portal_filter_chip_${portal.id}")
                  ) {
                    Box(
                      contentAlignment = Alignment.Center,
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                      Text(
                        text = if (count > 0) "${portal.displayNameArabic} ($count)" else portal.displayNameArabic,
                        color = if (isSelected || isFocused) Color.Black else Color.LightGray,
                        fontSize = 13.5.sp,
                        fontWeight = if (isSelected || isFocused) FontWeight.ExtraBold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                      )
                    }
                  }
                }
              }

              val itemsForSelectedPortal = portalResultsMap[selectedPortalTab] ?: emptyList()
              if (itemsForSelectedPortal.isNotEmpty()) {
                LazyColumn(
                  modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  items(itemsForSelectedPortal.take(15), key = { it.id }) { resultItem ->
                    val isCurrentUrl = activeWatchPageUrl == resultItem.url || currentUrl == resultItem.url
                    val rowInteraction = remember { MutableInteractionSource() }
                    val isFocused by rowInteraction.collectIsFocusedAsState()
                    val bringIntoView = remember { BringIntoViewRequester() }
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                          when {
                            isFocused -> CinemaFocusGlow.copy(alpha = 0.2f)
                            isCurrentUrl -> CinemaPrimary.copy(alpha = 0.25f)
                            else -> CinemaDarkBackground
                          }
                        )
                        .border(
                          width = if (isFocused) 2.dp else 1.dp,
                          color = when {
                            isFocused -> CinemaFocusGlow
                            isCurrentUrl -> CinemaPrimary
                            else -> CinemaBorder
                          },
                          shape = RoundedCornerShape(8.dp)
                        )
                        .bringIntoViewRequester(bringIntoView)
                        .onFocusChanged { if (it.isFocused) sourceScope.launch { bringIntoView.bringIntoView() } }
                        .focusable(interactionSource = rowInteraction)
                        .clickable(
                          interactionSource = rowInteraction,
                          indication = null
                        ) { onSelectSearchResult(resultItem, selectedPortalTab) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("source_result_${resultItem.id}"),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = resultItem.title,
                          color = Color.White,
                          fontSize = 14.sp,
                          fontWeight = FontWeight.SemiBold,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                        Text(
                          text = "${selectedPortalTab.displayNameArabic} • ${resultItem.type}",
                          color = Color.LightGray,
                          fontSize = 12.5.sp,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                      }
                      Spacer(modifier = Modifier.width(6.dp))
                      Box(
                        modifier = Modifier
                          .clip(RoundedCornerShape(6.dp))
                          .background(
                            when {
                              isFocused -> CinemaFocusGlow
                              isCurrentUrl -> CinemaSuccess
                              else -> CinemaPrimary
                            }
                          )
                          .padding(horizontal = 10.dp, vertical = 6.dp)
                      ) {
                        Text(
                          text = if (isCurrentUrl) "يعمل" else "تشغيل",
                          color = if (isFocused) Color.Black else Color.White,
                          fontSize = 12.5.sp,
                          fontWeight = FontWeight.ExtraBold,
                          maxLines = 1,
                          softWrap = false
                        )
                      }
                    }
                  }
                }
              } else {
                val searchBtnInteraction = remember { MutableInteractionSource() }
                val isSearchBtnFocused by searchBtnInteraction.collectIsFocusedAsState()
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                  contentAlignment = Alignment.Center
                ) {
                  Button(
                    onClick = { onSwitchPortalDirect(selectedPortalTab) },
                    interactionSource = searchBtnInteraction,
                    colors = ButtonDefaults.buttonColors(
                      containerColor = if (isSearchBtnFocused) CinemaFocusGlow else CinemaDarkBackground,
                      contentColor = if (isSearchBtnFocused) Color.Black else Color.White
                    ),
                    border = BorderStroke(
                      width = if (isSearchBtnFocused) 2.dp else 1.dp,
                      color = if (isSearchBtnFocused) Color.White else CinemaBorder
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .heightIn(min = 48.dp)
                      .focusable(interactionSource = searchBtnInteraction)
                      .testTag("btn_search_selected_portal")
                  ) {
                    Text(
                      text = "بحث في ${selectedPortalTab.displayNameArabic}",
                      fontSize = 14.sp,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
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

  // Centered TV Modal with Horizontal D-Pad Carousel for Server Selection
  if (showServerSelectorModal) {
    ServerSelectorTvModal(
      servers = seriesStructure.watchServers,
      activeServerId = currentActiveServer?.id,
      activeServerName = displayedServerLabel,
      currentUrl = currentUrl,
      onSelectServer = { selected ->
        showServerSelectorModal = false
        onSelectWatchServer(selected)
      },
      onDismiss = { showServerSelectorModal = false }
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerSelectorTvModal(
  servers: List<ScrapedWatchServer>,
  activeServerId: String?,
  activeServerName: String,
  currentUrl: String,
  onSelectServer: (ScrapedWatchServer) -> Unit,
  onDismiss: () -> Unit
) {
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val activeIndex = remember(servers, activeServerId, currentUrl) {
    val idx = servers.indexOfFirst {
      (activeServerId != null && it.id == activeServerId) ||
        (currentUrl.isNotBlank() && it.embedUrl == currentUrl)
    }
    if (idx >= 0) idx else 0
  }
  val focusRequesters = remember(servers.size) { List(servers.size) { FocusRequester() } }

  LaunchedEffect(servers.size, activeIndex) {
    if (servers.isNotEmpty()) {
      listState.scrollToItem(activeIndex.coerceIn(0, servers.lastIndex))
      delay(120L)
      try {
        focusRequesters[activeIndex.coerceIn(0, servers.lastIndex)].requestFocus()
      } catch (_: Throwable) {}
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = CinemaDarkBackground.copy(alpha = 0.99f),
        border = BorderStroke(1.5.dp, CinemaAccent.copy(alpha = 0.8f)),
        shadowElevation = 24.dp,
        modifier = Modifier
          .fillMaxWidth(0.92f)
          .widthIn(max = 680.dp)
          .padding(12.dp)
          .testTag("server_selector_tv_modal")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          // Modal Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(CinemaSuccess.copy(alpha = 0.16f))
                  .border(1.dp, CinemaSuccess, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Dns,
                  contentDescription = null,
                  tint = CinemaSuccess,
                  modifier = Modifier.size(20.dp)
                )
              }
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = "السيرفرات (${servers.size})",
                  color = Color.White,
                  fontSize = 18.sp,
                  fontWeight = FontWeight.ExtraBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "اختر بالأسهم واضغط OK",
                  color = Color.LightGray,
                  fontSize = 13.5.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }

            val closeInteraction = remember { MutableInteractionSource() }
            val isCloseFocused by closeInteraction.collectIsFocusedAsState()
            Surface(
              onClick = onDismiss,
              interactionSource = closeInteraction,
              color = if (isCloseFocused) CinemaFocusGlow else CinemaSurface,
              shape = CircleShape,
              border = BorderStroke(
                width = if (isCloseFocused) 2.dp else 1.dp,
                color = if (isCloseFocused) Color.White else CinemaBorder
              ),
              modifier = Modifier
                .size(48.dp)
                .focusable(interactionSource = closeInteraction)
                .testTag("btn_close_server_modal")
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "إغلاق نافذة السيرفرات",
                  tint = if (isCloseFocused) Color.Black else Color.LightGray,
                  modifier = Modifier.size(20.dp)
                )
              }
            }
          }

          // Active server indicator pill
          Surface(
            color = CinemaSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, CinemaBorder),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = CinemaSuccess,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = "الحالي: $activeServerName",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          // Horizontal D-Pad Carousel of Server Cards
          LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("server_selector_carousel")
          ) {
            itemsIndexed(servers, key = { _, srv -> srv.id }) { index, srv ->
              val isSelected = (activeServerId != null && srv.id == activeServerId) ||
                (currentUrl.isNotBlank() && srv.embedUrl == currentUrl)
              val cardInteraction = remember { MutableInteractionSource() }
              val isFocused by cardInteraction.collectIsFocusedAsState()
              val bringIntoViewRequester = remember { BringIntoViewRequester() }

              Surface(
                onClick = { onSelectServer(srv) },
                interactionSource = cardInteraction,
                color = when {
                  isFocused -> CinemaSurfaceVariant
                  isSelected -> CinemaSuccess.copy(alpha = 0.16f)
                  else -> CinemaSurface
                },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                  width = if (isFocused) 3.dp else if (isSelected) 1.5.dp else 1.dp,
                  color = when {
                    isFocused -> CinemaFocusGlow
                    isSelected -> CinemaSuccess
                    srv.isDirectEmbed -> CinemaAccent.copy(alpha = 0.45f)
                    else -> CinemaBorder
                  }
                ),
                shadowElevation = if (isFocused) 14.dp else 2.dp,
                modifier = Modifier
                  .width(220.dp)
                  .heightIn(min = 120.dp)
                  .scale(if (isFocused) 1.04f else 1.0f)
                  .bringIntoViewRequester(bringIntoViewRequester)
                  .focusRequester(focusRequesters[index])
                  .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                      scope.launch {
                        bringIntoViewRequester.bringIntoView()
                      }
                    }
                  }
                  .focusable(interactionSource = cardInteraction)
                  .testTag("server_carousel_card_${srv.id}")
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(13.dp),
                  verticalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Surface(
                      color = when {
                        isFocused -> CinemaFocusGlow
                        isSelected -> CinemaSuccess
                        else -> CinemaDarkBackground
                      },
                      shape = RoundedCornerShape(6.dp)
                    ) {
                      Text(
                        text = "#${index + 1}",
                        color = if (isFocused || isSelected) Color.Black else CinemaAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                      )
                    }

                    if (isSelected) {
                      Surface(
                        color = CinemaSuccess,
                        shape = RoundedCornerShape(6.dp)
                      ) {
                        Text(
                          text = "يعمل ✓",
                          color = Color.Black,
                          fontSize = 12.sp,
                          fontWeight = FontWeight.ExtraBold,
                          modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                      }
                    } else if (srv.isDirectEmbed) {
                      Text(
                        text = "مباشر ⚡",
                        color = CinemaSuccess,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(8.dp))

                  Text(
                    text = srv.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                  )

                  Spacer(modifier = Modifier.height(8.dp))

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = srv.qualityLabel.ifBlank { "Auto HD" },
                      color = if (isFocused) CinemaFocusGlow else Color.LightGray,
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.SemiBold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f)
                    )
                    Icon(
                      imageVector = Icons.Default.PlayArrow,
                      contentDescription = null,
                      tint = if (isFocused) CinemaFocusGlow else CinemaSuccess,
                      modifier = Modifier.size(20.dp)
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
private fun SidePanelFocusPill(
  label: String,
  icon: ImageVector,
  containerColor: Color,
  contentColor: Color,
  borderColor: Color,
  iconTint: Color = contentColor,
  onClick: () -> Unit,
  testTag: String
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()
  Surface(
    onClick = onClick,
    interactionSource = interactionSource,
    color = if (isFocused) CinemaFocusGlow else containerColor,
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(
      width = if (isFocused) 2.dp else 1.dp,
      color = if (isFocused) Color.White else borderColor
    ),
    modifier = Modifier
      .heightIn(min = 38.dp)
      .focusable(interactionSource = interactionSource)
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (isFocused) Color.Black else iconTint,
        modifier = Modifier.size(16.dp)
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = label,
        color = if (isFocused) Color.Black else contentColor,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        softWrap = false
      )
    }
  }
}

@Composable
private fun SidePanelSquareIconBtn(
  icon: ImageVector,
  tint: Color,
  contentDescription: String,
  onClick: () -> Unit,
  testTag: String
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()
  Surface(
    onClick = onClick,
    interactionSource = interactionSource,
    color = if (isFocused) CinemaFocusGlow else CinemaSurface,
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(
      width = if (isFocused) 2.dp else 1.dp,
      color = if (isFocused) Color.White else CinemaBorder
    ),
    modifier = Modifier
      .size(38.dp)
      .focusable(interactionSource = interactionSource)
      .testTag(testTag)
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = if (isFocused) Color.Black else tint,
        modifier = Modifier.size(18.dp)
      )
    }
  }
}
