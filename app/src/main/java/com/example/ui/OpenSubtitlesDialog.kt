package com.example.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SearchAndSubtitlesService
import com.example.model.SubtitleItem
import com.example.model.SubtitleSourceFilter
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.ClipboxWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OpenSubtitlesDialog(
  initialQuery: String,
  onDismiss: () -> Unit,
  onApplySubtitle: (SubtitleItem) -> Unit,
  service: SearchAndSubtitlesService = remember { SearchAndSubtitlesService() }
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var searchQuery by remember { mutableStateOf(initialQuery.ifBlank { "Silo" }) }
  var selectedLang by remember { mutableStateOf("ar") }
  var selectedSourceFilter by remember { mutableStateOf(SubtitleSourceFilter.ALL) }
  var isSearching by remember { mutableStateOf(false) }
  var subtitles by remember { mutableStateOf<List<SubtitleItem>>(emptyList()) }
  var appliedSubtitleId by remember { mutableStateOf<String?>(null) }
  var customUrl by remember { mutableStateOf("") }

  // External Local File Picker (.srt / .vtt / .ass)
  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      scope.launch {
        try {
          val (fileName, rawContent) = withContext(Dispatchers.IO) {
            var name = "ترجمة خارجية.srt"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
              val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
              if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex) ?: name
              }
            }
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
              it.readText()
            } ?: ""
            name to text
          }

          if (rawContent.isNotBlank()) {
            val vttDataUri = service.convertSrtToVttDataUri(rawContent)
            val externalSub = SubtitleItem(
              id = "local_file_${System.currentTimeMillis()}",
              releaseName = "📂 $fileName",
              languageCode = selectedLang,
              languageName = "ملف خارجي ($selectedLang)",
              format = if (fileName.endsWith(".vtt", ignoreCase = true)) "vtt" else "srt",
              downloadUrl = vttDataUri,
              rating = 5.0f,
              source = "ملف خارجي من الجهاز",
              rawVttDataUri = vttDataUri
            )
            appliedSubtitleId = externalSub.id
            onApplySubtitle(externalSub)
          } else {
            Toast.makeText(context, "تعذر قراءة محتوى ملف الترجمة", Toast.LENGTH_SHORT).show()
          }
        } catch (e: Exception) {
          Toast.makeText(context, "خطأ أثناء قراءة ملف الترجمة: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  fun performSearch() {
    if (searchQuery.isBlank()) return
    isSearching = true
    scope.launch {
      val res = service.searchAllSubtitles(
        query = searchQuery,
        languageCode = selectedLang,
        sourceFilter = selectedSourceFilter
      )
      subtitles = res
      isSearching = false
    }
  }

  LaunchedEffect(selectedLang, selectedSourceFilter) {
    if (searchQuery.isNotBlank()) {
      performSearch()
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = CinemaDarkBackground,
      border = BorderStroke(1.dp, CinemaBorder),
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .padding(vertical = 10.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
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
            Icon(
              imageVector = Icons.Default.Subtitles,
              contentDescription = null,
              tint = CinemaAccent,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = "مدير الترجمة الشامل (Subtitles)",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "ملف خارجي + OpenSubtitles.org + Addic7ed.com",
                color = Color.LightGray,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .size(38.dp)
              .testTag("btn_close_subtitles_dialog")
          ) {
            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.LightGray)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // External Local Subtitle File Picker Button
        Button(
          onClick = {
            filePickerLauncher.launch(arrayOf("*/*"))
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = CinemaSurfaceVariant,
            contentColor = Color.White
          ),
          border = BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.7f)),
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .testTag("btn_pick_external_subtitle_file")
        ) {
          Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            tint = CinemaAccent,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "إضافة ملف ترجمة من مصدر خارجي (جهازك .SRT / .VTT)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Subtitle Source Filter Tabs (All / OpenSubtitles.org / Addic7ed.com)
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          items(SubtitleSourceFilter.values().toList()) { filter ->
            val selected = selectedSourceFilter == filter
            FilterChip(
              selected = selected,
              onClick = { selectedSourceFilter = filter },
              label = {
                Text(
                  text = filter.labelAr,
                  fontSize = 11.sp,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                  maxLines = 1,
                  softWrap = false
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = ClipboxWhite,
                selectedLabelColor = Color.Black,
                containerColor = CinemaSurface,
                labelColor = Color.LightGray
              ),
              modifier = Modifier.heightIn(min = 34.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Query Row with Button
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
              Text(
                text = "ابحث باسم الفيلم أو المسلسل (مثال: Silo S01E01)...",
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            },
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .testTag("input_subtitles_search"),
            shape = RoundedCornerShape(10.dp),
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
            colors = ButtonDefaults.buttonColors(
              containerColor = CinemaAccent,
              contentColor = Color.Black
            ),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("btn_execute_subtitles_search")
          ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("بحث", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1, softWrap = false)
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Language Selector Chips
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "اللغة:",
            color = Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
          )
          Spacer(modifier = Modifier.width(6.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val languages = listOf(
              "ar" to "العربية 🇪🇬🇸🇦",
              "en" to "English 🇺🇸",
              "fr" to "Français 🇫🇷",
              "tr" to "Türkçe 🇹🇷",
              "ko" to "한국어 🇰🇷"
            )
            items(languages) { (code, label) ->
              FilterChip(
                selected = selectedLang == code,
                onClick = { selectedLang = code },
                label = { Text(label, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = CinemaPrimary,
                  selectedLabelColor = Color.White,
                  containerColor = CinemaSurface,
                  labelColor = Color.LightGray
                ),
                modifier = Modifier.heightIn(min = 32.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Loading or Results
        if (isSearching) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(170.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator(color = CinemaAccent, modifier = Modifier.size(32.dp))
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "جاري البحث في OpenSubtitles.org و Addic7ed.com...",
                color = Color.LightGray,
                fontSize = 12.sp
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .height(210.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(subtitles, key = { it.id }) { sub ->
              val isApplied = appliedSubtitleId == sub.id
              val isAddic7ed = sub.sourceSite.contains("Addic7ed", ignoreCase = true)
              val sourceBadgeColor = if (isAddic7ed) CinemaSecondary else CinemaAccent

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(if (isApplied) CinemaSuccess.copy(alpha = 0.2f) else CinemaSurface)
                  .border(
                    1.dp,
                    if (isApplied) CinemaSuccess else CinemaBorder,
                    RoundedCornerShape(10.dp)
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
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(sourceBadgeColor.copy(alpha = 0.22f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                      Text(
                        text = sub.sourceSite,
                        color = sourceBadgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                      )
                    }
                    Text(
                      text = "${sub.languageName} • ${sub.format.uppercase()} • ★ ${sub.rating}",
                      color = Color.LightGray,
                      fontSize = 10.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                  onClick = {
                    appliedSubtitleId = sub.id
                    onApplySubtitle(sub)
                  },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = if (isApplied) CinemaSuccess else CinemaPrimary,
                    contentColor = Color.White
                  ),
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                  modifier = Modifier
                    .heightIn(min = 38.dp)
                    .testTag("btn_apply_sub_${sub.id}")
                ) {
                  Icon(
                    imageVector = if (isApplied) Icons.Default.CheckCircle else Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (isApplied) "مفعلة" else "تطبيق",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
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
            placeholder = {
              Text(
                text = "أو الصق رابط ملف ترجمة مباشر (.srt / .vtt)...",
                color = Color.Gray,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
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
                  id = "custom_sub_${System.currentTimeMillis()}",
                  releaseName = "رابط ترجمة مباشر",
                  languageCode = selectedLang,
                  languageName = "مخصص ($selectedLang)",
                  format = if (customUrl.endsWith(".srt", ignoreCase = true)) "srt" else "vtt",
                  downloadUrl = customUrl,
                  source = "رابط خارجي"
                )
                onApplySubtitle(customSub)
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = CinemaSecondary, contentColor = Color.Black),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.heightIn(min = 44.dp)
          ) {
            Text("تطبيق الرابط", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
          }
        }
      }
    }
  }
}
