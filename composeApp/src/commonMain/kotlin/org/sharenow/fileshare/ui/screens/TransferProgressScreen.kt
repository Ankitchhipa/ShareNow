package org.sharenow.fileshare.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sharenow.fileshare.ui.components.GlassCard
import org.sharenow.fileshare.model.TransferFileItem
import org.sharenow.fileshare.model.TransferFileStatus
import org.sharenow.fileshare.ui.theme.*

@Composable
fun TransferProgressScreen(
    connectionLabel: String,
    fileName: String,
    progress: Float, // 0.0f to 1.0f
    currentFileProgress: Float,
    speed: String,
    timeRemaining: String,
    currentIndex: Int = 0,
    totalCount: Int = 0,
    transferredBytesLabel: String,
    totalBytesLabel: String,
    currentFileTransferredLabel: String,
    currentFileSizeLabel: String,
    transferItems: List<TransferFileItem>,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (totalCount > 0) "TRANSFERRING ($currentIndex/$totalCount)" else "TRANSFERRING",
            style = MaterialTheme.typography.titleMedium,
            color = PrimaryBlue,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = connectionLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = TextGray
        )
        
        Spacer(modifier = Modifier.height(28.dp))

        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(220.dp),
                color = PrimaryBlue,
                strokeWidth = 14.dp,
                trackColor = PrimaryBlue.copy(alpha = 0.1f),
                strokeCap = StrokeCap.Round,
            )
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp
                )
                Text(
                    text = speed,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextGray
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Overall progress
        /*LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 32.dp),
            color = PrimaryBlue,
            trackColor = PrimaryBlue.copy(alpha = 0.1f),
            strokeCap = StrokeCap.Round,
        )
        
        Text(
            text = "Total Progress",
            style = MaterialTheme.typography.labelSmall,
            color = TextGray,
            modifier = Modifier.padding(top = 8.dp)
        )*/

        //Spacer(modifier = Modifier.height(28.dp))

        /*GlassCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (fileName.isBlank()) "Preparing files..." else fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextDark,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Time Remaining: $timeRemaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )

                Spacer(modifier = Modifier.height(18.dp))

                LinearProgressIndicator(
                    progress = { currentFileProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = SecondaryBlue,
                    trackColor = SecondaryBlue.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Current File", color = TextGray, style = MaterialTheme.typography.labelSmall)
                        Text("$currentFileTransferredLabel / $currentFileSizeLabel", color = TextDark, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Overall", color = TextGray, style = MaterialTheme.typography.labelSmall)
                        Text("$transferredBytesLabel / $totalBytesLabel", color = TextDark, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))*/

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            Column {
                Text(
                    text = "Files In This Session",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextDark,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(14.dp))
                if (transferItems.isEmpty()) {
                    Text(
                        text = "Waiting for file list from the other device...",
                        color = TextGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(transferItems) { item ->
                            TransferFileRow(item = item,currentFileTransferredLabel = if (item.status == TransferFileStatus.Transferring) {
                                currentFileTransferredLabel
                            } else {
                                null
                            })
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Icon(Icons.Default.Stop, contentDescription = null, tint = Color.Red)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Cancel Transfer", color = Color.Red, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TransferFileRow(item: TransferFileItem, currentFileTransferredLabel: String?) {
    val accent = when (item.status) {
        TransferFileStatus.Completed -> AccentGreen
        TransferFileStatus.Transferring -> SecondaryBlue
        TransferFileStatus.Pending -> TextGray
    }
    val icon = when (item.status) {
        TransferFileStatus.Completed -> Icons.Default.CheckCircle
        TransferFileStatus.Transferring -> Icons.Default.Sync
        TransferFileStatus.Pending -> Icons.Default.Schedule
    }
    val statusText = when (item.status) {
        TransferFileStatus.Completed -> "Completed"
        TransferFileStatus.Transferring -> "Transferring"
        TransferFileStatus.Pending -> "Waiting"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = accent.copy(alpha = 0.14f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextDark,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))

            val sizeDisplay = when (item.status) {
                TransferFileStatus.Completed -> formatTransferSize(item.sizeInBytes) // Show total size
                TransferFileStatus.Transferring -> "${currentFileTransferredLabel ?: "0 B"} / ${formatTransferSize(item.sizeInBytes)}"
                TransferFileStatus.Pending -> formatTransferSize(item.sizeInBytes) // Show total size
            }

            Text(
                text = sizeDisplay,
                style = MaterialTheme.typography.labelMedium,
                color = TextGray
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    when(item.status) {
                        TransferFileStatus.Completed -> 1f
                        TransferFileStatus.Transferring -> item.progress
                        TransferFileStatus.Pending -> 0f
                    }
                },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = accent,
                trackColor = accent.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Round,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTransferSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> "${((bytes / gb) * 100).toInt() / 100.0} GB"
        bytes >= mb -> "${((bytes / mb) * 100).toInt() / 100.0} MB"
        bytes >= kb -> "${((bytes / kb) * 10).toInt() / 10.0} KB"
        else -> "$bytes B"
    }
}
