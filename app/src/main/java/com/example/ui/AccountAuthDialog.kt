package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.db.AccountAuthRepository
import com.example.data.db.AuthActionResult
import com.example.data.db.UserAccountEntity
import com.example.ui.theme.CinemaAccent
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaDarkBackground
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSuccess
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountAuthDialog(
  accountRepository: AccountAuthRepository,
  onAccountAuthenticated: (UserAccountEntity) -> Unit,
  onAccountLoggedOut: () -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val currentAccount by accountRepository.currentAccountFlow.collectAsState()
  val allAccounts by accountRepository.allAccountsFlow.collectAsState(initial = emptyList())

  var isRegisterMode by remember { mutableStateOf(false) }
  var emailInput by remember { mutableStateOf("") }
  var passwordInput by remember { mutableStateOf("") }
  var confirmPasswordInput by remember { mutableStateOf("") }
  var displayNameInput by remember { mutableStateOf("") }
  var showPassword by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var isSubmitting by remember { mutableStateOf(false) }

  fun submitForm() {
    if (isSubmitting) return
    errorMessage = null
    isSubmitting = true
    scope.launch {
      try {
        val result = if (isRegisterMode) {
          accountRepository.registerAccount(
            rawEmail = emailInput,
            password = passwordInput,
            confirmPassword = confirmPasswordInput,
            rawDisplayName = displayNameInput
          )
        } else {
          accountRepository.loginAccount(
            rawEmail = emailInput,
            password = passwordInput
          )
        }
        when (result) {
          is AuthActionResult.Success -> {
            passwordInput = ""
            confirmPasswordInput = ""
            onAccountAuthenticated(result.account)
            Toast.makeText(
              context,
              if (isRegisterMode) "تم إنشاء الحساب وتسجيل الدخول ✓" else "مرحباً ${result.account.displayName} ✓",
              Toast.LENGTH_SHORT
            ).show()
            onDismiss()
          }
          is AuthActionResult.Error -> {
            errorMessage = result.message
          }
        }
      } finally {
        isSubmitting = false
      }
    }
  }

  BasicAlertDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      Surface(
        color = CinemaSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, CinemaBorder),
        modifier = Modifier
          .fillMaxWidth(0.92f)
          .padding(vertical = 12.dp)
          .testTag("account_auth_dialog")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(CinemaPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.AccountCircle,
                  contentDescription = null,
                  tint = CinemaPrimary,
                  modifier = Modifier.size(26.dp)
                )
              }
              Column {
                Text(
                  text = "حسابات Asser TV",
                  color = Color.White,
                  fontSize = 18.sp,
                  fontWeight = FontWeight.ExtraBold
                )
                Text(
                  text = "تسجيل دخول محلي بالبريد وكلمة المرور فقط",
                  color = Color.LightGray,
                  fontSize = 12.5.sp
                )
              }
            }

            IconButton(
              onClick = onDismiss,
              modifier = Modifier.testTag("btn_close_account_dialog")
            ) {
              Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.LightGray)
            }
          }

          // Active Logged-In Account Card
          val active = currentAccount
          if (active != null) {
            Surface(
              color = CinemaSuccess.copy(alpha = 0.14f),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, CinemaSuccess.copy(alpha = 0.65f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = active.displayName,
                      color = Color.White,
                      fontSize = 17.sp,
                      fontWeight = FontWeight.ExtraBold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = active.email,
                      color = CinemaAccent,
                      fontSize = 13.5.sp,
                      fontWeight = FontWeight.Medium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    if (active.traktUsername.isNotBlank()) {
                      Text(
                        text = "حساب Trakt المرتبط: @${active.traktUsername}",
                        color = CinemaSuccess,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }

                  Button(
                    onClick = {
                      accountRepository.logout()
                      onAccountLoggedOut()
                      Toast.makeText(context, "تم تسجيل الخروج", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                      .heightIn(min = 42.dp)
                      .testTag("btn_account_logout")
                  ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("خروج", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                  }
                }
              }
            }
          }

          // Mode Switcher: Sign In vs Create Account
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Surface(
              onClick = {
                isRegisterMode = false
                errorMessage = null
              },
              color = if (!isRegisterMode) CinemaPrimary else CinemaDarkBackground,
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, if (!isRegisterMode) CinemaPrimary else CinemaBorder),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .testTag("tab_account_login")
            ) {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                  text = "تسجيل الدخول",
                  color = Color.White,
                  fontSize = 14.5.sp,
                  fontWeight = FontWeight.ExtraBold
                )
              }
            }

            Surface(
              onClick = {
                isRegisterMode = true
                errorMessage = null
              },
              color = if (isRegisterMode) CinemaPrimary else CinemaDarkBackground,
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, if (isRegisterMode) CinemaPrimary else CinemaBorder),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .testTag("tab_account_register")
            ) {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                  text = "حساب جديد",
                  color = Color.White,
                  fontSize = 14.5.sp,
                  fontWeight = FontWeight.ExtraBold
                )
              }
            }
          }

          if (isRegisterMode) {
            OutlinedTextField(
              value = displayNameInput,
              onValueChange = { displayNameInput = it },
              label = { Text("الاسم (اختياري)", fontSize = 13.sp) },
              leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = CinemaAccent) },
              singleLine = true,
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CinemaAccent,
                unfocusedBorderColor = CinemaBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = CinemaDarkBackground,
                unfocusedContainerColor = CinemaDarkBackground
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("input_account_name")
            )
          }

          OutlinedTextField(
            value = emailInput,
            onValueChange = {
              emailInput = it
              errorMessage = null
            },
            label = { Text("البريد الإلكتروني (Email)", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = CinemaAccent) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Email,
              imeAction = ImeAction.Next
            ),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CinemaAccent,
              unfocusedBorderColor = CinemaBorder,
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedContainerColor = CinemaDarkBackground,
              unfocusedContainerColor = CinemaDarkBackground
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("input_account_email")
          )

          OutlinedTextField(
            value = passwordInput,
            onValueChange = {
              passwordInput = it
              errorMessage = null
            },
            label = { Text("كلمة المرور (Password)", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = CinemaAccent) },
            trailingIcon = {
              IconButton(onClick = { showPassword = !showPassword }) {
                Icon(
                  imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  contentDescription = if (showPassword) "إخفاء" else "إظهار",
                  tint = Color.LightGray
                )
              }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Password,
              imeAction = if (isRegisterMode) ImeAction.Next else ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
              onDone = { if (!isRegisterMode) submitForm() }
            ),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CinemaAccent,
              unfocusedBorderColor = CinemaBorder,
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedContainerColor = CinemaDarkBackground,
              unfocusedContainerColor = CinemaDarkBackground
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("input_account_password")
          )

          if (isRegisterMode) {
            OutlinedTextField(
              value = confirmPasswordInput,
              onValueChange = {
                confirmPasswordInput = it
                errorMessage = null
              },
              label = { Text("تأكيد كلمة المرور", fontSize = 13.sp) },
              leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = CinemaAccent) },
              visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
              singleLine = true,
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
              ),
              keyboardActions = KeyboardActions(onDone = { submitForm() }),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CinemaAccent,
                unfocusedBorderColor = CinemaBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = CinemaDarkBackground,
                unfocusedContainerColor = CinemaDarkBackground
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("input_account_confirm_password")
            )
          }

          if (!errorMessage.isNullOrBlank()) {
            Surface(
              color = Color(0x33EF4444),
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, Color(0xFFEF4444)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = errorMessage ?: "",
                color = Color(0xFFFCA5A5),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
              )
            }
          }

          Button(
            onClick = { submitForm() },
            enabled = !isSubmitting,
            colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 13.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("btn_account_submit")
          ) {
            if (isSubmitting) {
              CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
              Text(
                text = if (isRegisterMode) "حفظ الحساب الجديد والدخول" else "دخول",
                color = Color.White,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold
              )
            }
          }

          // Saved Accounts list in local Room DB (1-tap fill email on Android TV!)
          if (allAccounts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "الحسابات المحفوظة في قاعدة البيانات (${allAccounts.size}):",
              color = Color.LightGray,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              allAccounts.forEach { acc ->
                val isCurrent = currentAccount?.id == acc.id
                Surface(
                  onClick = {
                    emailInput = acc.email
                    isRegisterMode = false
                    errorMessage = null
                  },
                  color = CinemaDarkBackground,
                  shape = RoundedCornerShape(10.dp),
                  border = BorderStroke(1.dp, if (isCurrent) CinemaSuccess else CinemaBorder),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = if (isCurrent) "${acc.displayName} (نشط ✓)" else acc.displayName,
                        color = if (isCurrent) CinemaSuccess else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        text = acc.email,
                        color = Color.Gray,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                    IconButton(
                      onClick = {
                        scope.launch {
                          accountRepository.deleteAccount(acc.id)
                          if (isCurrent) onAccountLoggedOut()
                        }
                      },
                      modifier = Modifier.size(34.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف الحساب",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
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
}
