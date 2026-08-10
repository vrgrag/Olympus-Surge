package com.olympussurge.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olympussurge.game.legal.LegalSection
import com.olympussurge.game.legal.LegalTexts

private val BG = Color(0xFF0A0F1E)
private val TITLE_COLOR = Color(0xFFFFFFFF)
private val HEADING_COLOR = Color(0xFF4FA8FF)
private val BODY_COLOR = Color(0xFFCDD5E8)
private val BACK_BG = Color(0x33FFFFFF)

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    WebPage(title = "Privacy Policy", note = LegalTexts.PRIVACY_EFFECTIVE, onBack = onBack) {
        for (section in LegalTexts.privacy) Section(section)
    }
}

@Composable
fun SupportScreen(versionName: String, onBack: () -> Unit) {
    WebPage(title = "Support", note = "Olympus Surge $versionName", onBack = onBack) {
        for (section in LegalTexts.support) Section(section)
    }
}

@Composable
private fun WebPage(
    title: String,
    note: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BG),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 24.dp, bottom = 40.dp),
        ) {
            // Back button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(BACK_BG)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("←", color = TITLE_COLOR, fontSize = 20.sp)
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = title,
                color = TITLE_COLOR,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = note, color = BODY_COLOR, fontSize = 13.sp)

            content()

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Section(section: LegalSection) {
    Spacer(Modifier.height(22.dp))
    Text(
        text = section.title,
        color = HEADING_COLOR,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
    Spacer(Modifier.height(6.dp))
    for (paragraph in section.body) {
        Text(text = paragraph, color = BODY_COLOR, fontSize = 14.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(4.dp))
    }
    for (bullet in section.bullets) {
        Text(text = "• $bullet", color = BODY_COLOR, fontSize = 14.sp, lineHeight = 22.sp)
    }
}
