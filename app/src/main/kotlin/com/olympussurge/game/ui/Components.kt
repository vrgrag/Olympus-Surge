package com.olympussurge.game.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olympussurge.game.R
import com.olympussurge.game.boot.Warmup

/** Palette lifted from the key art: marble, polished gold and storm blue. */
object OlympusColors {
    val Night = Color(0xFF080D22)
    val DeepBlue = Color(0xFF13224A)
    val Panel = Color(0xE6101B3C)
    val PanelSoft = Color(0xB30D1631)
    val SkyBlue = Color(0xFF4FA8FF)
    val Gold = Color(0xFFE9B44C)
    val GoldBright = Color(0xFFFFD98C)
    val Marble = Color(0xFFF4F1E8)
    val Muted = Color(0xFF9FB3D9)
    val Sapphire = Color(0xFF2E7BFF)
    val Ruby = Color(0xFFE23A46)
    val Emerald = Color(0xFF34C06A)
    val Amethyst = Color(0xFF9B5CFF)
}

@Composable
fun OlympusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = OlympusColors.Gold,
            secondary = OlympusColors.SkyBlue,
            background = OlympusColors.Night,
            surface = OlympusColors.DeepBlue,
            onPrimary = OlympusColors.Night,
            onBackground = OlympusColors.Marble,
            onSurface = OlympusColors.Marble,
        ),
        content = content,
    )
}

/**
 * Painter for a drawable, reusing the bitmap the splash screen already decoded
 * when there is one. Falls back to a normal resource load, so screens work the
 * same whether or not they were warmed.
 */
@Composable
fun artPainter(resId: Int): Painter {
    val warmed = Warmup.artOrNull(resId)
    return if (warmed != null) remember(resId) { BitmapPainter(warmed) } else painterResource(resId)
}

/** Backdrops available to screens; each one sets a different mood. */
enum class Backdrop(val drawable: Int) {
    HALL(R.drawable.bg_olympus_hall),
    CLOUDS(R.drawable.bg_clouds),
    TEMPLE(R.drawable.bg_temple),
    RUINS(R.drawable.bg_ruins),
}

/**
 * Shared page frame: artwork, a darkening scrim and an optional title bar with
 * a back action. Every screen uses it so navigation feels like one place.
 */
@Composable
fun OlympusPage(
    title: String? = null,
    backdrop: Backdrop = Backdrop.HALL,
    onBack: (() -> Unit)? = null,
    // Content-heavy pages need a darker scrim to stay readable; the menu keeps
    // the artwork bright because it has room to breathe.
    dimBackdrop: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = artPainter(backdrop.drawable),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (dimBackdrop) {
                            listOf(Color(0xCC050A1C), Color(0xE6040814))
                        } else {
                            listOf(Color(0x59050A1C), Color(0xB3040814))
                        },
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            if (title != null || onBack != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onBack != null) {
                            CircleIconButton(symbol = "<", onClick = onBack)
                            Spacer(Modifier.width(20.dp))
                        }
                        if (title != null) {
                            Text(
                                text = title,
                                color = OlympusColors.GoldBright,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 5.sp,
                            )
                        }
                    }
                    trailing?.invoke()
                }
            }
            content(PaddingValues(horizontal = 28.dp, vertical = 8.dp))
        }
    }
}

@Composable
fun CircleIconButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(OlympusColors.Panel)
            .border(BorderStroke(2.dp, OlympusColors.Gold), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            color = OlympusColors.GoldBright,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Primary call to action: gold-rimmed marble plate, sized for thumbs. */
@Composable
fun OlympusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = OlympusColors.GoldBright,
    fontSize: androidx.compose.ui.unit.TextUnit = 20.sp,
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF2B4A8C).copy(alpha = alpha),
                        Color(0xFF13224A).copy(alpha = alpha),
                    ),
                )
            )
            .border(
                BorderStroke(2.dp, accent.copy(alpha = alpha)),
                RoundedCornerShape(14.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = accent.copy(alpha = alpha),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Compact readout of a single number, used across menu, profile and results. */
@Composable
fun StatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(OlympusColors.Panel)
            .border(BorderStroke(1.dp, Color(0x33FFD98C)), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column {
            Text(
                text = label,
                color = OlympusColors.SkyBlue,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
            )
            Text(
                text = value,
                color = OlympusColors.GoldBright,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Framed surface used for cards, rows and list items. */
@Composable
fun OlympusPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = Color(0x33FFD98C),
    background: Color = OlympusColors.PanelSoft,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(16.dp)),
    ) {
        content()
    }
}

/** Thin progress track used by achievements and upgrade levels. */
@Composable
fun ProgressTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = OlympusColors.Gold,
) {
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x66081026)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(color)
        )
    }
}

/** Insets helper so full-screen pages stay clear of gesture areas. */
val safeInsets: WindowInsets
    @Composable get() = WindowInsets(left = 12, top = 0, right = 12, bottom = 0)
