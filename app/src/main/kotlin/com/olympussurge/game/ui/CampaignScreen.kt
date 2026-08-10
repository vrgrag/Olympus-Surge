package com.olympussurge.game.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olympussurge.core.save.PlayerProfile
import com.olympussurge.game.config.LevelDef
import com.olympussurge.game.config.Levels
import com.olympussurge.game.config.Objective

/**
 * The campaign map: every arena, its goal, its stars and whether it is open.
 *
 * Levels are laid out as a horizontal road because the phone is in landscape
 * and progression reads naturally left to right; the selected arena expands
 * into a briefing so the player knows what they are walking into.
 */
@Composable
fun CampaignScreen(
    profile: PlayerProfile,
    onBack: () -> Unit,
    onPlay: (LevelDef) -> Unit,
) {
    val cleared = remember(profile.levelStars) { profile.levelStars.keys }
    val firstOpen = remember(cleared) {
        Levels.all.lastOrNull { Levels.isUnlocked(it, cleared) } ?: Levels.all.first()
    }
    var selected by remember { mutableStateOf(firstOpen) }

    OlympusPage(
        title = "CAMPAIGN",
        backdrop = Backdrop.RUINS,
        onBack = onBack,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatChip(label = "STARS", value = "${profile.totalStars} / ${Levels.all.size * 3}")
                Spacer(Modifier.width(14.dp))
                StatChip(label = "ESSENCE", value = profile.essence.toString())
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LevelRoad(
                profile = profile,
                cleared = cleared,
                selected = selected,
                onSelect = { selected = it },
            )
            Spacer(Modifier.height(14.dp))
            Briefing(
                level = selected,
                stars = profile.starsOf(selected.id),
                unlocked = Levels.isUnlocked(selected, cleared),
                onPlay = { onPlay(selected) },
            )
        }
    }
}

@Composable
private fun LevelRoad(
    profile: PlayerProfile,
    cleared: Set<String>,
    selected: LevelDef,
    onSelect: (LevelDef) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(Levels.all) { level ->
            LevelNode(
                level = level,
                stars = profile.starsOf(level.id),
                unlocked = Levels.isUnlocked(level, cleared),
                selected = level.id == selected.id,
                onClick = { onSelect(level) },
            )
        }
    }
}

@Composable
private fun LevelNode(
    level: LevelDef,
    stars: Int,
    unlocked: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = when {
        !unlocked -> OlympusColors.Muted
        selected -> OlympusColors.GoldBright
        else -> OlympusColors.SkyBlue
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) OlympusColors.Panel else OlympusColors.PanelSoft)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, accent), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2B4A8C), Color(0xFF13224A)),
                    )
                )
                .border(BorderStroke(2.dp, accent), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (unlocked) "${level.index + 1}" else "\u2716",
                color = accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = level.name,
            color = if (unlocked) OlympusColors.Marble else OlympusColors.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
        )
        Spacer(Modifier.height(6.dp))
        Stars(earned = stars, dim = !unlocked)
    }
}

@Composable
private fun Stars(earned: Int, dim: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            Text(
                text = if (index < earned) "\u2605" else "\u2606",
                color = when {
                    dim -> OlympusColors.Muted.copy(alpha = 0.5f)
                    index < earned -> OlympusColors.GoldBright
                    else -> OlympusColors.Muted
                },
                fontSize = 16.sp,
            )
        }
    }
}

/** Briefing panel: goal, optional star goals and the launch button. */
@Composable
private fun Briefing(
    level: LevelDef,
    stars: Int,
    unlocked: Boolean,
    onPlay: () -> Unit,
) {
    OlympusPanel(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = level.name.uppercase(),
                    color = OlympusColors.GoldBright,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                Text(
                    text = level.subtitle,
                    color = OlympusColors.Muted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                GoalRow(
                    label = "GOAL",
                    text = level.objective.hudLabel,
                    done = stars >= 1,
                    accent = OlympusColors.SkyBlue,
                )
                GoalRow(
                    label = "STAR",
                    text = level.starGoals.describeSecond(),
                    done = stars >= 2,
                )
                GoalRow(
                    label = "STAR",
                    text = level.starGoals.describeThird(),
                    done = stars >= 3,
                )
            }

            Spacer(Modifier.width(24.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(280.dp),
            ) {
                Stars(earned = stars)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${level.waves.size} waves" +
                        if (level.endless) "  -  repeating" else "",
                    color = OlympusColors.Muted,
                    fontSize = 13.sp,
                )
                Text(
                    text = enemyPreview(level),
                    color = OlympusColors.SkyBlue,
                    fontSize = 12.sp,
                    maxLines = 2,
                )
                Spacer(Modifier.height(14.dp))
                OlympusButton(
                    text = if (unlocked) "ENTER THE ARENA" else "LOCKED",
                    enabled = unlocked,
                    onClick = onPlay,
                    fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                )
                if (!unlocked) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Clear the previous arena to open this one",
                        color = OlympusColors.Muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalRow(
    label: String,
    text: String,
    done: Boolean,
    accent: Color = OlympusColors.GoldBright,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp),
    ) {
        Text(
            text = if (done) "\u2714" else "\u2022",
            color = if (done) OlympusColors.Emerald else OlympusColors.Muted,
            fontSize = 16.sp,
            modifier = Modifier.width(22.dp),
        )
        Text(
            text = label,
            color = accent,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.width(52.dp),
        )
        Text(text = text, color = OlympusColors.Marble, fontSize = 14.sp)
    }
}

/** Names the threats the arena is built around, without dumping the roster. */
private fun enemyPreview(level: LevelDef): String {
    val boss = (level.objective as? Objective.SlayBoss)?.displayName
    val roster = level.waves
        .flatMap { wave -> wave.enemies.map { it.first } }
        .distinct()
        .take(4)
        .joinToString(", ") { name -> name.replace('_', ' ') }
    return if (boss != null) "Boss: $boss" else roster
}
