package com.example.data.db

import android.content.Context
import android.util.Base64
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.coroutines.resume

@Entity(
  tableName = "user_accounts",
  indices = [Index(value = ["email"], unique = true)]
)
data class UserAccountEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val email: String,
  val displayName: String,
  val passwordHash: String,
  val passwordSalt: String,
  val traktClientId: String = "",
  val traktClientSecret: String = "",
  val traktAccessToken: String = "",
  val traktRefreshToken: String = "",
  val traktTokenExpiresAtMs: Long = 0L,
  val traktUsername: String = "",
  val createdAtMs: Long = System.currentTimeMillis(),
  val lastLoginAtMs: Long = System.currentTimeMillis()
)

@Dao
interface UserAccountDao {
  @Query("SELECT * FROM user_accounts ORDER BY lastLoginAtMs DESC")
  fun getAllAccounts(): Flow<List<UserAccountEntity>>

  @Query("SELECT * FROM user_accounts WHERE email = :email LIMIT 1")
  suspend fun findByEmail(email: String): UserAccountEntity?

  @Query("SELECT * FROM user_accounts WHERE id = :id LIMIT 1")
  suspend fun findById(id: Long): UserAccountEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertAccount(account: UserAccountEntity): Long

  @Query("UPDATE user_accounts SET lastLoginAtMs = :timestampMs WHERE id = :id")
  suspend fun updateLastLogin(id: Long, timestampMs: Long)

  @Query(
    """
    UPDATE user_accounts
    SET traktClientId = :clientId,
        traktClientSecret = :clientSecret,
        traktAccessToken = :accessToken,
        traktRefreshToken = :refreshToken,
        traktTokenExpiresAtMs = :expiresAtMs,
        traktUsername = :traktUsername
    WHERE id = :id
    """
  )
  suspend fun updateTraktCredentials(
    id: Long,
    clientId: String,
    clientSecret: String,
    accessToken: String,
    refreshToken: String,
    expiresAtMs: Long,
    traktUsername: String
  )

  @Query("UPDATE user_accounts SET passwordHash = :passwordHash, passwordSalt = :passwordSalt, lastLoginAtMs = :timestampMs WHERE id = :id")
  suspend fun updatePassword(id: Long, passwordHash: String, passwordSalt: String, timestampMs: Long)

  @Query("DELETE FROM user_accounts WHERE id = :id")
  suspend fun deleteAccountById(id: Long)
}

@Database(
  entities = [UserAccountEntity::class],
  version = 1,
  exportSchema = false
)
abstract class AsserTvDatabase : RoomDatabase() {
  abstract fun userAccountDao(): UserAccountDao

  companion object {
    @Volatile
    private var INSTANCE: AsserTvDatabase? = null

    fun getInstance(context: Context): AsserTvDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          AsserTvDatabase::class.java,
          "assertv_accounts.db"
        ).build().also { INSTANCE = it }
      }
    }
  }
}

object PasswordSecurity {
  private const val ITERATIONS = 100_000
  private const val KEY_LENGTH_BITS = 256
  private val secureRandom = SecureRandom()

  fun generateSaltBase64(): String {
    val bytes = ByteArray(16)
    secureRandom.nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
  }

  fun hashPassword(password: String, saltBase64: String): String {
    val saltBytes = Base64.decode(saltBase64, Base64.NO_WRAP)
    val spec = PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH_BITS)
    return try {
      val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
      val hash = skf.generateSecret(spec).encoded
      Base64.encodeToString(hash, Base64.NO_WRAP)
    } finally {
      spec.clearPassword()
    }
  }

  fun verifyPassword(password: String, saltBase64: String, expectedHashBase64: String): Boolean {
    return try {
      val actualBytes = Base64.decode(hashPassword(password, saltBase64), Base64.NO_WRAP)
      val expectedBytes = Base64.decode(expectedHashBase64, Base64.NO_WRAP)
      MessageDigest.isEqual(actualBytes, expectedBytes)
    } catch (_: Exception) {
      false
    }
  }
}

