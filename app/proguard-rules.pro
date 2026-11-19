# Asser TV — ProGuard / R8 Keep Rules for 1GB RAM Shrunk Builds

-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep WebView @JavascriptInterface bridges (e.g. AndroidTvBridge / TvPlayerBridge)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.example.ui.TvPlayerBridge { *; }
-keepclassmembers class com.example.ui.TvPlayerBridge {
    public *;
}

# Keep Kotlin data models & enums used in JSON / preferences / Compose state
-keep class com.example.model.** { *; }
-keep class com.example.data.PlaybackProgressRecord { *; }
-keep class com.example.data.AppDisplayMode { *; }

# Media3 / ExoPlayer (HLS, DASH, SmoothStreaming, OkHttpDataSource, UI)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp & Okio
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Jsoup HTML parser (Phase 2 fast HTML scraper)
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-dontwarn javax.annotation.**

# Coil ImageLoader
-keep class coil.** { *; }
-dontwarn coil.**
