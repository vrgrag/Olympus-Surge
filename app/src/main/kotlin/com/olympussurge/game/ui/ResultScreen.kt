package com.olympussurge.game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olympussurge.core.meta.RunRecord
import com.olympussurge.game.R

/**
 * Post-run summary.
 *
 * Shows the rank the run earned on the hall of fame when it placed, which is
 * what turns a loss into a reason to try again.
 */
@Composable
fun RunResultScreen(
    record: RunRecord,
    rank: Int?,
    stars: Int,
    levelName: String,
    onRetry: () -> Unit,
    onHome: () -> Unit,
    onOpenCampaign: () -> Unit,
    onOpenLeaderboard: () -> Unit,
) {
    OlympusPage(backdrop = Backdrop.RUINS) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (record.victory) "$levelName CLEARED".uppercase() else "THE HERO HAS FALLEN",
                color = if (record.victory) OlympusColors.GoldBright else Color(0xFFFF8A7A),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 5.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { index ->
                    Text(
                        text = if (index < stars) "\u2605" else "\u2606",
                        color = if (index < stars) OlympusColors.GoldBright else OlympusColors.Muted,
                        fontSize = 40.sp,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = when {
                    rank == 1 -> "A new personal best"
                    rank != null -> "Ranked #$rank in the hall of fame"
                    else -> "This run did not place in the hall of fame"
                },
                color = OlympusColors.Muted,
                fontSize = 16.sp,
            )

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatChip("WAVES", record.waves.toString())
                StatChip("KILLS", record.kills.toString())
                StatChip("GEMS", record.gems.toString())
                StatChip("TIME", formatDuration(record.durationSeconds))
                StatChip(
                    label = "ESSENCE",
                    value = "+${record.essence}",
                    icon = R.drawable.icon_essence,
                )
            }

            Spacer(Modifier.height(34.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OlympusButton(
                    text = "HOME",
                    onClick = onHome,
                    modifier = Modifier
                        .width(200.dp)
                        .height(66.dp),
                    accent = OlympusColors.Muted,
                )
                OlympusButton(
                    text = "HALL OF FAME",
                    onClick = onOpenLeaderboard,
                    modifier = Modifier
                        .width(220.dp)
                        .height(66.dp),
                    accent = OlympusColors.SkyBlue,
                )
                OlympusButton(
                    text = "CAMPAIGN",
                    onClick = onOpenCampaign,
                    modifier = Modifier
                        .width(220.dp)
                        .height(66.dp),
                    accent = OlympusColors.SkyBlue,
                )
                OlympusButton(
                    text = if (record.victory) "PLAY AGAIN" else "RETRY",
                    onClick = onRetry,
                    modifier = Modifier
                        .width(230.dp)
                        .height(66.dp),
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String =
    "%d:%02d".format(seconds / 60, seconds % 60)
