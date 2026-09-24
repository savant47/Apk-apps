package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.TvPreferencesRepository
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant

@Composable
fun AdBlockSettingsDialog(
  repository: TvPreferencesRepository,
  onDismiss: () -> Unit,
  onSettingsChanged: () -> Unit = {}
) {
  val context = LocalContext.current
  var isAdBlockEnabled by remember { mutableStateOf(repository.isAdBlockEnabled()) }
  var isStrictPopupBlocker by remember { mutableStateOf(repository.isStrictPopupBlockerEnabled()) }
  var isAdGuardDnsEnabled by remember { mutableStateOf(repository.isAdGuardDnsEnabled()) }

  val adguardDnsHost = "dns.adguard-dns.com"

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = CinemaDarkBackground,
      border = BorderStroke(1.5.dp, CinemaSecondary.copy(alpha = 0.6f)),
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)
        .testTag("dialog_adblock_settings")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CinemaSuccess.copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = CinemaSuccess,
                modifier = Modifier.size(26.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "درع مانع الإعلانات & AdGuard DNS",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "حظر الإعلانات المنبثقة والتحويلات الإجبارية 100%",
                color = CinemaSuccess,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("btn_close_adblock_dialog")
          ) {
            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.LightGray)
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status Banner
        Card(
          shape = RoundedCornerShape(10.dp),
          colors = CardDefaults.cardColors(containerColor = CinemaSurface),
          border = BorderStroke(1.dp, if (isAdBlockEnabled && isStrictPopupBlocker) CinemaSuccess.copy(alpha = 0.5f) else CinemaBorder),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = if (isAdBlockEnabled && isStrictPopupBlocker) Icons.Default.CheckCircle else Icons.Default.Block,
              contentDescription = null,
              tint = if (isAdBlockEnabled && isStrictPopupBlocker) CinemaSuccess else Color.Gray,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = if (isAdBlockEnabled && isStrictPopupBlocker) "الحماية الصارمة نشطة بالكامل 🛡️" else "الحماية معطلة جزئياً",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "يتم منع أي تحويل منبثق (مثل luugy.com والإعلانات الخفية) تلقائياً عند النقر",
                color = Color.LightGray,
                fontSize = 11.sp
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Toggle 1: Strict Pop-up & Redirect Blocker
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CinemaSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "حظر النوافذ المنبثقة الصارم (Anti-Popup)",
              color = Color.White,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold
            )
            Text(
              text = "يمنع الصفحات من فتح إعلانات جانبية أو تغيير الرابط عند الضغط على زر التشغيل",
              color = Color.Gray,
              fontSize = 10.sp
            )
          }
          Switch(
            checked = isStrictPopupBlocker,
            onCheckedChange = { checked ->
              isStrictPopupBlocker = checked
              repository.setStrictPopupBlockerEnabled(checked)
              onSettingsChanged()
            },
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = CinemaSuccess,
              uncheckedThumbColor = Color.Gray,
              uncheckedTrackColor = CinemaSurfaceVariant
            ),
            modifier = Modifier.testTag("switch_strict_popup")
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Toggle 2: AdGuard Filter Engine
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CinemaSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "محرك فلترة AdGuard المدمج",
              color = Color.White,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold
            )
            Text(
              text = "اعتراض مسارات وشبكات الإعلانات (Adsterra, Propeller, Luugy, PopAds)",
              color = Color.Gray,
              fontSize = 10.sp
            )
          }
          Switch(
            checked = isAdGuardDnsEnabled,
            onCheckedChange = { checked ->
              isAdGuardDnsEnabled = checked
              repository.setAdGuardDnsEnabled(checked)
              onSettingsChanged()
            },
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = CinemaPrimary,
              uncheckedThumbColor = Color.Gray,
              uncheckedTrackColor = CinemaSurfaceVariant
            ),
            modifier = Modifier.testTag("switch_adguard_filter")
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // AdGuard Private DNS Host Section (System-Wide Guidance)
        Card(
          shape = RoundedCornerShape(10.dp),
          colors = CardDefaults.cardColors(containerColor = CinemaSecondary.copy(alpha = 0.12f)),
          border = BorderStroke(1.dp, CinemaSecondary.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Dns, contentDescription = null, tint = CinemaSecondary, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "إعداد DNS الخاص لنظام أندرويد (Private DNS)",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "يمكنك أيضاً وضع عنوان خادم AdGuard في إعدادات الهاتف لحجب الإعلانات على مستوى كافة التطبيقات:",
              color = Color.LightGray,
              fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Copyable DNS Box
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(CinemaDarkBackground)
                .border(1.dp, CinemaBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = adguardDnsHost,
                color = CinemaAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
              Button(
                onClick = {
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  val clip = ClipData.newPlainText("AdGuard DNS", adguardDnsHost)
                  clipboard.setPrimaryClip(clip)
                  Toast.makeText(context, "تم نسخ: $adguardDnsHost 📋", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CinemaSecondary),
                shape = RoundedCornerShape(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("نسخ", fontSize = 10.sp)
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Button to open system network settings
            OutlinedButton(
              onClick = {
                try {
                  val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                  }
                  context.startActivity(intent)
                } catch (_: Exception) {
                  try {
                    val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                      flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                  } catch (_: Exception) {
                    Toast.makeText(context, "افتح إعدادات الهاتف > الشبكة > DNS الخاص", Toast.LENGTH_LONG).show()
                  }
                }
              },
              border = BorderStroke(1.dp, CinemaBorder),
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(Icons.Default.Settings, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("فتح إعدادات DNS الخاص في الأندرويد ⚙️", color = Color.White, fontSize = 11.sp)
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Confirm & Close Button
        Button(
          onClick = {
            Toast.makeText(context, "تم حفظ إعدادات مانع الإعلانات بنجاح 🛡️", Toast.LENGTH_SHORT).show()
            onDismiss()
          },
          colors = ButtonDefaults.buttonColors(containerColor = CinemaSuccess),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("btn_save_adblock_settings")
        ) {
          Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("تم وحفظ الإعدادات", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}
