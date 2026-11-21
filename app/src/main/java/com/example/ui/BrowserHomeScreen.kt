package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppDisplayMode
import com.example.data.TvPreferencesRepository
import com.example.model.AppMainTab
import com.example.model.ControlMode
import com.example.model.QuickWebSite
import com.example.model.SmartOmniboxResolver
import com.example.model.StreamingPortal
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaFocusGlow
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant

@Composable
fun AppMainTabSwitcherBar(
  selectedTab: AppMainTab,
  onSelectTab: (AppMainTab) -> Unit,
  controlMode: ControlMode,
  displayMode: AppDisplayMode,
  onToggleControlMode: () -> Unit,
  onOpenDisplayMode: () -> Unit,
  onOpenBookmarks: () -> Unit,
  onOpenHistory: () -> Unit,
  onOpenAdBlock: () -> Unit,
  onOpenUpdate: () -> Unit = {},
  onOpenDiagnostics: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val configuration = LocalConfiguration.current
  val isMobileLayout = displayMode == AppDisplayMode.MOBILE || configuration.screenWidthDp < 600

  if (isMobileLayout) {
    Column(
      modifier = modifier
        .fillMaxWidth()
        .background(Color(0xFF070A0F))
        .border(width = 1.dp, color = CinemaSurfaceVariant)
        .padding(horizontal = 10.dp, vertical = 7.dp),
      verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Brand Logo + Full Unclipped Title
        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_assertv_logo),
            contentDescription = "Asser TV Logo",
            modifier = Modifier
              .size(28.dp)
              .clip(RoundedCornerShape(7.dp))
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Asser TV",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
          )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Well-proportioned Mobile Action Bar (Never clips Brand Title!)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Surface(
            onClick = onOpenDisplayMode,
            color = Color(0xFF0284C7).copy(alpha = 0.2f),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
            modifier = Modifier
              .heightIn(min = 34.dp)
              .testTag("top_chip_display_mode")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = if (displayMode == AppDisplayMode.TV) Icons.Default.Tv else Icons.Default.Smartphone,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(15.dp)
              )
              Text(
                text = if (displayMode == AppDisplayMode.TV) "شاشة" else "هاتف",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
              )
            }
          }

          CompactTopIconBtn(
            icon = Icons.Default.SystemUpdate,
            tint = CinemaSuccess,
            contentDescription = "تحديث التطبيق من جيت هب",
            onClick = onOpenUpdate,
            testTag = "top_chip_update"
          )
          CompactTopIconBtn(
            icon = Icons.Default.BugReport,
            tint = CinemaAccent,
            contentDescription = "سجل الأعطال واللوج",
            onClick = onOpenDiagnostics,
            testTag = "top_chip_diagnostics"
          )
          CompactTopIconBtn(
            icon = Icons.Default.Bookmark,
            tint = CinemaAccent,
            contentDescription = "المفضلة",
            onClick = onOpenBookmarks,
            testTag = "top_chip_bookmarks"
          )
          CompactTopIconBtn(
            icon = Icons.Default.History,
            tint = Color.LightGray,
            contentDescription = "السجل",
            onClick = onOpenHistory,
            testTag = "top_chip_history"
          )
          CompactTopIconBtn(
            icon = Icons.Default.Shield,
            tint = CinemaSuccess,
            contentDescription = "حماية AdGuard",
            onClick = onOpenAdBlock,
            testTag = "top_chip_adblock"
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        MainModeTabItem(
          title = "Asser Browser",
          subtitle = "بحث جوجل وتصفح",
          icon = Icons.Default.Public,
          isSelected = selectedTab == AppMainTab.BROWSER,
          activeColor = Color(0xFF38BDF8),
          onClick = { onSelectTab(AppMainTab.BROWSER) },
          testTag = "tab_asser_browser",
          modifier = Modifier.weight(1f)
        )

        MainModeTabItem(
          title = "Asser TV",
          subtitle = "أفلام ومسلسلات",
          icon = Icons.Default.LiveTv,
          isSelected = selectedTab == AppMainTab.ASSER_TV,
          activeColor = CinemaPrimary,
          onClick = { onSelectTab(AppMainTab.ASSER_TV) },
          testTag = "tab_asser_tv",
          modifier = Modifier.weight(1f)
        )
      }
    }
  } else {
    // TV / TABLET WIDESCREEN HEADER
    Row(
      modifier = modifier
        .fillMaxWidth()
        .background(Color(0xFF070A0F))
        .border(width = 1.dp, color = CinemaSurfaceVariant)
        .padding(horizontal = 12.dp, vertical = 7.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Image(
          painter = painterResource(id = R.drawable.ic_assertv_logo),
          contentDescription = "Asser TV Logo",
          modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
        )
        Text(
          text = "Asser TV",
          color = Color.White,
          fontSize = 17.sp,
          fontWeight = FontWeight.ExtraBold,
          maxLines = 1,
          softWrap = false
        )

        MainModeTabItem(
          title = "المتصفح",
          subtitle = "تصفح وجوجل",
          icon = Icons.Default.Public,
          isSelected = selectedTab == AppMainTab.BROWSER,
          activeColor = Color(0xFF38BDF8),
          onClick = { onSelectTab(AppMainTab.BROWSER) },
          testTag = "tab_asser_browser"
        )

        MainModeTabItem(
          title = "Asser TV",
          subtitle = "أفلام ومسلسلات",
          icon = Icons.Default.LiveTv,
          isSelected = selectedTab == AppMainTab.ASSER_TV,
          activeColor = CinemaPrimary,
          onClick = { onSelectTab(AppMainTab.ASSER_TV) },
          testTag = "tab_asser_tv"
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
          .weight(1f, fill = false)
          .horizontalScroll(rememberScrollState())
      ) {
        QuickTopUtilityChip(
          label = if (controlMode == ControlMode.VIRTUAL_MOUSE) "ماوس ✓" else "الماوس",
          icon = Icons.Default.Mouse,
          tint = if (controlMode == ControlMode.VIRTUAL_MOUSE) CinemaAccent else Color.LightGray,
          onClick = onToggleControlMode,
          testTag = "top_chip_mouse"
        )
        QuickTopUtilityChip(
          label = if (displayMode == AppDisplayMode.TV) "شاشة" else "هاتف",
          icon = if (displayMode == AppDisplayMode.TV) Icons.Default.Tv else Icons.Default.Smartphone,
          tint = Color(0xFF38BDF8),
          onClick = onOpenDisplayMode,
          testTag = "top_chip_display_mode"
        )
        QuickTopUtilityChip(
          label = "تحديث",
          icon = Icons.Default.SystemUpdate,
          tint = CinemaSuccess,
          onClick = onOpenUpdate,
          testTag = "top_chip_update"
        )
        QuickTopUtilityChip(
          label = "الأعطال",
          icon = Icons.Default.BugReport,
          tint = CinemaAccent,
          onClick = onOpenDiagnostics,
          testTag = "top_chip_diagnostics"
        )
        QuickTopUtilityChip(
          label = "المفضلة",
          icon = Icons.Default.Bookmark,
          tint = CinemaAccent,
          onClick = onOpenBookmarks,
          testTag = "top_chip_bookmarks"
        )
        QuickTopUtilityChip(
          label = "السجل",
          icon = Icons.Default.History,
          tint = Color.LightGray,
          onClick = onOpenHistory,
          testTag = "top_chip_history"
        )
        QuickTopUtilityChip(
          label = "AdGuard",
          icon = Icons.Default.Shield,
          tint = CinemaSuccess,
          onClick = onOpenAdBlock,
          testTag = "top_chip_adblock"
        )
      }
    }
  }
}

