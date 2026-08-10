package com.olympussurge.game.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olympussurge.core.save.PlayerProfile
import com.olympussurge.game.R
import com.olympussurge.game.config.Levels

/**
 * Home screen.
 *
 * One vertical rhythm instead of three competing columns: identity and wealth
 * on a top bar, the logo and the single most likely action in the middle, and
 * the other destinations as a row of cards along the bottom.
 */
@Composable
fun MenuScreen(
    profile: PlayerProfile,
    nextLevelName: String,
    onStartRun: () -> Unit,
    onOpenCampaign: () -> Unit,
    onOpenTemple: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    OlympusPage(backdrop = Backdrop.CLOUDS, dimBackdrop = false) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(top = 14.dp, bottom = 18.dp),
        ) {
            TopBar(profile, onOpenProfile, onOpenSettings)

            // The logo takes whatever height is left over after the call to
            // action has been given its space: short landscape screens are the
            // norm here, and the button must never be the thing that is cropped.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = artPainter(R.drawable.logo_olympus_surge),
                    contentDescription = "Olympus Surge",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 560.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "NEXT ARENA \u00B7 ${nextLevelName.uppercase()}",
                    color = OlympusColors.GoldBright,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                OlympusButton(
                    text = "PLAY",
                    onClick = onStartRun,
                    modifier = Modifier
                        .width(320.dp)
                        .height(58.dp),
                    fontSize = 24.sp,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                NavCard(
                    title = "CAMPAIGN",
                    subtitle = "${Levels.all.size} arenas to conquer",
                    accent = OlympusColors.GoldBright,
                    onClick = onOpenCampaign,
                    modifier = Modifier.weight(1f),
                )
                NavCard(
                    title = "TEMPLE",
                    subtitle = "${profile.essence} essence to spend",
                    accent = OlympusColors.SkyBlue,
                    onClick = onOpenTemple,
                    modifier = Modifier.weight(1f),
                )
                NavCard(
                    title = "HALL OF FAME",
                    subtitle = "Your finest runs",
                    accent = OlympusColors.Amethyst,
                    onClick = onOpenLeaderboard,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    profile: PlayerProfile,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PlayerBadge(profile, onOpenProfile, modifier = Modifier.widthIn(max = 330.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Pill(
                icon = R.drawable.icon_essence,
                value = profile.essence.toString(),
                label = "ESSENCE",
            )
            Pill(
                icon = null,
                value = "${profile.totalStars}/${Levels.all.size * 3}",
                label = "STARS",
            )
            CircleIconButton(symbol = "\u2699", onClick = onOpenSettings)
        }
    }
}

/** Small readout for the top bar: tighter than [StatChip], same language. */
@Composable
private fun Pill(icon: Int?, value: String, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(27.dp))
            .background(OlympusColors.Panel)
            .border(BorderStroke(1.dp, Color(0x40FFD98C)), RoundedCornerShape(27.dp))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                painter = artPainter(icon),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(9.dp))
        }
        Column {
            Text(
                text = label,
                color = OlympusColors.SkyBlue,
                fontSize = 9.sp,
                letterSpacing = 2.sp,
            )
            Text(
                text = value,
                color = OlympusColors.GoldBright,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PlayerBadge(
    profile: PlayerProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(30.dp))
            .background(OlympusColors.Panel)
            .border(BorderStroke(1.dp, Color(0x40FFD98C)), RoundedCornerShape(30.dp))
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = AvatarPainter.painterFor(profile),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(BorderStroke(2.dp, OlympusColors.Gold), CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = profile.displayName,
                color = OlympusColors.Marble,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = "${profile.victories} victories \u00B7 wave ${profile.bestWave} best",
                color = OlympusColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

/** Bottom-row destination: a wide plate with a title and one line of context. */
@Composable
private fun NavCard(
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(70.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xD91B2C58), Color(0xE60C1533)))
            )
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        // A thin accent edge along the bottom keeps the three cards distinct
        // without shouting over the artwork behind them.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.BottomCenter)
                .background(accent.copy(alpha = 0.7f))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = accent,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = OlympusColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}