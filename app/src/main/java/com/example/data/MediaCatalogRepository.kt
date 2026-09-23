package com.example.data

import com.example.model.MediaCategory
import com.example.model.MediaItem
import com.example.model.StreamServer
import com.example.model.StreamingPortal

enum class SortOption(val arabicLabel: String) {
  LATEST("الأحدث"),
  TOP_RATED("الأعلى تقييماً"),
  POPULAR("الأكثر مشاهدة")
}

object MediaCatalogRepository {

  // Test streams that stream smoothly over Android MediaPlayer
  private const val STREAM_BIG_BUCK_BUNNY = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
  private const val STREAM_SINTEL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
  private const val STREAM_TEARS_OF_STEEL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
  private const val STREAM_ELEPHANTS_DREAM = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"

  val allItems: List<MediaItem> = listOf(
    // 1. Dune Part Two (TopCinema)
    MediaItem(
      id = "tc_dune2",
      title = "Dune: Part Two",
      arabicTitle = "كثيب: الجزء الثاني",
      portal = StreamingPortal.TOP_CINEMA,
      category = MediaCategory.MOVIE,
      genres = listOf("أكشن", "خيال علمي", "مغامرة"),
      year = 2024,
      rating = 8.6,
      qualityBadge = "4K UHD",
      duration = "2س 46د",
      description = "يواصل بول أتريدس رحلته الأسطورية رفقة تشاني وفريمن بينما يسعى للانتقام من المتآمرين الذين دمروا عائلته ويواجه مصير الكون بأسره.",
      posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&q=80",
      backdropUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1280&q=80",
      portalUrl = "https://topcinema.io/home1/",
      defaultStreamUrl = STREAM_TEARS_OF_STEEL
    ),

    // 2. House of the Dragon (WatchSeries)
    MediaItem(
      id = "ws_hotd",
      title = "House of the Dragon",
      arabicTitle = "آل التنين (الموسم 2)",
      portal = StreamingPortal.WATCH_SERIES,
      category = MediaCategory.SERIES,
      genres = listOf("أكشن", "دراما", "فانتازيا"),
      year = 2024,
      rating = 8.5,
      qualityBadge = "1080p FHD",
      duration = "موسم 2 • 8 حلقات",
      description = "الصراع يشتعل بين عائلة تارغاريين على العرش الحديدي بعد وفاة الملك فيسيريس، في حرب أهلية دامية تعرف برقصة التنانين.",
      posterUrl = "https://images.unsplash.com/photo-1514539079130-25950c84af65?w=500&q=80",
      backdropUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1280&q=80",
      portalUrl = "https://watchseries.bar",
      defaultStreamUrl = STREAM_SINTEL
    ),

    // 3. Shogun (WatchSeries & TopCinema)
    MediaItem(
      id = "ws_shogun",
      title = "Shōgun",
      arabicTitle = "شوغون",
      portal = StreamingPortal.WATCH_SERIES,
      category = MediaCategory.SERIES,
      genres = listOf("دراما", "تاريخي", "مغامرة"),
      year = 2024,
      rating = 8.8,
      qualityBadge = "4K UHD",
      duration = "موسم 1 • 10 حلقات",
      description = "تدور الأحداث في اليابان الإقطاعية عام 1600، حيث يجد بحار إنجليزي نفسه متورطاً في صراع سياسي دموي يقوده اللورد توراناغا.",
      posterUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80",
      backdropUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1280&q=80",
      portalUrl = "https://watchseries.bar",
      defaultStreamUrl = STREAM_BIG_BUCK_BUNNY
    ),

    // 4. ولاد رزق 3 (TopCinema)
    MediaItem(
      id = "tc_welad_rizk3",
      title = "Welad Rizk 3: El Qadya",
      arabicTitle = "ولاد رزق 3: القاضية",
      portal = StreamingPortal.TOP_CINEMA,
      category = MediaCategory.MOVIE,
      genres = listOf("أكشن", "جريمة", "إثارة"),
      year = 2024,
      rating = 7.9,
      qualityBadge = "1080p FHD",
      duration = "2س 10د",
      description = "يعود الأخوة الأربعة مجدداً لخوض مغامرة خطيرة في العاصمة السعودية الرياض لتنفيذ عملية استيلاء غير مسبوقة.",
      posterUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&q=80",
      backdropUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=1280&q=80",
      portalUrl = "https://topcinema.io/home1/",
      defaultStreamUrl = STREAM_TEARS_OF_STEEL
    ),

    // 5. الحشاشين (TopCinema)
    MediaItem(
      id = "tc_hashashin",
      title = "El Hashashin",
      arabicTitle = "الحشاشين",
      portal = StreamingPortal.TOP_CINEMA,
      category = MediaCategory.SERIES,
      genres = listOf("تاريخي", "دراما", "إثارة"),
      year = 2024,
      rating = 8.7,
      qualityBadge = "4K UHD",
      duration = "موسم 1 • 30 حلقة",
      description = "قصة تأسيس جماعة الحشاشين بقيادة حسن الصباح في قلعة ألموت، وكيف فرضت الرعب على ملوك وسلاطين الشرق في القرن الحادي عشر.",
      posterUrl = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=500&q=80",
      backdropUrl = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=1280&q=80",
      portalUrl = "https://topcinema.io/home1/",
      defaultStreamUrl = STREAM_SINTEL
    ),

    // 6. The Boys (WatchSeries)
    MediaItem(
      id = "ws_the_boys4",
      title = "The Boys",
      arabicTitle = "الرفاق (The Boys)",
      portal = StreamingPortal.WATCH_SERIES,
      category = MediaCategory.SERIES,
      genres = listOf("أكشن", "كوميديا سوداء", "خيال علمي"),
      year = 2024,
      rating = 8.7,
      qualityBadge = "4K UHD",
      duration = "الموسم 4 • 8 حلقات",
      description = "تزداد الفوضى وسيطرة هوملاندر بينما تسعى فيكتوريا نيومان نحو الرئاسة، ويحاول بيلي بوتشر وفريقه التصدي للتهديد المحدق.",
      posterUrl = "https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?w=500&q=80",
      backdropUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1280&q=80",
      portalUrl = "https://watchseries.bar",
      defaultStreamUrl = STREAM_BIG_BUCK_BUNNY
    ),

    // 7. Oppenheimer (TopCinema)
    MediaItem(
      id = "tc_oppenheimer",
      title = "Oppenheimer",
      arabicTitle = "أوبنهايمر",
      portal = StreamingPortal.TOP_CINEMA,
      category = MediaCategory.MOVIE,
      genres = listOf("دراما", "تاريخي", "سيرة ذاتية"),
      year = 2023,
      rating = 8.9,
      qualityBadge = "4K IMAX",
      duration = "3س 00د",
      description = "ملحمة المخرج كريستوفر نولان حول العالم جيه روبرت أوبنهايمر ودوره المحوري في مشروع مانهاتن وصنع أول قنبلة ذرية في التاريخ.",
      posterUrl = "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?w=500&q=80",
      backdropUrl = "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?w=1280&q=80",
      portalUrl = "https://topcinema.io/home1/",
      defaultStreamUrl = STREAM_TEARS_OF_STEEL
    ),

    // 8. Demon Slayer: Hashira Training (WatchSeries & TopCinema)
    MediaItem(
      id = "tc_demon_slayer",
      title = "Demon Slayer: Hashira Training",
      arabicTitle = "قاتل الشياطين: تدريب الهاشيرا",
      portal = StreamingPortal.TOP_CINEMA,
      category = MediaCategory.ANIME,
      genres = listOf("أنمي", "أكشن", "فانتازيا"),
      year = 2024,
      rating = 8.5,
      qualityBadge = "1080p FHD",
      duration = "موسم 4 • 8 حلقات",
      description = "يخوض تانجيرو وأصدقاؤه تدريبات قاسية تحت إشراف أقوى محاربي الهاشيرا استعداداً للمعركة الفاصلة ضد موزان كيبوتسوجي.",
      posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&q=80",
      backdropUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1280&q=80",
      portalUrl = "https://topcinema.io/home1/",
      defaultStreamUrl = STREAM_BIG_BUCK_BUNNY
    ),

    // 9. Breaking Bad (WatchSeries)
    MediaItem(
      id = "ws_breaking_bad",
      title = "Breaking Bad",
      arabicTitle = "اختلال ضال (Breaking Bad)",
      portal = StreamingPortal.WATCH_SERIES,
      category = MediaCategory.SERIES,
      genres = listOf("جريمة", "دراما", "إثارة"),
      year = 2022,
      rating = 9.5,
      qualityBadge = "1080p Remastered",
      duration = "5 مواسم • 62 حلقة",
      description = "قصة معلم الكيمياء والتر وايت وتحوله الأسطوري إلى هايزنبرغ في عالم صناعة المخدرات بالتعاون مع تلميذه السابق جيسي بينكمان.",
      posterUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500&q=80",
      backdropUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=1280&q=80",
      portalUrl = "https://watchseries.bar",
      defaultStreamUrl = STREAM_SINTEL
    ),

    // 10. Interstellar (TopCinema)
    MediaItem(
      id = "tc_interstellar",
      title = "Interstellar",
      arabicTitle = "بين النجوم (Interstellar)",
      portal = StreamingPortal.TOP_CINEMA,
      category = MediaCategory.MOVIE,
      genres = listOf("خيال علمي", "مغامرة", "دراما"),
      year = 2020,
      rating = 8.7,
      qualityBadge = "4K IMAX",
      duration = "2س 49د",
      description = "فريق من رواد الفضاء يسافر عبر ثقب دودي في محاولة للعثور على موطن جديد للبشرية بعد أن أشرفت الحياة على الأرض على نهايتها.",
      posterUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=500&q=80",
      backdropUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=1280&q=80",
      portalUrl = "https://topcinema.io/home1/",
      defaultStreamUrl = STREAM_ELEPHANTS_DREAM
    ),

    // 11. Stranger Things (WatchSeries)
    MediaItem(
      id = "ws_stranger_things",
      title = "Stranger Things",
      arabicTitle = "أشياء غريبة (Stranger Things)",
      portal = StreamingPortal.WATCH_SERIES,
      category = MediaCategory.SERIES,
      genres = listOf("خيال علمي", "رعب", "دراما"),
      year = 2023,
      rating = 8.7,
      qualityBadge = "4K Dolby Vision",
      duration = "4 مواسم كاملة",
      description = "أحداث غامضة ومخلوقات من العالم المقلوب تجتاح بلدة هوكينز بعد اختفاء طفل صغير في ظروف مرعبة.",
      posterUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500&q=80",
      backdropUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1280&q=80",
      portalUrl = "https://watchseries.bar",
      defaultStreamUrl = STREAM_BIG_BUCK_BUNNY
    ),

    // 12. Attack on Titan (TopCinema)
    MediaItem(
      id = "tc_aot",
      title = "Attack on Titan: The Final Season",
      arabicTitle = "هجوم العمالقة: الموسم الأخير",
      portal = StreamingPortal.TOP_CINEMA,
      category = MediaCategory.ANIME,
      genres = listOf("أنمي", "أكشن", "دراما"),
      year = 2023,
      rating = 9.0,
      qualityBadge = "1080p FHD",
      duration = "الحلقات الخاصة الأخيرة",
      description = "إيرين ييغر يطلق العنان للدمار الشامل لحماية جزيرة باراديس، بينما يتحد أصدقاؤه السابقون في محاولة يائسة لإيقافه وإنقاذ البشرية.",
      posterUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80",
      backdropUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1280&q=80",
      portalUrl = "https://topcinema.io/home1/",
      defaultStreamUrl = STREAM_SINTEL
    )
  )

