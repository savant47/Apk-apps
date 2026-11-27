package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.TraktDeviceCodeResult
import com.example.data.TraktFullSyncResult
import com.example.data.TraktSyncService
import com.example.data.TraktTokenPollResult
import com.example.data.TvPreferencesRepository
import com.example.model.StreamingPortal
import com.example.model.TraktCalendarItem
import com.example.model.TraktParsedLink
import com.example.model.TraktWatchlistItem
import com.example.model.UpNextItem
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaFocusGlow
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.ClipboxWhite
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

object TraktUrlParser {
  private val showEpisodeRegex = Regex(
    """(?:https?://)?(?:www\.|app\.)?trakt\.tv/shows/([^/?#]+)/seasons/(\d+)/episodes/(\d+)""",
    RegexOption.IGNORE_CASE
  )
  private val showSeasonRegex = Regex(
    """(?:https?://)?(?:www\.|app\.)?trakt\.tv/shows/([^/?#]+)/seasons/(\d+)""",
    RegexOption.IGNORE_CASE
  )
  private val showOnlyRegex = Regex(
    """(?:https?://)?(?:www\.|app\.)?trakt\.tv/shows/([^/?#]+)""",
    RegexOption.IGNORE_CASE
  )
  private val movieRegex = Regex(
    """(?:https?://)?(?:www\.|app\.)?trakt\.tv/movies/([^/?#]+)""",
    RegexOption.IGNORE_CASE
  )

  fun slugToTitle(slug: String): String {
    val cleanSlug = slug
      .substringBefore('?')
      .substringBefore('#')
      .trim('/')
      .replace(Regex("-(?:19|20)\\d{2}$"), "")
    return cleanSlug
      .split('-', '_')
      .filter { it.isNotBlank() }
      .joinToString(" ") { word ->
        word.replaceFirstChar { ch ->
          if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
        }
      }
      .trim()
  }

  fun parse(url: String?): TraktParsedLink? {
    if (url.isNullOrBlank()) return null
    val trimmed = url.trim()

    showEpisodeRegex.find(trimmed)?.let { m ->
      val slug = m.groupValues[1]
      val s = m.groupValues[2].toIntOrNull()?.coerceAtLeast(1) ?: 1
      val e = m.groupValues[3].toIntOrNull()?.coerceAtLeast(1) ?: 1
      val title = slugToTitle(slug)
      if (title.isNotBlank()) {
        return TraktParsedLink(slug, title, s, e, false, trimmed)
      }
    }
    showSeasonRegex.find(trimmed)?.let { m ->
      val slug = m.groupValues[1]
      val s = m.groupValues[2].toIntOrNull()?.coerceAtLeast(1) ?: 1
      val title = slugToTitle(slug)
      if (title.isNotBlank()) {
        return TraktParsedLink(slug, title, s, 1, false, trimmed)
      }
    }
    movieRegex.find(trimmed)?.let { m ->
      val slug = m.groupValues[1]
      val title = slugToTitle(slug)
      if (title.isNotBlank()) {
        return TraktParsedLink(slug, title, null, null, true, trimmed)
      }
    }
    showOnlyRegex.find(trimmed)?.let { m ->
      val slug = m.groupValues[1]
      if (slug.lowercase() in setOf("trending", "popular", "favorited", "watched", "collected", "anticipated", "boxoffice")) {
        return null
      }
      val title = slugToTitle(slug)
      if (title.isNotBlank()) {
        return TraktParsedLink(slug, title, 1, 1, false, trimmed)
      }
    }
    return null
  }
}

private data class ActiveDeviceAuthState(
  val deviceCode: String,
  val userCode: String,
  val verificationUrl: String,
  val intervalSec: Int,
  val expiresAtMs: Long
)

/**
 * 100% Native Trakt Integration Screen (ZERO WebView!).
 * - When disconnected: Official Trakt OAuth Device Code setup & activation flow matching Asser TV's design.
 * - When connected: Native Up Next / Calendar / Watchlist Hub with posters, progress bars, Play in Asser TV, and 2-way Watched ✓ sync.
 */
