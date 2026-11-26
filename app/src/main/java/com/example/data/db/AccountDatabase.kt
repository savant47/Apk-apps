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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

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

    val account = dao.findByEmail(email)
      ?: return@withContext AuthActionResult.Error("البريد الإلكتروني أو كلمة المرور غير صحيحة")

    val valid = PasswordSecurity.verifyPassword(password, account.passwordSalt, account.passwordHash)
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