  fun getFeaturedHero(): MediaItem = allItems.first()

  fun findById(id: String): MediaItem? = allItems.find { it.id == id }

  fun getTopCinemaItems(): List<MediaItem> = allItems.filter { it.portal == StreamingPortal.TOP_CINEMA }

  fun getWatchSeriesItems(): List<MediaItem> = allItems.filter { it.portal == StreamingPortal.WATCH_SERIES }

  fun getTopRated(): List<MediaItem> = allItems.sortedByDescending { it.rating }

  fun getAnimeItems(): List<MediaItem> = allItems.filter { it.category == MediaCategory.ANIME }

  fun filterAndSearch(
    query: String,
    portal: StreamingPortal?,
    genre: String?,
    year: String?,
    minRating: Double?,
    sortBy: SortOption
  ): List<MediaItem> {
    var result = allItems

    if (query.isNotBlank()) {
      val q = query.trim().lowercase()
      result = result.filter {
        it.title.lowercase().contains(q) ||
            it.arabicTitle.contains(q) ||
            it.description.lowercase().contains(q) ||
            it.genres.any { g -> g.lowercase().contains(q) }
      }
    }

    if (portal != null) {
      result = result.filter { it.portal == portal }
    }

    if (!genre.isNullOrBlank() && genre != "الكل") {
      result = result.filter { it.genres.contains(genre) }
    }

    if (!year.isNullOrBlank() && year != "جميع السنوات") {
      when (year) {
        "2025-2026" -> result = result.filter { it.year >= 2025 }
        "2024" -> result = result.filter { it.year == 2024 }
        "2023" -> result = result.filter { it.year == 2023 }
        "2022" -> result = result.filter { it.year == 2022 }
        "كلاسيكيات (2021 وما قبل)" -> result = result.filter { it.year <= 2021 }
      }
    }

    if (minRating != null && minRating > 0.0) {
      result = result.filter { it.rating >= minRating }
    }

    result = when (sortBy) {
      SortOption.LATEST -> result.sortedByDescending { it.year }
      SortOption.TOP_RATED -> result.sortedByDescending { it.rating }
      SortOption.POPULAR -> result.sortedByDescending { it.rating + (it.year * 0.01) }
    }

    return result
  }
}
