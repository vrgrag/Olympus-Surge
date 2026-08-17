package com.olympussurge.game.atrium.push

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.olympussurge.game.R
import com.olympussurge.game.SanctumApplication
import com.olympussurge.game.atrium.SanctumStageActivity
import com.olympussurge.game.ui.OlympusTheme

/**
 * Pre-prompt shown once, right before the platform alerts dialog on the
 * very first entry to the remote panel. Layout rules (per operator brief,
 * spec §12):
 *
 *   • Both buttons are visually identical (same colour, border, size,
 *     shape). Accept sits first (left / top), Skip second — never a
 *     subdued text link.
 *   • Landscape: side-by-side Row, geometrically centred. NO horizontal
 *     safe-area padding — that would shift the pair off the artwork's
 *     centre line. Only the status-bar inset is added on top so the
 *     buttons never hide under a notch that lands at the very top.
 *   • Portrait: stacked Column, fixed-width buttons.
 *
 * Whatever the user picks we always advance to the stage.
 */
class OracleConsentActivity : ComponentActivity() {

    private val permissionRequester = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        vault().systemAlertPrompted = true
        proceed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw under any display cutout — the artwork bleeds all the
        // way to the top edge. The sibling stage (WebView) still pads
        // around the notch: that surface hosts partner-owned UI that
        // must not disappear under hardware.
        allowDrawUnderCutout()
        enableEdgeToEdge()
        goImmersive()
        setContent {
            OlympusTheme {
                ConsentScreen(onAccept = ::onAcceptTapped, onSkip = ::onSkipTapped)
            }
        }
    }

    private fun onAcceptTapped() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionRequester.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vault().systemAlertPrompted = true
            proceed()
        }
    }

    private fun onSkipTapped() {
        vault().consentDeclinedAt = System.currentTimeMillis()
        proceed()
    }

    private fun proceed() {
        val url = intent.getStringExtra(EXTRA_STAGE_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }
        startActivity(SanctumStageActivity.newIntent(this, url))
        finish()
    }

    private fun goImmersive() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun allowDrawUnderCutout() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val target = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = target
        }
    }

    private fun vault() = (application as SanctumApplication).vault

    companion object {
        private const val EXTRA_STAGE_URL = "sanctum.extra.consent_url"

        fun newIntent(context: Context, url: String): Intent =
            Intent(context, OracleConsentActivity::class.java).apply {
                putExtra(EXTRA_STAGE_URL, url)
            }
    }
}

@Composable
private fun ConsentScreen(onAccept: () -> Unit, onSkip: () -> Unit) {
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E22)),
    ) {
        // Bleed all the way to the top edge — the activity is
        // extended under the display cutout so the artwork paints
        // over the notch region. The buttons live in the bottom half
        // (see `Arrangement.Bottom` below) so no content overlaps
        // hardware.
        Image(
            painter = painterResource(R.drawable.bg_notifications),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        val bottomInset = if (landscape) 28.dp else 44.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomInset),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (landscape) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        16.dp,
                        Alignment.CenterHorizontally,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ConsentButton(label = "Accept", onClick = onAccept, widthDp = 180)
                    ConsentButton(label = "Skip", onClick = onSkip, widthDp = 180)
                }
            } else {
                ConsentButton(label = "Accept", onClick = onAccept, widthDp = 280)
                Spacer(Modifier.height(14.dp))
                ConsentButton(label = "Skip", onClick = onSkip, widthDp = 280)
            }
        }
    }
}

@Composable
private fun ConsentButton(label: String, onClick: () -> Unit, widthDp: Int) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFFC93B),
            contentColor = Color(0xFF15112A),
        ),
        border = BorderStroke(2.dp, Color(0xFF15112A)),
        modifier = Modifier
            .width(widthDp.dp)
            .height(56.dp)
            .border(
                width = 3.dp,
                color = Color.White,
                shape = RoundedCornerShape(28.dp),
            ),
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
