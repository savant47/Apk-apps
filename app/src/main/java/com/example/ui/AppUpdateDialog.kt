package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.BuildConfig
import com.example.data.AppUpdateManager
import com.example.data.TvPreferencesRepository
import com.example.data.UpdateDownloadState
import com.example.data.UpdateReleaseInfo
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaFocusGlow
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AppUpdateDialog(
  repository: TvPreferencesRepository,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val updateManager = remember { AppUpdateManager(context) }

  val currentVersion = BuildConfig.VERSION_NAME

  var repoInput by remember { mutableStateOf(repository.getGitHubRepo()) }
  var tokenInput by remember { mutableStateOf(repository.getGitHubToken()) }
  var isEditingRepo by remember { mutableStateOf(false) }

  var updateState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
  var downloadProgress by remember { mutableFloatStateOf(0f) }
  var downloadedSizeText by remember { mutableStateOf("") }
  var totalSizeText by remember { mutableStateOf("") }
  var releaseInfo by remember { mutableStateOf<UpdateReleaseInfo?>(null) }

  fun startCheck() {
    updateState = UpdateDownloadState.Checking
    scope.launch {
      val res = updateManager.checkForUpdates(repoInput, currentVersion, tokenInput)
      res.onSuccess { info ->
        releaseInfo = info
        if (info.isNewer) {
          updateState = UpdateDownloadState.UpdateAvailable(info)
        } else {
          updateState = UpdateDownloadState.UpToDate(currentVersion, info)
        }
      }.onFailure { err ->
        updateState = UpdateDownloadState.Error(err.localizedMessage ?: "فشل فحص التحديثات")
      }
    }
  }

  fun startDownload(info: UpdateReleaseInfo) {
    updateState = UpdateDownloadState.Downloading(0f, "0 MB", "...")
    scope.launch {
      val res = updateManager.downloadApk(
        downloadUrl = info.apkDownloadUrl,
        apiAssetUrl = info.apkApiAssetUrl,
        githubToken = tokenInput
      ) { prog, down, tot ->
        downloadProgress = prog
        downloadedSizeText = down
        totalSizeText = tot
        updateState = UpdateDownloadState.Downloading(prog, down, tot)
      }
      res.onSuccess { file ->
        updateState = UpdateDownloadState.ReadyToInstall(file, info)
      }.onFailure { err ->
        updateState = UpdateDownloadState.Error(err.localizedMessage ?: "فشل تنزيل ملف التحديث")
      }
    }
  }

  fun installApk(file: File) {
    val res = updateManager.installApk(file)
    res.onFailure { err ->
      Toast.makeText(context, err.localizedMessage, Toast.LENGTH_LONG).show()
    }
  }

  // Automatically check GitHub Releases when dialog opens
  LaunchedEffect(Unit) {
    startCheck()
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = CinemaDarkBackground,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, CinemaBorder),
        shadowElevation = 24.dp,
        modifier = modifier
          .fillMaxWidth(0.92f)
          .widthIn(max = 520.dp)
          .padding(vertical = 16.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          // 1. Header: Icon, Title, and Close Button
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(CinemaSuccess.copy(alpha = 0.15f))
                .border(1.dp, CinemaSuccess, CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = CinemaSuccess,
                modifier = Modifier.size(22.dp)
              )
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "التحديث الداخلي عبر GitHub Releases",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "تنزيل وتثبيت أحدث نسخة APK من قسم النسخ (Releases) مباشرة",
                color = Color.LightGray,
                fontSize = 11.sp
              )
            }

            IconButton(
              onClick = onDismiss,
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(CinemaSurface)
                .testTag("btn_close_update_dialog")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "إغلاق",
                tint = Color.LightGray,
                modifier = Modifier.size(18.dp)
              )
            }
          }

          // 2. Version & Safety Info Box
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = CinemaSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = CinemaSuccess,
                modifier = Modifier.size(24.dp)
              )
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = "الإصدار المثبت حالياً: v$currentVersion",
                  color = Color.White,
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "سجل المشاهدة والمفضلات محمية ومحفوظة بالكامل 🛡️",
                  color = CinemaSuccess,
                  fontSize = 11.5.sp
                )
              }
            }
          }

          // 3. GitHub Repository Configuration Box
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = CinemaSurfaceVariant.copy(alpha = 0.45f),
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "مستودع GitHub Releases:",
                  color = Color.LightGray,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium
                )
                Text(
                  text = if (isEditingRepo) "إلغاء التعديل" else "تعديل المصدر / Token ⚙️",
                  color = CinemaAccent,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier
                    .clickable { isEditingRepo = !isEditingRepo }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                )
              }

              if (isEditingRepo) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("اسم المستودع (مثل: savant47/Apk-apps)", fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                      focusedTextColor = Color.White,
                      unfocusedTextColor = Color.White,
                      focusedBorderColor = CinemaAccent,
                      unfocusedBorderColor = CinemaBorder
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                  )
                  OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("GitHub Token (اختياري إذا كان المستودع Private 🔒)", fontSize = 10.5.sp) },
                    placeholder = { Text("ghp_xxxxxxxxxxxx", fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                      focusedTextColor = Color.White,
                      unfocusedTextColor = Color.White,
                      focusedBorderColor = CinemaAccent,
                      unfocusedBorderColor = CinemaBorder
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                  )
                  Button(
                    onClick = {
                      repository.setGitHubRepo(repoInput)
                      repository.setGitHubToken(tokenInput)
                      isEditingRepo = false
                      Toast.makeText(context, "تم حفظ إعدادات التحديث من GitHub", Toast.LENGTH_SHORT).show()
                      startCheck()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaAccent),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                  ) {
                    Text("حفظ وفحص التحديثات", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  }
                }
              } else {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = CinemaDarkBackground,
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(
                    text = "$repoInput (Releases)",
                    color = CinemaAccent,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                  )
                }
              }
            }
          }

          // 4. Update Status and Action Button
          when (val state = updateState) {
            is UpdateDownloadState.Idle -> {
              TvDialogButton(
                text = "فحص التحديثات الآن 🔍",
                icon = Icons.Default.Refresh,
                containerColor = CinemaPrimary,
                contentColor = Color.White,
                onClick = { startCheck() },
                testTag = "btn_start_check_update"
              )
            }

            is UpdateDownloadState.Checking -> {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = CinemaSurface,
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center
                ) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = CinemaAccent,
                    strokeWidth = 2.5.dp
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = "جاري الاتصال بـ GitHub Releases وفحص أحدث إصدار...",
                    color = Color.White,
                    fontSize = 12.5.sp
                  )
                }
              }
            }

            is UpdateDownloadState.UpToDate -> {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = CinemaSuccess.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSuccess.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = CinemaSuccess,
                    modifier = Modifier.size(32.dp)
                  )
                  Text(
                    text = "تطبيقك محدث بالكامل (v${state.currentVersion})",
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "أحدث إصدار على GitHub Releases: ${state.latestInfo?.tagName ?: state.currentVersion}",
                    color = Color.LightGray,
                    fontSize = 11.5.sp
                  )
                  if (state.latestInfo != null) {
                    TvDialogButton(
                      text = "إعادة تنزيل وتثبيت النسخة (${state.latestInfo.tagName}) 🚀",
                      icon = Icons.Default.Download,
                      containerColor = CinemaSuccess,
                      contentColor = Color.Black,
                      onClick = { startDownload(state.latestInfo) },
                      testTag = "btn_force_download_update"
                    )
                  }
                  OutlinedButton(
                    onClick = { startCheck() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.height(36.dp)
                  ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إعادة الفحص", fontSize = 11.5.sp)
                  }
                }
              }
            }

            is UpdateDownloadState.UpdateAvailable -> {
              val info = state.info
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = CinemaSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaAccent),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = CinemaAccent,
                        modifier = Modifier.size(20.dp)
                      )
                      Text(
                        text = "إصدار جديد متوفر: ${info.tagName}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                    if (info.fileSizeBytes > 0) {
                      val sizeMB = String.format("%.1f MB", info.fileSizeBytes / (1024f * 1024f))
                      Text(
                        text = sizeMB,
                        color = CinemaAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }

                  if (info.releaseNotes.isNotBlank()) {
                    Surface(
                      shape = RoundedCornerShape(8.dp),
                      color = CinemaDarkBackground,
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Text(
                        text = info.releaseNotes,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(10.dp)
                      )
                    }
                  }

                  TvDialogButton(
                    text = "تنزيل وتثبيت التحديث الآن 🚀",
                    icon = Icons.Default.Download,
                    containerColor = CinemaSuccess,
                    contentColor = Color.Black,
                    onClick = { startDownload(info) },
                    testTag = "btn_download_update"
                  )
                }
              }
            }

            is UpdateDownloadState.Downloading -> {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = CinemaSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "جاري تنزيل ملف التحديث من GitHub Releases...",
                      color = Color.White,
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.Bold,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f)
                    )
                    Surface(
                      color = CinemaAccent.copy(alpha = 0.16f),
                      shape = RoundedCornerShape(8.dp),
                      border = androidx.compose.foundation.BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.5f))
                    ) {
                      Text(
                        text = "${(downloadProgress * 100).toInt()}%",
                        color = CinemaAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                      )
                    }
                  }

                  LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(7.dp)
                      .clip(RoundedCornerShape(4.dp)),
                    color = CinemaAccent,
                    trackColor = CinemaSurfaceVariant
                  )

                  val cleanDownloaded = downloadedSizeText.replace("MB", "", ignoreCase = true).trim()
                  val cleanTotal = totalSizeText.replace("MB", "", ignoreCase = true).trim()
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "تم تنزيل: $cleanDownloaded من $cleanTotal MB",
                      color = Color.LightGray,
                      fontSize = 11.5.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = "APK",
                      color = CinemaSuccess,
                      fontSize = 10.5.sp,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      softWrap = false
                    )
                  }
                }
              }
            }

            is UpdateDownloadState.ReadyToInstall -> {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = CinemaSuccess.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CinemaSuccess),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = CinemaSuccess,
                    modifier = Modifier.size(36.dp)
                  )
                  Text(
                    text = "اكتمل التنزيل بنجاح! جاهز للتثبيت 🎬",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "اضغط الزر بالأسفل لفتح مثبت الأندرويد. سجل المشاهدة والمفضلات لن تحذف.",
                    color = Color.LightGray,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center
                  )

                  TvDialogButton(
                    text = "تثبيت التحديث الآن 🚀",
                    icon = Icons.Default.SystemUpdate,
                    containerColor = CinemaSuccess,
                    contentColor = Color.Black,
                    onClick = { installApk(state.apkFile) },
                    testTag = "btn_install_apk"
                  )
                }
              }
            }

            is UpdateDownloadState.Error -> {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF331414),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text(
                    text = "⚠️ تعذر إكمال العملية:",
                    color = Color(0xFFFF5252),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = state.message,
                    color = Color.White,
                    fontSize = 11.5.sp
                  )
                  TvDialogButton(
                    text = "إعادة المحاولة",
                    icon = Icons.Default.Refresh,
                    containerColor = CinemaSurface,
                    contentColor = Color.White,
                    onClick = { startCheck() },
                    testTag = "btn_retry_check"
                  )
                }
              }
            }
          }

          // 5. Local Backup & Restore Box
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = CinemaSurfaceVariant.copy(alpha = 0.3f),
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = "💾 نسخة احتياطية محلية لبياناتك:",
                  color = Color.White,
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "حفظ نسخة من سجل المشاهدة والمفضلات في ذاكرة الجهاز واسترجاعها في أي وقت",
                  color = Color.LightGray,
                  fontSize = 10.5.sp
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OutlinedButton(
                  onClick = {
                    val json = repository.exportUserDataJson()
                    try {
                      val backupFile = File(context.filesDir, "savant_tv_backup.json")
                      backupFile.writeText(json)
                      Toast.makeText(context, "✅ تم حفظ نسخة احتياطية بنجاح!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                      Toast.makeText(context, "فشل حفظ النسخة: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                  },
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = CinemaAccent),
                  modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                ) {
                  Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("حفظ نسخة", fontSize = 11.5.sp)
                }

                OutlinedButton(
                  onClick = {
                    try {
                      val backupFile = File(context.filesDir, "savant_tv_backup.json")
                      if (backupFile.exists()) {
                        val success = repository.restoreUserDataJson(backupFile.readText())
                        if (success) {
                          Toast.makeText(context, "✅ تم استعادة بياناتك بنجاح!", Toast.LENGTH_SHORT).show()
                        } else {
                          Toast.makeText(context, "الملف غير صالح", Toast.LENGTH_SHORT).show()
                        }
                      } else {
                        Toast.makeText(context, "لم يتم العثور على نسخة سابقة", Toast.LENGTH_SHORT).show()
                      }
                    } catch (e: Exception) {
                      Toast.makeText(context, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                  },
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                  modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                ) {
                  Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("استرجاع", fontSize = 11.5.sp)
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
private fun TvDialogButton(
  text: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  containerColor: Color,
  contentColor: Color,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Button(
    onClick = onClick,
    interactionSource = interactionSource,
    colors = ButtonDefaults.buttonColors(
      containerColor = if (isFocused) CinemaFocusGlow else containerColor,
      contentColor = if (isFocused) Color.Black else contentColor
    ),
    shape = RoundedCornerShape(10.dp),
    modifier = modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 46.dp)
      .scale(if (isFocused) 1.02f else 1.0f)
      .border(
        width = if (isFocused) 2.dp else 0.dp,
        color = if (isFocused) Color.White else Color.Transparent,
        shape = RoundedCornerShape(10.dp)
      )
      .focusable(interactionSource = interactionSource)
      .testTag(testTag)
  ) {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = text,
      fontSize = 13.sp,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center
    )
  }
}
