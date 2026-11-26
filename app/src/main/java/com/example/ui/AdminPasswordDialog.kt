package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TvPreferencesRepository
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSurface
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPasswordDialog(
  title: String = "كلمة مرور المسؤول",
  subtitle: String = "أدخل كلمة مرور المسؤول للمتابعة",
  onVerified: () -> Unit,
  onDismiss: () -> Unit
) {
  var passwordInput by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var errorText by remember { mutableStateOf<String?>(null) }
  val inputFocusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) {
    delay(100L)
    try {
      inputFocusRequester.requestFocus()
    } catch (_: Throwable) {}
  }

  fun submitPassword() {
    if (TvPreferencesRepository.verifyAdminPassword(passwordInput)) {
      errorText = null
      onVerified()
    } else {
      errorText = "كلمة المرور غير صحيحة"
    }
  }

  BasicAlertDialog(onDismissRequest = onDismiss) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = BorderStroke(1.5.dp, CinemaAccent.copy(alpha = 0.7f)),
        modifier = Modifier
          .fillMaxWidth()
          .padding(8.dp)
          .testTag("admin_password_dialog")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = null,
              tint = CinemaAccent,
              modifier = Modifier.size(26.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
              )
              Text(
                text = subtitle,
                color = Color.LightGray,
                fontSize = 13.sp
              )
            }
          }

          OutlinedTextField(
            value = passwordInput,
            onValueChange = {
              passwordInput = it
              if (errorText != null) errorText = null
            },
            label = { Text("كلمة المرور", fontSize = 13.5.sp) },
            singleLine = true,
            isError = errorText != null,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(
                onClick = { passwordVisible = !passwordVisible },
                modifier = Modifier.testTag("btn_toggle_admin_pw_visibility")
              ) {
                Icon(
                  imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  contentDescription = if (passwordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                  tint = CinemaAccent
                )
              }
            },
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Password,
              imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
              onDone = { submitPassword() }
            ),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CinemaAccent,
              unfocusedBorderColor = CinemaBorder,
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedContainerColor = CinemaDarkBackground,
              unfocusedContainerColor = CinemaDarkBackground,
              focusedLabelColor = CinemaAccent,
              unfocusedLabelColor = Color.LightGray,
              errorBorderColor = Color(0xFFFF5252)
            ),
            modifier = Modifier
              .fillMaxWidth()
              .focusRequester(inputFocusRequester)
              .testTag("input_admin_password")
          )

          if (errorText != null) {
            Text(
              text = errorText ?: "",
              color = Color(0xFFFF6B6B),
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Start,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("txt_admin_password_error")
            )
          }

          Spacer(modifier = Modifier.height(2.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedButton(
              onClick = onDismiss,
              border = BorderStroke(1.dp, CinemaBorder),
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("btn_admin_password_cancel")
            ) {
              Text(
                text = "إلغاء",
                color = Color.LightGray,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
              )
            }

            Button(
              onClick = { submitPassword() },
              colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1.3f)
                .heightIn(min = 48.dp)
                .testTag("btn_admin_password_confirm")
            ) {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "تأكيد الدخول",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
              )
            }
          }
        }
      }
    }
  }
}
