package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.ClipboxWhite

@Composable
fun WatchSidePanel(
  activePortal: StreamingPortal,
  currentUrl: String,
  activeWatchPageUrl: String = currentUrl,
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
  isCompactMobileSheet: Boolean = false,
  modifier: Modifier = Modifier
) {
  var selectedPortalTab by remember(activePortal) { mutableStateOf(activePortal) }
  // 0 = Seasons & Episodes (if series) or Sources; 1 = Sources
  var activeSubTab by remember(seriesStructure.episodes.isNotEmpty(), seriesStructure.seasons.isNotEmpty()) {
    mutableIntStateOf(if (seriesStructure.episodes.isNotEmpty() || seriesStructure.seasons.isNotEmpty()) 0 else 1)
  }

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
      verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      // 1. Sleek Single-Line Header + Native Player Toggle + Fullscreen + Diagnostics Log + CC + Refresh + Close
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
          Spacer(modifier = Modifier.width(5.dp))
          Text(
            text = if (searchQuery.isNotBlank()) searchQuery else activePortal.displayNameArabic,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        if (isScrapingSeries) {
          CircularProgressIndicator(
            color = CinemaAccent,
            modifier = Modifier
              .padding(end = 5.dp)
              .size(14.dp),
            strokeWidth = 2.dp
          )
        }

        // Non-blocking switch button to Primary Native Player (replaces the old bottom banner that covered the video controls!)
        if (hasDetectedDirectStream && !isNativePlayerActive) {
          Surface(
            onClick = onSwitchToNativePlayer,
            color = CinemaSuccess,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .heightIn(min = 27.dp)
              .testTag("play_native_video_button")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(13.dp)
              )
              Spacer(modifier = Modifier.width(2.dp))
              Text(
                text = "المشغل الأساسي",
                color = Color.Black,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
              )
            }
          }
          Spacer(modifier = Modifier.width(4.dp))
        }

        // Video Fullscreen button (rotates to landscape and fills screen with the video player)
        Surface(
          onClick = onToggleVideoFullscreen,
          color = CinemaPrimary,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .heightIn(min = 27.dp)
            .testTag("side_panel_fullscreen_button")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Fullscreen,
              contentDescription = "تكبير المشغل ملء الشاشة",
              tint = Color.White,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
              text = "ملء الشاشة",
              color = Color.White,
              fontSize = 9.5.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              softWrap = false
            )
          }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Subtitles CC Button
        Surface(
          onClick = onOpenSubtitles,
          color = CinemaSurfaceVariant,
          border = BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.8f)),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .heightIn(min = 27.dp)
            .testTag("side_panel_subtitles_button")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Subtitles,
              contentDescription = null,
              tint = CinemaAccent,
              modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = "CC",
              color = Color.White,
              fontSize = 9.5.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false
            )
          }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Diagnostics & Log Copy Button
        IconButton(
          onClick = onOpenDiagnostics,
          modifier = Modifier
            .size(27.dp)
            .clip(CircleShape)
            .background(CinemaSurface)
            .border(1.dp, CinemaAccent.copy(alpha = 0.6f), CircleShape)
            .testTag("btn_open_diagnostics_log")
        ) {
          Icon(
            imageVector = Icons.Default.BugReport,
            contentDescription = "نسخ اللوج وتشخيص الأعطال",
            tint = CinemaAccent,
            modifier = Modifier.size(14.dp)
          )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // In-App GitHub Update Button
        IconButton(
          onClick = onOpenUpdate,
          modifier = Modifier
            .size(27.dp)
            .clip(CircleShape)
            .background(CinemaSurface)
            .testTag("btn_open_update_from_watch")
        ) {
          Icon(
            imageVector = Icons.Default.SystemUpdate,
            contentDescription = "تحديث التطبيق من جيت هب",
            tint = CinemaSuccess,
            modifier = Modifier.size(14.dp)
          )
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
          onClick = onRescrapePage,
          modifier = Modifier
            .size(27.dp)
            .clip(CircleShape)
            .background(CinemaSurface)
            .testTag("btn_rescrape_seasons_episodes")
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "تحديث السيرفرات والحلقات",
            tint = CinemaAccent,
            modifier = Modifier.size(13.dp)
          )
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
          onClick = onClosePanel,
          modifier = Modifier
            .size(27.dp)
            .clip(CircleShape)
            .background(CinemaSurface)
            .testTag("btn_close_watch_side_panel")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "إخفاء اللوحة",
            tint = Color.LightGray,
            modifier = Modifier.size(13.dp)
          )
        }
      }

      // 2. Compact Single-Line Scraped Watch Servers Strip
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(CinemaSurface)
          .border(1.dp, CinemaBorder, RoundedCornerShape(8.dp))
          .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.Dns,
          contentDescription = null,
          tint = CinemaSuccess,
          modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "السيرفرات (${seriesStructure.watchServers.size}):",
          color = Color.White,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          softWrap = false
        )
        Spacer(modifier = Modifier.width(6.dp))
        if (seriesStructure.watchServers.isNotEmpty()) {
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f)
          ) {
            items(seriesStructure.watchServers, key = { it.id }) { srv ->
              val isSelected = currentUrl == srv.embedUrl
              Surface(
                onClick = { onSelectWatchServer(srv) },
                color = if (isSelected) ClipboxWhite else CinemaDarkBackground,
                shape = RoundedCornerShape(7.dp),
                border = BorderStroke(
                  1.dp,
                  if (isSelected) ClipboxWhite else if (srv.isDirectEmbed) CinemaSuccess.copy(alpha = 0.7f) else CinemaBorder
                ),
                modifier = Modifier.heightIn(min = 28.dp)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isSelected) Color.Black else CinemaSuccess,
                    modifier = Modifier.size(12.dp)
                  )
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    text = srv.name,
                    color = if (isSelected) Color.Black else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                  )
                }
              }
            }
          }
        } else {
          Text(
            text = "جاري سحب السيرفرات...",
            color = Color.LightGray,
            fontSize = 10.sp,
            maxLines = 1
          )
        }
      }

      // 3. Auto-Detected Real Video Qualities Strip (No fake static list!)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(CinemaSurface)
          .border(1.dp, CinemaBorder, RoundedCornerShape(8.dp))
          .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.HighQuality,
          contentDescription = null,
          tint = if (detectedQualities.isNotEmpty()) CinemaSuccess else CinemaSecondary,
          modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = if (detectedQualities.isNotEmpty()) "جودات الفيديو:" else "الجودة:",
          color = Color.White,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          softWrap = false
        )
        Spacer(modifier = Modifier.width(6.dp))
        if (detectedQualities.isNotEmpty()) {
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f)
          ) {
            items(detectedQualities, key = { it.id }) { qOption ->
              val selected = currentQuality == qOption.id
              Surface(
                onClick = { onSelectQuality(qOption) },
                color = if (selected) ClipboxWhite else CinemaDarkBackground,
                shape = RoundedCornerShape(7.dp),
                border = BorderStroke(1.dp, if (selected) ClipboxWhite else CinemaSuccess.copy(alpha = 0.6f)),
                modifier = Modifier.heightIn(min = 26.dp)
              ) {
                Box(
                  contentAlignment = Alignment.Center,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                  Text(
                    text = qOption.label,
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                  )
                }
              }
            }
          }
        } else {
          Text(
            text = "تلقائي Auto (سيتم إظهار الجودات الفعلية فور بدء البث...)",
            color = CinemaAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
        }
      }

      // 4. Mode Switcher Tabs: [📺 المواسم والحلقات] vs [🌐 لستة المصادر والنتائج]
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Surface(
          onClick = { activeSubTab = 0 },
          color = if (activeSubTab == 0) ClipboxWhite else CinemaSurface,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, if (activeSubTab == 0) ClipboxWhite else CinemaBorder),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 32.dp)
            .testTag("tab_seasons_episodes")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.VideoLibrary,
              contentDescription = null,
              tint = if (activeSubTab == 0) Color.Black else CinemaAccent,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = "المواسم والحلقات (${seriesStructure.episodes.size})",
              color = if (activeSubTab == 0) Color.Black else Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              softWrap = false
            )
          }
        }

        Surface(
          onClick = { activeSubTab = 1 },
          color = if (activeSubTab == 1) ClipboxWhite else CinemaSurface,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, if (activeSubTab == 1) ClipboxWhite else CinemaBorder),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 32.dp)
            .testTag("tab_alternative_sources")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Layers,
              contentDescription = null,
              tint = if (activeSubTab == 1) Color.Black else CinemaPrimary,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = "لستة المصادر والنتائج",
              color = if (activeSubTab == 1) Color.Black else Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              softWrap = false
            )
          }
        }
      }

      // 5. Large, Expanded Content Area (No whole-page vertical scroll!)
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
                  text = "اختر الموسم (${seriesStructure.seasons.size} مواسم):",
                  color = CinemaAccent,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.ExtraBold
                )
                Text(
                  text = "الموسم الحالي: ${seriesStructure.selectedSeasonNumber}",
                  color = Color.LightGray,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.SemiBold
                )
              }

              LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                items(seriesStructure.seasons, key = { "${it.seasonNumber}_${it.url}" }) { season ->
                  val isSelected = season.seasonNumber == seriesStructure.selectedSeasonNumber
                  Surface(
                    onClick = { onSelectSeason(season) },
                    color = if (isSelected) CinemaPrimary else CinemaDarkBackground,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                      1.5.dp,
                      if (isSelected) ClipboxWhite else CinemaBorder
                    ),
                    modifier = Modifier.heightIn(min = 38.dp)
                  ) {
                    Box(
                      contentAlignment = Alignment.Center,
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                      Text(
                        text = season.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                      )
                    }
                  }
                }
              }
            }

            // Expanded Multi-Row Episodes Grid (Fills all remaining height clearly!)
            if (seriesStructure.episodes.isNotEmpty()) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "اختر الحلقة للتشغيل الفوري (${seriesStructure.episodes.size} حلقة متاحة):",
                  color = Color.White,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.ExtraBold
                )
              }

              LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
              ) {
                items(seriesStructure.episodes, key = { "${it.episodeNumber}_${it.url}" }) { ep ->
                  val isCurrentEp = activeWatchPageUrl == ep.url || currentUrl == ep.url
                  Surface(
                    onClick = { onSelectEpisode(ep) },
                    color = if (isCurrentEp) CinemaSuccess else CinemaDarkBackground,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                      1.5.dp,
                      if (isCurrentEp) ClipboxWhite else CinemaBorder
                    ),
                    modifier = Modifier
                      .fillMaxWidth()
                      .heightIn(min = 42.dp)
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isCurrentEp) Color.White else CinemaAccent,
                        modifier = Modifier.size(15.dp)
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                        text = "الحلقة ${ep.episodeNumber}",
                        color = Color.White,
                        fontSize = 12.sp,
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
                    "جاري استخراج المواسم والحلقات..."
                  } else {
                    "هذا العمل فيلم، أو يمكنك اختيار الحلقة من تبويب \"لستة المصادر والنتائج\" بجواره."
                  },
                  color = Color.LightGray,
                  fontSize = 12.sp,
                  textAlign = TextAlign.Center
                )
              }
            }
          }
        } else {
          // Tab 1: Alternative Sources & Search Results
          Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            val enabledPortals = StreamingPortal.values().filter { it.isEnabled }
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(enabledPortals) { portal ->
                val count = portalResultsMap[portal]?.size ?: 0
                val isSelected = selectedPortalTab == portal
                FilterChip(
                  selected = isSelected,
                  onClick = { selectedPortalTab = portal },
                  label = {
                    Text(
                      text = if (count > 0) "${portal.displayNameArabic} ($count)" else portal.displayNameArabic,
                      fontSize = 11.sp,
                      fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
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
                  modifier = Modifier.heightIn(min = 32.dp)
                )
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
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .background(if (isCurrentUrl) CinemaPrimary.copy(alpha = 0.25f) else CinemaDarkBackground)
                      .border(
                        1.dp,
                        if (isCurrentUrl) CinemaPrimary else CinemaBorder,
                        RoundedCornerShape(8.dp)
                      )
                      .clickable { onSelectSearchResult(resultItem, selectedPortalTab) }
                      .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = resultItem.title,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        text = "${selectedPortalTab.displayNameArabic} • ${resultItem.type} • ${resultItem.quality}",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isCurrentUrl) CinemaSuccess else CinemaPrimary)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                      Text(
                        text = if (isCurrentUrl) "يعمل الآن" else "تشغيل ▶",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
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
                Button(
                  onClick = { onSwitchPortalDirect(selectedPortalTab) },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = CinemaDarkBackground,
                    contentColor = Color.White
                  ),
                  border = BorderStroke(1.dp, CinemaBorder),
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                ) {
                  Text(
                    text = "البحث عن العمل في ${selectedPortalTab.displayNameArabic} 🔍",
                    fontSize = 11.sp,
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
