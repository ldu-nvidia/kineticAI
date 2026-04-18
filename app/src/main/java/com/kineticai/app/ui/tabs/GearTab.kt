package com.kineticai.app.ui.tabs

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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import com.kineticai.app.KineticAIApp
import com.kineticai.app.sensor.BleImuManager
import com.kineticai.app.ui.theme.AccentGreen
import com.kineticai.app.ui.theme.AccentOrange
import com.kineticai.app.ui.theme.AccentRed
import com.kineticai.app.ui.theme.AccentYellow
import com.kineticai.app.ui.theme.SkyBlue

@Composable
fun GearTab(modifier: Modifier = Modifier) {
    var selectedMode by remember { mutableIntStateOf(0) }
    var epicEmoji by remember { mutableStateOf("FIRE!") }
    var greatEmoji by remember { mutableStateOf("***") }
    var goodEmoji by remember { mutableStateOf("OK!") }
    var okEmoji by remember { mutableStateOf("meh") }
    var badEmoji by remember { mutableStateOf("...") }

    val context = LocalContext.current
    val app = context.applicationContext as KineticAIApp
    val bleMgr = app.bleImuManager
    val scanStatus by bleMgr.scanStatus.collectAsState()
    val leftState by bleMgr.leftState.collectAsState()
    val rightState by bleMgr.rightState.collectAsState()

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            bleMgr.startScan()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Gear & Sensors",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── BLE CONNECTION ──
        SectionTitle("Boot Sensors")

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BootStatus("LEFT", leftState == BleImuManager.ConnectionState.CONNECTED, SkyBlue)
                    BootStatus("RIGHT", rightState == BleImuManager.ConnectionState.CONNECTED, AccentOrange)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Status: $scanStatus",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        // Request runtime BT/location permissions; scan starts in the callback.
                        permissionLauncher.launch(requiredPermissions)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                ) {
                    Icon(Icons.Filled.BluetoothSearching, null, modifier = Modifier.size(18.dp))
                    Text("  Scan for Boots", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { bleMgr.disconnect() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Disconnect")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── WIFI DATA DOWNLOAD ──
        SectionTitle("WiFi Data Transfer")

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "After skiing, press Button A on each boot to enable WiFi mode, then tap Download.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { /* WiFi download */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Wifi, null, modifier = Modifier.size(18.dp))
                    Text("  Download 200Hz Data")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── DISPLAY MODE ──
        SectionTitle("Boot Display Mode")

        val modes = listOf("Metrics", "Emoji", "Stealth", "Show-off", "Image", "GIF", "Party")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            modes.forEachIndexed { i, name ->
                val sel = selectedMode == i
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) SkyBlue.copy(alpha = 0.2f) else Color.Transparent)
                        .border(1.dp, if (sel) SkyBlue else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .clickable { selectedMode = i }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name,
                        fontSize = 9.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color = if (sel) SkyBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── COLOR THEME ──
        SectionTitle("Accent Color")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ColorDot(SkyBlue, "Ice")
            ColorDot(AccentGreen, "Lime")
            ColorDot(AccentOrange, "Sun")
            ColorDot(AccentRed, "Lava")
            ColorDot(AccentYellow, "Gold")
            ColorDot(Color(0xFF9333EA), "Purp")
            ColorDot(Color(0xFFEC4899), "Pink")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── EMOJI CUSTOMIZER ──
        SectionTitle("Turn Reaction Text")

        EmojiRow("Epic (45°+)", epicEmoji, AccentYellow) { epicEmoji = it }
        EmojiRow("Great (30°+)", greatEmoji, AccentGreen) { greatEmoji = it }
        EmojiRow("Good (20°+)", goodEmoji, SkyBlue) { goodEmoji = it }
        EmojiRow("OK (10°+)", okEmoji, AccentOrange) { okEmoji = it }
        EmojiRow("Weak (<10°)", badEmoji, AccentRed) { badEmoji = it }

        Spacer(modifier = Modifier.height(16.dp))

        // ── IMAGE UPLOAD ──
        SectionTitle("Custom Image / GIF")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { /* image picker */ },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
            ) { Text("Upload Image") }
            Button(
                onClick = { /* gif picker */ },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
            ) { Text("Upload GIF") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { /* clear image */ },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Clear Custom Image") }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun BootStatus(label: String, connected: Boolean, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (connected) AccentGreen else AccentRed),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, fontWeight = FontWeight.Bold, color = color)
            Text(
                if (connected) "Connected" else "Not connected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColorDot(color: Color, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { /* send color */ },
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape),
        )
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmojiRow(label: String, value: String, color: Color, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 19) onValue(it) },
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    )
}
