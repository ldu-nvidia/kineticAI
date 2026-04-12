package com.mycarv.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.mycarv.app.sensor.BleCustomizer
import com.mycarv.app.ui.theme.AccentGreen
import com.mycarv.app.ui.theme.AccentOrange
import com.mycarv.app.ui.theme.AccentRed
import com.mycarv.app.ui.theme.AccentYellow
import com.mycarv.app.ui.theme.SkyBlue

@Composable
fun BootCustomizeScreen(
    onSendMode: (Int) -> Unit,
    onSendColor: (Int, Int, Int) -> Unit,
    onSendEmoji: (Int, String) -> Unit,
    onSendCelebration: (String) -> Unit,
    onSendStreak: (String) -> Unit,
    onPickImage: () -> Unit,
    onPickGif: () -> Unit,
    onClearImage: () -> Unit,
    onBack: () -> Unit,
) {
    var selectedMode by remember { mutableIntStateOf(0) }
    var epicEmoji by remember { mutableStateOf("FIRE!") }
    var greatEmoji by remember { mutableStateOf("***") }
    var goodEmoji by remember { mutableStateOf("OK!") }
    var okEmoji by remember { mutableStateOf("meh") }
    var badEmoji by remember { mutableStateOf("...") }
    var celebrationText by remember { mutableStateOf("YEAH!") }
    var streakText by remember { mutableStateOf("STREAK!") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Customize Boots",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── DISPLAY MODE ──
        SectionTitle("Display Mode")
        Text(
            "Choose what the boot screens show while skiing",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        val modes = listOf(
            Triple(0, "Metrics", "Edge angle, G-force, balance, streaks"),
            Triple(1, "Emoji", "Big emoji per turn, minimal numbers"),
            Triple(2, "Stealth", "Screen off, LED only, max battery"),
            Triple(3, "Show-off", "Huge numbers, flashy alternating display"),
            Triple(4, "Custom Image", "Your uploaded picture as background"),
            Triple(5, "GIF Animation", "Animated frames on loop"),
            Triple(6, "Party", "Rainbow cycling background"),
        )

        modes.forEach { (id, name, desc) ->
            ModeCard(
                name = name,
                description = desc,
                selected = selectedMode == id,
                onClick = {
                    selectedMode = id
                    onSendMode(id)
                },
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── COLOR THEME ──
        SectionTitle("Accent Color")
        Text(
            "Sets the main highlight color on both boots",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ColorDot(SkyBlue, "Ice Blue") { onSendColor(0x38, 0xBD, 0xF8) }
            ColorDot(AccentGreen, "Lime") { onSendColor(0x4A, 0xDE, 0x80) }
            ColorDot(AccentOrange, "Sunset") { onSendColor(0xFB, 0x92, 0x3C) }
            ColorDot(AccentRed, "Lava") { onSendColor(0xEF, 0x44, 0x44) }
            ColorDot(AccentYellow, "Gold") { onSendColor(0xFA, 0xCC, 0x15) }
            ColorDot(Color(0xFF9333EA), "Purple") { onSendColor(0x93, 0x33, 0xEA) }
            ColorDot(Color(0xFFEC4899), "Pink") { onSendColor(0xEC, 0x48, 0x99) }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── TURN EMOJI ──
        SectionTitle("Turn Reaction Emoji")
        Text(
            "Customize what shows after each turn (text displayed on boot screen)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        EmojiField("Epic turn (45°+)", epicEmoji, AccentYellow) {
            epicEmoji = it; onSendEmoji(4, it)
        }
        EmojiField("Great turn (30°+)", greatEmoji, AccentGreen) {
            greatEmoji = it; onSendEmoji(3, it)
        }
        EmojiField("Good turn (20°+)", goodEmoji, SkyBlue) {
            goodEmoji = it; onSendEmoji(2, it)
        }
        EmojiField("OK turn (10°+)", okEmoji, AccentOrange) {
            okEmoji = it; onSendEmoji(1, it)
        }
        EmojiField("Needs work (<10°)", badEmoji, AccentRed) {
            badEmoji = it; onSendEmoji(0, it)
        }

        Spacer(modifier = Modifier.height(12.dp))

        EmojiField("Celebration message", celebrationText, AccentYellow) {
            celebrationText = it; onSendCelebration(it)
        }
        EmojiField("Streak message", streakText, AccentOrange) {
            streakText = it; onSendStreak(it)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── CUSTOM IMAGE / GIF ──
        SectionTitle("Custom Image / GIF")
        Text(
            "Upload a picture or animated frames to display on boots (135×240 px)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onPickImage,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
            ) {
                Icon(Icons.Filled.Image, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Image")
            }
            Button(
                onClick = onPickGif,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
            ) {
                Icon(Icons.Filled.Movie, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("GIF Frames")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onClearImage,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text("Clear Custom Image", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun ModeCard(name: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.border(2.dp, SkyBlue, RoundedCornerShape(12.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                SkyBlue.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) SkyBlue else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColorDot(color: Color, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape),
        )
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmojiField(label: String, value: String, color: Color, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 19) onValue(it) },
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}
