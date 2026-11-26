package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.AppDisplayMode
import com.example.data.TraktSyncService
import com.example.data.TvPreferencesRepository
import com.example.model.ControlMode
import com.example.model.StreamingPortal
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.ClipboxWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedSettingsDialog(
  repository: TvPreferencesRepository,
  displayMode: AppDisplayMode,
  controlMode: ControlMode,
  onOpenDisplayMode: () -> Unit,
  onToggleControlMode: () -> Unit,
  onOpenAdBlock: () -> Unit,
  onOpenUpdate: () -> Unit,
  onOpenDiagnostics: () -> Unit,
  onAdminTraktKeysUpdated: () -> Unit = {},
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val blockedAdsCount by repository.blockedAdsCountFlow.collectAsState()
  val traktKeysVersion by repository.traktKeysVersionFlow.collectAsState()

  // Admin lock state (password: TvPreferencesRepository.ADMIN_PASSWORD)
  var isAdminUnlocked by remember { mutableStateOf(false) }
  var showPasswordBox by remember { mutableStateOf(false) }
  var passwordInput by remember { mutableStateOf("") }
  var passwordError by remember { mutableStateOf(false) }
  var showPasswordText by remember { mutableStateOf(false) }

  // Admin sub-tabs: 0 = Portals & URLs, 1 = Trakt Developer Keys
  var adminTab by remember { mutableIntStateOf(0) }

  // Portal edit state
  var selectedPortalForEdit by remember { mutableStateOf<StreamingPortal?>(null) }
  var editNameAr by remember { mutableStateOf("") }
  var editNameEn by remember { mutableStateOf("") }
  var editHomeUrl by remember { mutableStateOf("") }
  var editSearchUrl by remember { mutableStateOf("") }
  var editEnabled by remember { mutableStateOf(true) }

  // Trakt Admin Override Keys state
  var traktClientIdInput by remember(traktKeysVersion) {
    mutableStateOf(repository.getStoredAdminTraktClientId())
  }
  var traktClientSecretInput by remember(traktKeysVersion) {
    mutableStateOf(repository.getStoredAdminTraktClientSecret())
  }
  var showTraktSecret by remember { mutableStateOf(false) }

  val hasBuiltInSecrets = remember(traktKeysVersion) { repository.hasBuiltInTraktSecrets() }
  val hasAdminOverride = remember(traktKeysVersion) { repository.hasAdminTraktKeysOverride() }

  fun copyText(label: String, value: String) {
    try {
      val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
      cm?.setPrimaryClip(ClipData.newPlainText(label, value))
      Toast.makeText(context, "تم نسخ $label ✓", Toast.LENGTH_SHORT).show()
    } catch (_: Throwable) {}
  }

  fun openOfficialTraktCreateApp() {
    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TraktSyncService.TRAKT_CREATE_APP_URL)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (_: Throwable) {
      copyText("رابط Trakt", TraktSyncService.TRAKT_CREATE_APP_URL)
    }
  }

  fun verifyAdminAccess() {
    if (TvPreferencesRepository.verifyAdminPassword(passwordInput)) {
      passwordInput = ""
      passwordError = false
      showPasswordBox = false
      isAdminUnlocked = true
      Toast.makeText(context, "تم فتح إعدادات المسؤول المتقدمة ✓", Toast.LENGTH_SHORT).show()
    } else {
      passwordError = true
    }
  }

  BasicAlertDialog(
    onDismissRequest = {
      if (selectedPortalForEdit != null) {
        selectedPortalForEdit = null
      } else {
        onDismiss()
      }
    },
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      Surface(
        color = CinemaSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.5.dp, CinemaBorder),
        modifier = Modifier
          .fillMaxWidth(0.93f)
          .padding(vertical = 10.dp)
          .testTag("unified_settings_dialog")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Top Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .size(42.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(CinemaPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Settings,
                  contentDescription = null,
                  tint = CinemaAccent,
                  modifier = Modifier.size(24.dp)
                )
              }
              Column {
                Text(
                  text = "إعدادات Asser TV",
                  color = Color.White,
                  fontSize = 18.5.sp,
                  fontWeight = FontWeight.Black
                )
                Text(
                  text = "جميع إعدادات العرض والحماية والإدارة في مكان واحد",
                  color = Color.LightGray,
                  fontSize = 12.5.sp
                )
              }
            }

            IconButton(
              onClick = onDismiss,
              modifier = Modifier.testTag("btn_close_unified_settings")
            ) {
              Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.LightGray)
            }
          }

          if (selectedPortalForEdit != null) {
            // =================================================================
            // PORTAL LINK EDITOR SUB-VIEW
            // =================================================================
            val portal = selectedPortalForEdit!!
            val isCustom = portal == StreamingPortal.PORTAL_5

            Text(
              text = "تعديل ${portal.displayNameArabic}",
              color = CinemaAccent,
              fontSize = 17.sp,
              fontWeight = FontWeight.ExtraBold
            )

            if (isCustom) {
              OutlinedTextField(
                value = editNameAr,
                onValueChange = { editNameAr = it },
                label = { Text("الاسم بالعربية", fontSize = 13.sp) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = CinemaAccent,
                  unfocusedBorderColor = CinemaBorder,
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White,
                  focusedContainerColor = CinemaDarkBackground,
                  unfocusedContainerColor = CinemaDarkBackground
                ),
                modifier = Modifier.fillMaxWidth()
              )
              OutlinedTextField(
                value = editNameEn,
                onValueChange = { editNameEn = it },
                label = { Text("الاسم بالإنجليزية", fontSize = 13.sp) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = CinemaAccent,
                  unfocusedBorderColor = CinemaBorder,
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White,
                  focusedContainerColor = CinemaDarkBackground,
                  unfocusedContainerColor = CinemaDarkBackground
                ),
                modifier = Modifier.fillMaxWidth()
              )
            }

            OutlinedTextField(
              value = editHomeUrl,
              onValueChange = { editHomeUrl = it },
              label = { Text("الرابط الرئيسي", fontSize = 13.sp) },
              singleLine = true,
              textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CinemaAccent,
                unfocusedBorderColor = CinemaBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = CinemaDarkBackground,
                unfocusedContainerColor = CinemaDarkBackground
              ),
              modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
              value = editSearchUrl,
              onValueChange = { editSearchUrl = it },
              label = { Text("رابط البحث", fontSize = 13.sp) },
              singleLine = true,
              textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CinemaAccent,
                unfocusedBorderColor = CinemaBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = CinemaDarkBackground,
                unfocusedContainerColor = CinemaDarkBackground
              ),
              modifier = Modifier.fillMaxWidth()
            )

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
                Text("تفعيل هذه البوابة", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Button(
                onClick = { selectedPortalForEdit = null },
                colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
              ) {
                Text("رجوع", color = Color.White, fontWeight = FontWeight.Bold)
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
                    Toast.makeText(context, "تم حفظ التعديلات ✓", Toast.LENGTH_SHORT).show()
                    selectedPortalForEdit = null
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CinemaAccent),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
              ) {
                Text("حفظ", color = Color.Black, fontWeight = FontWeight.ExtraBold)
              }
            }
          } else {
            // =================================================================
            // SECTION 1: GENERAL USER SETTINGS
            // =================================================================
            Text(
              text = "إعدادات عامة",
              color = CinemaAccent,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.ExtraBold
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              SettingsActionRow(
                icon = if (displayMode == AppDisplayMode.TV) Icons.Default.Tv else Icons.Default.Smartphone,
                iconTint = Color(0xFF38BDF8),
                title = "وضع العرض",
                subtitle = if (displayMode == AppDisplayMode.TV) "الحالي: شاشة التلفاز (Android TV)" else "الحالي: الهاتف المحمول",
                onClick = {
                  onDismiss()
                  onOpenDisplayMode()
                },
                testTag = "settings_row_display_mode"
              )

              SettingsActionRow(
                icon = Icons.Default.Mouse,
                iconTint = CinemaAccent,
                title = "وضع التحكم بالريموت",
                subtitle = if (controlMode == ControlMode.VIRTUAL_MOUSE) "الحالي: الماوس الافتراضي" else "الحالي: أسهم التنقل (D-Pad)",
                onClick = onToggleControlMode,
                testTag = "settings_row_control_mode"
              )

              SettingsActionRow(
                icon = Icons.Default.Shield,
                iconTint = CinemaSuccess,
                title = "الحماية ومانع الإعلانات والنوافذ المنبثقة",
                subtitle = if (blockedAdsCount > 0) "تم حجب $blockedAdsCount إعلان ونافذة منبثقة" else "حماية AdGuard ومنع النوافذ المنبثقة",
                onClick = {
                  onDismiss()
                  onOpenAdBlock()
                },
                testTag = "settings_row_adblock"
              )

              SettingsActionRow(
                icon = Icons.Default.SystemUpdate,
                iconTint = CinemaSuccess,
                title = "تحديثات التطبيق",
                subtitle = "التحقق من وجود إصدار أحدث وتنزيله مباشرة",
                onClick = {
                  onDismiss()
                  onOpenUpdate()
                },
                testTag = "settings_row_update"
              )

              SettingsActionRow(
                icon = Icons.Default.BugReport,
                iconTint = Color(0xFFF59E0B),
                title = "سجل الأعطال والتشخيص",
                subtitle = "عرض ونسخ تقارير الأداء والذاكرة",
                onClick = {
                  onDismiss()
                  onOpenDiagnostics()
                },
                testTag = "settings_row_diagnostics"
              )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // =================================================================
            // SECTION 2: PASSWORD-PROTECTED ADMIN AREA (ahayabasharya89a)
            // =================================================================
            Surface(
              color = if (isAdminUnlocked) Color(0xFF101926) else Color(0xFF171316),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(
                1.5.dp,
                if (isAdminUnlocked) CinemaAccent.copy(alpha = 0.7f) else CinemaPrimary.copy(alpha = 0.55f)
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isAdminUnlocked) {
                      showPasswordBox = !showPasswordBox
                      passwordError = false
                    }
                    .testTag("settings_admin_lock_header"),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                  ) {
                    Icon(
                      imageVector = if (isAdminUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                      contentDescription = null,
                      tint = if (isAdminUnlocked) CinemaSuccess else CinemaAccent,
                      modifier = Modifier.size(22.dp)
                    )
                    Column {
                      Text(
                        text = "إعدادات المسؤول المتقدمة",
                        color = Color.White,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Black
                      )
                      Text(
                        text = if (isAdminUnlocked) {
                          "تم التحقق — تعديل روابط البوابات ومفاتيح Trakt"
                        } else {
                          "محمية بكلمة مرور المسؤول (تعديل الروابط ومفاتيح Trakt)"
                        },
                        color = if (isAdminUnlocked) CinemaSuccess else Color.LightGray,
                        fontSize = 12.sp
                      )
                    }
                  }

                  if (!isAdminUnlocked) {
                    Surface(
                      onClick = {
                        showPasswordBox = !showPasswordBox
                        passwordError = false
                      },
                      color = CinemaPrimary,
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier
                        .heightIn(min = 38.dp)
                        .testTag("btn_unlock_admin_settings")
                    ) {
                      Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                      ) {
                        Text("فتح", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                      }
                    }
                  }
                }

                // Inline Password Verification Box
                AnimatedVisibility(visible = showPasswordBox && !isAdminUnlocked) {
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                      value = passwordInput,
                      onValueChange = {
                        passwordInput = it
                        passwordError = false
                      },
                      label = { Text("كلمة مرور المسؤول", fontSize = 13.sp) },
                      singleLine = true,
                      visualTransformation = if (showPasswordText) VisualTransformation.None else PasswordVisualTransformation(),
                      trailingIcon = {
                        IconButton(onClick = { showPasswordText = !showPasswordText }) {
                          Icon(
                            imageVector = if (showPasswordText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPasswordText) "إخفاء" else "إظهار",
                            tint = Color.LightGray
                          )
                        }
                      },
                      keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                      ),
                      keyboardActions = KeyboardActions(onDone = { verifyAdminAccess() }),
                      isError = passwordError,
                      colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CinemaAccent,
                        unfocusedBorderColor = CinemaBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = CinemaDarkBackground,
                        unfocusedContainerColor = CinemaDarkBackground
                      ),
                      shape = RoundedCornerShape(12.dp),
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_admin_settings_password")
                    )

                    if (passwordError) {
                      Text(
                        text = "كلمة المرور غير صحيحة",
                        color = Color(0xFFFCA5A5),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }

                    Button(
                      onClick = { verifyAdminAccess() },
                      colors = ButtonDefaults.buttonColors(containerColor = CinemaAccent),
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .testTag("btn_verify_admin_settings_password")
                    ) {
                      Text("تأكيد كلمة المرور", color = Color.Black, fontSize = 14.5.sp, fontWeight = FontWeight.Black)
                    }
                  }
                }

                // Unlocked Admin Panel: Portals & Links + Trakt Developer Keys
                AnimatedVisibility(visible = isAdminUnlocked) {
                  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Surface(
                        onClick = { adminTab = 0 },
                        color = if (adminTab == 0) CinemaPrimary else CinemaDarkBackground,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (adminTab == 0) CinemaPrimary else CinemaBorder),
                        modifier = Modifier
                          .weight(1f)
                          .heightIn(min = 44.dp)
                          .testTag("tab_admin_portals")
                      ) {
                        Row(
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                          horizontalArrangement = Arrangement.Center,
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          Icon(Icons.Default.Language, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(5.dp))
                          Text("البوابات والروابط", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                        }
                      }

                      Surface(
                        onClick = { adminTab = 1 },
                        color = if (adminTab == 1) Color(0xFFED1C24) else CinemaDarkBackground,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (adminTab == 1) Color(0xFFED1C24) else CinemaBorder),
                        modifier = Modifier
                          .weight(1f)
                          .heightIn(min = 44.dp)
                          .testTag("tab_admin_trakt_keys")
                      ) {
                        Row(
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                          horizontalArrangement = Arrangement.Center,
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          Icon(Icons.Default.Key, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(5.dp))
                          Text("مفاتيح Trakt", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                        }
                      }
                    }

                    if (adminTab == 0) {
                      // Admin Tab 0: Portals & Links Management (no "المصادر" word)
                      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                                  shape = RoundedCornerShape(8.dp),
                                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                  modifier = Modifier.heightIn(min = 38.dp)
                                ) {
                                  Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                  Spacer(modifier = Modifier.width(4.dp))
                                  Text("تعديل", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                              }

                              Spacer(modifier = Modifier.height(4.dp))
                              Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                              ) {
                                Text(
                                  text = if (portal.homeUrl.isNotBlank()) portal.homeUrl else "غير مفعل",
                                  color = if (portal.isEnabled) CinemaAccent else Color.Gray,
                                  fontSize = 12.5.sp,
                                  maxLines = 1,
                                  overflow = TextOverflow.Ellipsis,
                                  modifier = Modifier.weight(1f)
                                )
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
                              }
                            }
                          }
                        }
                      }
                    } else {
                      // Admin Tab 1: Trakt Developer Keys (Secrets status + optional Admin Override)
                      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                          color = if (hasBuiltInSecrets || hasAdminOverride) {
                            CinemaSuccess.copy(alpha = 0.14f)
                          } else {
                            Color(0x26F59E0B)
                          },
                          shape = RoundedCornerShape(12.dp),
                          border = BorderStroke(
                            1.dp,
                            if (hasBuiltInSecrets || hasAdminOverride) CinemaSuccess else CinemaAccent
                          ),
                          modifier = Modifier.fillMaxWidth()
                        ) {
                          Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                          ) {
                            Icon(
                              imageVector = if (hasBuiltInSecrets || hasAdminOverride) Icons.Default.CheckCircle else Icons.Default.Key,
                              contentDescription = null,
                              tint = if (hasBuiltInSecrets || hasAdminOverride) CinemaSuccess else CinemaAccent,
                              modifier = Modifier.size(20.dp)
                            )
                            Text(
                              text = when {
                                hasAdminOverride -> "تعمل حالياً بمفتاح المسؤول المخصص (يتجاوز Secrets المدمج)."
                                hasBuiltInSecrets -> "مفتاح Trakt المدمج عبر Secrets مفعّل وجاهز لجميع المستخدمين ✓"
                                else -> "لم يتم ضبط TRAKT_CLIENT_ID في Secrets بعد. يمكنك إدخاله هنا مؤقتاً أو إضافته في Secrets."
                              },
                              color = Color.White,
                              fontSize = 12.5.sp,
                              lineHeight = 18.sp
                            )
                          }
                        }

                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                          Surface(
                            onClick = { openOfficialTraktCreateApp() },
                            color = Color(0x1F38BDF8),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.55f)),
                            modifier = Modifier
                              .weight(1f)
                              .heightIn(min = 42.dp)
                          ) {
                            Row(
                              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                              verticalAlignment = Alignment.CenterVertically,
                              horizontalArrangement = Arrangement.Center
                            ) {
                              Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                              Spacer(modifier = Modifier.width(5.dp))
                              Text("إنشاء تطبيق Trakt", color = Color(0xFF38BDF8), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                            }
                          }

                          Surface(
                            onClick = { copyText("Redirect URI", TraktSyncService.REDIRECT_URI_OOB) },
                            color = CinemaDarkBackground,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.5f)),
                            modifier = Modifier
                              .weight(1f)
                              .heightIn(min = 42.dp)
                          ) {
                            Row(
                              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                              verticalAlignment = Alignment.CenterVertically,
                              horizontalArrangement = Arrangement.Center
                            ) {
                              Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CinemaAccent, modifier = Modifier.size(15.dp))
                              Spacer(modifier = Modifier.width(5.dp))
                              Text("نسخ Redirect URI", color = CinemaAccent, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                            }
                          }
                        }

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                          Text(
                            text = "Redirect URI: ${TraktSyncService.REDIRECT_URI_OOB}",
                            color = Color.LightGray,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth()
                          )
                        }

                        OutlinedTextField(
                          value = traktClientIdInput,
                          onValueChange = { traktClientIdInput = it },
                          label = { Text("Trakt Client ID (اختياري للمسؤول)", fontSize = 12.5.sp) },
                          singleLine = true,
                          colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CinemaAccent,
                            unfocusedBorderColor = CinemaBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = CinemaDarkBackground,
                            unfocusedContainerColor = CinemaDarkBackground
                          ),
                          shape = RoundedCornerShape(12.dp),
                          modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_input_trakt_client_id")
                        )

                        OutlinedTextField(
                          value = traktClientSecretInput,
                          onValueChange = { traktClientSecretInput = it },
                          label = { Text("Trakt Client Secret (اختياري — اتركه فارغاً إذا لا يوجد)", fontSize = 12.sp) },
                          singleLine = true,
                          visualTransformation = if (showTraktSecret) VisualTransformation.None else PasswordVisualTransformation(),
                          trailingIcon = {
                            IconButton(onClick = { showTraktSecret = !showTraktSecret }) {
                              Icon(
                                imageVector = if (showTraktSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showTraktSecret) "إخفاء" else "إظهار",
                                tint = Color.LightGray
                              )
                            }
                          },
                          colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CinemaAccent,
                            unfocusedBorderColor = CinemaBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = CinemaDarkBackground,
                            unfocusedContainerColor = CinemaDarkBackground
                          ),
                          shape = RoundedCornerShape(12.dp),
                          modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_input_trakt_client_secret")
                        )

                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                          Button(
                            onClick = {
                              val cId = traktClientIdInput.trim()
                              val cSec = traktClientSecretInput.trim()
                              if (cId.isBlank()) {
                                Toast.makeText(context, "يرجى إدخال Trakt Client ID أولاً", Toast.LENGTH_SHORT).show()
                                return@Button
                              }
                              repository.saveTraktAppKeys(cId, cSec)
                              onAdminTraktKeysUpdated()
                              Toast.makeText(context, "تم حفظ مفتاح Trakt للمسؤول ✓", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaSuccess),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                              .weight(1f)
                              .heightIn(min = 46.dp)
                              .testTag("btn_admin_save_trakt_keys")
                          ) {
                            Text("حفظ المفاتيح", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Black)
                          }

                          if (hasAdminOverride) {
                            Button(
                              onClick = {
                                repository.clearAdminTraktAppKeys()
                                traktClientIdInput = ""
                                traktClientSecretInput = ""
                                onAdminTraktKeysUpdated()
                                Toast.makeText(context, "تمت استعادة مفاتيح Secrets الافتراضية", Toast.LENGTH_SHORT).show()
                              },
                              colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
                              shape = RoundedCornerShape(10.dp),
                              modifier = Modifier
                                .heightIn(min = 46.dp)
                                .testTag("btn_admin_clear_trakt_keys")
                            ) {
                              Text("استعادة الافتراضي", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }

            Button(
              onClick = onDismiss,
              colors = ButtonDefaults.buttonColors(
                containerColor = ClipboxWhite,
                contentColor = Color.Black
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("btn_unified_settings_done")
            ) {
              Text("إغلاق", fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsActionRow(
  icon: ImageVector,
  iconTint: Color,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  testTag: String
) {
  Surface(
    onClick = onClick,
    color = CinemaDarkBackground,
    shape = RoundedCornerShape(13.dp),
    border = BorderStroke(1.dp, CinemaBorder),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(iconTint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(20.dp)
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          color = Color.White,
          fontSize = 14.5.sp,
          fontWeight = FontWeight.ExtraBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = subtitle,
          color = Color.LightGray,
          fontSize = 12.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
