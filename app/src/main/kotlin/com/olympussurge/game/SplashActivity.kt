package com.olympussurge.game

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.olympussurge.game.ui.OlympusTheme
import com.olympussurge.game.ui.SplashScreen

/**
 * Launch screen. Warms the app up and hands over to [MainActivity].
 *
 * Unlike the rest of the app this activity follows the device, because the key
 * art ships in both orientations and a player who starts with the phone upright
 * should see the upright piece rather than a rotated crop.
 */
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        goFullscreen()
        setContent {
            OlympusTheme {
                SplashScreen(onReady = ::openMenu)
            }
        }
    }

    private fun openMenu() {
        // Cross-fade rather than a slide: the menu shares the splash artwork, so
        // sliding would read as two different places.
        val transition = ActivityOptions.makeCustomAnimation(
            this,
            android.R.anim.fade_in,
            android.R.anim.fade_out,
        )
        startActivity(Intent(this, MainActivity::class.java), transition.toBundle())
        // Dropped from the stack so back from the menu never lands here again.
        finish()
    }

    private fun goFullscreen() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
