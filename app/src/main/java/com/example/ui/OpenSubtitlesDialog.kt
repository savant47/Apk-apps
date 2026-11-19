package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SearchAndSubtitlesService
import com.example.model.SubtitleItem
import com.example.model.SubtitleSourceFilter
import com.example.model.SubtitleStyleConfig
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OpenSubtitlesDialog(
  initialQuery: String,
  onDismiss: () -> Unit,
  onApplySubtitle: (SubtitleItem) -> Unit,
  service: SearchAndSubtitlesService,
  embedUrlHint: String = "",
  refererUrlHint: String = "",
  subtitleStyle: SubtitleStyleConfig = SubtitleStyleConfig(),
  onUpdateSubtitleStyle: (SubtitleStyleConfig) -> Unit = {},
  activeSubtitle: SubtitleItem? = null,
  onClearSubtitle: () -> Unit = {}
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val keyboardController = LocalSoftwareKeyboardController.current

  var searchQuery by remember { mutableStateOf(initialQuery) }
  var selectedLang by remember { mutableStateOf("ar") }
  var selectedSource by remember { mutableStateOf(SubtitleSourceFilter.ALL) }
  var subtitles by remember { mutableStateOf<List<SubtitleItem>>(emptyList()) }
  var isLoading by remember { mutableStateOf(false) }
  var hasSearchedOnce by remember { mutableStateOf(false) }
  var downloadingSubId by remember { mutableStateOf<String?>(null) }
  // 0 = Search & Select Subtitles, 1 = Subtitle Appearance Settings (Color / Background / Size / Sync)
  var selectedDialogTab by remember { mutableIntStateOf(0) }

  var activeSearchJob by remember { mutableStateOf<Job?>(null) }
  var activeDownloadJob by remember { mutableStateOf<Job?>(null) }

  val searchInputFocusRequester = remember { FocusRequester() }
  val searchBtnFocusRequester = remember { FocusRequester() }
  val firstResultFocusRequester = remember { FocusRequester() }

  val languages = remember {
    listOf(
      "ar" to "العربية",
      "en" to "English",
      "tr" to "Türkçe",
      "fr" to "Français"
    )
  }

  fun cancelPendingJobs() {
    activeSearchJob?.cancel()
    activeSearchJob = null
    activeDownloadJob?.cancel()
    activeDownloadJob = null
  }

  fun triggerSearch() {
    val q = searchQuery.trim()
    if (q.isBlank()) return
    keyboardController?.hide()
    activeSearchJob?.cancel()
    activeSearchJob = scope.launch {
      isLoading = true
      hasSearchedOnce = true
      try {
        subtitles = service.searchAllSubtitles(
          query = q,
          languageCode = selectedLang,
          sourceFilter = selectedSource
        )
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Exception) {
        subtitles = emptyList()
      } finally {
        isLoading = false
      }
    }
  }

  fun downloadAndApplySubtitle(sub: SubtitleItem) {
    if (downloadingSubId != null) return
    activeDownloadJob?.cancel()
    activeDownloadJob = scope.launch {
      downloadingSubId = sub.id
      try {
        val resolved = service.resolveAndParseSubtitleItem(context, sub)
        downloadingSubId = null
        if (resolved.parsedCues.isEmpty() && resolved.localFileUri.isBlank()) {
          Toast.makeText(context, "تعذر تحميل ملف الترجمة، جرّب نتيجة أخرى", Toast.LENGTH_SHORT).show()
        } else {
          onApplySubtitle(resolved)
        }
      } catch (ce: CancellationException) {
        downloadingSubId = null
        throw ce
      } catch (_: Exception) {
        downloadingSubId = null
        Toast.makeText(context, "حدث خطأ أثناء تحميل الترجمة", Toast.LENGTH_SHORT).show()
      }
    }
  }

  // Cancel any in-flight subtitle network jobs immediately if dialog closes
  DisposableEffect(Unit) {
    onDispose {
      cancelPendingJobs()
    }
  }

  // Auto-focus Search button on open so TV D-Pad users have immediate visual focus without popping keyboard
  LaunchedEffect(Unit) {
    delay(80L)
    try {
      searchBtnFocusRequester.requestFocus()
    } catch (_: Exception) {
    }
  }

  // Non-blocking search on open and when language/source filter changes (cancels stale requests cleanly)
  LaunchedEffect(selectedLang, selectedSource) {
    if (searchQuery.isNotBlank()) {
      triggerSearch()
    }
  }

  Dialog(
    onDismissRequest = {
      cancelPendingJobs()
      onDismiss()
    },
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      Surface(
        modifier = Modifier
          .fillMaxWidth(0.95f)
          .fillMaxHeight(0.92f),
        shape = RoundedCornerShape(18.dp),
        color = CinemaSurface,
        border = BorderStroke(1.5.dp, CinemaBorder)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
        ) {
          // Header
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
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(CinemaSecondary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Subtitles,
                  contentDescription = null,
                  tint = CinemaAccent,
                  modifier = Modifier.size(22.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "الترجمة الفورية وإعدادات المظهر (CC)",
                  color = Color.White,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.ExtraBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = if (activeSubtitle != null) {
                    "مفعلة الآن: ${activeSubtitle.releaseName} (${activeSubtitle.parsedCues.size} سطر)"
                  } else {
                    "الفيديو متوقف مؤقتاً لتسريع البحث • تنقل بأسهم الريموت واضغط OK"
                  },
                  color = if (activeSubtitle != null) CinemaSuccess else CinemaAccent,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }

            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (activeSubtitle != null) {
                val clearInteraction = remember { MutableInteractionSource() }
                val isClearFocused by clearInteraction.collectIsFocusedAsState()
                Surface(
                  onClick = {
                    cancelPendingJobs()
                    onClearSubtitle()
                    Toast.makeText(context, "تم إيقاف الترجمة الخارجية", Toast.LENGTH_SHORT).show()
                  },
                  interactionSource = clearInteraction,
                  color = if (isClearFocused) CinemaPrimary else CinemaPrimary.copy(alpha = 0.2f),
                  shape = RoundedCornerShape(10.dp),
                  border = BorderStroke(
                    width = if (isClearFocused) 2.5.dp else 1.dp,
                    color = if (isClearFocused) CinemaFocusGlow else CinemaPrimary
                  ),
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .scale(if (isClearFocused) 1.05f else 1f)
                    .testTag("btn_clear_active_subtitle")
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = Icons.Default.ClosedCaptionDisabled,
                      contentDescription = "إيقاف الترجمة",
                      tint = if (isClearFocused) Color.White else CinemaPrimary,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                      text = "إيقاف",
                      color = Color.White,
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.ExtraBold
                    )
                  }
                }
              }

              val closeInteraction = remember { MutableInteractionSource() }
              val isCloseFocused by closeInteraction.collectIsFocusedAsState()
              Surface(
                onClick = {
                  cancelPendingJobs()
                  onDismiss()
                },
                interactionSource = closeInteraction,
                shape = CircleShape,
                color = if (isCloseFocused) CinemaPrimary else CinemaSurfaceVariant,
                border = BorderStroke(
                  width = if (isCloseFocused) 2.5.dp else 1.dp,
                  color = if (isCloseFocused) CinemaFocusGlow else CinemaBorder
                ),
                modifier = Modifier
                  .size(48.dp)
                  .scale(if (isCloseFocused) 1.08f else 1f)
                  .testTag("close_subtitles_dialog")
              ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "إغلاق",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Top Mode Switcher: [1. البحث وتحميل الترجمة] | [2. إعدادات مظهر الترجمة]
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            val tab0Interaction = remember { MutableInteractionSource() }
            val isTab0Focused by tab0Interaction.collectIsFocusedAsState()
            Surface(
              onClick = { selectedDialogTab = 0 },
              interactionSource = tab0Interaction,
              color = if (selectedDialogTab == 0) ClipboxWhite else CinemaDarkBackground,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(
                width = if (isTab0Focused) 2.5.dp else 1.dp,
                color = when {
                  isTab0Focused -> CinemaFocusGlow
                  selectedDialogTab == 0 -> ClipboxWhite
                  else -> CinemaBorder
                }
              ),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .scale(if (isTab0Focused) 1.02f else 1f)
                .testTag("tab_subtitle_search")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.ClosedCaption,
                  contentDescription = null,
                  tint = if (selectedDialogTab == 0) Color.Black else CinemaAccent,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "بحث وتحميل الترجمة",
                  color = if (selectedDialogTab == 0) Color.Black else Color.White,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.ExtraBold,
                  maxLines = 1,
                  softWrap = false,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }

            val tab1Interaction = remember { MutableInteractionSource() }
            val isTab1Focused by tab1Interaction.collectIsFocusedAsState()
            Surface(
              onClick = { selectedDialogTab = 1 },
              interactionSource = tab1Interaction,
              color = if (selectedDialogTab == 1) ClipboxWhite else CinemaDarkBackground,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(
                width = if (isTab1Focused) 2.5.dp else 1.dp,
                color = when {
                  isTab1Focused -> CinemaFocusGlow
                  selectedDialogTab == 1 -> ClipboxWhite
                  else -> CinemaAccent.copy(alpha = 0.7f)
                }
              ),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .scale(if (isTab1Focused) 1.02f else 1f)
                .testTag("tab_subtitle_style_settings")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Palette,
                  contentDescription = null,
                  tint = if (selectedDialogTab == 1) Color.Black else CinemaAccent,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "إعدادات اللون والخلفية والحجم",
                  color = if (selectedDialogTab == 1) Color.Black else Color.White,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.ExtraBold,
                  maxLines = 1,
                  softWrap = false,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          if (selectedDialogTab == 1) {
            // =========================================================================
            // TAB 2: SUBTITLE APPEARANCE SETTINGS (COLOR, BACKGROUND, FONT SIZE, SYNC)
            // =========================================================================
            SubtitleAppearanceSettingsContent(
              style = subtitleStyle,
              onStyleChange = onUpdateSubtitleStyle,
              modifier = Modifier.weight(1f)
            )
          } else {
            // =========================================================================
            // TAB 1: SEARCH & DOWNLOAD REAL SUBTITLES (FULL TV D-PAD SUPPORT)
            // =========================================================================
            // Source Filter Tabs (Custom TV-focusable pills with BringIntoViewRequester)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              SubtitleSourceFilter.values().forEach { filter ->
                val isSelected = selectedSource == filter
                TvSubtitleSelectablePill(
                  label = filter.labelAr,
                  isSelected = isSelected,
                  selectedContainerColor = CinemaPrimary.copy(alpha = 0.28f),
                  selectedContentColor = CinemaAccent,
                  selectedBorderColor = CinemaPrimary,
                  onClick = { selectedSource = filter },
                  modifier = Modifier.testTag("sub_source_${filter.name}")
                )
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Input & Search Button (D-Pad Center/Enter on field opens IME cleanly)
            var isInputFocused by remember { mutableStateOf(false) }
            val searchBtnInteraction = remember { MutableInteractionSource() }
            val isSearchBtnFocused by searchBtnInteraction.collectIsFocusedAsState()

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
                    "اسم الفيلم أو المسلسل بالإنجليزية (مثال: VisionQuest S01E08)...",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                },
                singleLine = true,
                leadingIcon = {
                  Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = if (isInputFocused) CinemaFocusGlow else CinemaAccent
                  )
                },
                colors = OutlinedTextFieldDefaults.colors(
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White,
                  focusedContainerColor = CinemaDarkBackground,
                  unfocusedContainerColor = CinemaDarkBackground,
                  focusedBorderColor = CinemaFocusGlow,
                  unfocusedBorderColor = CinemaBorder
                ),
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                  onSearch = {
                    keyboardController?.hide()
                    triggerSearch()
                  }
                ),
                modifier = Modifier
                  .weight(1f)
                  .focusRequester(searchInputFocusRequester)
                  .onFocusChanged { isInputFocused = it.isFocused }
                  .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyUp &&
                      (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                    ) {
                      keyboardController?.show()
                      false
                    } else {
                      false
                    }
                  }
                  .testTag("subtitles_search_input")
              )

              Surface(
                onClick = { triggerSearch() },
                interactionSource = searchBtnInteraction,
                color = if (isSearchBtnFocused) CinemaAccent else CinemaPrimary,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                  width = if (isSearchBtnFocused) 2.5.dp else 1.dp,
                  color = if (isSearchBtnFocused) Color.White else CinemaPrimary
                ),
                modifier = Modifier
                  .heightIn(min = 52.dp)
                  .focusRequester(searchBtnFocusRequester)
                  .scale(if (isSearchBtnFocused) 1.05f else 1f)
                  .testTag("subtitles_search_btn")
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "بحث عن الترجمة",
                    tint = if (isSearchBtnFocused) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                  )
                  Text(
                    text = "بحث",
                    color = if (isSearchBtnFocused) Color.Black else Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Language Chips (TV-focusable pills with visible D-Pad ring)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "اللغة:",
                color = Color.LightGray,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
              )
              languages.forEach { (code, label) ->
                val isSelected = selectedLang == code
                TvSubtitleSelectablePill(
                  label = label,
                  isSelected = isSelected,
                  selectedContainerColor = CinemaSecondary,
                  selectedContentColor = Color.White,
                  selectedBorderColor = CinemaAccent,
                  onClick = { selectedLang = code },
                  modifier = Modifier.testTag("sub_lang_$code")
                )
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Results List
            if (isLoading) {
              Box(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxWidth(),
                contentAlignment = Alignment.Center
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  CircularProgressIndicator(color = CinemaAccent)
                  Spacer(modifier = Modifier.height(10.dp))
                  Text(
                    text = "جاري البحث السريع عن ملفات الترجمة (.SRT / .VTT)...",
                    color = Color.LightGray,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                  )
                }
              }
            } else if (subtitles.isEmpty()) {
              Box(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxWidth(),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = if (hasSearchedOnce) {
                    "لم يتم العثور على ترجمة مطابقة حالياً — عدّل الاسم بالإنجليزية واضغط بحث"
                  } else {
                    "اكتب اسم الفيلم أو المسلسل باللغة الإنجليزية واضغط بحث لعرض ملفات الترجمة"
                  },
                  color = Color.Gray,
                  fontSize = 13.sp,
                  textAlign = TextAlign.Center
                )
              }
            } else {
              LazyColumn(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                itemsIndexed(subtitles, key = { _, item -> item.id }) { index, sub ->
                  val isAddic7ed = sub.sourceSite.contains("Addic7ed", ignoreCase = true)
                  val isEmbedded = sub.sourceSite.contains("مدمج", ignoreCase = true)
                  val isDownloadingThis = downloadingSubId == sub.id
                  val isCurrentlyActive = activeSubtitle?.id == sub.id
                  val itemInteraction = remember { MutableInteractionSource() }
                  val isItemFocused by itemInteraction.collectIsFocusedAsState()
                  val itemBringIntoView = remember { BringIntoViewRequester() }

                  LaunchedEffect(isItemFocused) {
                    if (isItemFocused) {
                      itemBringIntoView.bringIntoView()
                    }
                  }

                  // Single focusable Surface per row (eliminates nested Button focus traps for TV D-Pad)
                  Surface(
                    onClick = { downloadAndApplySubtitle(sub) },
                    enabled = downloadingSubId == null,
                    interactionSource = itemInteraction,
                    modifier = Modifier
                      .fillMaxWidth()
                      .bringIntoViewRequester(itemBringIntoView)
                      .then(
                        if (index == 0) Modifier.focusRequester(firstResultFocusRequester)
                        else Modifier
                      )
                      .testTag("subtitle_item_${sub.id}"),
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                      isItemFocused -> CinemaPrimary.copy(alpha = 0.34f)
                      isCurrentlyActive -> CinemaPrimary.copy(alpha = 0.22f)
                      else -> CinemaSurfaceVariant
                    },
                    border = BorderStroke(
                      width = if (isItemFocused) 2.5.dp else 1.dp,
                      color = when {
                        isItemFocused -> CinemaFocusGlow
                        isCurrentlyActive -> CinemaSuccess
                        isEmbedded -> CinemaSuccess.copy(alpha = 0.6f)
                        isAddic7ed -> CinemaAccent.copy(alpha = 0.5f)
                        else -> CinemaBorder
                      }
                    )
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = sub.releaseName,
                          color = Color.White,
                          fontSize = 13.sp,
                          fontWeight = FontWeight.Bold,
                          maxLines = 2,
                          overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                          Surface(
                            color = when {
                              isEmbedded -> CinemaSuccess.copy(alpha = 0.2f)
                              isAddic7ed -> CinemaAccent.copy(alpha = 0.18f)
                              else -> CinemaSecondary.copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(6.dp)
                          ) {
                            Text(
                              text = sub.sourceSite,
                              color = when {
                                isEmbedded -> CinemaSuccess
                                isAddic7ed -> CinemaAccent
                                else -> Color(0xFF90CAF9)
                              },
                              fontSize = 10.sp,
                              fontWeight = FontWeight.Bold,
                              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                          }
                          Text(
                            text = "${sub.languageName} • ${sub.format.uppercase()}",
                            color = CinemaSuccess,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                          )
                          Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                              imageVector = Icons.Default.Star,
                              contentDescription = null,
                              tint = CinemaAccent,
                              modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                              text = "${sub.rating}",
                              color = Color.LightGray,
                              fontSize = 10.5.sp
                            )
                          }
                        }
                      }

                      Spacer(modifier = Modifier.width(10.dp))

                      // Visual status badge (row itself handles click/OK cleanly)
                      Surface(
                        color = when {
                          isDownloadingThis -> CinemaAccent
                          isCurrentlyActive -> CinemaSuccess
                          isItemFocused -> ClipboxWhite
                          else -> CinemaSecondary
                        },
                        shape = RoundedCornerShape(8.dp)
                      ) {
                        Row(
                          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          if (isDownloadingThis) {
                            CircularProgressIndicator(
                              color = Color.Black,
                              strokeWidth = 2.dp,
                              modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                              text = "جاري السحب...",
                              color = Color.Black,
                              fontSize = 11.sp,
                              fontWeight = FontWeight.ExtraBold
                            )
                          } else {
                            Icon(
                              imageVector = if (isCurrentlyActive) Icons.Default.Check else Icons.Default.Download,
                              contentDescription = null,
                              tint = if (isItemFocused && !isCurrentlyActive) Color.Black else Color.White,
                              modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                              text = if (isCurrentlyActive) "مفعلة" else "تفعيل",
                              color = if (isItemFocused && !isCurrentlyActive) Color.Black else Color.White,
                              fontSize = 11.5.sp,
                              fontWeight = FontWeight.ExtraBold
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
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvSubtitleSelectablePill(
  label: String,
  isSelected: Boolean,
  selectedContainerColor: Color,
  selectedContentColor: Color,
  selectedBorderColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val interaction = remember { MutableInteractionSource() }
  val isFocused by interaction.collectIsFocusedAsState()
  val bringIntoViewRequester = remember { BringIntoViewRequester() }

  LaunchedEffect(isFocused) {
    if (isFocused) {
      bringIntoViewRequester.bringIntoView()
    }
  }

  Surface(
    onClick = onClick,
    interactionSource = interaction,
    color = when {
      isFocused && isSelected -> selectedContainerColor
      isFocused -> CinemaSurfaceVariant
      isSelected -> selectedContainerColor
      else -> CinemaDarkBackground
    },
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(
      width = if (isFocused) 2.5.dp else 1.dp,
      color = when {
        isFocused -> CinemaFocusGlow
        isSelected -> selectedBorderColor
        else -> CinemaBorder
      }
    ),
    modifier = modifier
      .heightIn(min = 44.dp)
      .bringIntoViewRequester(bringIntoViewRequester)
      .scale(if (isFocused) 1.04f else 1f)
  ) {
    Box(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = label,
        color = when {
          isFocused -> Color.White
          isSelected -> selectedContentColor
          else -> Color.LightGray
        },
        fontSize = 11.5.sp,
        fontWeight = if (isSelected || isFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubtitleAppearanceSettingsContent(
  style: SubtitleStyleConfig,
  onStyleChange: (SubtitleStyleConfig) -> Unit,
  modifier: Modifier = Modifier
) {
  val textColors = remember {
    listOf(
      0xFFFFD700L to "أصفر سينمائي",
      0xFFFFFFFFL to "أبيض ناصع",
      0xFF00E5FFL to "سماوي واضح",
      0xFF4ADE80L to "أخضر مريح",
      0xFFFB923CL to "برتقالي دافئ"
    )
  }

  val backgroundColors = remember {
    listOf(
      0xBF000000L to "أسود 75% (افتراضي)",
      0xF2000000L to "أسود داكن 95%",
      0x73000000L to "أسود خفيف 45%",
      0xCC0B1528L to "كحلي سينمائي",
      0x00000000L to "شفاف بدون خلفية"
    )
  }

  val fontSizes = remember {
    listOf(
      20f to "20sp",
      24f to "24sp",
      28f to "28sp",
      32f to "32sp",
      36f to "36sp",
      42f to "42sp",
      48f to "48sp",
      54f to "54sp",
      60f to "60sp (تلفاز ضخم)"
    )
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // 1. Live Subtitle Preview Card
    Surface(
      color = Color(0xFF101722),
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.6f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 100.dp)
          .padding(14.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "معاينة حية لمظهر الترجمة داخل المشغل (${style.fontSizeSp.toInt()}sp)",
            color = Color.LightGray,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium
          )
          Spacer(modifier = Modifier.height(8.dp))
          Box(
            modifier = Modifier
              .background(
                color = Color(style.backgroundColorArgb),
                shape = RoundedCornerShape(8.dp)
              )
              .padding(horizontal = 16.dp, vertical = 8.dp)
          ) {
            Text(
              text = "ترجمة Asser TV الاحترافية • وضوح كامل لشاشات التلفاز",
              color = Color(style.textColorArgb),
              fontSize = style.fontSizeSp.coerceIn(14f, 60f).sp,
              lineHeight = (style.fontSizeSp.coerceIn(14f, 60f) * 1.32f).sp,
              fontWeight = FontWeight.ExtraBold,
              textAlign = TextAlign.Center,
              style = TextStyle(
                shadow = Shadow(
                  color = Color.Black,
                  offset = Offset(2f, 2f),
                  blurRadius = 8f
                )
              )
            )
          }
        }
      }
    }

    // 2. Font Color Selection (لون خط الترجمة)
    Text(
      text = "لون خط الترجمة (Text Color):",
      color = CinemaAccent,
      fontSize = 12.sp,
      fontWeight = FontWeight.ExtraBold
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      textColors.forEach { (argb, label) ->
        val isSelected = style.textColorArgb == argb
        val interaction = remember { MutableInteractionSource() }
        val isFocused by interaction.collectIsFocusedAsState()
        val bringIntoView = remember { BringIntoViewRequester() }
        LaunchedEffect(isFocused) {
          if (isFocused) bringIntoView.bringIntoView()
        }
        Surface(
          onClick = { onStyleChange(style.copy(textColorArgb = argb)) },
          interactionSource = interaction,
          color = if (isSelected) CinemaPrimary.copy(alpha = 0.25f) else CinemaDarkBackground,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(
            width = if (isFocused) 2.5.dp else if (isSelected) 1.5.dp else 1.dp,
            color = when {
              isFocused -> CinemaFocusGlow
              isSelected -> Color(argb)
              else -> CinemaBorder
            }
          ),
          modifier = Modifier
            .heightIn(min = 44.dp)
            .bringIntoViewRequester(bringIntoView)
            .scale(if (isFocused) 1.04f else 1f)
            .testTag("sub_text_color_$argb")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Box(
              modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            )
            Text(
              text = label,
              color = if (isSelected) Color(argb) else Color.White,
              fontSize = 11.5.sp,
              fontWeight = if (isSelected || isFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false
            )
          }
        }
      }
    }

    // 3. Background Color & Opacity Selection (لون وشفافية الخلفية)
    Text(
      text = "لون وشفافية خلفية الترجمة (Background):",
      color = CinemaAccent,
      fontSize = 12.sp,
      fontWeight = FontWeight.ExtraBold
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      backgroundColors.forEach { (argb, label) ->
        val isSelected = style.backgroundColorArgb == argb
        val interaction = remember { MutableInteractionSource() }
        val isFocused by interaction.collectIsFocusedAsState()
        val bringIntoView = remember { BringIntoViewRequester() }
        LaunchedEffect(isFocused) {
          if (isFocused) bringIntoView.bringIntoView()
        }
        Surface(
          onClick = { onStyleChange(style.copy(backgroundColorArgb = argb)) },
          interactionSource = interaction,
          color = if (isSelected) ClipboxWhite else CinemaDarkBackground,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = when {
              isFocused -> CinemaFocusGlow
              isSelected -> ClipboxWhite
              else -> CinemaBorder
            }
          ),
          modifier = Modifier
            .heightIn(min = 44.dp)
            .bringIntoViewRequester(bringIntoView)
            .scale(if (isFocused) 1.04f else 1f)
            .testTag("sub_bg_color_$argb")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
          ) {
            if (isSelected) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(14.dp)
              )
            }
            Text(
              text = label,
              color = if (isSelected) Color.Black else Color.White,
              fontSize = 11.5.sp,
              fontWeight = if (isSelected || isFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false
            )
          }
        }
      }
    }

    // 4. Font Size Selection (حجم خط الترجمة - حتى 60sp للشاشات الكبيرة)
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Icon(
          imageVector = Icons.Default.FormatSize,
          contentDescription = null,
          tint = CinemaAccent,
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = "حجم خط الترجمة (16sp حتى 60sp للتلفاز):",
          color = CinemaAccent,
          fontSize = 12.sp,
          fontWeight = FontWeight.ExtraBold
        )
      }
      Surface(
        color = CinemaPrimary,
        shape = RoundedCornerShape(8.dp)
      ) {
        Text(
          text = "${style.fontSizeSp.toInt()}sp",
          color = Color.White,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.ExtraBold,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
      }
    }

    // TV-friendly -2sp / Slider / +2sp row so D-Pad users can adjust smoothly without dragging
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      val decInteraction = remember { MutableInteractionSource() }
      val isDecFocused by decInteraction.collectIsFocusedAsState()
      Surface(
        onClick = {
          onStyleChange(style.copy(fontSizeSp = (style.fontSizeSp - 2f).coerceIn(16f, 60f)))
        },
        interactionSource = decInteraction,
        color = if (isDecFocused) CinemaPrimary else CinemaDarkBackground,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
          width = if (isDecFocused) 2.5.dp else 1.dp,
          color = if (isDecFocused) CinemaFocusGlow else CinemaBorder
        ),
        modifier = Modifier
          .size(48.dp)
          .testTag("sub_font_size_dec")
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
          Icon(
            imageVector = Icons.Default.Remove,
            contentDescription = "تصغير حجم الخط",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      Slider(
        value = style.fontSizeSp.coerceIn(16f, 60f),
        onValueChange = { raw ->
          val stepped = (Math.round(raw / 2f) * 2f).coerceIn(16f, 60f)
          onStyleChange(style.copy(fontSizeSp = stepped))
        },
        valueRange = 16f..60f,
        steps = 21,
        colors = SliderDefaults.colors(
          thumbColor = CinemaAccent,
          activeTrackColor = CinemaPrimary,
          inactiveTrackColor = CinemaBorder
        ),
        modifier = Modifier
          .weight(1f)
          .testTag("sub_font_size_slider")
      )

      val incInteraction = remember { MutableInteractionSource() }
      val isIncFocused by incInteraction.collectIsFocusedAsState()
      Surface(
        onClick = {
          onStyleChange(style.copy(fontSizeSp = (style.fontSizeSp + 2f).coerceIn(16f, 60f)))
        },
        interactionSource = incInteraction,
        color = if (isIncFocused) CinemaPrimary else CinemaDarkBackground,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
          width = if (isIncFocused) 2.5.dp else 1.dp,
          color = if (isIncFocused) CinemaFocusGlow else CinemaBorder
        ),
        modifier = Modifier
          .size(48.dp)
          .testTag("sub_font_size_inc")
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "تكبير حجم الخط",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      fontSizes.forEach { (sizeSp, label) ->
        val isSelected = style.fontSizeSp.toInt() == sizeSp.toInt()
        TvSubtitleSelectablePill(
          label = label,
          isSelected = isSelected,
          selectedContainerColor = CinemaPrimary,
          selectedContentColor = Color.White,
          selectedBorderColor = ClipboxWhite,
          onClick = { onStyleChange(style.copy(fontSizeSp = sizeSp)) },
          modifier = Modifier.testTag("sub_font_size_${sizeSp.toInt()}")
        )
      }
    }

    // 5. Subtitle Sync Timing Offset (تقديم وتأخير وقت الترجمة)
    val syncSec = style.syncOffsetMs / 1000.0
    Text(
      text = "ضبط تزامن وقت الترجمة (الحالي: ${if (syncSec >= 0) "+$syncSec" else "$syncSec"} ثانية):",
      color = CinemaAccent,
      fontSize = 12.sp,
      fontWeight = FontWeight.ExtraBold
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      val syncButtons = remember {
        listOf(
          -2000L to "-2.0 ثانية",
          -500L to "-0.5 ثانية",
          0L to "ضبط افتراضي (0.0s)",
          500L to "+0.5 ثانية",
          2000L to "+2.0 ثانية"
        )
      }
      syncButtons.forEach { (deltaMs, label) ->
        val isReset = deltaMs == 0L
        val isSelected = isReset && style.syncOffsetMs == 0L
        TvSubtitleSelectablePill(
          label = label,
          isSelected = isSelected,
          selectedContainerColor = CinemaSuccess.copy(alpha = 0.25f),
          selectedContentColor = Color.White,
          selectedBorderColor = CinemaSuccess,
          onClick = {
            val nextOffset = if (isReset) 0L else (style.syncOffsetMs + deltaMs)
            onStyleChange(style.copy(syncOffsetMs = nextOffset))
          },
          modifier = Modifier.testTag("sub_sync_${deltaMs}")
        )
      }
    }
  }
}
