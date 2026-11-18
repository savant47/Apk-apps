package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AppDiagnosticsLogger
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.ClipboxWhite

@Composable
fun DiagnosticsLogDialog(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val logs by AppDiagnosticsLogger.logsFlow.collectAsState()

  fun copyReportToClipboard() {
    val report = AppDiagnosticsLogger.buildFullCopyableReport()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("AsserTV Diagnostics Log", report))
    Toast.makeText(
      context,
      "✅ تم نسخ تقرير الأعطال واللوج بالكامل! الصقه هنا مباشرة",
      Toast.LENGTH_LONG
    ).show()
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
          .widthIn(max = 560.dp)
          .fillMaxHeight(0.86f)
          .padding(vertical = 10.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
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
                  text = "سجل الأعطال وتشخيص المشغل (Log)",
                  color = Color.White,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.ExtraBold
                )
                Text(
                  text = "انسخ التقرير بضغطة واحدة وأرسله في المحادثة بدون سكرين شوت",
                  color = Color.LightGray,
                  fontSize = 10.5.sp
                )
              }
            }

            IconButton(
              onClick = onDismiss,
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(CinemaSurface)
                .testTag("btn_close_diagnostics_dialog")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "إغلاق",
                tint = Color.LightGray,
                modifier = Modifier.size(16.dp)
              )
            }
          }

          // 2. Primary One-Tap Copy Button + Clear Button
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
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .testTag("btn_copy_diagnostics_log")
            ) {
              Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "نسخ اللوج بالكامل (Copy-Paste)",
                color = Color.Black,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold
              )
            }

            OutlinedButton(
              onClick = { AppDiagnosticsLogger.clear() },
              border = BorderStroke(1.dp, CinemaBorder),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
              modifier = Modifier
                .heightIn(min = 44.dp)
                .testTag("btn_clear_diagnostics_log")
            ) {
              Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("مسح", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
          }

          // 3. Live State Summary Box
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
                text = "المشغل الحالي: ${AppDiagnosticsLogger.activePlayerMode}",
                color = CinemaSuccess,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
              )
              Text(
                text = "السيرفر: ${AppDiagnosticsLogger.activeServerName.ifBlank { "افتراضي" }} • أبعاد البث: ${AppDiagnosticsLogger.actualPlayingResolution}",
                color = Color.White,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "الجودات الملتقطة: ${AppDiagnosticsLogger.detectedQualitiesSummary}",
                color = CinemaAccent,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "رابط البث المسحوب: ${AppDiagnosticsLogger.extractedStreamUrl.ifBlank { "لم يُسحب رابط مباشر بعد" }}",
                color = Color.LightGray,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (AppDiagnosticsLogger.lastPlayerError != "لا توجد أخطاء") {
                Text(
                  text = "آخر خطأ: ${AppDiagnosticsLogger.lastPlayerError}",
                  color = Color(0xFFFF6B6B),
                  fontSize = 10.5.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }

          // 4. Live Chronological Log List
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
                  text = "لا توجد أحداث مسجلة حالياً. قم بتشغيل أي فيلم أو مسلسل وسيظهر التشخيص هنا تلقائياً.",
                  color = Color.Gray,
                  fontSize = 11.sp,
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
                      fontSize = 10.sp,
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