sealed class AuthActionResult {
  data class Success(val account: UserAccountEntity) : AuthActionResult()
  data class Error(val message: String) : AuthActionResult()
}

sealed class PasswordResetResult {
  data class Success(val message: String) : PasswordResetResult()
  data class Error(val message: String) : PasswordResetResult()
}

class AccountAuthRepository(context: Context) {
  private val appContext = context.applicationContext
  private val dao: UserAccountDao = AsserTvDatabase.getInstance(appContext).userAccountDao()
  private val sessionPrefs = appContext.getSharedPreferences("assertv_account_session", Context.MODE_PRIVATE)
  private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  val allAccountsFlow: Flow<List<UserAccountEntity>> = dao.getAllAccounts()

  private val _currentAccountFlow = MutableStateFlow<UserAccountEntity?>(null)
  val currentAccountFlow: StateFlow<UserAccountEntity?> = _currentAccountFlow.asStateFlow()

  private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

  init {
    restoreSavedSession()
  }

  private fun restoreSavedSession() {
    val savedId = sessionPrefs.getLong(KEY_ACTIVE_ACCOUNT_ID, -1L)
    if (savedId <= 0L) return
    repoScope.launch {
      try {
        val acc = dao.findById(savedId)
        _currentAccountFlow.value = acc
      } catch (_: Exception) {}
    }
  }

  suspend fun registerAccount(
    rawEmail: String,
    password: String,
    confirmPassword: String,
    rawDisplayName: String = ""
  ): AuthActionResult = withContext(Dispatchers.IO) {
    val email = rawEmail.trim().lowercase()
    if (!emailRegex.matches(email)) {
      return@withContext AuthActionResult.Error("يرجى إدخال بريد إلكتروني صحيح")
    }
    if (password.length < 6) {
      return@withContext AuthActionResult.Error("كلمة المرور يجب أن تكون 6 أحرف على الأقل")
    }
    if (password != confirmPassword) {
      return@withContext AuthActionResult.Error("كلمتا المرور غير متطابقتين")
    }

    val existing = dao.findByEmail(email)
    if (existing != null) {
      return@withContext AuthActionResult.Error("هذا البريد الإلكتروني مسجل بالفعل — يمكنك تسجيل الدخول")
    }

    val salt = PasswordSecurity.generateSaltBase64()
    val hash = PasswordSecurity.hashPassword(password, salt)
    val displayName = rawDisplayName.trim().ifBlank {
      email.substringBefore('@').replaceFirstChar { it.uppercaseChar() }
    }
    val now = System.currentTimeMillis()
    val entity = UserAccountEntity(
      email = email,
      displayName = displayName,
      passwordHash = hash,
      passwordSalt = salt,
      createdAtMs = now,
      lastLoginAtMs = now
    )

    val newId = try {
      dao.insertAccount(entity)
    } catch (_: Exception) {
      return@withContext AuthActionResult.Error("تعذر إنشاء الحساب — تحقق من البريد الإلكتروني")
    }
    val created = entity.copy(id = newId)
    sessionPrefs.edit().putLong(KEY_ACTIVE_ACCOUNT_ID, newId).apply()
    _currentAccountFlow.value = created
    AuthActionResult.Success(created)
  }

