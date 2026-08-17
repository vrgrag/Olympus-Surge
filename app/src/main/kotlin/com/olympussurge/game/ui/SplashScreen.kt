package com.olympussurge.game.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olympussurge.game.R
import com.olympussurge.game.boot.Warmup
import kotlinx.coroutines.delay
import kotlin.system.measureTimeMillis

/** Minimum wall-clock time we hold each warmup step for, in ms.
 *  Sized so 4 real steps × MIN_STEP_MS ≈ the router's worst-case
 *  offline gate (~2.4s TCP probe) — the bar's advancement covers the
 *  whole splash duration on a warm device, not just the first second. */
private const val MIN_STEP_MS = 560L

/**
 * Boot screen shown while the app warms up.
 *
 * The bar tracks [Warmup] step by step, so it only reaches the far right once
 * every step has actually finished; the last sliver is never given away early.
 * Portrait and landscape each get their own key art, picked from the current
 * configuration rather than from a fixed orientation.
 */
@Composable
fun SplashScreen(onReady: () -> Unit) {
    val context = LocalContext.current
    val steps = Warmup.steps
    var completed by remember { mutableIntStateOf(0) }
    var stage by remember { mutableStateOf(steps.first().label) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Enforce a minimum wall-clock duration per step so the bar
        // advances at a readable pace even when the underlying work
        // completes in a few tens of milliseconds. Without this, on a
        // warm device the bar would leap 0 → 92 % in well under a
        // second and then just sit there while the router probed the
        // network — reading to the user as "loaded but stuck".
        for (step in steps) {
            stage = step.label
            val elapsed = measureTimeMillis { step.run(context) }
            val hold = MIN_STEP_MS - elapsed
            if (hold > 0) delay(hold)
            completed++
        }
        stage = "Entering Olympus"
        finished = true
        // The full bar is worth a beat on screen: it is the only frame that
        // tells the player the wait is genuinely over.
        delay(420)
        onReady()
    }

    // Held just short of full until the last step lands.
    val target = if (finished) 1f else (completed.toFloat() / steps.size) * 0.92f
    val progress by animateFloatAsState(
        targetValue = target,
        // Animation duration matches the min-step hold so successive
        // segments blend into one continuous fill rather than a set of
        // discrete jumps.
        animationSpec = tween(durationMillis = 420),
        label = "loadProgress",
    )

    val portrait = LocalConfiguration.current.let { it.screenHeightDp > it.screenWidthDp }

    Box(modifier = Modifier.fillMaxSize().background(OlympusColors.Night)) {
        Image(
            painter = painterResource(
                if (portrait) R.drawable.splash_portrait else R.drawable.splash_landscape
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color(0x00050A1C), Color(0xF2040814)))
                )
                .padding(horizontal = 32.dp)
                .padding(top = 46.dp, bottom = 34.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                LoadingLabel(finished)
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = if (finished) OlympusColors.GoldBright else OlympusColors.SkyBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(text = stage, color = OlympusColors.Muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            LoadingBar(progress)
        }
    }
}

/** "Loading" with a tail of dots that cycles one, two, three and back. */
@Composable
private fun LoadingLabel(finished: Boolean) {
    var dots by remember { mutableIntStateOf(1) }
    LaunchedEffect(finished) {
        if (finished) {
            dots = 3
            return@LaunchedEffect
        }
        while (true) {
            delay(420)
            dots = if (dots >= 3) 1 else dots + 1
        }
    }
    Text(
        text = "Loading" + ".".repeat(dots),
        color = OlympusColors.Marble,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
    )
}

/** Horizontal track that fills left to right, with a gold cap on the edge. */
@Composable
private fun LoadingBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC0A1230)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(OlympusColors.Sapphire, OlympusColors.SkyBlue, Color(0xFFBFE3FF)),
                    )
                ),
        )
    }
}