@Composable
private fun CompactTopIconBtn(
  icon: ImageVector,
  tint: Color,
  contentDescription: String,
  onClick: () -> Unit,
  testTag: String
) {
  Surface(
    onClick = onClick,
    color = CinemaSurface,
    shape = RoundedCornerShape(8.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
    modifier = Modifier
      .size(30.dp)
      .testTag(testTag)
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(15.dp)
      )
    }
  }
}

@Composable
private fun MainModeTabItem(
  title: String,
  subtitle: String,
  icon: ImageVector,
  isSelected: Boolean,
  activeColor: Color,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  val bgColor = when {
    isSelected -> activeColor.copy(alpha = 0.22f)
    isFocused -> CinemaSurfaceVariant
    else -> CinemaSurface
  }
  val borderColor = when {
    isFocused -> CinemaFocusGlow
    isSelected -> activeColor
    else -> CinemaBorder
  }

  Box(
    modifier = modifier
      .heightIn(min = 54.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(bgColor)
      .border(
        width = if (isFocused || isSelected) 2.dp else 1.dp,
        color = borderColor,
        shape = RoundedCornerShape(10.dp)
      )
      .clickable(interactionSource = interactionSource, indication = null) { onClick() }
      .focusable(interactionSource = interactionSource)
      .padding(horizontal = 10.dp, vertical = 6.dp)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = if (isSelected) activeColor else if (isFocused) CinemaFocusGlow else Color.LightGray,
        modifier = Modifier.size(20.dp)
      )
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = title,
          color = Color.White,
          fontSize = 15.sp,
          lineHeight = 19.sp,
          fontWeight = FontWeight.ExtraBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = subtitle,
          color = if (isSelected) activeColor else Color.LightGray,
          fontSize = 12.5.sp,
          lineHeight = 15.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun QuickTopUtilityChip(
  label: String,
  icon: ImageVector,
  tint: Color,
  onClick: () -> Unit,
  testTag: String
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Box(
    modifier = Modifier
      .heightIn(min = 44.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(if (isFocused) CinemaFocusGlow.copy(alpha = 0.25f) else CinemaSurface)
      .border(
        width = if (isFocused) 2.dp else 1.dp,
        color = if (isFocused) CinemaFocusGlow else CinemaBorder,
        shape = RoundedCornerShape(8.dp)
      )
      .clickable(interactionSource = interactionSource, indication = null) { onClick() }
      .focusable(interactionSource = interactionSource)
      .padding(horizontal = 12.dp, vertical = 7.dp)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isFocused) CinemaFocusGlow else tint,
        modifier = Modifier.size(17.dp)
      )
      Text(
        text = label,
        color = if (isFocused) CinemaFocusGlow else Color.White,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false
      )
    }
  }
}

