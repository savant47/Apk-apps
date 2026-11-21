package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteGuideDialog(
  onDismiss: () -> Unit
) {
  BasicAlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.widthIn(max = 720.dp)
  ) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = CinemaSurface,
      border = androidx.compose.foundation.BorderStroke(1.5.dp, CinemaSurfaceVariant)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(22.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.SettingsRemote,
              contentDescription = null,
              tint = CinemaSecondary,
              modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "دليل الريموت",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
              )
              Text(
                text = "اختصارات التحكم السريع",
                fontSize = 14.sp,
                color = Color.LightGray
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("close_guide_dialog")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "إغلاق",
              tint = Color.LightGray
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          RemoteKeyInfoRow(
            keyBadge = "الأسهم",
            badgeColor = CinemaSecondary,
            title = "التنقل / الماوس",
            description = "تنقل بين القوائم أو حرّك مؤشر الماوس"
          )

          RemoteKeyInfoRow(
            keyBadge = "OK / الوسط",
            badgeColor = CinemaAccent,
            title = "الاختيار والنقر",
            description = "فتح العنصر المحدد أو النقر بالماوس"
          )

          RemoteKeyInfoRow(
            keyBadge = "Menu",
            badgeColor = CinemaPrimary,
            title = "شريط الأدوات",
            description = "إظهار أو إخفاء الشريط العلوي"
          )

          RemoteKeyInfoRow(
            keyBadge = "Back",
            badgeColor = Color(0xFF64748B),
            title = "الرجوع",
            description = "رجوع للخلف أو الخروج من ملء الشاشة"
          )

          RemoteKeyInfoRow(
            keyBadge = "Play / Pause",
            badgeColor = CinemaSuccess,
            title = "تشغيل وإيقاف",
            description = "تحكم مباشر بمشغل الفيديو"
          )

          RemoteKeyInfoRow(
            keyBadge = "أصفر / أزرق",
            badgeColor = Color(0xFFEAB308),
            title = "اختصارات سريعة",
            description = "الأصفر للماوس والأزرق لملء الشاشة"
          )

          RemoteKeyInfoRow(
            keyBadge = "الترس ⚙️",
            badgeColor = Color(0xFFFFB300),
            title = "إعدادات المشغل",
            description = "الجودة والترجمة والسرعة والأبعاد"
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.CenterEnd
        ) {
          Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.heightIn(min = 48.dp)
          ) {
            Text(text = "حسناً", color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold)
          }
        }
      }
    }
  }
}

@Composable
fun RemoteKeyInfoRow(
  keyBadge: String,
  badgeColor: Color,
  title: String,
  description: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(CinemaDarkBackground)
      .border(1.dp, CinemaSurfaceVariant, RoundedCornerShape(8.dp))
      .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .width(140.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(badgeColor.copy(alpha = 0.2f))
        .border(1.dp, badgeColor, RoundedCornerShape(6.dp))
        .padding(horizontal = 8.dp, vertical = 7.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = keyBadge,
        color = badgeColor,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp
      )
    }

    Spacer(modifier = Modifier.width(14.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 15.5.sp
      )
      Text(
        text = description,
        color = Color.LightGray,
        fontSize = 13.5.sp
      )
    }
  }
}
