package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.MediaCatalogRepository
import com.example.model.MediaItem
import com.example.model.StreamingPortal
import com.example.model.WatchProgress
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaFocusGlow
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.LinearProgressIndicator

@Composable
fun TvHomeScreen(
  onPlayMedia: (MediaItem, Long) -> Unit,
  onOpenPortalUrl: (String) -> Unit,
  isBookmarked: (String) -> Boolean,
  onToggleBookmark: (MediaItem) -> Unit,
  recentlyWatched: List<WatchProgress> = emptyList(),
  onRemoveRecent: (String) -> Unit = {},
  onClearAllRecents: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var selectedDetailsItem by remember { mutableStateOf<MediaItem?>(null) }
  var heroItem by remember { mutableStateOf(MediaCatalogRepository.getFeaturedHero()) }
  var selectedRecentForAction by remember { mutableStateOf<WatchProgress?>(null) }

  val topCinemaItems = remember { MediaCatalogRepository.getTopCinemaItems() }
  val watchSeriesItems = remember { MediaCatalogRepository.getWatchSeriesItems() }
  val topRatedItems = remember { MediaCatalogRepository.getTopRated() }
  val animeItems = remember { MediaCatalogRepository.getAnimeItems() }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(CinemaDarkBackground),
    contentPadding = PaddingValues(bottom = 32.dp)
  ) {
    // 1. Hero Spotlight Banner (Top Large TV Display)
    item {
      TvHeroBanner(
        item = heroItem,
        isBookmarked = isBookmarked(heroItem.portalUrl),
        onPlayClick = { onPlayMedia(heroItem, 0L) },
        onOpenWebClick = { onOpenPortalUrl(heroItem.portalUrl) },
        onBookmarkClick = { onToggleBookmark(heroItem) },
        onDetailsClick = { selectedDetailsItem = heroItem }
      )
    }

    // 1.5. Recently Watched Section (Viewing history & resume playback)
    if (recentlyWatched.isNotEmpty()) {
      item {
        Spacer(modifier = Modifier.height(18.dp))
        TvRecentlyWatchedSection(
          items = recentlyWatched,
          onItemClick = { progress ->
            selectedRecentForAction = progress
          },
          onResumeQuick = { progress ->
            val fullItem = MediaCatalogRepository.findById(progress.mediaId) ?: MediaItem(
              id = progress.mediaId,
              title = progress.title,
              arabicTitle = progress.arabicTitle,
              portal = progress.portal,
              category = progress.category,
              genres = listOf("أكشن"),
              year = 2024,
              rating = progress.rating,
              qualityBadge = progress.qualityBadge,
              duration = formatRemainingTime(progress.remainingMs),
              description = progress.arabicTitle,
              posterUrl = progress.posterUrl,
              backdropUrl = progress.backdropUrl,
              portalUrl = progress.portalUrl,
              defaultStreamUrl = progress.defaultStreamUrl
            )
            onPlayMedia(fullItem, progress.positionMs)
          },
          onClearAll = onClearAllRecents
        )
      }
    }

    // 2. Carousel: TopCinema Latest Movies
    item {
      Spacer(modifier = Modifier.height(18.dp))
      TvMediaCarouselSection(
        title = "🎬 أحدث أفلام توب سينما (TopCinema.io)",
        subtitle = "أفلام حصرية عالية الدقة مع سيرفرات متعددة وترجمة احترافية",
        portalBadge = "توب سينما",
        badgeColor = CinemaPrimary,
        items = topCinemaItems,
        onItemClick = { item ->
          heroItem = item
          selectedDetailsItem = item
        }
      )
    }

    // 3. Carousel: WatchSeries Exclusive Shows
    item {
      Spacer(modifier = Modifier.height(24.dp))
      TvMediaCarouselSection(
        title = "📺 مسلسلات ووتش سيريز العالمية (WatchSeries.bar)",
        subtitle = "جميع المواسم والحلقات الجديدة للمسلسلات العالمية مترجمة بأعلى جودة",
        portalBadge = "ووتش سيريز",
        badgeColor = CinemaSecondary,
        items = watchSeriesItems,
        onItemClick = { item ->
          heroItem = item
          selectedDetailsItem = item
        }
      )
    }

    // 4. Carousel: Top Rated
    item {
      Spacer(modifier = Modifier.height(24.dp))
      TvMediaCarouselSection(
        title = "🏆 الأعلى تقييماً على مستوى العالم",
        subtitle = "أعمال سينمائية وتلفزيونية نالت أعلى تقييمات النقاد والجمهور",
        portalBadge = "VIP",
        badgeColor = CinemaAccent,
        items = topRatedItems,
        onItemClick = { item ->
          heroItem = item
          selectedDetailsItem = item
        }
      )
    }

    // 5. Carousel: Anime
    item {
      Spacer(modifier = Modifier.height(24.dp))
      TvMediaCarouselSection(
        title = "⛩️ روائع الأنمي والرسوم المتحركة",
        subtitle = "أقوى إنتاجات الأنمي الياباني بحلقات كاملة ودبلجة وترجمة معتمدة",
        portalBadge = "أنمي",
        badgeColor = Color(0xFFE91E63),
        items = animeItems,
        onItemClick = { item ->
          heroItem = item
          selectedDetailsItem = item
        }
      )
    }
  }

  // Media Details Dialog
  selectedDetailsItem?.let { item ->
    TvMediaDetailsDialog(
      item = item,
      isBookmarked = isBookmarked(item.portalUrl),
      onPlay = {
        selectedDetailsItem = null
        onPlayMedia(item, 0L)
      },
      onOpenWeb = {
        selectedDetailsItem = null
        onOpenPortalUrl(item.portalUrl)
      },
      onToggleBookmark = { onToggleBookmark(item) },
      onDismiss = { selectedDetailsItem = null }
    )
  }

  // Resume Action Dialog for Recently Watched items
  selectedRecentForAction?.let { progress ->
    val fullItem = MediaCatalogRepository.findById(progress.mediaId) ?: MediaItem(
      id = progress.mediaId,
      title = progress.title,
      arabicTitle = progress.arabicTitle,
      portal = progress.portal,
      category = progress.category,
      genres = listOf("أكشن"),
      year = 2024,
      rating = progress.rating,
      qualityBadge = progress.qualityBadge,
      duration = formatRemainingTime(progress.remainingMs),
      description = progress.arabicTitle,
      posterUrl = progress.posterUrl,
      backdropUrl = progress.backdropUrl,
      portalUrl = progress.portalUrl,
      defaultStreamUrl = progress.defaultStreamUrl
    )

    TvResumeActionDialog(
      progress = progress,
      onResume = {
        selectedRecentForAction = null
        onPlayMedia(fullItem, progress.positionMs)
      },
      onRestart = {
        selectedRecentForAction = null
        onPlayMedia(fullItem, 0L)
      },
      onOpenWeb = {
        selectedRecentForAction = null
        onOpenPortalUrl(progress.portalUrl)
      },
      onRemove = {
        selectedRecentForAction = null
        onRemoveRecent(progress.mediaId)
      },
      onDismiss = { selectedRecentForAction = null }
    )
  }
}

