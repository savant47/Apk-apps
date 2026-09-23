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
                text = "دليل استخدام ريموت BeoutQ QS285 و Android TV",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Text(
                text = "تحكم سلس وسريع بجميع مزايا العرض وتشغيل الوسائط",
                fontSize = 12.sp,
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

        Spacer(modifier = Modifier.height(16.dp))

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          RemoteKeyInfoRow(
            keyBadge = "D-Pad الأسهم",
            badgeColor = CinemaSecondary,
            title = "التنقل والتمرير / تحريك الماوس",
            description = "في الوضع الافتراضي تقوم الأسهم بالتمرير لأعلى وأسفل، وفي وضع الماوس تحرك المؤشر بدقة فوق مشغلات الفيديو والأزرار."
          )

          RemoteKeyInfoRow(
            keyBadge = "OK / الوسط",
            badgeColor = CinemaAccent,
            title = "الاختيار والنقر المباشر",
            description = "يقوم بالنقر على الروابط أو إرسال نقرة حقيقية في المكان الذي يقف عليه مؤشر الماوس لتشغيل السيرفرات."
          )

          RemoteKeyInfoRow(
            keyBadge = "Menu القائمة",
            badgeColor = CinemaPrimary,
            title = "إظهار أو إخفاء شريط الأدوات",
            description = "اضغط زر Menu في أي لحظة لإخفاء الشريط العلوي والتمتع بكامل الشاشة 100%، أو إظهاره للتبديل بين المواقع."
          )

          RemoteKeyInfoRow(
            keyBadge = "Back الرجوع",
            badgeColor = Color(0xFF64748B),
            title = "الرجوع / خروج ملء الشاشة",
            description = "يرجعك للصفحة السابقة في المتصفح، أو يخرج من وضع الفيديو بملء الشاشة."
          )

          RemoteKeyInfoRow(
            keyBadge = "Play / Pause",
            badgeColor = CinemaSuccess,
            title = "إيقاف وتشغيل الفيديو",
            description = "زر التشغيل والإيقاف في الريموت يتحكم مباشرة بمشغل الفيديو في TopCinema و WatchSeries."
          )

          RemoteKeyInfoRow(
            keyBadge = "الزر الأصفر / الأزرق",
            badgeColor = Color(0xFFEAB308),
            title = "اختصارات سريعة",
            description = "الزر الأصفر يبدل وضع الماوس مباشرة، والزر الأزرق يفعل وضع ملء الشاشة الكامل."
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.CenterEnd
        ) {
          Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
            shape = RoundedCornerShape(8.dp)
          ) {
            Text(text = "فهمت، ابدأ المشاهدة", color = Color.White, fontWeight = FontWeight.Bold)
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
        .width(130.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(badgeColor.copy(alpha = 0.2f))
        .border(1.dp, badgeColor, RoundedCornerShape(6.dp))
        .padding(horizontal = 8.dp, vertical = 6.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = keyBadge,
        color = badgeColor,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp
      )
    }

    Spacer(modifier = Modifier.width(14.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp
      )
      Text(
        text = description,
        color = Color.LightGray,
        fontSize = 11.sp
      )
    }
  }
}
