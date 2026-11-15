package com.example.ui

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant

data class VideoQualityOption(
  val id: String,
  val title: String,
  val subtitle: String,
  val badge: String,
  val icon: ImageVector,
  val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQualityDialog(
  currentQuality: String = "auto",
  onSelectQuality: (qualityId: String, qualityLabel: String) -> Unit,
  onDismiss: () -> Unit
) {
  var selectedId by remember { mutableStateOf(currentQuality) }

  val options = remember {
    listOf(
      VideoQualityOption(
        id = "auto",
        title = "تلقائي (Auto)",
        subtitle = "ضبط ديناميكي حسب سرعة الإنترنت المتوفرة لمنع التوقف",
        badge = "موصى به",
        icon = Icons.Default.AutoMode,
        color = CinemaAccent
      ),
      VideoQualityOption(
        id = "1080",
        title = "جودة فائقة 1080p (FHD)",
        subtitle = "أعلى نقاء وتفاصيل واضحة جداً للسرعات العالية وشبكات الألياف",
        badge = "FHD",
        icon = Icons.Default.Hd,
        color = Color(0xFF38BDF8)
      ),
      VideoQualityOption(
        id = "720",
        title = "جودة عالية 720p (HD)",
        subtitle = "توازن مثالي بين وضوح الصورة العالي وسرعة التحميل للشاشات",
        badge = "HD",
        icon = Icons.Default.HighQuality,
        color = CinemaSuccess
      ),
      VideoQualityOption(
        id = "480",
        title = "جودة متوسطة 480p (SD)",
        subtitle = "تعمل بسلاسة فائقة وتمنع التقطيع مع سرعات الإنترنت المتوسطة",
        badge = "480p",
        icon = Icons.Default.NetworkCheck,
        color = Color(0xFFFFB300)
      ),
      VideoQualityOption(
        id = "360",
        title = "جودة موفرة 360p (Data Saver)",
        subtitle = "توفير عالي لباقة الإنترنت مع صورة واضحة وتشغيل فوري بدون انتظار",
        badge = "360p",
        icon = Icons.Default.Speed,
        color = Color(0xFF4CAF50)
      ),
      VideoQualityOption(
        id = "240",
        title = "جودة اقتصادية 240p (Low)",
        subtitle = "أسرع تحميل وموفرة جداً لبيانات الإنترنت والشبكات الضعيفة",
        badge = "240p",
        icon = Icons.Default.Speed,
        color = Color(0xFFFF7043)
      )
    )
  }

  BasicAlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier
      .widthIn(max = 540.dp)
      .fillMaxWidth(0.95f)
      .padding(8.dp)
  ) {
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = CinemaSurface,
      border = BorderStroke(2.dp, CinemaBorder)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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
                .background(CinemaPrimary.copy(alpha = 0.2f), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.HighQuality,
                contentDescription = null,
                tint = CinemaAccent,
                modifier = Modifier.size(24.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "جودة الفيديو (Video Quality)",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "اختر الجودة المناسبة لسرعة اتصالك",
                color = Color.LightGray,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("btn_close_quality_dialog")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "إغلاق",
              tint = Color.Gray
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Options List
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          options.forEach { opt ->
            val isSelected = selectedId == opt.id
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (isSelected) CinemaSurfaceVariant else CinemaSurface,
              border = BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) opt.color else CinemaBorder
              ),
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  selectedId = opt.id
                  onSelectQuality(opt.id, opt.title)
                }
                .testTag("quality_option_${opt.id}")
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.weight(1f)
                ) {
                  Box(
                    modifier = Modifier
                      .size(36.dp)
                      .background(opt.color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = opt.icon,
                      contentDescription = null,
                      tint = opt.color,
                      modifier = Modifier.size(20.dp)
                    )
                  }

                  Spacer(modifier = Modifier.width(12.dp))

                  Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        text = opt.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Box(
                        modifier = Modifier
                          .background(opt.color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                          .padding(horizontal = 6.dp, vertical = 2.dp)
                      ) {
                        Text(
                          text = opt.badge,
                          color = opt.color,
                          fontSize = 10.sp,
                          fontWeight = FontWeight.Bold
                        )
                      }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = opt.subtitle,
                      color = Color.LightGray,
                      fontSize = 11.sp
                    )
                  }
                }

                if (isSelected) {
                  Box(
                    modifier = Modifier
                      .size(24.dp)
                      .background(opt.color, CircleShape),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = "محدد",
                      tint = Color.Black,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Info Tip
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color.Black.copy(alpha = 0.3f),
          border = BorderStroke(1.dp, CinemaBorder),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "💡 نصيحة: يمكنك أيضاً النقر مباشرة على أيقونة الترس ⚙️ في مشغل الفيديو بالأسفل؛ حيث تم تحسين المشغل لعرض الجودات (240p - 720p) بأزرار كبيرة واضحة للشاشات.",
            color = Color.Gray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(10.dp)
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
          onClick = onDismiss,
          colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
        ) {
          Text("إغلاق النافذة", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
      }
    }
  }
}
