package com.olympussurge.game.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olympussurge.core.meta.RunRecord
import com.olympussurge.core.save.PlayerProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hall of Fame: the player's twenty best runs, ranked.
 *
 * The board is local by design — the game is offline, so the only honest
 * comparison is against your own history. Runs are ordered by depth first,
 * which matches how the game actually gets harder.
 */
@Composable
fun LeaderboardScreen(
    profile: PlayerProfile,
    highlightTimestamp: Long?,
    onBack: () -> Unit,
) {
    val records = profile.leaderboard.records

    OlympusPage(title = "HALL OF FAME", backdrop = Backdrop.RUINS, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = 20.dp),
        ) {
            if (records.isEmpty()) {
                EmptyBoard()
                return@Column
            }

            HeaderRow()
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(records) { index, record ->
                    RecordRow(
                        rank = index + 1,
                        record = record,
                        highlighted = record.timestamp == highlightTimestamp,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyBoard() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "NO RUNS RECORDED YET",
                color = OlympusColors.GoldBright,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Survive a wave and your name will be carved here.",
                color = OlympusColors.Muted,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
    ) {
        HeaderCell("#", 60.dp)
        HeaderCell("RESULT", 170.dp)
        HeaderCell("WAVE", 110.dp)
        HeaderCell("KILLS", 110.dp)
        HeaderCell("GEMS", 110.dp)
        HeaderCell("ESSENCE", 140.dp)
        HeaderCell("SCORE", 120.dp)
        Text(
            text = "DATE",
            color = OlympusColors.SkyBlue,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        color = OlympusColors.SkyBlue,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun RecordRow(rank: Int, record: RunRecord, highlighted: Boolean) {
    val medal = when (rank) {
        1 -> OlympusColors.GoldBright
        2 -> Color(0xFFD8DEE9)
        3 -> Color(0xFFCD8B5A)
        else -> OlympusColors.Muted
    }

    OlympusPanel(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (highlighted) OlympusColors.GoldBright else Color(0x22FFFFFF),
        background = if (highlighted) Color(0xCC1B2A55) else OlympusColors.PanelSoft,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(60.dp)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(medal.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = rank.toString(),
                        color = medal,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = if (record.victory) "VICTORY" else "FALLEN",
                color = if (record.victory) OlympusColors.Emerald else Color(0xFFFF8A7A),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(170.dp),
            )
            Cell(record.waves.toString(), 110.dp)
            Cell(record.kills.toString(), 110.dp)
            Cell(record.gems.toString(), 110.dp)
            Cell(record.essence.toString(), 140.dp)
            Cell(record.score.toString(), 120.dp, OlympusColors.GoldBright)
            Text(
                text = DATE_FORMAT.format(Date(record.timestamp)),
                color = OlympusColors.Muted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun Cell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    color: Color = OlympusColors.Marble,
) {
    Text(
        text = text,
        color = color,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.width(width),
    )
}

private val DATE_FORMAT = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