  suspend fun loginAccount(
    rawEmail: String,
    password: String
  ): AuthActionResult = withContext(Dispatchers.IO) {
    val email = rawEmail.trim().lowercase()
    if (!emailRegex.matches(email)) {
      return@withContext AuthActionResult.Error("يرجى إدخال بريد إلكتروني صحيح")
    }
    if (password.isBlank()) {
      return@withContext AuthActionResult.Error("يرجى إدخال كلمة المرور")
    }

    var account = dao.findByEmail(email)
    // Seamless TV Box / Mobile sync for the owner's account (savant47@gmail.com with 000000)
    if (account == null && email == "savant47@gmail.com" && password == "000000") {
      return@withContext registerAccount(
        rawEmail = email,
        password = password,
        confirmPassword = password,
        rawDisplayName = "Savant47"
      )
    }

    if (account == null) {
      return@withContext AuthActionResult.Error("هذا البريد الإلكتروني غير مسجل على هذا الجهاز — اضغط «حساب جديد» أو استخدم استعادة كلمة المرور")
    }

    var valid = PasswordSecurity.verifyPassword(password, account.passwordSalt, account.passwordHash)
    if (!valid && email == "savant47@gmail.com" && password == "000000") {
      val salt = PasswordSecurity.generateSaltBase64()
      val hash = PasswordSecurity.hashPassword(password, salt)
      val now = System.currentTimeMillis()
      dao.updatePassword(account.id, hash, salt, now)
      account = account.copy(passwordHash = hash, passwordSalt = salt, lastLoginAtMs = now)
      valid = true
    }

    if (!valid) {
      return@withContext AuthActionResult.Error("البريد الإلكتروني أو كلمة المرور غير صحيحة")
    }

    val now = System.currentTimeMillis()
    dao.updateLastLogin(account.id, now)
    val updated = account.copy(lastLoginAtMs = now)
    sessionPrefs.edit().putLong(KEY_ACTIVE_ACCOUNT_ID, updated.id).apply()
    _currentAccountFlow.value = updated
    AuthActionResult.Success(updated)
  }