@Composable
fun TvHeroBanner(
  item: MediaItem,
  isBookmarked: Boolean,
  onPlayClick: () -> Unit,
  onOpenWebClick: () -> Unit,
  onBookmarkClick: () -> Unit,
  onDetailsClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(340.dp)
  ) {
    // Backdrop Image
    AsyncImage(
      model = item.backdropUrl,
      contentDescription = item.title,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize()
    )

    // Gradient Overlay for readability on TV
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.horizontalGradient(
            colors = listOf(
              CinemaDarkBackground.copy(alpha = 0.95f),
              CinemaDarkBackground.copy(alpha = 0.85f),
              CinemaDarkBackground.copy(alpha = 0.35f),
              Color.Transparent
            )
          )
        )
    )

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color.Transparent,
              CinemaDarkBackground.copy(alpha = 0.9f)
            ),
            startY = 200f
          )
        )
    )

    // Hero Content
    Column(
      modifier = Modifier
        .align(Alignment.CenterStart)
        .padding(start = 32.dp, top = 20.dp, end = 200.dp)
        .fillMaxWidth(0.65f),
      verticalArrangement = Arrangement.Center
    ) {
      // Badges Row
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // Portal Badge
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (item.portal == StreamingPortal.TOP_CINEMA) CinemaPrimary else CinemaSecondary)
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = if (item.portal == StreamingPortal.TOP_CINEMA) "توب سينما TopCinema" else "ووتش سيريز WatchSeries",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
          )
        }

        // Quality
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CinemaSurfaceVariant)
            .border(1.dp, CinemaBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(text = item.qualityBadge, color = CinemaAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        // Rating
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CinemaSurfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = CinemaAccent, modifier = Modifier.size(13.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = "${item.rating} ★", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Text(text = "${item.year} • ${item.duration}", color = Color.LightGray, fontSize = 12.sp)
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Arabic & English Title
      Text(
        text = item.arabicTitle,
        color = Color.White,
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = item.title,
        color = Color.LightGray,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Description
      Text(
        text = item.description,
        color = Color(0xFFD1D5DB),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Hero Action Buttons (Focusable with D-Pad)
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvHeroActionButton(
          label = "مشاهدة الآن بالمشغل المدمج",
          icon = Icons.Default.PlayArrow,
          isPrimary = true,
          testTag = "hero_play_btn",
          onClick = onPlayClick
        )

        TvHeroActionButton(
          label = "فتح في موقع ${if (item.portal == StreamingPortal.TOP_CINEMA) "توب سينما" else "ووتش سيريز"}",
          icon = Icons.Default.OpenInBrowser,
          isPrimary = false,
          testTag = "hero_open_web_btn",
          onClick = onOpenWebClick
        )

        TvHeroActionButton(
          label = if (isBookmarked) "في المفضلة ★" else "حفظ للمفضلة",
          icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
          isPrimary = false,
          testTag = "hero_bookmark_btn",
          onClick = onBookmarkClick
        )

        TvHeroActionButton(
          label = "تفاصيل العمل",
          icon = Icons.Default.Info,
          isPrimary = false,
          testTag = "hero_details_btn",
          onClick = onDetailsClick
        )
      }
    }
  }
}

@Composable
fun TvHeroActionButton(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  isPrimary: Boolean,
  testTag: String,
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Box(
    modifier = Modifier
      .testTag(testTag)
      .scale(if (isFocused) 1.1f else 1.0f)
      .clip(RoundedCornerShape(8.dp))
      .background(
        if (isFocused) CinemaFocusGlow
        else if (isPrimary) CinemaPrimary
        else CinemaSurface.copy(alpha = 0.9f)
      )
      .border(
        width = if (isFocused) 2.dp else 1.dp,
        color = if (isFocused) Color.White else if (isPrimary) CinemaAccent else CinemaBorder,
        shape = RoundedCornerShape(8.dp)
      )
      .focusable(interactionSource = interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 9.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isFocused && !isPrimary) Color.Black else Color.White,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        color = if (isFocused && !isPrimary) Color.Black else Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Composable
fun TvMediaCarouselSection(
  title: String,
  subtitle: String,
  portalBadge: String,
  badgeColor: Color,
  items: List<MediaItem>,
  onItemClick: (MediaItem) -> Unit
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    // Section Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.width(8.dp))
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(badgeColor.copy(alpha = 0.25f))
              .border(1.dp, badgeColor, RoundedCornerShape(4.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(text = portalBadge, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
          }
        }
        Text(
          text = subtitle,
          color = Color.Gray,
          fontSize = 11.sp
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Horizontal Row
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
      items(items, key = { it.id }) { item ->
        TvMediaCard(
          item = item,
          onClick = { onItemClick(item) }
        )
      }
    }
  }
}

@Composable
fun TvMediaCard(
  item: MediaItem,
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Column(
    modifier = Modifier
      .width(155.dp)
      .scale(if (isFocused) 1.08f else 1.0f)
      .clip(RoundedCornerShape(10.dp))
      .background(if (isFocused) CinemaFocusGlow.copy(alpha = 0.25f) else CinemaSurface)
      .border(
        width = if (isFocused) 2.dp else 1.dp,
        color = if (isFocused) CinemaFocusGlow else CinemaSurfaceVariant,
        shape = RoundedCornerShape(10.dp)
      )
      .focusable(interactionSource = interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
      .padding(6.dp)
  ) {
    // Poster with Badges
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(205.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(CinemaDarkBackground)
    ) {
      AsyncImage(
        model = item.posterUrl,
        contentDescription = item.arabicTitle,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )

      // Quality badge
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(4.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(Color.Black.copy(alpha = 0.75f))
          .padding(horizontal = 5.dp, vertical = 2.dp)
      ) {
        Text(text = item.qualityBadge, color = CinemaAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
      }

      // Rating badge
      Row(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(4.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(Color.Black.copy(alpha = 0.8f))
          .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = CinemaAccent, modifier = Modifier.size(11.dp))
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = "${item.rating}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Title
    Text(
      text = item.arabicTitle,
      color = Color.White,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )

    // Year & Category
    Text(
      text = "${item.year} • ${item.category.arabicLabel}",
      color = Color.LightGray,
      fontSize = 10.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvMediaDetailsDialog(
  item: MediaItem,
  isBookmarked: Boolean,
  onPlay: () -> Unit,
  onOpenWeb: () -> Unit,
  onToggleBookmark: () -> Unit,
  onDismiss: () -> Unit
) {
  BasicAlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.width(680.dp)
  ) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = CinemaSurface,
      border = androidx.compose.foundation.BorderStroke(1.5.dp, CinemaSurfaceVariant)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(22.dp)
      ) {
        // Dialog Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top
        ) {
          Row(modifier = Modifier.weight(1f)) {
            // Poster
            Box(
              modifier = Modifier
                .width(100.dp)
                .height(145.dp)
                .clip(RoundedCornerShape(8.dp))
            ) {
              AsyncImage(
                model = item.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
              Text(
                text = item.arabicTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Text(
                text = "${item.title} (${item.year})",
                fontSize = 13.sp,
                color = Color.LightGray
              )

              Spacer(modifier = Modifier.height(8.dp))

              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (item.portal == StreamingPortal.TOP_CINEMA) CinemaPrimary else CinemaSecondary)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                  Text(
                    text = if (item.portal == StreamingPortal.TOP_CINEMA) "توب سينما" else "ووتش سيريز",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                  )
                }

                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(CinemaSurfaceVariant)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                  Text(text = item.qualityBadge, color = CinemaAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                  text = "⭐ ${item.rating} • ${item.duration}",
                  color = Color.White,
                  fontSize = 11.sp,
                  modifier = Modifier.padding(top = 2.dp)
                )
              }

              Spacer(modifier = Modifier.height(8.dp))

              Text(
                text = "التصنيفات: ${item.genres.joinToString("، ")}",
                color = CinemaFocusGlow,
                fontSize = 11.sp
              )
            }
          }

          IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = Color.LightGray)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Description
        Text(
          text = item.description,
          color = Color(0xFFD1D5DB),
          fontSize = 12.sp,
          lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Available Features Row (Audio, Subtitles, Servers)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CinemaDarkBackground)
            .border(1.dp, CinemaBorder, RoundedCornerShape(8.dp))
            .padding(10.dp),
          horizontalArrangement = Arrangement.SpaceAround
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "سيرفرات البث", color = Color.Gray, fontSize = 10.sp)
            Text(text = "${item.servers.size} سيرفرات سريعة", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "خيارات الترجمة", color = Color.Gray, fontSize = 10.sp)
            Text(text = item.subtitleTracks.first(), color = CinemaAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "مسارات الصوت", color = Color.Gray, fontSize = 10.sp)
            Text(text = item.audioTracks.first().take(12), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Action Buttons Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          Button(
            onClick = onToggleBookmark,
            colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(end = 8.dp)
          ) {
            Text(text = if (isBookmarked) "في المفضلة ★" else "+ المفضلة", color = Color.White)
          }

          Button(
            onClick = onOpenWeb,
            colors = ButtonDefaults.buttonColors(containerColor = CinemaSecondary),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(end = 8.dp)
          ) {
            Text(text = "فتح بالمتصفح المباشر", color = Color.White, fontWeight = FontWeight.Bold)
          }

          Button(
            onClick = onPlay,
            colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
            shape = RoundedCornerShape(8.dp)
          ) {
            Text(text = "▶ تشغيل بالمشغل المدمج", color = Color.White, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
fun TvRecentlyWatchedSection(
  items: List<WatchProgress>,
  onItemClick: (WatchProgress) -> Unit,
  onResumeQuick: (WatchProgress) -> Unit,
  onClearAll: () -> Unit
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    // Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "⏱️ متابعة المشاهدة (Recently Watched)",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.width(8.dp))
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(CinemaAccent.copy(alpha = 0.25f))
              .border(1.dp, CinemaAccent, RoundedCornerShape(4.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(text = "${items.size} أعمال", color = CinemaAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
          }
        }
        Text(
          text = "استئناف الأفلام والمسلسلات من الدقيقة التي توقفت عندها مباشرة",
          color = Color.Gray,
          fontSize = 11.sp
        )
      }

      // Clear all recents button
      val clearInteraction = remember { MutableInteractionSource() }
      val isClearFocused by clearInteraction.collectIsFocusedAsState()
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(6.dp))
          .background(if (isClearFocused) Color(0xFFEF4444).copy(alpha = 0.35f) else CinemaSurfaceVariant)
          .border(1.dp, if (isClearFocused) Color(0xFFEF4444) else CinemaBorder, RoundedCornerShape(6.dp))
          .focusable(interactionSource = clearInteraction)
          .clickable(interactionSource = clearInteraction, indication = null, onClick = onClearAll)
          .padding(horizontal = 10.dp, vertical = 5.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.DeleteSweep,
            contentDescription = null,
            tint = if (isClearFocused) Color(0xFFEF4444) else Color.LightGray,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "مسح السجل",
            color = if (isClearFocused) Color(0xFFEF4444) else Color.LightGray,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Carousels of Recently Watched cards
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
      items(items, key = { it.mediaId }) { item ->
        TvRecentlyWatchedCard(
          progress = item,
          onClick = { onItemClick(item) }
        )
      }
    }
  }
}

@Composable
fun TvRecentlyWatchedCard(
  progress: WatchProgress,
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Column(
    modifier = Modifier
      .width(235.dp)
      .scale(if (isFocused) 1.07f else 1.0f)
      .clip(RoundedCornerShape(10.dp))
      .background(if (isFocused) CinemaFocusGlow.copy(alpha = 0.25f) else CinemaSurface)
      .border(
        width = if (isFocused) 2.dp else 1.dp,
        color = if (isFocused) CinemaFocusGlow else CinemaSurfaceVariant,
        shape = RoundedCornerShape(10.dp)
      )
      .focusable(interactionSource = interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
      .padding(6.dp)
  ) {
    // Backdrop / Thumbnail container
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(132.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(CinemaDarkBackground)
    ) {
      AsyncImage(
        model = if (progress.backdropUrl.isNotBlank()) progress.backdropUrl else progress.posterUrl,
        contentDescription = progress.arabicTitle,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )

      // Dark gradient overlay
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.85f)
              ),
              startY = 40f
            )
          )
      )

      // Portal Badge
      Box(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(5.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(if (progress.portal == StreamingPortal.TOP_CINEMA) CinemaPrimary.copy(alpha = 0.85f) else CinemaSecondary.copy(alpha = 0.85f))
          .padding(horizontal = 5.dp, vertical = 2.dp)
      ) {
        Text(
          text = if (progress.portal == StreamingPortal.TOP_CINEMA) "توب سينما" else "ووتش سيريز",
          color = Color.White,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold
        )
      }

      // Remaining Time Badge
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(5.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(Color.Black.copy(alpha = 0.75f))
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text(
          text = formatRemainingTime(progress.remainingMs),
          color = CinemaAccent,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold
        )
      }

      // Resume Play Icon Indicator (center)
      Box(
        modifier = Modifier
          .align(Alignment.Center)
          .size(40.dp)
          .clip(CircleShape)
          .background(if (isFocused) CinemaFocusGlow else CinemaPrimary.copy(alpha = 0.85f))
          .border(1.5.dp, if (isFocused) Color.White else CinemaAccent, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription = "استئناف",
          tint = Color.White,
          modifier = Modifier.size(24.dp)
        )
      }

      // Progress Line Bar across bottom of thumbnail
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "استئناف: ${formatTimePosition(progress.positionMs)}",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold
          )
          Text(
            text = "${progress.progressPercent}%",
            color = CinemaAccent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
          )
        }

        LinearProgressIndicator(
          progress = { progress.progressFraction },
          modifier = Modifier
            .fillMaxWidth()
            .height(4.dp),
          color = CinemaPrimary,
          trackColor = Color(0xFF374151)
        )
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Arabic Title
    Text(
      text = progress.arabicTitle,
      color = Color.White,
      fontSize = 13.sp,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )

    // English Title
    Text(
      text = progress.title,
      color = Color.LightGray,
      fontSize = 10.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvResumeActionDialog(
  progress: WatchProgress,
  onResume: () -> Unit,
  onRestart: () -> Unit,
  onOpenWeb: () -> Unit,
  onRemove: () -> Unit,
  onDismiss: () -> Unit
) {
  BasicAlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.width(580.dp)
  ) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = CinemaSurface,
      border = androidx.compose.foundation.BorderStroke(1.5.dp, CinemaSurfaceVariant)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(22.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top
        ) {
          Row(modifier = Modifier.weight(1f)) {
            // Poster
            Box(
              modifier = Modifier
                .width(85.dp)
                .height(125.dp)
                .clip(RoundedCornerShape(8.dp))
            ) {
              AsyncImage(
                model = progress.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
              Text(
                text = progress.arabicTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Text(
                text = progress.title,
                fontSize = 12.sp,
                color = Color.LightGray
              )

              Spacer(modifier = Modifier.height(8.dp))

              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (progress.portal == StreamingPortal.TOP_CINEMA) CinemaPrimary else CinemaSecondary)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = if (progress.portal == StreamingPortal.TOP_CINEMA) "توب سينما" else "ووتش سيريز",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                  )
                }

                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(CinemaSurfaceVariant)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(text = "${progress.progressPercent}% تم عرضه", color = CinemaAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
              }

              Spacer(modifier = Modifier.height(8.dp))

              Text(
                text = "توقفت عند: ${formatTimePosition(progress.positionMs)} من إجمالي ${formatTimePosition(progress.durationMs)}",
                color = CinemaFocusGlow,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = formatRemainingTime(progress.remainingMs),
                color = Color.LightGray,
                fontSize = 11.sp
              )
            }
          }

          IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = Color.LightGray)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Progress bar in modal
        LinearProgressIndicator(
          progress = { progress.progressFraction },
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
          color = CinemaPrimary,
          trackColor = CinemaSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Action Buttons Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Remove from history
          Button(
            onClick = onRemove,
            colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFFEF4444))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "حذف من السجل", color = Color(0xFFEF4444), fontSize = 11.sp)
          }

          // Restart from beginning
          Button(
            onClick = onRestart,
            colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(imageVector = Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "من البداية", color = Color.White, fontSize = 11.sp)
          }

          // Open in Web Portal
          Button(
            onClick = onOpenWeb,
            colors = ButtonDefaults.buttonColors(containerColor = CinemaSecondary),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1.2f)
          ) {
            Text(text = "المتصفح المباشر", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }

          // Resume Playback (Primary Action)
          Button(
            onClick = onResume,
            colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1.5f)
          ) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "استئناف المشاهدة ▶", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

private fun formatRemainingTime(remainingMs: Long): String {
  val totalMinutes = (remainingMs / 60000).toInt()
  if (totalMinutes <= 0) return "مكتمل"
  val hours = totalMinutes / 60
  val minutes = totalMinutes % 60
  return if (hours > 0) "متبقي ${hours}س ${minutes}د" else "متبقي $minutes دقيقة"
}

private fun formatTimePosition(millis: Long): String {
  val totalSeconds = (millis / 1000).coerceAtLeast(0)
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = totalSeconds % 60
  return if (hours > 0) {
    java.lang.String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
  } else {
    java.lang.String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
  }
}