@Composable
fun TraktPortalScreen(
  repository: TvPreferencesRepository,
  traktSyncService: TraktSyncService,
  onPlayTraktTarget: (
    showTitle: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    isMovie: Boolean,
    preferredPortal: StreamingPortal
  ) -> Unit,
  onMarkUpNextItemWatched: (UpNextItem) -> Unit,
  onTraktSyncNow: () -> Unit,
  onTraktCredentialsSaved: (
    clientId: String,
    clientSecret: String,
    accessToken: String,
    refreshToken: String,
    expiresAtMs: Long,
    username: String
  ) -> Unit,
  onTraktDisconnected: () -> Unit,
  onOpenSettings: () -> Unit = {},
  isSyncingNow: Boolean = false,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val isConnected by repository.traktConnectedFlow.collectAsState()
  val traktUsername by repository.traktUsernameFlow.collectAsState()
  val upNextItems by repository.upNextFlow.collectAsState()
  val traktSyncStats by repository.traktSyncStatsFlow.collectAsState()
  val traktLastSyncError by repository.traktLastSyncErrorFlow.collectAsState()
  val traktKeysVersion by repository.traktKeysVersionFlow.collectAsState()

  var selectedLaunchPortal by remember { mutableStateOf(StreamingPortal.WATCH_SERIES) }
  var markingItemId by remember { mutableStateOf<String?>(null) }

  // Effective Developer Keys (Admin Override -> BuildConfig Secrets). Never exposed to normal users!
  val effectiveClientId = remember(traktKeysVersion) { repository.getTraktClientId() }
  val effectiveClientSecret = remember(traktKeysVersion) { repository.getTraktClientSecret() }
  val hasValidAppKeys = effectiveClientId.isNotBlank()

  // Auto-Generated Device Code OAuth State
  var isGeneratingCode by remember { mutableStateOf(false) }
  var authError by remember { mutableStateOf<String?>(null) }
  var activeDeviceAuth by remember { mutableStateOf<ActiveDeviceAuthState?>(null) }
  var remainingSeconds by remember { mutableIntStateOf(0) }
  var codeRefreshNonce by remember { mutableIntStateOf(0) }

  fun copyToClipboard(label: String, value: String) {
    try {
      val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
      cm?.setPrimaryClip(ClipData.newPlainText(label, value))
      Toast.makeText(context, "تم نسخ $label ✓", Toast.LENGTH_SHORT).show()
    } catch (_: Throwable) {}
  }

  fun openExternalTraktUrl(url: String) {
    // Strictly allow only official https://trakt.tv or https://auth.trakt.tv URLs
    if (!url.startsWith("https://trakt.tv/") && !url.startsWith("https://auth.trakt.tv/")) return
    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (_: Throwable) {
      copyToClipboard("الرابط", url)
    }
  }

  // AUTO-GENERATE Device Code immediately when Trakt tab opens (like BeeTV / HDO Box)
  LaunchedEffect(isConnected, effectiveClientId, effectiveClientSecret, codeRefreshNonce) {
    if (isConnected) {
      activeDeviceAuth = null
      authError = null
      isGeneratingCode = false
      return@LaunchedEffect
    }
    if (!hasValidAppKeys) {
      activeDeviceAuth = null
      authError = null
      isGeneratingCode = false
      return@LaunchedEffect
    }
    if (activeDeviceAuth != null && remainingSeconds > 15 && codeRefreshNonce == 0) {
      return@LaunchedEffect
    }
    isGeneratingCode = true
    authError = null
    try {
      when (val res = traktSyncService.requestDeviceCode(effectiveClientId)) {
        is TraktDeviceCodeResult.Success -> {
          activeDeviceAuth = ActiveDeviceAuthState(
            deviceCode = res.deviceCode,
            userCode = res.userCode,
            verificationUrl = res.verificationUrl.ifBlank { TraktSyncService.TRAKT_ACTIVATE_URL },
            intervalSec = res.intervalSec,
            expiresAtMs = System.currentTimeMillis() + (res.expiresInSec * 1000L)
          )
          remainingSeconds = res.expiresInSec
        }
        is TraktDeviceCodeResult.Error -> {
          activeDeviceAuth = null
          authError = res.message
        }
      }
    } finally {
      isGeneratingCode = false
    }
  }

  // Smooth 1-second visual countdown timer while code is active
  LaunchedEffect(activeDeviceAuth?.deviceCode) {
    val auth = activeDeviceAuth ?: return@LaunchedEffect
    while (isActive && activeDeviceAuth?.deviceCode == auth.deviceCode) {
      val leftSec = ((auth.expiresAtMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
      remainingSeconds = leftSec
      if (leftSec <= 0) break
      delay(1000L)
    }
  }

  // Automatic Background Polling while Device Code is active
  LaunchedEffect(activeDeviceAuth?.deviceCode, effectiveClientId, effectiveClientSecret) {
    val auth = activeDeviceAuth ?: return@LaunchedEffect
    if (!hasValidAppKeys) return@LaunchedEffect
    var pollIntervalMs = (auth.intervalSec.coerceAtLeast(4)) * 1000L
    while (isActive && activeDeviceAuth?.deviceCode == auth.deviceCode) {
      val leftSec = ((auth.expiresAtMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
      if (leftSec <= 0) {
        authError = "انتهت صلاحية كود التفعيل — اضغط لتوليد كود جديد"
        activeDeviceAuth = null
        break
      }

      delay(pollIntervalMs)
      if (!isActive || activeDeviceAuth?.deviceCode != auth.deviceCode) break

      when (
        val pollRes = traktSyncService.pollDeviceToken(
          clientId = effectiveClientId,
          clientSecret = effectiveClientSecret,
          deviceCode = auth.deviceCode
        )
      ) {
        is TraktTokenPollResult.Authorized -> {
          val tokens = pollRes.tokens
          val username = traktSyncService.fetchUserProfile(effectiveClientId, tokens.accessToken)
          repository.saveTraktOAuthCredentials(
            clientId = effectiveClientId,
            clientSecret = effectiveClientSecret,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresAtMs = tokens.expiresAtMs,
            username = username
          )
          onTraktCredentialsSaved(
            effectiveClientId,
            effectiveClientSecret,
            tokens.accessToken,
            tokens.refreshToken,
            tokens.expiresAtMs,
            username
          )
          activeDeviceAuth = null
          authError = null
          Toast.makeText(
            context,
            if (username.isNotBlank()) "تم ربط حساب Trakt (@$username) بنجاح ✓" else "تم ربط حساب Trakt بنجاح ✓",
            Toast.LENGTH_LONG
          ).show()
          onTraktSyncNow()
          break
        }
        TraktTokenPollResult.Pending -> {
          // Keep polling quietly
        }
        TraktTokenPollResult.SlowDown -> {
          pollIntervalMs = (pollIntervalMs + 3000L).coerceAtMost(15_000L)
        }
        TraktTokenPollResult.Expired -> {
          authError = "انتهت صلاحية الكود — اضغط لتوليد كود جديد"
          activeDeviceAuth = null
          break
        }
        TraktTokenPollResult.Denied -> {
          authError = "تم رفض الربط من Trakt — يمكنك توليد كود جديد"
          activeDeviceAuth = null
          break
        }
        is TraktTokenPollResult.Error -> {
          authError = pollRes.message
          activeDeviceAuth = null
          break
        }
      }
    }
  }

  // Strictly filtered to started shows with unwatched aired episodes (0 < watchedCount < totalCount), unified with Home screen
  val displayedUpNext = remember(upNextItems) {
    upNextItems.filter { item ->
      item.fromTrakt && item.totalCount > 0 && item.watchedCount in 1 until item.totalCount
    }
  }

  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Column(
      modifier = modifier
        .fillMaxSize()
        .background(CinemaDarkBackground)
        .testTag("trakt_portal_screen")
    ) {
      if (!isConnected) {
        // =====================================================================
        // STATE A: 1-STEP AUTO-GENERATED TRAKT ACTIVATION + QR CODE (ZERO DEV SETUP)
        // =====================================================================
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Surface(
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.5.dp, Color(0xFF7F1D1D)),
            color = Color.Transparent,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(22.dp))
              .background(
                Brush.verticalGradient(
                  colors = listOf(Color(0xFF331117), Color(0xFF141820), Color(0xFF0E1218))
                )
              )
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              // Top Icon + Title Header
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFED1C24)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.QueuePlayNext,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                  )
                }

                Text(
                  text = "ربط حساب Trakt.tv بالكود",
                  color = Color.White,
                  fontSize = 21.sp,
                  fontWeight = FontWeight.Black,
                  lineHeight = 27.sp,
                  modifier = Modifier.weight(1f)
                )
              }

              Text(
                text = "امسح رمز QR بهاتفك أو افتح رابط التفعيل وأدخل الكود الظاهر أدناه لربط حسابك وعرض قسم «التالي للمشاهدة» تلقائياً مرتباً بآخر مشاهدة.",
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp,
                lineHeight = 21.sp
              )

              if (!hasValidAppKeys) {
                // Clean, non-technical message when developer Secrets haven't been set yet
                Surface(
                  color = Color(0xFF101622),
                  shape = RoundedCornerShape(16.dp),
                  border = BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.6f)),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("trakt_unconfigured_card")
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Settings,
                      contentDescription = null,
                      tint = CinemaAccent,
                      modifier = Modifier.size(34.dp)
                    )
                    Text(
                      text = "خدمة ربط Trakt بانتظار التفعيل من المسؤول",
                      color = Color.White,
                      fontSize = 16.sp,
                      fontWeight = FontWeight.ExtraBold,
                      textAlign = TextAlign.Center
                    )
                    Text(
                      text = "بمجرد إضافة مفاتيح التطبيق في Secrets أو من خلال ترس الإعدادات للمسؤول، سيظهر كود التفعيل ورمز QR هنا تلقائياً لجميع المستخدمين.",
                      color = Color.LightGray,
                      fontSize = 13.sp,
                      lineHeight = 20.sp,
                      textAlign = TextAlign.Center
                    )
                    Button(
                      onClick = onOpenSettings,
                      colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
                      shape = RoundedCornerShape(12.dp),
                      modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_trakt_open_admin_settings")
                    ) {
                      Icon(Icons.Default.Settings, contentDescription = null, tint = CinemaAccent, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("فتح الإعدادات", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                  }
                }
              } else if (isGeneratingCode && activeDeviceAuth == null) {
                Surface(
                  color = Color(0xFF0B1220),
                  shape = RoundedCornerShape(16.dp),
                  border = BorderStroke(1.dp, CinemaBorder),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    CircularProgressIndicator(
                      color = Color(0xFFED1C24),
                      strokeWidth = 3.dp,
                      modifier = Modifier.size(38.dp)
                    )
                    Text(
                      text = "جاري تجهيز كود التفعيل ورمز QR تلقائياً...",
                      color = Color.White,
                      fontSize = 14.5.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }
              } else {
                val deviceAuth = activeDeviceAuth
                if (deviceAuth != null) {
                  val activateUrl = deviceAuth.verificationUrl.ifBlank { TraktSyncService.TRAKT_ACTIVATE_URL }
                  Surface(
                    color = Color(0xFF0B1220),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.5.dp, CinemaAccent.copy(alpha = 0.85f)),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("trakt_active_code_card")
                  ) {
                    BoxWithConstraints(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                    ) {
                      val isWide = maxWidth >= 520.dp
                      if (isWide) {
                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.spacedBy(18.dp),
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          TraktQrActivationBlock(
                            url = activateUrl,
                            qrSize = 164.dp
                          )
                          TraktCodeAndActionsColumn(
                            userCode = deviceAuth.userCode,
                            activateUrl = activateUrl,
                            remainingSeconds = remainingSeconds,
                            isRefreshing = isGeneratingCode,
                            onCopyCode = { copyToClipboard("كود التفعيل", deviceAuth.userCode) },
                            onOpenActivate = { openExternalTraktUrl(activateUrl) },
                            onRefreshCode = { codeRefreshNonce++ },
                            modifier = Modifier.weight(1f)
                          )
                        }
                      } else {
                        Column(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalAlignment = Alignment.CenterHorizontally,
                          verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                          TraktQrActivationBlock(
                            url = activateUrl,
                            qrSize = 172.dp
                          )
                          TraktCodeAndActionsColumn(
                            userCode = deviceAuth.userCode,
                            activateUrl = activateUrl,
                            remainingSeconds = remainingSeconds,
                            isRefreshing = isGeneratingCode,
                            onCopyCode = { copyToClipboard("كود التفعيل", deviceAuth.userCode) },
                            onOpenActivate = { openExternalTraktUrl(activateUrl) },
                            onRefreshCode = { codeRefreshNonce++ },
                            modifier = Modifier.fillMaxWidth()
                          )
                        }
                      }
                    }
                  }
                }

                if (!authError.isNullOrBlank()) {
                  Surface(
                    color = Color(0x33EF4444),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Column(
                      modifier = Modifier.padding(12.dp),
                      verticalArrangement = Arrangement.spacedBy(10.dp),
                      horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                      Text(
                        text = authError ?: "",
                        color = Color(0xFFFCA5A5),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                      )
                      Button(
                        onClick = { codeRefreshNonce++ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFED1C24)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                          .fillMaxWidth()
                          .heightIn(min = 48.dp)
                          .testTag("btn_trakt_retry_code")
                      ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("توليد كود تفعيل جديد", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
                      }
                    }
                  }
                }
              }

              // 3 Clean Feature Highlights (ZERO "كاش محلي")
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TraktFeatureHighlightRow(
                  icon = Icons.Default.Sync,
                  iconTint = Color(0xFF38BDF8),
                  text = "عرض حلقاتك غير المشاهدة في «التالي للمشاهدة» مرتبة بآخر مشاهدة"
                )
                TraktFeatureHighlightRow(
                  icon = Icons.Default.CheckCircle,
                  iconTint = CinemaSuccess,
                  text = "تسجيل المشاهدة تلقائياً عند 80% أو بالضغط على ✓"
                )
                TraktFeatureHighlightRow(
                  icon = Icons.Default.LocalFireDepartment,
                  iconTint = Color(0xFFED1C24),
                  text = "عرض مباشر على الواجهة الرئيسية مع استكمال المشاهدة"
                )
              }
            }
          }
        }
      } else {
        // =====================================================================
        // STATE B: CONNECTED NATIVE TRAKT HUB (NEXT TO WATCH ONLY - UNIFIED UI)
        // =====================================================================
        Surface(
          color = CinemaSurface,
          border = BorderStroke(1.dp, CinemaBorder),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // Row 1: Unified Section Title + Count Badge + Full "تحديث" Button + Account Pill + Disconnect
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
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
                    text = "${displayedUpNext.size}",
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Surface(
                  onClick = onTraktSyncNow,
                  color = CinemaDarkBackground,
                  shape = RoundedCornerShape(8.dp),
                  border = BorderStroke(1.dp, CinemaSuccess.copy(alpha = 0.75f)),
                  modifier = Modifier
                    .heightIn(min = 36.dp)
                    .testTag("btn_trakt_sync_now")
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                  ) {
                    if (isSyncingNow) {
                      CircularProgressIndicator(
                        color = CinemaSuccess,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(15.dp)
                      )
                    } else {
                      Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "تحديث",
                        tint = CinemaSuccess,
                        modifier = Modifier.size(15.dp)
                      )
                    }
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

                Surface(
                  onClick = {
                    repository.disconnectTrakt(clearAppKeys = false)
                    onTraktDisconnected()
                    Toast.makeText(context, "تم فصل حساب Trakt", Toast.LENGTH_SHORT).show()
                  },
                  color = Color(0x26EF4444),
                  shape = RoundedCornerShape(8.dp),
                  border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.65f)),
                  modifier = Modifier
                    .heightIn(min = 36.dp)
                    .testTag("btn_trakt_disconnect")
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.LinkOff,
                      contentDescription = "فصل",
                      tint = Color(0xFFFCA5A5),
                      modifier = Modifier.size(15.dp)
                    )
                    Text(
                      text = "فصل",
                      color = Color(0xFFFCA5A5),
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      softWrap = false
                    )
                  }
                }
              }
            }

            // Row 2: Connected Account Pill + Full Portal Switcher (WatchSeries / TopCinema) + Sort Order Badge
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Surface(
                  color = CinemaSuccess.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(8.dp),
                  border = BorderStroke(1.dp, CinemaSuccess.copy(alpha = 0.55f))
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(CinemaSuccess)
                    )
                    Text(
                      text = if (traktUsername.isNotBlank()) "@$traktUsername" else "متصل",
                      color = CinemaSuccess,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.ExtraBold,
                      maxLines = 1,
                      softWrap = false
                    )
                  }
                }

                listOf(StreamingPortal.WATCH_SERIES, StreamingPortal.TOP_CINEMA).forEach { portal ->
                  val isSelected = selectedLaunchPortal == portal
                  Surface(
                    onClick = { selectedLaunchPortal = portal },
                    color = if (isSelected) ClipboxWhite else CinemaDarkBackground,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isSelected) ClipboxWhite else CinemaBorder),
                    modifier = Modifier.heightIn(min = 32.dp)
                  ) {
                    Box(
                      contentAlignment = Alignment.Center,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                      Text(
                        text = portal.displayNameEnglish,
                        color = if (isSelected) Color.Black else Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                      )
                    }
                  }
                }
              }

              Surface(
                color = CinemaDarkBackground,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CinemaBorder)
              ) {
                Text(
                  text = "مرتب بآخر مشاهدة",
                  color = CinemaAccent,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                  maxLines = 1,
                  softWrap = false
                )
              }
            }

            // Row 3: Official Sync & Filter Summary Strip (ملخص المسلسلات المسحوبة والمفلترة)
            if (traktSyncStats.totalWatchedFetched > 0) {
              val totalFilteredOut = traktSyncStats.completedFilteredCount +
                traktSyncStats.hiddenFilteredCount +
                traktSyncStats.noNextEpisodeFilteredCount
              Surface(
                color = CinemaDarkBackground,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CinemaBorder),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("trakt_sync_stats_banner")
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "المسحوب: ${traktSyncStats.totalWatchedFetched}",
                    color = Color.LightGray,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                  )
                  Text(
                    text = "المستبعد (مكتمل/مخفي): $totalFilteredOut",
                    color = CinemaAccent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                  )
                  Text(
                    text = "الصافي: ${displayedUpNext.size}",
                    color = CinemaSuccess,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                  )
                }
              }
            }

            // Error Banner if Trakt request failed (never show fake data)
            if (!traktLastSyncError.isNullOrBlank()) {
              Surface(
                color = Color(0x33EF4444),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("trakt_sync_error_banner")
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = traktLastSyncError ?: "",
                    color = Color(0xFFFCA5A5),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Surface(
                    onClick = onTraktSyncNow,
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
        }

        // Next to Watch List ONLY
        if (displayedUpNext.isEmpty()) {
          TraktEmptySectionCard(
            message = if (isSyncingNow) "جاري سحب حلقاتك من Trakt..." else "لا توجد حلقات غير مشاهدة حالياً — اضغط «تحديث الآن»",
            isLoading = isSyncingNow,
            onSync = onTraktSyncNow,
            modifier = Modifier.weight(1f)
          )
        } else {
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .testTag("trakt_next_to_watch_list"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(displayedUpNext, key = { it.id }) { item ->
              TraktNextToWatchRow(
                item = item,
                isMarkingWatched = markingItemId == item.id,
                onPlay = {
                  onPlayTraktTarget(
                    item.showTitle,
                    item.seasonNumber,
                    item.episodeNumber,
                    false,
                    selectedLaunchPortal
                  )
                },
                onMarkWatched = {
                  if (markingItemId != null) return@TraktNextToWatchRow
                  markingItemId = item.id
                  onMarkUpNextItemWatched(item)
                  scope.launch {
                    delay(800L)
                    if (markingItemId == item.id) markingItemId = null
                  }
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
private fun TraktFeatureHighlightRow(
  icon: ImageVector,
  iconTint: Color,
  text: String
) {
  Surface(
    color = Color(0xFF141A24),
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, CinemaBorder),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 11.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(19.dp))
      Text(
        text = text,
        color = Color(0xFFD1D5DB),
        fontSize = 13.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.weight(1f)
      )
    }
  }
}

@Composable
private fun TraktEmptySectionCard(
  message: String,
  isLoading: Boolean,
  onSync: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(20.dp),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      color = CinemaSurface,
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, CinemaBorder),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        if (isLoading) {
          CircularProgressIndicator(color = CinemaPrimary, modifier = Modifier.size(36.dp))
        }
        Text(
          text = message,
          color = Color.LightGray,
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center
        )
        if (!isLoading) {
          Button(
            onClick = onSync,
            colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.heightIn(min = 46.dp)
          ) {
            Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("تحديث الآن • Sync now", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
          }
        }
      }
    }
  }
}

@Composable
private fun TraktNextToWatchRow(
  item: UpNextItem,
  isMarkingWatched: Boolean,
  onPlay: () -> Unit,
  onMarkWatched: () -> Unit
) {
  val context = LocalContext.current
  val cardInteraction = remember { MutableInteractionSource() }
  val isCardFocused by cardInteraction.collectIsFocusedAsState()
  val watchedInteraction = remember { MutableInteractionSource() }
  val isWatchedFocused by watchedInteraction.collectIsFocusedAsState()

  val seasonCode = item.seasonNumber.toString().padStart(2, '0')
  val epCode = item.episodeNumber.toString().padStart(2, '0')
  val formattedCode = "S.$seasonCode E.$epCode"

  val progressFraction = if (item.totalCount > 0) {
    (item.watchedCount.toFloat() / item.totalCount.toFloat()).coerceIn(0f, 1f)
  } else {
    0f
  }
  val leftCount = if (item.totalCount > 0) (item.totalCount - item.watchedCount).coerceAtLeast(0) else 0
  val isSeasonPremiere = item.seasonNumber > 1 && item.episodeNumber == 1
  val progressLabel = when {
    isSeasonPremiere && leftCount <= 1 -> "${item.watchedCount}/${item.totalCount} • Premiere"
    isSeasonPremiere -> "${item.watchedCount}/${item.totalCount} ($leftCount left • Premiere)"
    item.totalCount > 0 -> "${item.watchedCount}/${item.totalCount} ($leftCount left)"
    else -> "Season ${item.seasonNumber} • Episode ${item.episodeNumber}"
  }

  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Surface(
      onClick = onPlay,
      interactionSource = cardInteraction,
      color = CinemaSurface,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(
        width = if (isCardFocused) 2.5.dp else 1.dp,
        color = if (isCardFocused) CinemaFocusGlow else CinemaBorder
      ),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("trakt_next_row_${item.id}")
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Tall Show Poster
        TraktPosterThumb(
          title = item.showTitle,
          posterUrl = item.posterUrl,
          context = context,
          widthDp = 76,
          heightDp = 112
        )

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = item.showTitle,
            color = if (isCardFocused) CinemaFocusGlow else Color.White,
            fontSize = 17.5.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Surface(
              color = CinemaSurfaceVariant,
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
            ) {
              Text(
                text = formattedCode,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                maxLines = 1,
                softWrap = false
              )
            }

            if (item.episodeTitle.isNotBlank()) {
              Text(
                text = item.episodeTitle,
                color = Color(0xFFD1D5DB),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = progressLabel,
              color = Color(0xFF9CA3AF),
              fontSize = 12.5.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false
            )

            if (item.totalCount > 0) {
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(4.dp)
                  .clip(RoundedCornerShape(50))
                  .background(Color(0xFF2D333B))
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth(progressFraction.coerceAtLeast(0.03f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(CinemaPrimary)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(2.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Surface(
              onClick = onPlay,
              color = CinemaPrimary,
              shape = RoundedCornerShape(50),
              modifier = Modifier
                .heightIn(min = 38.dp)
                .testTag("btn_trakt_row_play_${item.id}")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل", tint = Color.White, modifier = Modifier.size(18.dp))
                Text("تشغيل", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
              }
            }

            Surface(
              onClick = onMarkWatched,
              interactionSource = watchedInteraction,
              color = if (isWatchedFocused) CinemaSuccess else Color.Transparent,
              shape = RoundedCornerShape(50),
              border = BorderStroke(
                width = if (isWatchedFocused) 2.dp else 1.5.dp,
                color = if (isWatchedFocused) ClipboxWhite else Color.White.copy(alpha = 0.85f)
              ),
              modifier = Modifier
                .width(92.dp)
                .heightIn(min = 38.dp)
                .testTag("btn_trakt_row_watched_${item.id}")
            ) {
              Box(contentAlignment = Alignment.Center) {
                if (isMarkingWatched) {
                  CircularProgressIndicator(
                    color = if (isWatchedFocused) Color.Black else Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                  )
                } else {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "تمت المشاهدة",
                    tint = if (isWatchedFocused) Color.Black else Color.White,
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

@Composable
private fun TraktCalendarRow(
  item: TraktCalendarItem,
  onPlay: () -> Unit
) {
  val context = LocalContext.current
  val sPad = item.seasonNumber.toString().padStart(2, '0')
  val ePad = item.episodeNumber.toString().padStart(2, '0')
  val airDateShort = item.firstAiredIso.take(10)

  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Surface(
      onClick = onPlay,
      color = CinemaSurface,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, CinemaBorder),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        TraktPosterThumb(title = item.showTitle, posterUrl = item.posterUrl, context = context, widthDp = 64, heightDp = 94)

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          if (airDateShort.isNotBlank()) {
            Surface(
              color = CinemaAccent.copy(alpha = 0.16f),
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.5f))
            ) {
              Text(
                text = airDateShort,
                color = CinemaAccent,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
              )
            }
          }
          Text(
            text = item.showTitle,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = if (item.episodeTitle.isNotBlank()) "S.$sPad E.$ePad • ${item.episodeTitle}" else "S.$sPad E.$ePad",
            color = Color(0xFFD1D5DB),
            fontSize = 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Surface(
          onClick = onPlay,
          color = CinemaPrimary,
          shape = RoundedCornerShape(50),
          modifier = Modifier.heightIn(min = 38.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل", tint = Color.White, modifier = Modifier.size(16.dp))
            Text("شغّل", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
          }
        }
      }
    }
  }
}

@Composable
private fun TraktWatchlistRow(
  item: TraktWatchlistItem,
  onPlay: () -> Unit
) {
  val context = LocalContext.current
  val isMovie = item.mediaType == "movie"
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Surface(
      onClick = onPlay,
      color = CinemaSurface,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, CinemaBorder),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        TraktPosterThumb(title = item.title, posterUrl = item.posterUrl, context = context, widthDp = 60, heightDp = 88)

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
              color = if (isMovie) CinemaPrimary.copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.2f),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text(
                text = if (isMovie) "فيلم" else "مسلسل",
                color = if (isMovie) CinemaPrimary else Color(0xFF38BDF8),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
              )
            }
            if (item.year.isNotBlank()) {
              Text(text = item.year, color = Color.Gray, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
          }
          Text(
            text = item.title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Surface(
          onClick = onPlay,
          color = CinemaPrimary,
          shape = RoundedCornerShape(50),
          modifier = Modifier.heightIn(min = 38.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل", tint = Color.White, modifier = Modifier.size(16.dp))
            Text("تشغيل", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
          }
        }
      }
    }
  }
}

@Composable
private fun TraktPosterThumb(
  title: String,
  posterUrl: String,
  context: Context,
  widthDp: Int,
  heightDp: Int
) {
  Box(
    modifier = Modifier
      .width(widthDp.dp)
      .height(heightDp.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(
        Brush.verticalGradient(
          colors = listOf(Color(0xFF1F2937), Color(0xFF111827))
        )
      )
      .border(1.dp, CinemaBorder, RoundedCornerShape(10.dp)),
    contentAlignment = Alignment.Center
  ) {
    val initials = remember(title) {
      title
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "TV" }
    }
    Text(
      text = initials,
      color = Color.White.copy(alpha = 0.65f),
      fontSize = 18.sp,
      fontWeight = FontWeight.Black
    )
    if (posterUrl.isNotBlank()) {
      AsyncImage(
        model = remember(posterUrl) {
          ImageRequest.Builder(context)
            .data(posterUrl)
            .size(180, 260)
            .allowHardware(Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
            .crossfade(false)
            .build()
        },
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}

@Composable
private fun TraktHubTabChip(
  label: String,
  icon: ImageVector,
  isActive: Boolean,
  onClick: () -> Unit,
  testTag: String
) {
  Surface(
    onClick = onClick,
    color = if (isActive) CinemaPrimary else CinemaDarkBackground,
    shape = RoundedCornerShape(50),
    border = BorderStroke(1.dp, if (isActive) CinemaAccent else CinemaBorder),
    modifier = Modifier
      .heightIn(min = 38.dp)
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isActive) Color.White else CinemaAccent,
        modifier = Modifier.size(16.dp)
      )
      Text(
        text = label,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false
      )
    }
  }
}

@Composable
private fun TraktQrActivationBlock(
  url: String,
  qrSize: Dp
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Surface(
      color = Color.White,
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(2.dp, CinemaAccent.copy(alpha = 0.8f)),
      modifier = Modifier.testTag("trakt_qr_code_card")
    ) {
      Box(
        modifier = Modifier.padding(10.dp),
        contentAlignment = Alignment.Center
      ) {
        LocalQrCodeCanvas(
          content = url,
          size = qrSize
        )
      }
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Icon(
        imageVector = Icons.Default.QrCode2,
        contentDescription = null,
        tint = CinemaAccent,
        modifier = Modifier.size(16.dp)
      )
      Text(
        text = "امسح الكود بكاميرا الهاتف",
        color = Color(0xFFE2E8F0),
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Composable
private fun TraktCodeAndActionsColumn(
  userCode: String,
  activateUrl: String,
  remainingSeconds: Int,
  isRefreshing: Boolean,
  onCopyCode: () -> Unit,
  onOpenActivate: () -> Unit,
  onRefreshCode: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      text = "أدخل هذا الكود في صفحة التفعيل على Trakt:",
      color = Color.LightGray,
      fontSize = 14.sp,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      Surface(
        color = Color(0xFF1A2232),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, CinemaPrimary)
      ) {
        Text(
          text = userCode,
          color = CinemaAccent,
          fontSize = 34.sp,
          fontWeight = FontWeight.Black,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 4.sp,
          modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag("trakt_activation_code_text")
        )
      }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      Text(
        text = activateUrl.removePrefix("https://"),
        color = Color(0xFF38BDF8),
        fontSize = 13.5.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold
      )
    }

    Button(
      onClick = onOpenActivate,
      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFED1C24)),
      shape = RoundedCornerShape(12.dp),
      contentPadding = PaddingValues(vertical = 13.dp, horizontal = 16.dp),
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 50.dp)
        .testTag("btn_trakt_open_activate")
    ) {
      Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text("فتح رابط التفعيل (trakt.tv/activate)", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Black)
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = onCopyCode,
        colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 48.dp)
          .testTag("btn_trakt_copy_code")
      ) {
        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CinemaAccent, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("نسخ الكود", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
      }

      Button(
        onClick = onRefreshCode,
        enabled = !isRefreshing,
        colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 48.dp)
          .testTag("btn_trakt_refresh_code")
      ) {
        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("كود جديد", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      CircularProgressIndicator(
        color = CinemaSuccess,
        strokeWidth = 2.5.dp,
        modifier = Modifier.size(17.dp)
      )
      val mins = remainingSeconds / 60
      val secs = remainingSeconds % 60
      Text(
        text = "بانتظار موافقتك على Trakt... (%02d:%02d)".format(Locale.US, mins, secs),
        color = CinemaSuccess,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Composable
private fun LocalQrCodeCanvas(
  content: String,
  size: Dp
) {
  val matrix = remember(content) {
    try {
      val hints = mapOf(EncodeHintType.MARGIN to 1)
      QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 33, 33, hints)
    } catch (_: Throwable) {
      null
    }
  }
  Canvas(modifier = Modifier.size(size)) {
    drawRect(color = Color.White)
    val m = matrix ?: return@Canvas
    val cols = m.width.coerceAtLeast(1)
    val rows = m.height.coerceAtLeast(1)
    val cellW = this.size.width / cols
    val cellH = this.size.height / rows
    for (y in 0 until rows) {
      for (x in 0 until cols) {
        if (m.get(x, y)) {
          drawRect(
            color = Color(0xFF0A0E17),
            topLeft = Offset(x * cellW, y * cellH),
            size = Size(cellW + 0.5f, cellH + 0.5f)
          )
        }
      }
    }
  }
}

