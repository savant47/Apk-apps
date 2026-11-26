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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.runtime.collectAsState
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
  var isStrictIframeSandbox by remember { mutableStateOf(repository.isStrictIframeSandboxEnabled()) }
  var isAdGuardDnsEnabled by remember { mutableStateOf(repository.isAdGuardDnsEnabled()) }
  val blockedAdsCount by repository.blockedAdsCountFlow.collectAsState()

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
          .verticalScroll(rememberScrollState())
          .padding(18.dp)
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
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "مانع الإعلانات",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "حظر الإعلانات والنوافذ المنبثقة",
                color = CinemaSuccess,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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

        Spacer(modifier = Modifier.height(14.dp))

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
              modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (isAdBlockEnabled && isStrictPopupBlocker) "الحماية مفعّلة 🛡️" else "الحماية جزئية",
                color = Color.White,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "تم حجب $blockedAdsCount إعلان/نافذة منبثقة",
                color = CinemaAccent,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag("txt_blocked_ads_count")
              )
            }
            if (blockedAdsCount > 0) {
              OutlinedButton(
                onClick = { repository.resetBlockedAdsCount() },
                border = BorderStroke(1.dp, CinemaBorder),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                  .heightIn(min = 36.dp)
                  .testTag("btn_reset_blocked_ads_count")
              ) {
                Text("تصفير", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
              text = "حظر النوافذ المنبثقة",
              color = Color.White,
              fontSize = 15.5.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "منع الإعلانات والتحويلات",
              color = Color.LightGray,
              fontSize = 13.sp
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

        // Toggle 1b: Strict Iframe Sandbox (Smart Anti-Popup without "Please Disable Sandbox" breakages)
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
              text = "Strict Iframe Sandbox",
              color = Color.White,
              fontSize = 15.5.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "حظر اختطاف النقرات والبوب-أب داخل المشغل بدون تعارض Sandbox",
              color = Color.LightGray,
              fontSize = 12.5.sp
            )
          }
          Switch(
            checked = isStrictIframeSandbox,
            onCheckedChange = { checked ->
              isStrictIframeSandbox = checked
              repository.setStrictIframeSandboxEnabled(checked)
              onSettingsChanged()
            },
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = CinemaSuccess,
              uncheckedThumbColor = Color.Gray,
              uncheckedTrackColor = CinemaSurfaceVariant
            ),
            modifier = Modifier.testTag("switch_strict_iframe_sandbox")
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
              text = "فلتر AdGuard",
              color = Color.White,
              fontSize = 15.5.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "حظر شبكات الإعلانات",
              color = Color.LightGray,
              fontSize = 13.sp
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
              Icon(Icons.Default.Dns, contentDescription = null, tint = CinemaSecondary, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "AdGuard DNS",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "لإضافته في إعدادات الشبكة:",
              color = Color.LightGray,
              fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Copyable DNS Box
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(CinemaDarkBackground)
                .border(1.dp, CinemaBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = adguardDnsHost,
                color = CinemaAccent,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Button(
                onClick = {
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  val clip = ClipData.newPlainText("AdGuard DNS", adguardDnsHost)
                  clipboard.setPrimaryClip(clip)
                  Toast.makeText(context, "تم نسخ: $adguardDnsHost", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CinemaSecondary),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.heightIn(min = 40.dp)
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("نسخ", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
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
                    Toast.makeText(context, "افتح الإعدادات > الشبكة > DNS الخاص", Toast.LENGTH_LONG).show()
                  }
                }
              },
              border = BorderStroke(1.dp, CinemaBorder),
              shape = RoundedCornerShape(6.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
            ) {
              Icon(Icons.Default.Settings, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(17.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("إعدادات DNS ⚙️", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Confirm & Close Button
        Button(
          onClick = {
            Toast.makeText(context, "تم حفظ الإعدادات 🛡️", Toast.LENGTH_SHORT).show()
            onDismiss()
          },
          colors = ButtonDefaults.buttonColors(containerColor = CinemaSuccess),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("btn_save_adblock_settings")
        ) {
          Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("حفظ", color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
        }
      }
    }
  }
}
