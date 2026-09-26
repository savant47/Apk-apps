package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalMaterial3Api::class)
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
  var isEditingRepo by remember { mutableStateOf(false) }

  var updateState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
  var downloadProgress by remember { mutableFloatStateOf(0f) }
  var downloadedSizeText by remember { mutableStateOf("") }
  var totalSizeText by remember { mutableStateOf("") }
  var downloadedApkFile by remember { mutableStateOf<File?>(null) }
  var releaseInfo by remember { mutableStateOf<UpdateReleaseInfo?>(null) }

  fun startCheck() {
    updateState = UpdateDownloadState.Checking
    scope.launch {
      val res = updateManager.checkForUpdates(repoInput, currentVersion)
      res.onSuccess { info ->
        releaseInfo = info
        if (info.isNewer) {
          updateState = UpdateDownloadState.UpdateAvailable(info)
        } else {
          updateState = UpdateDownloadState.UpToDate(currentVersion)
        }
      }.onFailure { err ->
        updateState = UpdateDownloadState.Error(err.localizedMessage ?: "فشل فحص التحديثات")
      }
    }
  }

  fun startDownload(url: String) {
    updateState = UpdateDownloadState.Downloading(0f, "0 MB", "...")
    scope.launch {
      val res = updateManager.downloadApk(url) { prog, down, tot ->
        downloadProgress = prog
        downloadedSizeText = down
        totalSizeText = tot
        updateState = UpdateDownloadState.Downloading(prog, down, tot)
      }
      res.onSuccess { file ->
        downloadedApkFile = file
        updateState = UpdateDownloadState.ReadyToInstall(file, releaseInfo)
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

  BasicAlertDialog(
    onDismissRequest = onDismiss,
    modifier = modifier.widthIn(max = 680.dp)
  ) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = CinemaDarkBackground,
      border = androidx.compose.foundation.BorderStroke(1.5.dp, CinemaBorder),
      shadowElevation = 24.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // 1. Header with App Icon, Title and Close Button
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(CinemaSuccess.copy(alpha = 0.15f))
                .border(1.dp, CinemaSuccess, CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = CinemaSuccess,
                modifier = Modifier.size(24.dp)
              )
            }
            Column {
              Text(
                text = "تحديث التطبيق المباشر (OTA Updater)",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "تثبيت النسخ الجديدة وحفظ سجل المشاهدة والمفضلات تلقائياً",
                color = Color.LightGray,
                fontSize = 12.sp
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("btn_close_update_dialog")
          ) {
            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.LightGray)
          }
        }

        // 2. Data Safety & Version Badge Box
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = CinemaSurface,
          border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = CinemaSuccess,
                modifier = Modifier.size(22.dp)
              )
              Column {
                Text(
                  text = "الإصدار الحالي المثبت: v$currentVersion",
                  color = Color.White,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "🛡️ سجل المشاهدة (History & Continue Watching) والمفضلات محمية ومحفوظة 100%",
                  color = CinemaSuccess,
                  fontSize = 11.5.sp
                )
              }
            }
          }
        }

        // 3. GitHub Repository / Update Source Configuration
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = CinemaSurfaceVariant.copy(alpha = 0.5f),
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
                text = "مصدر التحديثات (GitHub Repository):",
                color = Color.LightGray,
                fontSize = 12.sp
              )
              Text(
                text = if (isEditingRepo) "إلغاء التعديل" else "تعديل الرابط ⚙️",
                color = CinemaAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                  .clickable { isEditingRepo = !isEditingRepo }
                  .padding(4.dp)
              )
            }

            if (isEditingRepo) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OutlinedTextField(
                  value = repoInput,
                  onValueChange = { repoInput = it },
                  placeholder = { Text("username/repository أو رابط مباشر", fontSize = 12.sp) },
                  colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = CinemaAccent,
                    unfocusedBorderColor = CinemaBorder
                  ),
                  modifier = Modifier.weight(1f),
                  singleLine = true
                )
                Button(
                  onClick = {
                    repository.setGitHubRepo(repoInput)
                    isEditingRepo = false
                    Toast.makeText(context, "تم حفظ مصدر التحديث بنجاح", Toast.LENGTH_SHORT).show()
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = CinemaAccent)
                ) {
                  Text("حفظ", color = Color.Black, fontWeight = FontWeight.Bold)
                }
              }
            } else {
              Text(
                text = repoInput,
                color = CinemaAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }

        // 4. Update Status & Actions Section
        when (val state = updateState) {
          is UpdateDownloadState.Idle -> {
            TvDialogButton(
              text = "🔍 فحص التحديثات الآن (Check for Updates)",
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
                  .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(24.dp),
                  color = CinemaAccent,
                  strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                  text = "جاري الاتصال بـ GitHub وفحص أحدث إصدار متاح...",
                  color = Color.White,
                  fontSize = 13.sp
                )
              }
            }
          }

          is UpdateDownloadState.UpToDate -> {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = CinemaSuccess.copy(alpha = 0.1f),
              border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSuccess.copy(alpha = 0.4f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = CinemaSuccess,
                  modifier = Modifier.size(36.dp)
                )
                Text(
                  text = "تطبيقك محدث بالكامل! (v${state.currentVersion})",
                  color = Color.White,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "أنت تستخدم أحدث نسخة مع جميع تحسينات مشغلات الفيديو والأيقونات وسرعة التلفزيون.",
                  color = Color.LightGray,
                  fontSize = 12.sp,
                  textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                  onClick = { startCheck() },
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                  Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("إعادة الفحص")
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
                  .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.CloudDownload,
                      contentDescription = null,
                      tint = CinemaAccent,
                      modifier = Modifier.size(24.dp)
                    )
                    Text(
                      text = "تحديث جديد متوفر: ${info.tagName}",
                      color = Color.White,
                      fontSize = 16.sp,
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
                  Text(
                    text = "ملاحظات الإصدار الجديد:",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                  )
                  Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CinemaDarkBackground,
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Text(
                      text = info.releaseNotes,
                      color = Color.White,
                      fontSize = 12.sp,
                      modifier = Modifier.padding(10.dp)
                    )
                  }
                }

                TvDialogButton(
                  text = "🚀 تنزيل وتثبيت التحديث الآن",
                  icon = Icons.Default.Download,
                  containerColor = CinemaSuccess,
                  contentColor = Color.Black,
                  onClick = { startDownload(info.apkDownloadUrl) },
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
                  .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = "جاري تنزيل ملف التحديث...",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "${(downloadProgress * 100).toInt()}%",
                    color = CinemaAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                  )
                }

                LinearProgressIndicator(
                  progress = { downloadProgress },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                  color = CinemaAccent,
                  trackColor = CinemaSurfaceVariant
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = "تم تنزيل: $downloadedSizeText من $totalSizeText",
                    color = Color.LightGray,
                    fontSize = 11.sp
                  )
                  Text(
                    text = "يرجى الانتظار...",
                    color = Color.LightGray,
                    fontSize = 11.sp
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
                  .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = CinemaSuccess,
                  modifier = Modifier.size(40.dp)
                )
                Text(
                  text = "اكتمل التنزيل بنجاح! جاهز للتثبيت 🎬",
                  color = Color.White,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "اضغط على الزر بالأسفل لفتح مثبت الأندرويد واختيار (تثبيت / Install). بياناتك وسجل المشاهدة سيظلان كما هما تماماً.",
                  color = Color.LightGray,
                  fontSize = 12.sp,
                  textAlign = TextAlign.Center
                )

                TvDialogButton(
                  text = "تثبيت التحديث الآن 🚀 (Install APK)",
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
                  .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  text = "⚠️ تنبيه:",
                  color = Color(0xFFFF5252),
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = state.message,
                  color = Color.White,
                  fontSize = 12.5.sp
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

        // 5. Data Backup & Restore Quick Section (Peace of mind)
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = CinemaSurfaceVariant.copy(alpha = 0.3f),
          border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "نسخة احتياطية محلية إضافية:",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "يمكنك حفظ نسخة من بياناتك في ذاكرة التلفزيون في أي وقت",
                color = Color.LightGray,
                fontSize = 10.5.sp
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              OutlinedButton(
                onClick = {
                  val json = repository.exportUserDataJson()
                  try {
                    val backupFile = File(context.filesDir, "savant_tv_backup.json")
                    backupFile.writeText(json)
                    Toast.makeText(context, "✅ تم حفظ نسخة احتياطية محلية لبياناتك بنجاح!", Toast.LENGTH_SHORT).show()
                  } catch (e: Exception) {
                    Toast.makeText(context, "فشل حفظ النسخة: ${e.message}", Toast.LENGTH_SHORT).show()
                  }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CinemaAccent)
              ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("حفظ نسخة", fontSize = 11.sp)
              }

              OutlinedButton(
                onClick = {
                  try {
                    val backupFile = File(context.filesDir, "savant_tv_backup.json")
                    if (backupFile.exists()) {
                      val success = repository.restoreUserDataJson(backupFile.readText())
                      if (success) {
                        Toast.makeText(context, "✅ تم استعادة جميع بياناتك وسجل المشاهدة بنجاح!", Toast.LENGTH_SHORT).show()
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
              ) {
                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("استرجاع", fontSize = 11.sp)
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
      .height(48.dp)
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
    Text(text = text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
  }
}
