package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.data.AppDiagnosticsLogger
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.ClipboxWhite
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiagnosticsLogDialog(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val logs by AppDiagnosticsLogger.logsFlow.collectAsState()

  // Load any saved fatal crash report and SIGKILL breadcrumbs from filesDir on dialog open
  var lastCrashLog by remember { mutableStateOf(AppDiagnosticsLogger.loadLastCrashLog(context)) }
  var lastBreadcrumbs by remember { mutableStateOf(AppDiagnosticsLogger.loadLastBreadcrumbs(context)) }

  // QR Upload State (paste.rs)
  var isUploadingQr by remember { mutableStateOf(false) }
  var uploadedPasteUrl by remember { mutableStateOf<String?>(null) }
  var uploadQrError by remember { mutableStateOf<String?>(null) }

  fun copyReportToClipboard() {
    val report = AppDiagnosticsLogger.buildFullCopyableReport(context)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("AsserTV Diagnostics Log", report))
    Toast.makeText(
      context,
      "✅ تم نسخ تقرير الأعطال واللوج بالكامل! الصقه هنا مباشرة",
      Toast.LENGTH_LONG
    ).show()
  }

  fun shareLogFileForTv() {
    try {
      val logFile = AppDiagnosticsLogger.exportReportToCacheFile(context)
      val fileUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        logFile
      )
      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, fileUri)
        putExtra(Intent.EXTRA_SUBJECT, "Asser TV Diagnostics & Crash Log")
        putExtra(Intent.EXTRA_TITLE, "asser_tv_log.txt")
        clipData = ClipData.newRawUri("asser_tv_log.txt", fileUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val chooser = Intent.createChooser(shareIntent, "مشاركة ملف اللوج (Send Files to TV)").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(chooser)
    } catch (e: Exception) {
      Toast.makeText(
        context,
        "تعذر فتح المشاركة: ${e.localizedMessage ?: "لا يوجد تطبيق مشاركة مثبت"}",
        Toast.LENGTH_LONG
      ).show()
    }
  }

  fun uploadAndShowQrCode() {
    if (isUploadingQr) return
    isUploadingQr = true
    uploadQrError = null
    scope.launch {
      val fullReport = AppDiagnosticsLogger.buildFullCopyableReport(context)
      val result = AppDiagnosticsLogger.uploadReportToPasteRs(fullReport)
      isUploadingQr = false
      result.onSuccess { shortUrl ->
        uploadedPasteUrl = shortUrl
      }.onFailure { err ->
        uploadQrError = err.localizedMessage ?: "فشل رفع اللوج"
        Toast.makeText(context, uploadQrError, Toast.LENGTH_LONG).show()
      }
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = CinemaDarkBackground,
        border = BorderStroke(1.5.dp, CinemaBorder),
        shadowElevation = 24.dp,
        modifier = modifier
          .fillMaxWidth(0.95f)
          .widthIn(max = 600.dp)
          .fillMaxHeight(0.90f)
          .padding(vertical = 8.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // 1. Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(CinemaAccent.copy(alpha = 0.15f))
                  .border(1.dp, CinemaAccent, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.BugReport,
                  contentDescription = null,
                  tint = CinemaAccent,
                  modifier = Modifier.size(20.dp)
                )
              }
              Column {
                Text(
                  text = "سجل الأعطال",
                  color = Color.White,
                  fontSize = 18.sp,
                  fontWeight = FontWeight.ExtraBold
                )
                Text(
                  text = "نسخ أو إرسال اللوج عبر QR",
                  color = Color.LightGray,
                  fontSize = 13.sp
                )
              }
            }

            IconButton(
              onClick = onDismiss,
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(CinemaSurface)
                .testTag("btn_close_diagnostics_dialog")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "إغلاق",
                tint = Color.LightGray,
                modifier = Modifier.size(20.dp)
              )
            }
          }

          // 2. SAVED FATAL CRASH REPORT CARD (shown prominently in RED if last_crash_log.txt exists)
          val crashText = lastCrashLog
          if (!crashText.isNullOrBlank()) {
            Surface(
              color = Color(0xFF2B0B0E),
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.5.dp, Color(0xFFFF453A)),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("last_crash_log_card")
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                  ) {
                    Icon(
                      imageVector = Icons.Default.ErrorOutline,
                      contentDescription = "تقرير آخر انهيار",
                      tint = Color(0xFFFF453A),
                      modifier = Modifier.size(18.dp)
                    )
                    Text(
                      text = "⚠️ آخر انهيار محفوظ",
                      color = Color(0xFFFF6961),
                      fontSize = 14.sp,
                      fontWeight = FontWeight.ExtraBold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }

                  Surface(
                    onClick = {
                      AppDiagnosticsLogger.clearLastCrashLog(context)
                      lastCrashLog = null
                      Toast.makeText(context, "تم مسح سجل الانهيار", Toast.LENGTH_SHORT).show()
                    },
                    color = Color(0x33FF453A),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF453A)),
                    modifier = Modifier
                      .heightIn(min = 38.dp)
                      .testTag("btn_clear_last_crash_log")
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "مسح",
                        tint = Color(0xFFFF8A80),
                        modifier = Modifier.size(16.dp)
                      )
                      Text(
                        text = "مسح",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }
                }

                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp, max = 120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF170607))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
                ) {
                  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                      text = crashText,
                      color = Color(0xFFFFB4AB),
                      fontSize = 11.5.sp,
                      fontFamily = FontFamily.Monospace,
                      lineHeight = 16.sp
                    )
                  }
                }
              }
            }
          }

          // 2b. PERSISTENT LIFECYCLE BREADCRUMBS (Survives OS LMKD / SIGKILL kills)
          val breadcrumbText = lastBreadcrumbs
          if (!breadcrumbText.isNullOrBlank()) {
            Surface(
              color = Color(0xFF231909),
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.75f)),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("last_breadcrumbs_card")
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "🧭 آخر خطوات التشغيل",
                    color = CinemaAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Surface(
                    onClick = {
                      AppDiagnosticsLogger.clearLastBreadcrumbs(context)
                      lastBreadcrumbs = null
                      Toast.makeText(context, "تم المسح", Toast.LENGTH_SHORT).show()
                    },
                    color = CinemaAccent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.7f)),
                    modifier = Modifier
                      .heightIn(min = 36.dp)
                      .testTag("btn_clear_breadcrumbs")
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "مسح",
                        tint = CinemaAccent,
                        modifier = Modifier.size(15.dp)
                      )
                      Text(
                        text = "مسح",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }
                }

                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp, max = 96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF140F05))
                    .padding(7.dp)
                    .verticalScroll(rememberScrollState())
                ) {
                  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                      text = breadcrumbText,
                      color = Color(0xFFFFE0A3),
                      fontSize = 11.sp,
                      fontFamily = FontFamily.Monospace,
                      lineHeight = 15.sp
                    )
                  }
                }
              }
            }
          }

          // 3. Primary Copy & Clear Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = { copyReportToClipboard() },
              colors = ButtonDefaults.buttonColors(
                containerColor = CinemaSuccess,
                contentColor = Color.Black
              ),
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("btn_copy_diagnostics_log")
            ) {
              Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(19.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "نسخ اللوج",
                color = Color.Black,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold
              )
            }

            OutlinedButton(
              onClick = { AppDiagnosticsLogger.clear() },
              border = BorderStroke(1.dp, CinemaBorder),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("btn_clear_diagnostics_log")
            ) {
              Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text("مسح", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
          }

          // 4. Android TV Easy Export Row: Share Log File + Upload & Show QR Code
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = { shareLogFileForTv() },
              colors = ButtonDefaults.buttonColors(
                containerColor = CinemaSecondary,
                contentColor = Color.Black
              ),
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("btn_share_log_file")
            ) {
              Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "مشاركة ملف",
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "مشاركة ملف",
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            Button(
              onClick = { uploadAndShowQrCode() },
              enabled = !isUploadingQr,
              colors = ButtonDefaults.buttonColors(
                containerColor = CinemaAccent,
                contentColor = Color.Black
              ),
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("btn_upload_qr_log")
            ) {
              if (isUploadingQr) {
                CircularProgressIndicator(
                  color = Color.Black,
                  strokeWidth = 2.dp,
                  modifier = Modifier.size(17.dp)
                )
              } else {
                Icon(
                  imageVector = Icons.Default.QrCode2,
                  contentDescription = "رفع QR",
                  tint = Color.Black,
                  modifier = Modifier.size(19.dp)
                )
              }
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isUploadingQr) "جاري الرفع..." else "رفع QR",
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          // 5. QR Code Display Card (appears when paste.rs upload succeeds — generated locally via ZXing!)
          val shortUrl = uploadedPasteUrl
          if (!shortUrl.isNullOrBlank()) {
            val qrImageBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = shortUrl) {
              value = withContext(Dispatchers.Default) {
                generateLocalQrBitmap(shortUrl, sizePx = 360)?.asImageBitmap()
              }
            }

            Surface(
              color = CinemaSurface,
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.5.dp, CinemaAccent),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("qr_upload_result_card")
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                // High-contrast white Quiet Zone frame for instant phone camera scanning from TV
                Box(
                  modifier = Modifier
                    .size(124.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .padding(6.dp),
                  contentAlignment = Alignment.Center
                ) {
                  val bmp = qrImageBitmap
                  if (bmp != null) {
                    Image(
                      bitmap = bmp,
                      contentDescription = "QR Code للرابط المختصر $shortUrl",
                      contentScale = ContentScale.Fit,
                      modifier = Modifier.size(112.dp)
                    )
                  } else {
                    CircularProgressIndicator(
                      color = Color.Black,
                      strokeWidth = 2.dp,
                      modifier = Modifier.size(28.dp)
                    )
                  }
                }

                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text(
                    text = "📱 امسح الكود بهاتفك:",
                    color = CinemaSuccess,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                  )
                  Surface(
                    color = CinemaDarkBackground,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CinemaBorder)
                  ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                      Text(
                        text = shortUrl,
                        color = CinemaAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(horizontal = 8.dp, vertical = 6.dp)
                      )
                    }
                  }
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    OutlinedButton(
                      onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("AsserTV Paste URL", shortUrl))
                        Toast.makeText(context, "تم نسخ الرابط", Toast.LENGTH_SHORT).show()
                      },
                      border = BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.7f)),
                      colors = ButtonDefaults.outlinedButtonColors(contentColor = CinemaAccent),
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                      modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                      Text("نسخ", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                      onClick = { uploadedPasteUrl = null },
                      border = BorderStroke(1.dp, CinemaBorder),
                      colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                      modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                      Text("إخفاء", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                  }
                }
              }
            }
          } else if (!uploadQrError.isNullOrBlank()) {
            Text(
              text = "⚠️ $uploadQrError",
              color = Color(0xFFFF8A80),
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold
            )
          }

          // 6. Live State Summary Box
          Surface(
            color = CinemaSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, CinemaBorder),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
              Text(
                text = "المشغل: ${AppDiagnosticsLogger.activePlayerMode}",
                color = CinemaSuccess,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold
              )
              Text(
                text = "السيرفر: ${AppDiagnosticsLogger.activeServerName.ifBlank { "افتراضي" }} • الدقة: ${AppDiagnosticsLogger.actualPlayingResolution}",
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "الجودات: ${AppDiagnosticsLogger.detectedQualitiesSummary}",
                color = CinemaAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "البث: ${AppDiagnosticsLogger.extractedStreamUrl.ifBlank { "—" }}",
                color = Color.LightGray,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (AppDiagnosticsLogger.lastPlayerError != "لا توجد أخطاء") {
                Text(
                  text = "الخطأ: ${AppDiagnosticsLogger.lastPlayerError}",
                  color = Color(0xFFFF6B6B),
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }

          // 7. Live Chronological Log List
          Surface(
            color = Color(0xFF05070A),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, CinemaBorder),
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
          ) {
            if (logs.isEmpty()) {
              Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "لا توجد أحداث بعد",
                  color = Color.LightGray,
                  fontSize = 14.sp,
                  modifier = Modifier.padding(16.dp)
                )
              }
            } else {
              LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
              ) {
                items(logs.reversed()) { entry ->
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(6.dp))
                      .background(
                        if (entry.isError) Color(0x33FF3B30) else Color(0x19FFFFFF)
                      )
                      .padding(horizontal = 8.dp, vertical = 5.dp)
                  ) {
                    Text(
                      text = "${entry.timestamp} [${entry.tag}] ${entry.message}",
                      color = if (entry.isError) Color(0xFFFF8A80) else ClipboxWhite,
                      fontSize = 12.sp,
                      fontFamily = FontFamily.Monospace
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

internal fun generateLocalQrBitmap(content: String, sizePx: Int = 360): Bitmap? {
  if (content.isBlank() || sizePx <= 0) return null
  return try {
    val hints = mapOf(
      EncodeHintType.MARGIN to 1,
      EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
      EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val width = bitMatrix.width
    val height = bitMatrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
      val offset = y * width
      for (x in 0 until width) {
        pixels[offset + x] = if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
      }
    }
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
      setPixels(pixels, 0, width, 0, 0, width, height)
    }
  } catch (_: Throwable) {
    null
  }
}

