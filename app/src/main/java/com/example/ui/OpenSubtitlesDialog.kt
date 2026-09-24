package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.SearchAndSubtitlesService
import com.example.model.SubtitleItem
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import kotlinx.coroutines.launch

@Composable
fun OpenSubtitlesDialog(
  initialQuery: String,
  onDismiss: () -> Unit,
  onApplySubtitle: (SubtitleItem) -> Unit,
  service: SearchAndSubtitlesService = remember { SearchAndSubtitlesService() }
) {
  val scope = rememberCoroutineScope()
  var searchQuery by remember { mutableStateOf(initialQuery.ifBlank { "Silo" }) }
  var selectedLang by remember { mutableStateOf("ar") }
  var isSearching by remember { mutableStateOf(false) }
  var subtitles by remember { mutableStateOf<List<SubtitleItem>>(emptyList()) }
  var appliedSubtitleId by remember { mutableStateOf<String?>(null) }
  var customUrl by remember { mutableStateOf("") }

  fun performSearch() {
    if (searchQuery.isBlank()) return
    isSearching = true
    scope.launch {
      val res = service.searchOpenSubtitles(searchQuery, selectedLang)
      subtitles = res
      isSearching = false
    }
  }

  LaunchedEffect(selectedLang) {
    if (searchQuery.isNotBlank()) {
      performSearch()
    }
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = CinemaDarkBackground,
      border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Subtitles,
              contentDescription = null,
              tint = CinemaAccent,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = "ترجمة وكابشنز OpenSubtitles",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "استحضار وتحميل الترجمة أونلاين لمشغل الفيديو",
                color = Color.Gray,
                fontSize = 11.sp
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("btn_close_subtitles_dialog")
          ) {
            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.LightGray)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // WatchSeries DudeBar & General Subtitle Tip Card
        Surface(
          color = CinemaPrimary.copy(alpha = 0.15f),
          shape = RoundedCornerShape(8.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, CinemaPrimary.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Subtitles,
              contentDescription = null,
              tint = CinemaAccent,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "لمشاهدة ووتش سيريز (WatchSeries DudeBar): مشغلات الفيديو تدعم زر [CC] الداخلي للترجمة، أو اختر أي ملف بالأسفل لتثبيته فوراً",
              color = Color.White,
              fontSize = 10.sp,
              lineHeight = 14.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Query Row with Button
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("اسم الفيلم أو المسلسل...", color = Color.Gray, fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .testTag("input_subtitles_search"),
            colors = TextFieldDefaults.colors(
              focusedContainerColor = CinemaSurface,
              unfocusedContainerColor = CinemaSurface,
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedIndicatorColor = CinemaAccent,
              unfocusedIndicatorColor = CinemaBorder
            )
          )

          Button(
            onClick = { performSearch() },
            colors = ButtonDefaults.buttonColors(containerColor = CinemaAccent),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.testTag("btn_execute_subtitles_search")
          ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("بحث", fontWeight = FontWeight.Bold)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Language Selector Chips
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("اللغة: ", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          Spacer(modifier = Modifier.width(6.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val languages = listOf(
              "ar" to "العربية 🇪🇬🇸🇦",
              "en" to "English 🇺🇸🇬🇧",
              "fr" to "Français 🇫🇷"
            )
            items(languages) { (code, label) ->
              FilterChip(
                selected = selectedLang == code,
                onClick = { selectedLang = code },
                label = { Text(label, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = CinemaPrimary,
                  selectedLabelColor = Color.White,
                  containerColor = CinemaSurface,
                  labelColor = Color.LightGray
                )
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Loading or Results
        if (isSearching) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(150.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator(color = CinemaAccent, modifier = Modifier.size(32.dp))
              Spacer(modifier = Modifier.height(8.dp))
              Text("جاري استحضار الترجمات من OpenSubtitles.org...", color = Color.LightGray, fontSize = 12.sp)
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .height(200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(subtitles, key = { it.id }) { sub ->
              val isApplied = appliedSubtitleId == sub.id
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isApplied) CinemaSuccess.copy(alpha = 0.2f) else CinemaSurface)
                  .border(
                    1.dp,
                    if (isApplied) CinemaSuccess else CinemaBorder,
                    RoundedCornerShape(8.dp)
                  )
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = sub.releaseName,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CinemaSecondary.copy(alpha = 0.3f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                      Text(sub.languageName, color = CinemaAccent, fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "${sub.format.uppercase()} • تقييم: ${sub.rating} ★",
                      color = Color.Gray,
                      fontSize = 10.sp
                    )
                  }
                }

                Button(
                  onClick = {
                    appliedSubtitleId = sub.id
                    onApplySubtitle(sub)
                  },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = if (isApplied) CinemaSuccess else CinemaPrimary
                  ),
                  shape = RoundedCornerShape(6.dp),
                  modifier = Modifier.testTag("btn_apply_sub_${sub.id}")
                ) {
                  Icon(
                    imageVector = if (isApplied) Icons.Default.CheckCircle else Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (isApplied) "مفعلة" else "تطبيق الترجمة",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Direct Custom Subtitle URL Option
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          OutlinedTextField(
            value = customUrl,
            onValueChange = { customUrl = it },
            placeholder = { Text("أو أدخل رابط ملف ترجمة مباشر (.vtt/.srt)...", color = Color.Gray, fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = TextFieldDefaults.colors(
              focusedContainerColor = CinemaSurface,
              unfocusedContainerColor = CinemaSurface,
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White
            )
          )
          Button(
            onClick = {
              if (customUrl.isNotBlank()) {
                val customSub = SubtitleItem(
                  id = "custom_sub",
                  releaseName = "ترجمة مخصصة",
                  languageCode = selectedLang,
                  languageName = "مخصص",
                  format = if (customUrl.endsWith(".srt", ignoreCase = true)) "srt" else "vtt",
                  downloadUrl = customUrl
                )
                onApplySubtitle(customSub)
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = CinemaSecondary),
            shape = RoundedCornerShape(6.dp)
          ) {
            Text("تطبيق", fontSize = 11.sp)
          }
        }
      }
    }
  }
}
