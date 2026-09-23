package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.MediaCatalogRepository
import com.example.data.SortOption
import com.example.data.TvPreferencesRepository
import com.example.model.StreamingPortal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    assertEquals("Cinema TV", appName)
  }

  @Test
  fun `verify catalog search by portal`() {
    val topCinemaItems = MediaCatalogRepository.getTopCinemaItems()
    val watchSeriesItems = MediaCatalogRepository.getWatchSeriesItems()

    assertTrue("TopCinema items should not be empty", topCinemaItems.isNotEmpty())
    assertTrue("WatchSeries items should not be empty", watchSeriesItems.isNotEmpty())

    topCinemaItems.forEach {
      assertEquals(StreamingPortal.TOP_CINEMA, it.portal)
    }

    watchSeriesItems.forEach {
      assertEquals(StreamingPortal.WATCH_SERIES, it.portal)
    }
  }

  @Test
  fun `verify advanced search and filter`() {
    val searchResults = MediaCatalogRepository.filterAndSearch(
      query = "Dune",
      portal = StreamingPortal.TOP_CINEMA,
      genre = "أكشن",
      year = "2024",
      minRating = 8.0,
      sortBy = SortOption.TOP_RATED
    )

    assertTrue("Results for Dune should be found", searchResults.isNotEmpty())
    val item = searchResults.first()
    assertEquals("Dune: Part Two", item.title)
    assertTrue("Rating should be >= 8.0", item.rating >= 8.0)
    assertTrue("Should contain Action genre", item.genres.contains("أكشن"))
    assertNotNull("Stream URL should not be null", item.defaultStreamUrl)
    assertTrue("Should have servers", item.servers.isNotEmpty())
  }

  @Test
  fun `verify bookmarks and history in preferences`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = TvPreferencesRepository(context)

    val added = repository.addBookmark("Test Movie", "https://topcinema.io/home1/movie1", StreamingPortal.TOP_CINEMA)
    assertTrue("Bookmark should be added", added)
    assertTrue("Should be marked as bookmarked", repository.isBookmarked("https://topcinema.io/home1/movie1"))

    repository.addHistory("Watched Episode", "https://watchseries.bar/ep1", StreamingPortal.WATCH_SERIES)
    assertTrue("History should contain entry", repository.historyFlow.value.any { it.url == "https://watchseries.bar/ep1" })
  }

  @Test
  fun `verify recently watched progress tracking and resume`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = TvPreferencesRepository(context)

    // Verify initial seed or empty list
    val initialWatchList = repository.watchProgressFlow.value
    assertTrue("Should have initial recently watched entries", initialWatchList.isNotEmpty())

    val testMedia = MediaCatalogRepository.allItems.first()
    // Save progress: 30 minutes in out of 120 minutes (7,200,000 ms)
    val position = 30 * 60 * 1000L
    val duration = 120 * 60 * 1000L
    repository.saveWatchProgress(testMedia, position, duration)

    val updatedProgress = repository.getWatchProgress(testMedia.id)
    assertNotNull("Saved progress should be found", updatedProgress)
    assertEquals(testMedia.id, updatedProgress?.mediaId)
    assertEquals(position, updatedProgress?.positionMs)
    assertEquals(duration, updatedProgress?.durationMs)
    assertEquals(25, updatedProgress?.progressPercent)
    assertEquals(0.25f, updatedProgress?.progressFraction ?: 0f, 0.01f)
    assertEquals(90 * 60 * 1000L, updatedProgress?.remainingMs)

    // Remove from history
    repository.removeWatchProgress(testMedia.id)
    assertEquals(null, repository.getWatchProgress(testMedia.id))
  }
}
