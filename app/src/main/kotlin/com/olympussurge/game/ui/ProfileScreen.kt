package com.olympussurge.game.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olympussurge.core.meta.Achievement
import com.olympussurge.core.save.PlayerProfile

/**
 * Player identity and lifetime record.
 *
 * The left column is who you are (portrait, name, headline totals); the right
 * column is what you have proven, as achievements with real progress bars fed
 * by the same counters the rest of the game uses.
 */
@Composable
fun ProfileScreen(
    profile: PlayerProfile,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onPickPreset: (AvatarPreset) -> Unit,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
) {
    OlympusPage(title = "PROFILE", backdrop = Backdrop.RUINS, onBack = onBack) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IdentityCard(
                    profile = profile,
                    onRename = onRename,
                    onPickPhoto = onPickPhoto,
                    onClearPhoto = onClearPhoto,
                )
                AvatarPicker(profile = profile, onPick = onPickPreset)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LifetimeStats(profile)
                Text(
                    text = "ACHIEVEMENTS",
                    color = OlympusColors.SkyBlue,
                    fontSize = 14.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold,
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(Achievement.entries.toList()) { achievement ->
                        AchievementRow(achievement, profile)
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentityCard(
    profile: PlayerProfile,
    onRename: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
) {
    OlympusPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .border(2.dp, OlympusColors.Gold, CircleShape),
            ) {
                Image(
                    painter = AvatarPainter.painterFor(profile),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                NameField(profile.displayName, onRename)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OlympusButton(
                        text = "PHOTO",
                        onClick = onPickPhoto,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        accent = OlympusColors.SkyBlue,
                    )
                    if (profile.avatarPhotoPath != null) {
                        OlympusButton(
                            text = "CLEAR",
                            onClick = onClearPhoto,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            accent = OlympusColors.Muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NameField(current: String, onRename: (String) -> Unit) {
    var text by remember(current) { mutableStateOf(current) }

    Column {
        Text(
            text = "NAME",
            color = OlympusColors.SkyBlue,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = text,
            onValueChange = { value ->
                // Committing on every keystroke keeps the header in sync with
                // what is typed without needing a save button.
                text = value.take(20)
                onRename(text)
            },
            singleLine = true,
            textStyle = TextStyle(
                color = OlympusColors.GoldBright,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
            cursorBrush = SolidColor(OlympusColors.Gold),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x66081026))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AvatarPicker(profile: PlayerProfile, onPick: (AvatarPreset) -> Unit) {
    OlympusPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "CHOOSE A PATRON",
                color = OlympusColors.SkyBlue,
                fontSize = 12.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (preset in AvatarPreset.entries) {
                    val selected = profile.avatarPhotoPath == null && profile.avatar == preset.key
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) OlympusColors.GoldBright else Color(0x55FFFFFF),
                                    shape = CircleShape,
                                )
                                .clickable { onPick(preset) },
                        ) {
                            Image(
                                painter = painterResource(preset.drawable),
                                contentDescription = preset.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = preset.displayName.substringBefore(' '),
                            color = if (selected) OlympusColors.GoldBright else OlympusColors.Muted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LifetimeStats(profile: PlayerProfile) {
    // Two rows of three: six chips in one row squeeze the labels into
    // one-letter-per-line columns on a phone.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatChip("RUNS", profile.runs.toString(), Modifier.weight(1f))
            StatChip("WINS", profile.victories.toString(), Modifier.weight(1f))
            StatChip("BEST WAVE", profile.bestWave.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatChip("KILLS", profile.totalKills.toString(), Modifier.weight(1f))
            StatChip("GEMS", profile.totalGems.toString(), Modifier.weight(1f))
            StatChip("ESSENCE", profile.essence.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun AchievementRow(achievement: Achievement, profile: PlayerProfile) {
    val unlocked = achievement.isUnlocked(profile)
    OlympusPanel(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (unlocked) Color(0x66FFD98C) else Color(0x22FFFFFF),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (unlocked) OlympusColors.Gold else Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (unlocked) "*" else "-",
                    color = if (unlocked) OlympusColors.Night else OlympusColors.Muted,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.title,
                    color = if (unlocked) OlympusColors.GoldBright else OlympusColors.Marble,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = achievement.description,
                    color = OlympusColors.Muted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                ProgressTrack(
                    fraction = achievement.fraction(profile),
                    modifier = Modifier.fillMaxWidth(),
                    color = if (unlocked) OlympusColors.Gold else OlympusColors.SkyBlue,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${achievement.progressOf(profile).coerceAtMost(achievement.goal)}" +
                    " / ${achievement.goal}",
                color = OlympusColors.Muted,
                fontSize = 13.sp,
            )
        }
    }
}
