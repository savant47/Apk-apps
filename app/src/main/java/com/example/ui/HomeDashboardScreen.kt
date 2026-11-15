package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.res.painterResource
import com.example.R


@Composable
fun HomeDashboardScreen(
  repository: TvPreferencesRepository,
  onLaunchUrl: (url: String, portal: StreamingPortal, title: String) -> Unit,
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

  var searchQuery by remember { mutableStateOf("") }
  var isSearching by remember { mutableStateOf(false) }
  var portalResultsMap by remember { mutableStateOf<Map<StreamingPortal, List<SearchResultItem>>>(emptyMap()) }
  var hasSearched by remember { mutableStateOf(false) }
  var showSourcesDialog by remember { mutableStateOf(false) }
  var spellingSuggestion by remember { mutableStateOf<String?>(null) }

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
    spellingSuggestion = getSpellingSuggestion(q)

    scope.launch {
      val results = searchService.searchAllPortals(q)
      portalResultsMap = results
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
    // 1. Compact Sub-Header for Asser TV (no duplicate buttons that already exist in AppMainTabSwitcherBar)
    item {
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
            contentDescription = "Asser TV",
            tint = CinemaPrimary,
            modifier = Modifier.size(22.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Asser TV • بوابة الأفلام والمسلسلات",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            onClick = { showSourcesDialog = true },
            color = CinemaSurface,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
            modifier = Modifier.testTag("btn_dashboard_sources")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                Icons.Default.Settings,
                contentDescription = "إدارة المصادر",
                tint = CinemaAccent,
                modifier = Modifier.size(15.dp)
              )
              Text(
                text = "المصادر",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
              )
            }
          }

          IconButton(
            onClick = onOpenUpdate,
            modifier = Modifier
              .size(34.dp)
              .clip(CircleShape)
              .background(CinemaSurface)
              .border(1.dp, CinemaSuccess.copy(alpha = 0.6f), CircleShape)
              .testTag("btn_dashboard_update")
          ) {
            Icon(
              Icons.Default.SystemUpdate,
              contentDescription = "تحديث التطبيق",
              tint = CinemaSuccess,
              modifier = Modifier.size(17.dp)
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface.copy(alpha = 0.85f)),
        border = androidx.compose.foundation.BorderStroke(
          width = 1.dp,
          brush = Brush.linearGradient(
            listOf(
              Color.White.copy(alpha = 0.12f),
              CinemaBorder.copy(alpha = 0.4f)
            )
          )
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(
            text = "🔍 ابحث في جميع بوابات المشاهدة معاً:",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
                  text = "اكتب اسم الفيلم أو المسلسل (مثل Silo)...",
                  color = Color.Gray,
                  fontSize = 12.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
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
                unfocusedIndicatorColor = CinemaBorder.copy(alpha = 0.5f)
              ),
              shape = RoundedCornerShape(12.dp),
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
              shape = RoundedCornerShape(12.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
              modifier = Modifier.testTag("btn_dual_search_action")
            ) {
              Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "بحث",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false
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
      // Results rendered dynamically for all enabled portals!
      StreamingPortal.values().filter { it.isEnabled }.forEach { portal ->
        val results = portalResultsMap[portal] ?: emptyList()
        item {
          PortalResultsSection(
            portal = portal,
            query = searchQuery,
            results = results,
            onItemClick = { item ->
              onLaunchUrl(item.url, portal, item.title)
            },
            spellingSuggestion = spellingSuggestion,
            onSearchSuggestion = { corrected ->
              executeDualSearch(corrected)
            },
            onLaunchManualUrl = { manualUrl ->
              onLaunchUrl(manualUrl, portal, "${portal.displayNameArabic} (تصفح يدوي)")
            }
          )
        }
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


  }

  if (showSourcesDialog) {
    SourcesManagementDialog(
      repository = repository,
      onDismiss = { showSourcesDialog = false }
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
  
  // Fields for editing a portal
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
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = CinemaSurface),
      border = androidx.compose.foundation.BorderStroke(1.5.dp, CinemaBorder),
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
      ) {
        if (selectedPortalForEdit == null) {
          // Main Portal List View
          Text(
            text = "⚙️ إدارة وبوابات مصادر الفيديو:",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "يمكنك تعديل روابط المواقع الافتراضية، أو تفعيل وتخصيص المصادر الإضافية 4 و 5 بأي اسم وموقع من دماغك!",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
          )
          Spacer(modifier = Modifier.height(14.dp))

          Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            StreamingPortal.values().forEach { portal ->
               val isCustom = portal == StreamingPortal.PORTAL_5
              Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = CinemaDarkBackground),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (portal.isEnabled) CinemaBorder else Color.DarkGray),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Box(
                        modifier = Modifier
                          .size(8.dp)
                          .clip(CircleShape)
                          .background(if (portal.isEnabled) CinemaAccent else Color.Gray)
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = portal.displayNameArabic,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = "(${portal.displayNameEnglish})",
                        color = Color.Gray,
                        fontSize = 11.sp
                      )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = if (portal.homeUrl.isNotBlank()) portal.homeUrl else "غير مهيأ بعد (مغلق)",
                      color = if (portal.isEnabled) CinemaAccent else Color.Gray,
                      fontSize = 11.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }

                  Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCustom) {
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
                    }

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
                      shape = RoundedCornerShape(6.dp),
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                      modifier = Modifier.height(28.dp)
                    ) {
                      Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("تعديل", fontSize = 10.sp, color = Color.White)
                    }
                  }
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))
          Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = CinemaSecondary),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(42.dp)
          ) {
            Text("إغلاق", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
          }
        } else {
          // Edit Portal Form View
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

          // Name AR (Custom portals only)
          if (isCustom) {
            Text("اسم الموقع بالعربية:", color = Color.LightGray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
              value = editNameAr,
              onValueChange = { editNameAr = it },
              singleLine = true,
              colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = CinemaAccent),
              modifier = Modifier.fillMaxWidth(),
              placeholder = { Text("مثال: أكوام", fontSize = 11.sp, color = Color.Gray) }
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text("اسم الموقع بالإنجليزية (English Name):", color = Color.LightGray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
              value = editNameEn,
              onValueChange = { editNameEn = it },
              singleLine = true,
              colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = CinemaAccent),
              modifier = Modifier.fillMaxWidth(),
              placeholder = { Text("Example: Akoam", fontSize = 11.sp, color = Color.Gray) }
            )
            Spacer(modifier = Modifier.height(10.dp))
          }

          // Home URL
          Text("الرابط الرئيسي (Home URL):", color = Color.LightGray, fontSize = 11.sp)
          Spacer(modifier = Modifier.height(4.dp))
          OutlinedTextField(
            value = editHomeUrl,
            onValueChange = { editHomeUrl = it },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = CinemaAccent),
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://example.com/", fontSize = 11.sp, color = Color.Gray) }
          )
          Spacer(modifier = Modifier.height(10.dp))

          // Search URL
          Text("رابط البحث (Search URL):", color = Color.LightGray, fontSize = 11.sp)
          Spacer(modifier = Modifier.height(4.dp))
          OutlinedTextField(
            value = editSearchUrl,
            onValueChange = { editSearchUrl = it },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = CinemaAccent),
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ضع %s مكان الكلمة المبحوثة، مثال: https://example.com/search/%s", fontSize = 11.sp, color = Color.Gray) }
          )
          Spacer(modifier = Modifier.height(14.dp))

          // Enable checkbox for Custom portals in form
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

          // Action Buttons
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = { selectedPortalForEdit = null },
              colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.weight(1f).height(40.dp)
            ) {
              Text("إلغاء", fontSize = 12.sp, color = Color.White)
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
              modifier = Modifier.weight(1f).height(40.dp)
            ) {
              Text("حفظ 💾", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
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
    StreamingPortal.EGY_DEAD -> Color(0xFF4CAF50) // Beautiful green for EgyDead
    StreamingPortal.FLIXER -> Color(0xFF9C27B0) // Beautiful Purple for Flixer
    else -> CinemaSurfaceVariant
  }

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
          text = "🎬 نتائج ${portal.displayNameArabic} (${portal.displayNameEnglish})",
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "😞 لا توجد نتائج مباشرة لموقع ${portal.displayNameArabic} لـ \"$query\"",
            color = Color.LightGray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(6.dp))

          if (spellingSuggestion != null && onSearchSuggestion != null) {
            Text(
              text = "هل تقصد البحث عن الاسم الصحيح التالي؟",
              color = Color.Gray,
              fontSize = 11.sp,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
              onClick = { onSearchSuggestion(spellingSuggestion) },
              colors = ButtonDefaults.buttonColors(containerColor = CinemaAccent, contentColor = Color.Black),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.height(32.dp)
            ) {
              Text(text = "👉 $spellingSuggestion", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
          }

          if (onLaunchManualUrl != null) {
            Text(
              text = "أو يمكنك محاولة التصفح والبحث اليدوي في الموقع مباشرة:",
              color = Color.Gray,
              fontSize = 11.sp,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
              onClick = { onLaunchManualUrl(portal.buildSearchUrl(query)) },
              colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.height(32.dp)
            ) {
              Text("البحث اليدوي المباشر في الموقع 🔍", fontSize = 11.sp, color = Color.White)
            }
          }
        }
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
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = CinemaSurface.copy(alpha = 0.85f)),
    border = androidx.compose.foundation.BorderStroke(
      width = if (isFocused) 2.dp else 1.dp,
      brush = if (isFocused) {
        Brush.linearGradient(listOf(CinemaFocusGlow, CinemaSecondary))
      } else {
        Brush.linearGradient(
          listOf(
            Color.White.copy(alpha = 0.08f),
            CinemaBorder.copy(alpha = 0.3f)
          )
        )
      }
    ),
    modifier = Modifier
      .width(170.dp)
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
          .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
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
  val portalColor = when (entry.portal) {
    StreamingPortal.TOP_CINEMA -> CinemaPrimary
    StreamingPortal.WATCH_SERIES -> CinemaSecondary
    StreamingPortal.LAROZA -> CinemaAccent
    StreamingPortal.EGY_DEAD -> Color(0xFF4CAF50)
    StreamingPortal.FLIXER -> Color(0xFF9C27B0)
    else -> CinemaSecondary
  }
  val formattedDate = remember(entry.timestamp) {
    try {
      val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
      sdf.format(Date(entry.timestamp))
    } catch (_: Exception) {
      ""
    }
  }

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = CinemaSurface.copy(alpha = 0.8f)),
    border = androidx.compose.foundation.BorderStroke(
      width = 1.dp,
      brush = Brush.linearGradient(
        listOf(
          Color.White.copy(alpha = 0.12f),
          CinemaBorder.copy(alpha = 0.4f)
        )
      )
    ),
    modifier = Modifier
      .width(205.dp)
      .clickable { onResume() }
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
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
          modifier = Modifier.size(32.dp)
        ) {
          Box(
            modifier = Modifier
              .size(24.dp)
              .background(Color(0xFFD32F2F).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
              .border(1.dp, Color(0xFFD32F2F).copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = "حذف العنصر",
              tint = Color(0xFFFF5252),
              modifier = Modifier.size(13.dp)
            )
          }
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
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(30.dp)
      ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("متابعة المشاهدة", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
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
  var minDistance = 3 // Threshold: edit distance must be < 3
  
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
