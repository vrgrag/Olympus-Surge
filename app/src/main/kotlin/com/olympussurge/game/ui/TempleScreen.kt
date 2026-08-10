package com.olympussurge.game.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olympussurge.core.meta.TempleUpgrade
import com.olympussurge.core.save.PlayerProfile
import com.olympussurge.game.R

/**
 * Temple of Olympus: permanent upgrades bought with essence.
 *
 * Each card states its exact effect, the level reached and the next price, so
 * the player can always tell what the essence in their pocket is worth before
 * spending it.
 */
@Composable
fun TempleScreen(
    profile: PlayerProfile,
    onBack: () -> Unit,
    onPurchase: (TempleUpgrade) -> Unit,
) {
    OlympusPage(
        title = "TEMPLE OF OLYMPUS",
        backdrop = Backdrop.TEMPLE,
        onBack = onBack,
        trailing = {
            StatChip(
                label = "ESSENCE",
                value = profile.essence.toString(),
                icon = R.drawable.icon_essence,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Two wide rows rather than four narrow cards: on a phone in
            // landscape the extra width buys legible titles and prices.
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 20.dp),
            ) {
                items(TempleUpgrade.entries.toList()) { upgrade ->
                    UpgradeCard(
                        upgrade = upgrade,
                        level = profile.levelOf(upgrade),
                        essence = profile.essence,
                        onPurchase = { onPurchase(upgrade) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpgradeCard(
    upgrade: TempleUpgrade,
    level: Int,
    essence: Int,
    onPurchase: () -> Unit,
) {
    val maxed = level >= upgrade.maxLevel
    val cost = upgrade.costAt(level)
    val affordable = !maxed && essence >= cost

    OlympusPanel(
        modifier = Modifier.fillMaxWidth(),
        borderColor = when {
            maxed -> Color(0x88FFD98C)
            affordable -> Color(0x664FA8FF)
            else -> Color(0x22FFFFFF)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0x33081026)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(iconOf(upgrade)),
                    contentDescription = null,
                    modifier = Modifier.size(46.dp),
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = upgrade.title,
                    color = OlympusColors.GoldBright,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = upgrade.effectPerLevel,
                    color = OlympusColors.Muted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "LVL $level/${upgrade.maxLevel}",
                        color = OlympusColors.SkyBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    ProgressTrack(
                        fraction = level.toFloat() / upgrade.maxLevel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(124.dp),
            ) {
                Text(
                    text = if (maxed) "MAX" else "$cost",
                    color = when {
                        maxed -> OlympusColors.Muted
                        affordable -> OlympusColors.GoldBright
                        else -> Color(0xFFB06A6A)
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                OlympusButton(
                    text = if (maxed) "DONE" else "UPGRADE",
                    onClick = onPurchase,
                    enabled = affordable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    accent = if (maxed) OlympusColors.Muted else OlympusColors.GoldBright,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/**
 * Icons are addressed by name in the core module so it stays free of Android
 * resource ids; the lookup happens once here.
 */
@Composable
private fun iconOf(upgrade: TempleUpgrade): Int = when (upgrade) {
    TempleUpgrade.VITALITY -> R.drawable.icon_vitality
    TempleUpgrade.WRATH -> R.drawable.icon_wrath
    TempleUpgrade.SWIFTNESS -> R.drawable.icon_swiftness
    TempleUpgrade.STORM -> R.drawable.icon_storm
    TempleUpgrade.FORTUNE -> R.drawable.icon_fortune
    TempleUpgrade.MAGNET -> R.drawable.icon_magnet
    TempleUpgrade.RESILIENCE -> R.drawable.icon_resilience
    TempleUpgrade.STARTING_GEM -> R.drawable.icon_starting_gem
}
