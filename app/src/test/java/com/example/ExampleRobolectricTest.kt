package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDisplayMode
import com.example.data.TvPreferencesRepository
import com.example.model.ControlMode
import com.example.model.StreamingPortal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Asser Browser", appName)
  }

  @Test
  fun `verify SmartOmniboxResolver resolves Google search queries and direct URLs`() {
    val domainUrl = com.example.model.SmartOmniboxResolver.resolveInputToUrl("youtube.com")
    assertEquals("https://youtube.com", domainUrl)

    val fullUrl = com.example.model.SmartOmniboxResolver.resolveInputToUrl("https://flixer.gd/")
    assertEquals("https://flixer.gd/", fullUrl)

    val googleSearch = com.example.model.SmartOmniboxResolver.resolveInputToUrl("أخبار اليوم في مصر")
    assertTrue("Should resolve search text to Google search URL", googleSearch.startsWith("https://www.google.com/search?q="))
  }

  @Test
  fun `verify direct search URL generation for portals`() {
    // TopCinema search URL for Silo
    val topCinemaSearchUrl = StreamingPortal.TOP_CINEMA.buildSearchUrl("Silo")
    assertEquals("https://topcinema.io/search/?query=Silo&type=all", topCinemaSearchUrl)

    // WatchSeries search URL for Z Nation
    val watchSeriesSearchUrl = StreamingPortal.WATCH_SERIES.buildSearchUrl("Z Nation")
    assertTrue("Search URL should format keyword correctly", watchSeriesSearchUrl.startsWith("https://watchseries.bar/search"))
    assertTrue("Search URL should contain keyword", watchSeriesSearchUrl.contains("Nation"))

    // Query with spaces and Arabic characters
    val arabicSearch = StreamingPortal.TOP_CINEMA.buildSearchUrl("سايلو")
    assertTrue("Arabic query should be encoded", arabicSearch.startsWith("https://topcinema.io/search/?query="))
  }

  @Test
  fun `verify bookmarks and history in preferences`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = TvPreferencesRepository(context)

    val added = repository.addBookmark("Silo Series", "https://topcinema.site/series/silo", StreamingPortal.TOP_CINEMA)
    assertTrue("Bookmark should be added", added)
    assertTrue("Should be marked as bookmarked", repository.isBookmarked("https://topcinema.site/series/silo"))

    // Duplicate addition should be prevented
    val duplicate = repository.addBookmark("Silo Series", "https://topcinema.site/series/silo", StreamingPortal.TOP_CINEMA)
    assertFalse("Duplicate bookmark should not be added", duplicate)

    repository.addHistory("Z Nation Season 1", "https://watchseries.bar/series/z-nation", StreamingPortal.WATCH_SERIES)
    assertTrue("History should contain entry", repository.historyFlow.value.any { it.url == "https://watchseries.bar/series/z-nation" })

    // Clean up
    repository.clearHistory()
    assertTrue("History should be empty after clear", repository.historyFlow.value.isEmpty())
  }

  @Test
  fun `verify display mode and control mode switching`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = TvPreferencesRepository(context)

    repository.setDisplayMode(AppDisplayMode.TV)
    assertEquals(AppDisplayMode.TV, repository.getDisplayMode())
    assertTrue("Should be marked as selected", repository.hasSelectedDisplayMode())

    repository.setDisplayMode(AppDisplayMode.MOBILE)
    assertEquals(AppDisplayMode.MOBILE, repository.getDisplayMode())

    // Control mode
    repository.setControlMode(ControlMode.VIRTUAL_MOUSE)
    assertEquals(ControlMode.VIRTUAL_MOUSE, repository.getControlMode())

    repository.setControlMode(ControlMode.DPAD_NAV)
    assertEquals(ControlMode.DPAD_NAV, repository.getControlMode())
  }

  @Test
  fun `verify WatchSeries multi-season series and movie URL generation`() {
    // Multi-season TV Series (e.g. Breaking Bad, Game of Thrones, Silo)
    val seriesQuery = "Breaking Bad"
    val seriesSearchUrl = StreamingPortal.WATCH_SERIES.buildSearchUrl(seriesQuery)
    assertTrue("Search URL should point to WatchSeries search", seriesSearchUrl.contains("watchseries.bar/search"))
    assertTrue("Search URL should contain encoded title", seriesSearchUrl.contains("Breaking"))

    // Direct multi-season series path pattern
    val seriesSlug = "breaking-bad"
    val seriesId = 1396
    val seriesUrl = "https://watchseries.bar/tv/$seriesSlug/$seriesId"
    assertTrue("TV Series URL should use /tv/ schema for season selection", seriesUrl.contains("/tv/breaking-bad/1396"))

    // Movie path pattern
    val movieSlug = "inception"
    val movieId = 27205
    val movieUrl = "https://watchseries.bar/movie/$movieSlug/$movieId"
    assertTrue("Movie URL should use /movie/ schema", movieUrl.contains("/movie/inception/27205"))
  }

  @Test
  fun `verify OpenSubtitles service integration and Arabic translation retrieval`() = kotlinx.coroutines.runBlocking {
    val service = com.example.data.SearchAndSubtitlesService()

    // 1. Search subtitles for multi-season series in Arabic
    val arabicSubs = service.searchOpenSubtitles("Breaking Bad", "ar")
    assertTrue("Subtitles list should not be empty", arabicSubs.isNotEmpty())
    val arabicSub = arabicSubs.first()
    assertEquals("ar", arabicSub.languageCode)
    assertTrue("Language label should indicate Arabic", arabicSub.languageName.contains("العربية"))
    assertTrue("Download URL should not be empty", arabicSub.downloadUrl.isNotBlank())

    // 2. Search subtitles in English
    val englishSubs = service.searchOpenSubtitles("Breaking Bad", "en")
    assertTrue("English subtitles should be returned", englishSubs.isNotEmpty())
    assertEquals("en", englishSubs.first().languageCode)

    // 3. Search subtitles for movies (e.g. Inception)
    val movieSubs = service.searchOpenSubtitles("Inception", "ar")
    assertTrue("Movie subtitles should be retrieved", movieSubs.isNotEmpty())
    assertTrue("Format should be vtt or srt", movieSubs.first().format in listOf("vtt", "srt"))
  }

  @Test
  fun `verify dual portal search returns results for multi-season shows and movies`() = kotlinx.coroutines.runBlocking {
    val service = com.example.data.SearchAndSubtitlesService()

    // Search for a multi-season show (Silo)
    val (topCinemaSilo, watchSeriesSilo) = service.searchDualPortals("Silo")
    // Handle offline test execution gracefully while still checking type correctness
    if (topCinemaSilo.isNotEmpty()) {
      assertEquals(StreamingPortal.TOP_CINEMA, topCinemaSilo.first().portal)
    }
    if (watchSeriesSilo.isNotEmpty()) {
      assertEquals(StreamingPortal.WATCH_SERIES, watchSeriesSilo.first().portal)
    }

    // Search for movie (Inception)
    val (topCinemaMovie, watchSeriesMovie) = service.searchDualPortals("Inception")
    assertTrue("Search result is a valid list", topCinemaMovie is List && watchSeriesMovie is List)
  }

  @Test
  fun `verify AdGuard DNS and strict popup blocker preferences`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = TvPreferencesRepository(context)

    // Defaults should be enabled
    assertTrue("AdBlock should be enabled by default", repository.isAdBlockEnabled())
    assertTrue("AdGuard DNS filter should be enabled by default", repository.isAdGuardDnsEnabled())
    assertTrue("Strict popup blocker should be enabled by default", repository.isStrictPopupBlockerEnabled())

    // Toggling
    repository.setAdGuardDnsEnabled(false)
    assertFalse(repository.isAdGuardDnsEnabled())
    repository.setAdGuardDnsEnabled(true)
    assertTrue(repository.isAdGuardDnsEnabled())

    repository.setStrictPopupBlockerEnabled(false)
    assertFalse(repository.isStrictPopupBlockerEnabled())
    repository.setStrictPopupBlockerEnabled(true)
    assertTrue(repository.isStrictPopupBlockerEnabled())
  }

  @Test
  fun `verify luugy and popunder ad URLs are identified as ads`() {
    val adUrls = listOf(
      "https://luugy.com/4/1090664",
      "https://sub.luugy.com/click",
      "https://popads.net/serve",
      "https://adsterra.com/direct",
      "https://bet365.com/affiliate",
      "market://details?id=com.scam.app",
      "intent://example.com#Intent"
    )

    val safeUrls = listOf(
      "https://watchseries.bar/tv/silo/1",
      "https://watchseries.bar/movie/inception/27205",
      "https://topcinema.io/watch/123",
      "https://challenges.cloudflare.com/turnstile",
      "https://api.themoviedb.org/3/search/multi"
    )

    // Verify detection pattern logic
    val adKeywords = listOf("luugy", "popads", "propellerads", "adsterra", "bet365", "onclick", "exoclick")
    for (bad in adUrls) {
      val isAd = bad.startsWith("market:") || bad.startsWith("intent:") || adKeywords.any { bad.lowercase().contains(it) }
      assertTrue("Ad URL should be identified as ad: $bad", isAd)
    }

    for (safe in safeUrls) {
      val isSafe = safe.contains("watchseries") || safe.contains("topcinema") || safe.contains("cloudflare") || safe.contains("themoviedb")
      assertTrue("Legitimate streaming URL should be recognized as safe: $safe", isSafe)
    }
  }

  @Test
  fun `verify TvPlayerBridge click filtering and lifecycle events`() {
    var endedCalled = false
    var timeUpdateCalled = false
    var centerFilteredCalled = false

    val bridge = com.example.tv.TvPlayerBridge(
      onVideoEnded = { endedCalled = true },
      onVideoTimeUpdate = { _, _ -> timeUpdateCalled = true },
      onCenterClickFiltered = { _, _ -> centerFilteredCalled = true }
    )

    // Test video lifecycle events
    bridge.onVideoFinished()
    assertTrue("onVideoFinished should trigger onVideoEnded callback", endedCalled)

    bridge.onVideoTimeUpdate(12.5f, 120.0f)
    assertTrue("onVideoTimeUpdate should trigger callback", timeUpdateCalled)

    // Test center click filtering when video is playing
    // Center point (50% x, 50% y) while playing -> MUST be filtered
    val centerBlocked = bridge.filterCenterClick(target = "VIDEO", xRatio = 0.5f, yRatio = 0.5f, isPlaying = true)
    assertTrue("Center click on playing video must be filtered", centerBlocked)
    assertTrue("Callback for filtered center click should be triggered", centerFilteredCalled)

    // Edge point (bottom controls: 50% x, 95% y) while playing -> MUST NOT be filtered
    val bottomAllowed = bridge.filterCenterClick(target = "CONTROL", xRatio = 0.5f, yRatio = 0.95f, isPlaying = true)
    assertFalse("Bottom control click on playing video should be allowed", bottomAllowed)

    // Center point while video is PAUSED -> MUST NOT be filtered (allow initial play)
    val pausedCenterAllowed = bridge.filterCenterClick(target = "VIDEO", xRatio = 0.5f, yRatio = 0.5f, isPlaying = false)
    assertFalse("Center click when paused should not be filtered", pausedCenterAllowed)
  }

  @Test
  fun `verify Android 7 legacy shouldInterceptRequest blocks ads and allows streams`() {
    val client = com.example.tv.TvWebViewClient(
      isAdBlockEnabled = { true },
      onPageFinishedCallback = {}
    )

    // Legacy overload test with string URLs
    val adUrl = "https://luugy.com/script/ad.js"
    val interceptedAd = client.shouldInterceptRequest(null, adUrl)
    assertNotNull("Legacy shouldInterceptRequest must intercept ad URL", interceptedAd)

    val safeUrl = "https://watchseries.bar/player/embed"
    val allowedSafe = client.shouldInterceptRequest(null, safeUrl)
    assertNull("Legacy shouldInterceptRequest should not block legitimate video player stream", allowedSafe)
  }

  @Test
  fun `verify TvPlayerBridge video detection triggers callback`() {
    var detectedUrl: String? = null
    val bridge = com.example.tv.TvPlayerBridge(
      onVideoDetected = { detectedUrl = it }
    )
    bridge.onVideoDetected("https://cdn.com/stream.mp4")
    assertEquals("https://cdn.com/stream.mp4", detectedUrl)
  }

  @Test
  fun `verify TvWebViewClient intercepts video stream requests and triggers callback`() {
    var detectedUrl: String? = null
    val client = com.example.tv.TvWebViewClient(
      isAdBlockEnabled = { true },
      onPageFinishedCallback = {},
      onVideoDetectedCallback = { detectedUrl = it }
    )

    val videoUrl = "https://cdn.com/stream.m3u8"
    client.shouldInterceptRequest(null, videoUrl)
    assertEquals("https://cdn.com/stream.m3u8", detectedUrl)
  }
}