@Composable
fun BrowserHomeScreen(
  repository: TvPreferencesRepository,
  onOpenWebUrl: (url: String, title: String) -> Unit,
  onSwitchToAsserTv: () -> Unit,
  onOpenBookmarks: () -> Unit,
  onOpenHistory: () -> Unit,
  modifier: Modifier = Modifier
) {
  var omniboxInput by remember { mutableStateOf("") }
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current

  val displayMode by repository.displayModeFlow.collectAsState()
  val configuration = LocalConfiguration.current
  val isMobileLayout = displayMode == AppDisplayMode.MOBILE || configuration.screenWidthDp < 600

  val history by repository.historyFlow.collectAsState()
  val bookmarks by repository.bookmarksFlow.collectAsState()

  val quickWebSites = remember {
    listOf(
      QuickWebSite("google", "بحث جوجل", "Google Search", "https://www.google.com", 0xFF4285F4),
      QuickWebSite("youtube", "يوتيوب", "YouTube", "https://www.youtube.com", 0xFFFF0000),
      QuickWebSite("kooora", "كووورة", "Kooora Sports", "https://www.kooora.com", 0xFF10B981),
      QuickWebSite("yallakora", "يلا كورة", "YallaKora", "https://www.yallakora.com", 0xFFF59E0B),
      QuickWebSite("wikipedia", "ويكيبيديا", "Wikipedia", "https://ar.wikipedia.org", 0xFF64748B),
      QuickWebSite("translate", "ترجمة جوجل", "Google Translate", "https://translate.google.com", 0xFF38BDF8),
      QuickWebSite("youm7", "أخبار اليوم", "News Portal", "https://www.youm7.com", 0xFFEC4899),
      QuickWebSite("facebook", "فيسبوك", "Facebook", "https://www.facebook.com", 0xFF1877F2)
    )
  }

  val googleQuickChips = remember {
    listOf(
      "أخبار اليوم مباشر",
      "مباريات اليوم",
      "طقس اليوم",
      "يوتيوب",
      "ترجمة جوجل",
      "مواقيت الصلاة",
      "أسعار العملات"
    )
  }

  fun submitOmnibox(forceGoogleSearch: Boolean = false) {
    val raw = omniboxInput.trim()
    keyboardController?.hide()
    focusManager.clearFocus()
    if (raw.isBlank()) {
      onOpenWebUrl("https://www.google.com", "Google")
      return
    }
    val targetUrl = if (forceGoogleSearch) {
      SmartOmniboxResolver.buildGoogleSearchUrl(raw)
    } else {
      SmartOmniboxResolver.resolveInputToUrl(raw)
    }
    val label = if (targetUrl.contains("google.com/search")) "بحث جوجل: $raw" else raw
    onOpenWebUrl(targetUrl, label)
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(CinemaDarkBackground)
      .padding(horizontal = if (isMobileLayout) 12.dp else 16.dp, vertical = 10.dp)
      .testTag("asser_browser_home_screen"),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // 1. Hero Google Search & Omnibox Card
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(CinemaSurface)
          .border(1.dp, CinemaBorder, RoundedCornerShape(14.dp))
          .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
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
              imageVector = Icons.Default.Public,
              contentDescription = null,
              tint = Color(0xFF38BDF8),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "متصفح Google",
              color = Color.White,
              fontSize = if (isMobileLayout) 18.sp else 20.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = omniboxInput,
          onValueChange = { omniboxInput = it },
          textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium),
          placeholder = {
            Text(
              text = "ابحث أو اكتب رابطاً...",
              color = Color.Gray,
              fontSize = 15.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = "بحث",
              tint = Color(0xFF38BDF8),
              modifier = Modifier.size(22.dp)
            )
          },
          trailingIcon = {
            if (omniboxInput.isNotEmpty()) {
              IconButton(onClick = { omniboxInput = "" }) {
                Icon(
                  imageVector = Icons.Default.Clear,
                  contentDescription = "مسح",
                  tint = Color.LightGray
                )
              }
            }
          },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { submitOmnibox(false) }),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF0B0F17),
            unfocusedContainerColor = Color(0xFF0B0F17),
            focusedBorderColor = Color(0xFF38BDF8),
            unfocusedBorderColor = CinemaBorder,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
          ),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("browser_omnibox_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (isMobileLayout) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Button(
                onClick = { submitOmnibox(forceGoogleSearch = true) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("btn_google_search_submit")
              ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "جوجل",
                  color = Color.White,
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 15.sp,
                  maxLines = 1,
                  softWrap = false,
                  overflow = TextOverflow.Ellipsis
                )
              }

              Button(
                onClick = { submitOmnibox(forceGoogleSearch = false) },
                colors = ButtonDefaults.buttonColors(containerColor = CinemaAccent),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("btn_omnibox_go")
              ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "فتح الرابط",
                  color = Color.Black,
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 15.sp,
                  maxLines = 1,
                  softWrap = false,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }

            Button(
              onClick = onSwitchToAsserTv,
              colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("btn_hero_switch_assertv")
            ) {
              Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Asser TV • أفلام ومسلسلات",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Button(
              onClick = { submitOmnibox(forceGoogleSearch = true) },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("btn_google_search_submit")
            ) {
              Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("جوجل", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, softWrap = false)
            }

            Button(
              onClick = { submitOmnibox(forceGoogleSearch = false) },
              colors = ButtonDefaults.buttonColors(containerColor = CinemaAccent),
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("btn_omnibox_go")
            ) {
              Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("فتح الرابط", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, softWrap = false)
            }

            Button(
              onClick = onSwitchToAsserTv,
              colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("btn_hero_switch_assertv")
            ) {
              Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Asser TV 🎬", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, softWrap = false)
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(googleQuickChips) { chip ->
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            Box(
              modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) CinemaFocusGlow.copy(alpha = 0.25f) else CinemaSurfaceVariant)
                .border(
                  width = if (isFocused) 2.dp else 1.dp,
                  color = if (isFocused) CinemaFocusGlow else CinemaBorder,
                  shape = RoundedCornerShape(8.dp)
                )
                .clickable(interactionSource = interactionSource, indication = null) {
                  omniboxInput = chip
                  submitOmnibox(forceGoogleSearch = true)
                }
                .focusable(interactionSource = interactionSource)
                .padding(horizontal = 12.dp, vertical = 7.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = chip,
                color = if (isFocused) CinemaFocusGlow else Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
              )
            }
          }
        }
      }
    }

    // 2. Quick Web Sites Grid
    item {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "مواقع سريعة",
          color = Color.White,
          fontSize = 17.sp,
          fontWeight = FontWeight.ExtraBold,
          maxLines = 1
        )

        val columnsPerRow = if (isMobileLayout) 2 else 4
        val rows = quickWebSites.chunked(columnsPerRow)
        rows.forEach { rowSites ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            rowSites.forEach { site ->
              QuickWebTileCard(
                site = site,
                onClick = { onOpenWebUrl(site.url, site.titleArabic) },
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }
    }

    // 3. Direct Asser TV Portals Strip
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(CinemaSurface)
          .border(1.dp, CinemaBorder, RoundedCornerShape(12.dp))
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
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
              Icons.Default.PlayCircleFilled,
              contentDescription = null,
              tint = CinemaPrimary,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "بوابات Asser TV",
              color = Color.White,
              fontSize = 16.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          Surface(
            onClick = onSwitchToAsserTv,
            color = CinemaPrimary.copy(alpha = 0.22f),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaPrimary),
            modifier = Modifier.heightIn(min = 38.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = "بحث موحد ⬅",
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
              )
            }
          }
        }

        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          items(StreamingPortal.values().filter { it.isEnabled }) { portal ->
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            Box(
              modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isFocused) CinemaPrimary.copy(alpha = 0.3f) else CinemaSurfaceVariant)
                .border(
                  width = if (isFocused) 2.dp else 1.dp,
                  color = if (isFocused) CinemaFocusGlow else CinemaPrimary.copy(alpha = 0.6f),
                  shape = RoundedCornerShape(10.dp)
                )
                .clickable(interactionSource = interactionSource, indication = null) {
                  onOpenWebUrl(portal.homeUrl, portal.displayNameArabic)
                }
                .focusable(interactionSource = interactionSource)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("browser_quick_portal_${portal.id}"),
              contentAlignment = Alignment.Center
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LiveTv, contentDescription = null, tint = CinemaAccent, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = portal.displayNameArabic,
                  color = Color.White,
                  fontSize = 14.5.sp,
                  fontWeight = FontWeight.ExtraBold,
                  maxLines = 1,
                  softWrap = false
                )
              }
            }
          }
        }
      }
    }

    // 4. Recent Browsing History & Bookmarks
    if (history.isNotEmpty() || bookmarks.isNotEmpty()) {
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CinemaSurface)
            .border(1.dp, CinemaBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "آخر الزيارات",
              color = Color.White,
              fontSize = 16.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Surface(
                onClick = onOpenBookmarks,
                color = CinemaSurfaceVariant,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.heightIn(min = 36.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = "المفضلة (${bookmarks.size})",
                    color = CinemaAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                  )
                }
              }
              Surface(
                onClick = { repository.clearHistory() },
                color = CinemaSurfaceVariant,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.heightIn(min = 36.dp)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                  Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(15.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("مسح", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
              }
            }
          }

          history.take(5).forEach { item ->
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) CinemaSurfaceVariant else Color(0xFF0B0F17))
                .border(
                  width = if (isFocused) 2.dp else 1.dp,
                  color = if (isFocused) CinemaFocusGlow else CinemaBorder,
                  shape = RoundedCornerShape(8.dp)
                )
                .clickable(interactionSource = interactionSource, indication = null) {
                  onOpenWebUrl(item.url, item.title)
                }
                .focusable(interactionSource = interactionSource)
                .padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = item.title.ifBlank { item.url },
                  color = Color.White,
                  fontSize = 14.5.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = item.url,
                  color = Color.Gray,
                  fontSize = 12.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Icon(
                imageVector = Icons.Default.OpenInBrowser,
                contentDescription = "فتح",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun QuickWebTileCard(
  site: QuickWebSite,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()
  val accentColor = Color(site.accentColorHex)

  val icon = when (site.id) {
    "google" -> Icons.Default.Search
    "youtube" -> Icons.Default.PlayCircleFilled
    "kooora", "yallakora" -> Icons.Default.SportsSoccer
    "translate" -> Icons.Default.Translate
    else -> Icons.Default.Language
  }

  Box(
    modifier = modifier
      .heightIn(min = 68.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(if (isFocused) accentColor.copy(alpha = 0.22f) else CinemaSurface)
      .border(
        width = if (isFocused) 2.dp else 1.dp,
        color = if (isFocused) CinemaFocusGlow else accentColor.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp)
      )
      .clickable(interactionSource = interactionSource, indication = null) { onClick() }
      .focusable(interactionSource = interactionSource)
      .padding(horizontal = 12.dp, vertical = 10.dp)
      .testTag("quick_tile_${site.id}"),
    contentAlignment = Alignment.CenterStart
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(accentColor.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = site.titleArabic,
          tint = accentColor,
          modifier = Modifier.size(20.dp)
        )
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = site.titleArabic,
          color = Color.White,
          fontSize = 15.sp,
          lineHeight = 19.sp,
          fontWeight = FontWeight.ExtraBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = site.titleEnglish,
          color = Color.Gray,
          fontSize = 12.sp,
          lineHeight = 15.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
