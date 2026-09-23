package com.example.ui.search

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.MediaCatalogRepository
import com.example.data.SortOption
import com.example.model.MediaItem
import com.example.model.StreamingPortal
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaFocusGlow
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant

@Composable
fun TvSearchScreen(
  onSelectMedia: (MediaItem) -> Unit,
  modifier: Modifier = Modifier
) {
  var searchQuery by remember { mutableStateOf("") }
  var selectedPortal by remember { mutableStateOf<StreamingPortal?>(null) }
  var selectedGenre by remember { mutableStateOf("الكل") }
  var selectedYear by remember { mutableStateOf("جميع السنوات") }
  var selectedMinRating by remember { mutableStateOf<Double?>(null) }
  var selectedSort by remember { mutableStateOf(SortOption.LATEST) }

  var showOnScreenKeyboard by remember { mutableStateOf(false) }

  // Available filter choices
  val genres = listOf("الكل", "أكشن", "دراما", "خيال علمي", "جريمة", "تاريخي", "أنمي", "فانتازيا", "مغامرة")
  val years = listOf("جميع السنوات", "2024", "2023", "2022", "كلاسيكيات (2021 وما قبل)")
  val ratings = listOf(
    Pair("جميع التقييمات", null),
    Pair("9.0+ ★", 9.0),
    Pair("8.5+ ★", 8.5),
    Pair("8.0+ ★", 8.0)
  )

  val quickTags = listOf("Dune", "House of Dragon", "الحشاشين", "Shogun", "ولاد رزق", "أكشن", "أنمي", "Breaking Bad")

  val searchResults by remember(searchQuery, selectedPortal, selectedGenre, selectedYear, selectedMinRating, selectedSort) {
    derivedStateOf {
      MediaCatalogRepository.filterAndSearch(
        query = searchQuery,
        portal = selectedPortal,
        genre = selectedGenre,
        year = selectedYear,
        minRating = selectedMinRating,
        sortBy = selectedSort
      )
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(CinemaDarkBackground)
      .padding(horizontal = 24.dp, vertical = 12.dp)
  ) {
    // Top Search Input Box & Keyboard Toggle
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(12.dp))
          .background(CinemaSurface)
          .border(1.5.dp, CinemaSurfaceVariant, RoundedCornerShape(12.dp))
          .padding(horizontal = 16.dp, vertical = 10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = CinemaAccent,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = if (searchQuery.isEmpty()) "ابحث عن الأفلام والمسلسلات في TopCinema و WatchSeries..." else searchQuery,
            color = if (searchQuery.isEmpty()) Color.Gray else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
          )
          if (searchQuery.isNotEmpty()) {
            IconButton(
              onClick = { searchQuery = "" },
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "مسح",
                tint = Color.LightGray,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      // TV Virtual Keyboard Toggle Button
      FilterPill(
        label = if (showOnScreenKeyboard) "إخفاء لوحة التحكم" else "لوحة مفاتيح الريموت",
        isSelected = showOnScreenKeyboard,
        testTag = "toggle_tv_keyboard_btn",
        onClick = { showOnScreenKeyboard = !showOnScreenKeyboard }
      )
    }

    // On-Screen TV Virtual Keyboard for D-Pad
    if (showOnScreenKeyboard) {
      Spacer(modifier = Modifier.height(10.dp))
      TvVirtualKeyboard(
        onCharTyped = { char -> searchQuery += char },
        onBackspace = { if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1) },
        onSpace = { searchQuery += " " },
        onClear = { searchQuery = "" }
      )
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Quick Search Tags
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(vertical = 4.dp)
    ) {
      items(quickTags) { tag ->
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        Box(
          modifier = Modifier
            .scale(if (isFocused) 1.08f else 1.0f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) CinemaFocusGlow.copy(alpha = 0.3f) else CinemaSurfaceVariant)
            .border(1.dp, if (isFocused) CinemaFocusGlow else CinemaBorder, RoundedCornerShape(8.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) {
              searchQuery = tag
            }
            .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
          Text(text = tag, color = if (isFocused) CinemaFocusGlow else Color.LightGray, fontSize = 11.sp)
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Interactive Filters Bar (Portal, Genre, Year, Rating, Sort)
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(CinemaSurface.copy(alpha = 0.85f))
        .border(1.dp, CinemaSurfaceVariant, RoundedCornerShape(12.dp))
        .padding(horizontal = 14.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Row 1: Source Portal + Sorting
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(text = "المصدر:", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          FilterPill(
            label = "الكل",
            isSelected = selectedPortal == null,
            testTag = "filter_portal_all",
            onClick = { selectedPortal = null }
          )
          FilterPill(
            label = "توب سينما TopCinema",
            isSelected = selectedPortal == StreamingPortal.TOP_CINEMA,
            testTag = "filter_portal_tc",
            activeColor = CinemaPrimary,
            onClick = { selectedPortal = StreamingPortal.TOP_CINEMA }
          )
          FilterPill(
            label = "ووتش سيريز WatchSeries",
            isSelected = selectedPortal == StreamingPortal.WATCH_SERIES,
            testTag = "filter_portal_ws",
            activeColor = CinemaSecondary,
            onClick = { selectedPortal = StreamingPortal.WATCH_SERIES }
          )
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(text = "الترتيب:", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          SortOption.entries.forEach { sort ->
            FilterPill(
              label = sort.arabicLabel,
              isSelected = selectedSort == sort,
              testTag = "filter_sort_${sort.name}",
              activeColor = CinemaAccent,
              onClick = { selectedSort = sort }
            )
          }
        }
      }

      // Row 2: Genre + Year + Rating
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Genres
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.weight(1f)
        ) {
          item {
            Text(
              text = "النوع:",
              color = Color.Gray,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(end = 4.dp, top = 4.dp)
            )
          }
          items(genres) { g ->
            FilterPill(
              label = g,
              isSelected = selectedGenre == g,
              testTag = "filter_genre_$g",
              onClick = { selectedGenre = g }
            )
          }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Years
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(text = "السنة:", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
          years.take(3).forEach { y ->
            FilterPill(
              label = y,
              isSelected = selectedYear == y,
              testTag = "filter_year_$y",
              onClick = { selectedYear = y }
            )
          }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Ratings
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          ratings.take(3).forEach { (label, minVal) ->
            FilterPill(
              label = label,
              isSelected = selectedMinRating == minVal,
              testTag = "filter_rating_$label",
              activeColor = CinemaAccent,
              onClick = { selectedMinRating = minVal }
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Results Header Count
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "نتائج البحث والتصنيف (${searchResults.size}) عنصر",
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
      )
      if (searchQuery.isNotEmpty() || selectedPortal != null || selectedGenre != "الكل" || selectedYear != "جميع السنوات" || selectedMinRating != null) {
        Text(
          text = "إعادة ضبط التصفية",
          color = CinemaAccent,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier
            .clickable {
              searchQuery = ""
              selectedPortal = null
              selectedGenre = "الكل"
              selectedYear = "جميع السنوات"
              selectedMinRating = null
              selectedSort = SortOption.LATEST
            }
            .padding(4.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Results Grid
    if (searchResults.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Default.FilterList,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(48.dp)
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "لم يتم العثور على محتوى يطابق معايير البحث الحالية.",
            color = Color.LightGray,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
          )
          Text(
            text = "جرب تغيير خيارات التصفية أو البحث عن اسم فيلم أو مسلسل آخر",
            color = Color.Gray,
            fontSize = 12.sp
          )
        }
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        items(searchResults, key = { it.id }) { item ->
          TvSearchCard(
            item = item,
            onClick = { onSelectMedia(item) }
          )
        }
      }
    }
  }
}

@Composable
fun FilterPill(
  label: String,
  isSelected: Boolean,
  testTag: String,
  onClick: () -> Unit,
  activeColor: Color = CinemaFocusGlow
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Box(
    modifier = Modifier
      .testTag(testTag)
      .scale(if (isFocused) 1.08f else 1.0f)
      .clip(RoundedCornerShape(8.dp))
      .background(if (isSelected) activeColor.copy(alpha = 0.25f) else if (isFocused) CinemaFocusGlow.copy(alpha = 0.25f) else CinemaDarkBackground)
      .border(if (isSelected) 1.5.dp else if (isFocused) 1.5.dp else 1.dp, if (isSelected) activeColor else if (isFocused) CinemaFocusGlow else CinemaBorder, RoundedCornerShape(8.dp))
      .focusable(interactionSource = interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 5.dp)
  ) {
    Text(
      text = label,
      color = if (isSelected) activeColor else if (isFocused) CinemaFocusGlow else Color.White,
      fontSize = 11.sp,
      fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
    )
  }
}

@Composable
fun TvSearchCard(
  item: MediaItem,
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Column(
    modifier = Modifier
      .scale(if (isFocused) 1.06f else 1.0f)
      .clip(RoundedCornerShape(12.dp))
      .background(if (isFocused) CinemaFocusGlow.copy(alpha = 0.25f) else CinemaSurface)
      .border(
        width = if (isFocused) 2.dp else 1.dp,
        color = if (isFocused) CinemaFocusGlow else CinemaSurfaceVariant,
        shape = RoundedCornerShape(12.dp)
      )
      .focusable(interactionSource = interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
      .padding(8.dp)
  ) {
    // Poster image with quality badge & portal indicator
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(170.dp)
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
          .padding(6.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(Color.Black.copy(alpha = 0.75f))
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text(text = item.qualityBadge, color = CinemaAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
      }

      // Portal badge
      Box(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(6.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(if (item.portal == StreamingPortal.TOP_CINEMA) CinemaPrimary else CinemaSecondary)
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text(
          text = if (item.portal == StreamingPortal.TOP_CINEMA) "توب سينما" else "ووتش سيريز",
          color = Color.White,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Title
    Text(
      text = item.arabicTitle,
      color = Color.White,
      fontSize = 13.sp,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )

    // Subtitle & Rating
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "${item.year} • ${item.genres.firstOrNull() ?: ""}",
        color = Color.LightGray,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Star,
          contentDescription = null,
          tint = CinemaAccent,
          modifier = Modifier.size(13.dp)
        )
        Text(
          text = item.rating.toString(),
          color = CinemaAccent,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
fun TvVirtualKeyboard(
  onCharTyped: (String) -> Unit,
  onBackspace: () -> Unit,
  onSpace: () -> Unit,
  onClear: () -> Unit
) {
  val row1Arabic = listOf("ا", "ب", "ت", "ث", "ج", "ح", "خ", "د", "ذ", "ر", "ز", "س")
  val row2Arabic = listOf("ش", "ص", "ض", "ط", "ظ", "ع", "غ", "ف", "ق", "ك", "ل", "م")
  val row3Arabic = listOf("ن", "هـ", "و", "ي", "ة", "1", "2", "3", "4", "5", "6", "7")
  val row4English = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l")
  val row5English = listOf("m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z")

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(CinemaSurface)
      .border(1.dp, CinemaSurfaceVariant, RoundedCornerShape(12.dp))
      .padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    KeyboardRow(keys = row1Arabic, onKeyClick = onCharTyped)
    KeyboardRow(keys = row2Arabic, onKeyClick = onCharTyped)
    KeyboardRow(keys = row3Arabic, onKeyClick = onCharTyped)
    KeyboardRow(keys = row4English, onKeyClick = onCharTyped)
    KeyboardRow(keys = row5English, onKeyClick = onCharTyped)

    // Actions Row (Space, Backspace, Clear)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
      TvKeyButton(label = "مسافة [Space]", width = 160.dp, onClick = onSpace)
      Spacer(modifier = Modifier.width(8.dp))
      TvKeyButton(label = "حذف ⌫", width = 110.dp, onClick = onBackspace)
      Spacer(modifier = Modifier.width(8.dp))
      TvKeyButton(label = "مسح الكل", width = 110.dp, onClick = onClear)
    }
  }
}

@Composable
fun KeyboardRow(
  keys: List<String>,
  onKeyClick: (String) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    keys.forEach { key ->
      TvKeyButton(label = key, width = 36.dp, onClick = { onKeyClick(key) })
      Spacer(modifier = Modifier.width(4.dp))
    }
  }
}

@Composable
fun TvKeyButton(
  label: String,
  width: androidx.compose.ui.unit.Dp,
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Box(
    modifier = Modifier
      .width(width)
      .height(34.dp)
      .scale(if (isFocused) 1.15f else 1.0f)
      .clip(RoundedCornerShape(6.dp))
      .background(if (isFocused) CinemaFocusGlow.copy(alpha = 0.35f) else CinemaDarkBackground)
      .border(if (isFocused) 1.5.dp else 1.dp, if (isFocused) CinemaFocusGlow else CinemaBorder, RoundedCornerShape(6.dp))
      .focusable(interactionSource = interactionSource)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      color = if (isFocused) CinemaFocusGlow else Color.White,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold
    )
  }
}
