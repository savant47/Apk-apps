package com.example

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.data.TvPreferencesRepository
import com.example.ui.ActivityKeyEventCallback
import com.example.ui.KeyEventProvider
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity(), KeyEventProvider {

  private var keyEventCallback: ActivityKeyEventCallback? = null
  private lateinit var repository: TvPreferencesRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    hideSystemBarsForTv()

    repository = TvPreferencesRepository(applicationContext)

    setContent {
      MyApplicationTheme {
        MainScreen(
          repository = repository,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    hideSystemBarsForTv()
  }

  private fun hideSystemBarsForTv() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      window.insetsController?.let { controller ->
        controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      }
    } else {
      @Suppress("DEPRECATION")
      window.decorView.systemUiVisibility = (
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
          or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
          or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
          or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
          or View.SYSTEM_UI_FLAG_FULLSCREEN
      )
    }
  }

  override fun setKeyEventCallback(callback: ActivityKeyEventCallback?) {
    this.keyEventCallback = callback
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    // Intercept remote keys first with custom handler
    if (keyEventCallback?.onDispatchKeyEvent(event) == true) {
      return true
    }
    return super.dispatchKeyEvent(event)
  }
}
