package com.olympussurge.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olympussurge.core.save.PlayerProfile

/**
 * Settings and the legal corner.
 *
 * Audio and haptics apply immediately; destructive actions ask for a second
 * tap rather than opening a dialog, which keeps the flow inside one screen.
 */
@Composable
fun SettingsScreen(
    profile: PlayerProfile,
    versionName: String,
    onBack: () -> Unit,
    onSfxChange: (Float) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onDiagnosticsChange: (Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenSupport: () -> Unit,
) {
    OlympusPage(title = "SETTINGS", backdrop = Backdrop.TEMPLE, onBack = onBack) { padding ->
        // Landscape phones leave barely 300dp under the title bar, so both
        // columns scroll: a setting that is off-screen is a setting that does
        // not exist.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AudioCard(profile, onSfxChange)
                // Both toggles share one panel: two framed cards cost enough
                // padding to push the second one off a landscape phone.
                OlympusPanel(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ToggleRow(
                            title = "Haptic feedback",
                            subtitle = "Short vibration on impacts and pickups",
                            checked = profile.hapticsEnabled,
                            onCheckedChange = onHapticsChange,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0x22FFD98C))
                        )
                        ToggleRow(
                            title = "Performance overlay",
                            subtitle = "Frame rate and counters during battle",
                            checked = profile.showDiagnostics,
                            onCheckedChange = onDiagnosticsChange,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .width(390.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AboutCard(versionName, onOpenPrivacy, onOpenSupport)
                DangerCard(onResetProgress)
            }
        }
    }
}

/**
 * Only a sound-effects slider is shown: the game ships no music track, so a
 * music control would promise something that never plays.
 */
@Composable
private fun AudioCard(profile: PlayerProfile, onSfxChange: (Float) -> Unit) {
    var sfx by remember(profile.sfxVolume) { mutableStateOf(profile.sfxVolume) }

    OlympusPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "SOUND",
                color = OlympusColors.SkyBlue,
                fontSize = 11.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold,
            )
            VolumeSlider("Effects", sfx) { value ->
                sfx = value
                onSfxChange(value)
            }
        }
    }
}

@Composable
private fun VolumeSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, color = OlympusColors.Marble, fontSize = 15.sp)
            Text(
                text = "${(value * 100).toInt()}%",
                color = OlympusColors.GoldBright,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        // Inset so the thumb at either end reads as a handle on a track rather
        // than something clipped by the panel edge.
        Slider(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.padding(horizontal = 6.dp),
            colors = SliderDefaults.colors(
                thumbColor = OlympusColors.Gold,
                activeTrackColor = OlympusColors.Gold,
                inactiveTrackColor = Color(0x33FFFFFF),
            ),
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = OlympusColors.Marble, fontSize = 15.sp)
            Text(text = subtitle, color = OlympusColors.Muted, fontSize = 11.sp)
        }
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (checked) OlympusColors.Gold else Color(0x33FFFFFF)),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (checked) OlympusColors.Night else OlympusColors.Muted)
            )
        }
    }
}

@Composable
private fun AboutCard(
    versionName: String,
    onOpenPrivacy: () -> Unit,
    onOpenSupport: () -> Unit,
) {
    OlympusPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "ABOUT",
                color = OlympusColors.SkyBlue,
                fontSize = 11.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Olympus Surge",
                    color = OlympusColors.GoldBright,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(text = versionName, color = OlympusColors.Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Plays fully offline. Progress is stored only on this device.",
                color = OlympusColors.Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
            // Both documents are bundled, so they open without a connection.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OlympusButton(
                    text = "PRIVACY",
                    onClick = onOpenPrivacy,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    accent = OlympusColors.SkyBlue,
                    fontSize = 14.sp,
                )
                OlympusButton(
                    text = "SUPPORT",
                    onClick = onOpenSupport,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    accent = OlympusColors.SkyBlue,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun DangerCard(onResetProgress: () -> Unit) {
    // Two-step confirmation: the first tap arms the button, the second wipes.
    var armed by remember { mutableStateOf(false) }

    OlympusPanel(
        modifier = Modifier.fillMaxWidth(),
        borderColor = Color(0x55FF8A7A),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "RESET PROGRESS",
                color = Color(0xFFFF8A7A),
                fontSize = 11.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (armed) {
                    "Erases essence, upgrades and the hall of fame. Tap again to confirm."
                } else {
                    "Erases every run, upgrade and record on this device."
                },
                color = OlympusColors.Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
            OlympusButton(
                text = if (armed) "CONFIRM RESET" else "RESET",
                onClick = {
                    if (armed) {
                        onResetProgress()
                        armed = false
                    } else {
                        armed = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                accent = Color(0xFFFF8A7A),
                fontSize = 17.sp,
            )
        }
    }
}
