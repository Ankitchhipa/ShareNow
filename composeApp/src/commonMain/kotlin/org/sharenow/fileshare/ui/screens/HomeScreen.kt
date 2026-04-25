package org.sharenow.fileshare.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sharenow.fileshare.ui.theme.*
import org.sharenow.fileshare.ui.components.GlassCard
import org.sharenow.fileshare.platform.getFilesByCategory
import org.sharenow.fileshare.platform.getStorageStats
import org.sharenow.fileshare.model.FileCategory

@Composable
fun HomeScreen(
    deviceName: String,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit
) {
    val statsState = produceState(initialValue = Pair("...", "...")) {
        try {
            val allFiles = getFilesByCategory(FileCategory.Images) + 
                          getFilesByCategory(FileCategory.Videos) + 
                          getFilesByCategory(FileCategory.Audio)
            
            val count = allFiles.size.toString()
            
            val storage = getStorageStats()
            val usedPercent = ((storage.usedBytes.toDouble() / storage.totalBytes.toDouble()) * 100).toInt()
            
            value = Pair(count, "$usedPercent%")
        } catch (e: Exception) {
            value = Pair("0", "0%")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background Decorations
        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(x = (-150).dp, y = (-150).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(PrimaryBlue.copy(alpha = 0.15f), Color.Transparent)
                    ), 
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 100.dp, y = (-50).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(SecondaryBlue.copy(alpha = 0.1f), Color.Transparent)
                    ), 
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hello,",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextGray
                    )
                    Text(
                        text = "SHARE NOW",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextDark,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
                
                Surface(
                    color = SurfaceWhite.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SettingsCell, 
                            contentDescription = null, 
                            tint = PrimaryBlue, 
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            deviceName, 
                            color = TextDark, 
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.8f))

            // Central Send/Receive Buttons
            SendReceiveSection(onSendClick, onReceiveClick)

            Spacer(modifier = Modifier.weight(1f))

            // Quick Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard("Files", statsState.value.first, Icons.Default.Folder, Modifier.weight(1f))
                StatCard("Storage", statsState.value.second, Icons.Default.Storage, Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SendReceiveSection(onSendClick: () -> Unit, onReceiveClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AttractiveActionButton(
            text = "Send",
            subText = "Share files",
            icon = Icons.AutoMirrored.Filled.Send,
            colors = listOf(PrimaryBlue, Color(0xFF818CF8)),
            modifier = Modifier.weight(1f),
            onClick = onSendClick
        )
        
        AttractiveActionButton(
            text = "Receive",
            subText = "Get files",
            icon = Icons.Default.Download,
            colors = listOf(SecondaryBlue, Color(0xFF38BDF8)),
            modifier = Modifier.weight(1f),
            onClick = onReceiveClick
        )
    }
}

@Composable
fun AttractiveActionButton(
    text: String,
    subText: String,
    icon: ImageVector,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f)

    Surface(
        modifier = modifier
            .height(200.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(32.dp),
        shadowElevation = 12.dp,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors))
                .padding(20.dp)
        ) {
            // Decorative background icon
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 30.dp, y = 30.dp),
                tint = Color.White.copy(alpha = 0.15f)
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = text,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subText,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(4.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = SoftGray
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = label, color = TextGray, style = MaterialTheme.typography.labelSmall)
                Text(text = value, color = TextDark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
