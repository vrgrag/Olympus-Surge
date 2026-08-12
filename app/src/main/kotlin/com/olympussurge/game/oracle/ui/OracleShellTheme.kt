package com.olympussurge.game.oracle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colours + typography shared by all gray-flow Compose screens.
 *
 * Deliberately a mini theme rather than a full Material palette —
 * we only need three surfaces (background night, gold, sky) and the
 * primary text style used across loading / offline / invite.
 */
object OracleShellPalette {
    val Night = Color(0xFF061027)
    val NightSecondary = Color(0xFF0B1A3B)

    val SkyStart = Color(0xFF63BEF8)
    val SkyEnd = Color(0xFF2E78C9)
    val SkyDeep = Color(0xFF193F7F)

    val GoldStart = Color(0xFFFCE58A)
    val GoldEnd = Color(0xFFC9922B)

    val TextPrimary = Color(0xFFF5F1DC)
    val TextMuted = Color(0xFFB8C4D8)
}

@Composable
fun OracleShellTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        background = OracleShellPalette.Night,
        surface = OracleShellPalette.NightSecondary,
        primary = OracleShellPalette.SkyStart,
        onPrimary = Color.White,
        onBackground = OracleShellPalette.TextPrimary,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(
                color = OracleShellPalette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
        ) { content() }
    }
}

/** Sky brush used by every primary button. */
fun skyBrush(): Brush = Brush.verticalGradient(
    colors = listOf(OracleShellPalette.SkyStart, OracleShellPalette.SkyEnd),
)

/** Muted variant for the Skip button (still a real button, not a
 *  faded text link — see pitfalls §9). */
fun mutedSkyBrush(): Brush = Brush.verticalGradient(
    colors = listOf(OracleShellPalette.SkyEnd, OracleShellPalette.SkyDeep),
)

/**
 * Label with a soft dark shadow so it reads against the button
 * gradient regardless of the background artwork.
 */
val OracleButtonTextStyle: TextStyle = TextStyle(
    color = Color.White,
    fontSize = 18.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = 18.sp,   // guards against baseline drift (pitfalls §10)
    letterSpacing = 0.4.sp,
    shadow = Shadow(
        color = Color(0x99001730),
        offset = Offset(0f, 2f),
        blurRadius = 4f,
    ),
)

@Composable
fun OracleSpinnerOverlay(size: Dp = 32.dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size + 24.dp)
                .background(Color(0x66000000), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(size),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        }
    }
}

@Composable
fun OracleLoadingCaption() {
    val phase by com.olympussurge.game.oracle.ui.util.rememberDotsPhase()
    val dots = when (phase) { 0 -> "" ; 1 -> "." ; 2 -> ". ." ; else -> ". . ." }
    Text(
        text = "Loading $dots",
        style = TextStyle(
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            shadow = Shadow(
                color = Color(0xB3000000),
                offset = Offset(0f, 2f),
                blurRadius = 6f,
            ),
        ),
    )
}
