package com.example

import com.example.data.AppDiagnosticsLogger
import com.example.data.SearchAndSubtitlesService
import com.example.data.db.PasswordSecurity
import com.example.model.SearchResultItem
import com.example.model.StreamingPortal
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class Android7CompatibilityUnitTest {

  @Test
  fun pbkdf2HmacSha256Fallback_matchesStandardSecretKeyFactory_byteForByte() {
    val passwords = listOf("000000", "AsserTV@2026!", "كلمة_مرور_123")
    val salt = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
    val iterations = 1_000
    val keyLengthBits = 256

    for (pwd in passwords) {
      val spec = PBEKeySpec(pwd.toCharArray(), salt, iterations, keyLengthBits)
      val expected = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(spec)
        .encoded
      val actual = PasswordSecurity.pbkdf2HmacSha256Fallback(
        password = pwd.toCharArray(),
        salt = salt,
        iterations = iterations,
        keyLengthBytes = keyLengthBits / 8
      )
      assertArrayEquals("PBKDF2 fallback must match JCE PBKDF2WithHmacSHA256 for '$pwd'", expected, actual)
    }
  }

  @Test
  fun pasteRsSanitizer_enforcesUtf8ByteLimitAndStripsControlBytes() {
    val head = "=== FATAL CRASH REPORT ===\nException: Test\n"
    val arabicBody = "⚠️ سجل عربي طويل جداً مع رموز تعبيرية 🎬🔥\u0000\u0007\r\n".repeat(4_000)
    val tail = "\n=== END_TAIL_MARKER ==="
    val raw = head + arabicBody + tail

    val maxBytes = 16 * 1024
    val sanitizedBytes = AppDiagnosticsLogger.sanitizeAndTrimForPasteRs(raw, maxUtf8Bytes = maxBytes)
    assertTrue("Trimmed payload must not exceed maxUtf8Bytes", sanitizedBytes.size <= maxBytes)

    val text = String(sanitizedBytes, Charsets.UTF_8)
    assertFalse("NUL bytes must be stripped", text.contains('\u0000'))
    assertFalse("BEL control bytes must be stripped", text.contains('\u0007'))
    assertFalse("CR must be normalized to LF", text.contains('\r'))
    assertTrue("Crash header must be preserved", text.startsWith("=== FATAL CRASH REPORT ==="))
    assertTrue("Latest tail marker must be preserved", text.endsWith("=== END_TAIL_MARKER ==="))
  }

  @Test
  fun pasteRsErrorSummary_extractsReadableBodyFromRocketHtml() {
    val rocketHtml = """
      <!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><meta name="color-scheme" content="light dark"><title>400 Bad Request</title></head>
      <body><h1>400: Bad Request</h1><p>The request could not be understood by the server due to malformed syntax.</p></body></html>
    """.trimIndent()
    val summary = AppDiagnosticsLogger.extractPasteRsErrorSummary(rocketHtml)
    assertFalse("Summary should not contain head meta tags", summary.contains("color-scheme"))
    assertTrue("Summary should contain readable body text", summary.contains("400: Bad Request"))
  }

  @Test
  fun syntheticFallbackResultDetection_distinguishesRealFromFallbackHits() {
    val fallbackItem = SearchResultItem(
      id = "tc_direct_1",
      title = "The Good Place - مشاهدة مباشرة",
      url = "https://topcinema.io/search/?query=The+Good+Place",
      portal = StreamingPortal.TOP_CINEMA,
      posterUrl = ""
    )
    val realItem = SearchResultItem(
      id = "a1b2c3d4-real-id",
      title = "مسلسل The Good Place الموسم الثاني",
      url = "https://topcinemaa.tv/series/the-good-place-s2/",
      portal = StreamingPortal.TOP_CINEMA,
      posterUrl = ""
    )
    assertTrue(SearchAndSubtitlesService.isSyntheticFallbackResult(fallbackItem))
    assertFalse(SearchAndSubtitlesService.isSyntheticFallbackResult(realItem))
    assertEquals(StreamingPortal.TOP_CINEMA, realItem.portal)
  }
}