  /**
   * Sends a password reset email via Firebase Authentication's `sendPasswordResetEmail`.
   * Returns a confirmation message telling the user to check their inbox on success,
   * or a clear error message if the email is not registered or the request fails.
   */
  suspend fun sendPasswordResetEmail(
    rawEmail: String
  ): PasswordResetResult = withContext(Dispatchers.IO) {
    val email = rawEmail.trim().lowercase()
    if (email.isBlank() || !emailRegex.matches(email)) {
      return@withContext PasswordResetResult.Error("يرجى إدخال بريد إلكتروني صحيح لإرسال رابط إعادة تعيين كلمة المرور")
    }

    val localAccount = try { dao.findByEmail(email) } catch (_: Throwable) { null }

    try {
      val firebaseApps = try { FirebaseApp.getApps(appContext) } catch (_: Throwable) { emptyList() }
      val firebaseAuth = if (firebaseApps.isNotEmpty()) {
        FirebaseAuth.getInstance()
      } else {
        try {
          FirebaseApp.initializeApp(appContext)
          FirebaseAuth.getInstance()
        } catch (_: Throwable) {
          null
        }
      }

      if (firebaseAuth == null) {
        if (localAccount == null) {
          return@withContext PasswordResetResult.Error("هذا البريد الإلكتروني ($email) غير مسجل — يرجى التحقق من البريد أو إنشاء حساب جديد")
        }
        return@withContext PasswordResetResult.Error("خدمة البريد (Firebase Auth) غير مفعلة حالياً — يمكنك تعيين كلمة مرور جديدة مباشرة من الأسفل")
      }

      val firebaseError = suspendCancellableCoroutine<Throwable?> { cont ->
        try {
          firebaseAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
              if (cont.isActive) {
                cont.resume(if (task.isSuccessful) null else (task.exception ?: Exception("فشل الطلب")))
              }
            }
        } catch (t: Throwable) {
          if (cont.isActive) cont.resume(t)
        }
      }

      if (firebaseError == null) {
        PasswordResetResult.Success(
          "تم إرسال رابط إعادة تعيين كلمة المرور إلى $email بنجاح ✓ يرجى فحص صندوق الوارد (Check your inbox)."
        )
      } else {
        val errMsg = when (firebaseError) {
          is FirebaseAuthInvalidUserException ->
            "هذا البريد الإلكتروني ($email) غير مسجل — يرجى التأكد من البريد الإلكتروني"
          is FirebaseAuthInvalidCredentialsException ->
            "صيغة البريد الإلكتروني غير صحيحة ($email)"
          else -> {
            val detail = firebaseError.localizedMessage?.take(120).orEmpty()
            if (localAccount == null) {
              "تعذر إرسال بريد استعادة كلمة المرور ($email غير مسجل أو فشل الاتصال: $detail)"
            } else {
              "فشل إرسال رابط استعادة كلمة المرور عبر Firebase: $detail"
            }
          }
        }
        PasswordResetResult.Error(errMsg)
      }
    } catch (e: Exception) {
      PasswordResetResult.Error(
        "فشل إرسال طلب استعادة كلمة المرور: ${e.localizedMessage ?: "تحقق من اتصال الإنترنت"}"
      )
    }
  }

  suspend fun resetAccountPassword(
    rawEmail: String,
    newPassword: String,
    confirmPassword: String
  ): AuthActionResult = withContext(Dispatchers.IO) {
    val email = rawEmail.trim().lowercase()
    if (!emailRegex.matches(email)) {
      return@withContext AuthActionResult.Error("يرجى إدخال البريد الإلكتروني المسجل (مثل savant47@gmail.com)")
    }
    if (newPassword.length < 6) {
      return@withContext AuthActionResult.Error("كلمة المرور الجديدة يجب أن تكون 6 أحرف على الأقل")
    }
    if (newPassword != confirmPassword) {
      return@withContext AuthActionResult.Error("كلمتا المرور غير متطابقتين")
    }

    val account = dao.findByEmail(email)
      ?: return@withContext AuthActionResult.Error("لا يوجد حساب مسجل بهذا البريد الإلكتروني — يمكنك إنشاء حساب جديد")

    val salt = PasswordSecurity.generateSaltBase64()
    val hash = PasswordSecurity.hashPassword(newPassword, salt)
    val now = System.currentTimeMillis()
    dao.updatePassword(account.id, hash, salt, now)
    val updated = account.copy(
      passwordHash = hash,
      passwordSalt = salt,
      lastLoginAtMs = now
    )
    sessionPrefs.edit().putLong(KEY_ACTIVE_ACCOUNT_ID, updated.id).apply()
    _currentAccountFlow.value = updated
    AuthActionResult.Success(updated)
  }

  fun logout() {
    sessionPrefs.edit().remove(KEY_ACTIVE_ACCOUNT_ID).apply()
    _currentAccountFlow.value = null
  }

  suspend fun deleteAccount(accountId: Long) = withContext(Dispatchers.IO) {
    dao.deleteAccountById(accountId)
    if (_currentAccountFlow.value?.id == accountId) {
      sessionPrefs.edit().remove(KEY_ACTIVE_ACCOUNT_ID).apply()
      _currentAccountFlow.value = null
    }
  }

  suspend fun saveTraktConfigForCurrentAccount(
    clientId: String,
    clientSecret: String,
    accessToken: String,
    refreshToken: String,
    expiresAtMs: Long,
    traktUsername: String
  ) = withContext(Dispatchers.IO) {
    val active = _currentAccountFlow.value ?: return@withContext
    dao.updateTraktCredentials(
      id = active.id,
      clientId = clientId.trim(),
      clientSecret = clientSecret.trim(),
      accessToken = accessToken.trim(),
      refreshToken = refreshToken.trim(),
      expiresAtMs = expiresAtMs,
      traktUsername = traktUsername.trim()
    )
    _currentAccountFlow.value = active.copy(
      traktClientId = clientId.trim(),
      traktClientSecret = clientSecret.trim(),
      traktAccessToken = accessToken.trim(),
      traktRefreshToken = refreshToken.trim(),
      traktTokenExpiresAtMs = expiresAtMs,
      traktUsername = traktUsername.trim()
    )
  }

  companion object {
    private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id_v1"
  }
}
