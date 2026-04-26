package org.sharenow.fileshare.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.PortableWifiOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sharenow.fileshare.ui.theme.*
import org.sharenow.fileshare.platform.QrScannerView
import org.sharenow.fileshare.platform.isSystemHotspotEnabled
import org.sharenow.fileshare.platform.isWifiEnabled
import org.sharenow.fileshare.model.ConnectionPayload
import org.sharenow.fileshare.ui.components.GlassCard
import kotlinx.coroutines.delay

@Composable
fun ScanningScreen(
    qrBitmap: ImageBitmap? = null,
    connectionStatus: String = "",
    errorMessage: String? = null,
    isConnecting: Boolean = false,
    onOpenWifiSettings: (() -> Unit)? = null,
    onClose: () -> Unit,
    onDeviceSelected: (ConnectionPayload) -> Unit
) {
    var showScanner by remember { mutableStateOf(false) }
    var hasScannedQr by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition()
    val isReceiver = qrBitmap == null
    var wifiEnabled by remember(isReceiver) { mutableStateOf(if (isReceiver) isWifiEnabled() else true) }
    var hotspotConflictEnabled by remember { mutableStateOf(isSystemHotspotEnabled()) }

    // To this (Adding a delay/check):
    val showConnectionLoader by remember(isConnecting, connectionStatus) {
        derivedStateOf {
            isConnecting || (qrBitmap != null && connectionStatus.isNotBlank() && !connectionStatus.contains("Waiting for receiver", ignoreCase = true))
        }
    }

    LaunchedEffect(isConnecting) {
        if (isConnecting) {
            showScanner = false
        }
    }

    LaunchedEffect(isReceiver, qrBitmap) {
        while (true) {
            hotspotConflictEnabled = isSystemHotspotEnabled()
            if (isReceiver) {
                wifiEnabled = isWifiEnabled()
            }
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextDark)
            }
            Text(
                text = if (qrBitmap != null) "SEND" else "RECEIVE",
                style = MaterialTheme.typography.titleMedium,
                color = TextDark,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            if (qrBitmap == null) {
                IconButton(onClick = { showScanner = !showScanner }) {
                    Icon(
                        if (showScanner) Icons.Default.Close else Icons.Default.QrCodeScanner,
                        contentDescription = "QR Scan",
                        tint = PrimaryBlue
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (qrBitmap != null) {
            // Sender: Show QR Code
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Receiver should scan this QR",
                        color = TextGray,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    Surface(
                        modifier = Modifier
                            .size(280.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = SurfaceWhite,
                        shadowElevation = 12.dp
                    ) {
                        Box(modifier = Modifier.padding(24.dp)) {
                            Image(
                                bitmap = qrBitmap,
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        } else if (showConnectionLoader) {
            ConnectionProgressHero(status = connectionStatus)
        } else if (showScanner) {
            // Receiver: Scan QR Code
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(2.dp, PrimaryBlue, RoundedCornerShape(24.dp))
            ) {
                QrScannerView(
                    modifier = Modifier.fillMaxSize(),
                    onQrScanned = { qrValue ->
                        if (hasScannedQr) return@QrScannerView
                        ConnectionPayload.decode(qrValue)?.let {
                            hasScannedQr = true
                            showScanner = false
                            onDeviceSelected(it)
                        }
                    },
                    onPermissionChanged = {},
                    onError = {}
                )
            }
        } else {
            // Receiver: Radar Animation (Mock)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                repeat(3) { i ->
                    val delay = i * 600
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1.5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, delayMillis = delay, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        )
                    )
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, delayMillis = delay, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        )
                    )
                    Box(
                        modifier = Modifier
                            .size(300.dp)
                            .scale(scale)
                            .border(2.dp, PrimaryBlue.copy(alpha = alpha), CircleShape)
                    )
                }

                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = PrimaryBlue,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("📱", fontSize = 32.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        val statusText = when {
            errorMessage != null -> errorMessage
            isConnecting -> "Connection in progress. Keep Wi-Fi on and stay on this screen."
            hotspotConflictEnabled -> "Turn off system Hotspot/Tethering on this device before connecting."
            isReceiver && !wifiEnabled -> "Wi-Fi is off on this device. Turn on Wi-Fi before scanning the QR code."
            connectionStatus.isNotBlank() -> connectionStatus
            qrBitmap != null -> "Waiting for receiver to scan..."
            showScanner -> "Align QR code within the frame"
            else -> "Ready to receive files..."
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge,
            color = TextDark,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(24.dp))

        GlassCard {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isReceiver) {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = CircleShape,
                        color = if (wifiEnabled) AccentGreen.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (wifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = if (wifiEnabled) AccentGreen else Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = PrimaryBlue,
                        strokeWidth = 2.dp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = when {
                        qrBitmap != null -> "Sender is hosting"
                        wifiEnabled -> "Wi-Fi is on. Open 'Send' on the other device"
                        else -> "Wi-Fi is off. Turn it on to receive files"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
            }
        }

        if (hotspotConflictEnabled) {
            Spacer(modifier = Modifier.height(14.dp))
            GlassCard {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = CircleShape,
                        color = Color.Red.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PortableWifiOff,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "System hotspot is on. Turn it off, then try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGray
                    )
                }
            }
        }

        if (qrBitmap == null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Tip: Keep Wi-Fi enabled on the receiving phone before scanning the QR code.",
                color = TextGray.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (onOpenWifiSettings != null) {
                OutlinedButton(onClick = onOpenWifiSettings) {
                    Text("Open Settings")
                }
            }
        }
    }
}

@Composable
private fun ConnectionProgressHero(status: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(180.dp),
                    color = PrimaryBlue,
                    strokeWidth = 10.dp,
                    trackColor = PrimaryBlue.copy(alpha = 0.12f)
                )
                Surface(
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape,
                    color = PrimaryBlue.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (status.isBlank()) "Connecting..." else status,
                style = MaterialTheme.typography.titleMedium,
                color = TextDark,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Keep both devices on this screen",
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray
            )
        }
    }
}
