package org.sharenow.fileshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import org.sharenow.fileshare.model.ReceivedFile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sharenow.fileshare.data.HistoryManager
import org.sharenow.fileshare.model.TransferHistoryItem
import org.sharenow.fileshare.model.TransferDirection
import org.sharenow.fileshare.model.fileCategoryFromName
import org.sharenow.fileshare.platform.currentTimeMillis
import org.sharenow.fileshare.platform.getShareNowReceivedFiles
import org.sharenow.fileshare.ui.theme.*
import org.sharenow.fileshare.ui.components.GlassCard

@Composable
fun HistoryScreen(onPreviewFile: (ReceivedFile) -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("All", "Sent", "Received")
    val historyItems by HistoryManager.historyItems.collectAsState()
    var storedFiles by remember { mutableStateOf<List<ReceivedFile>>(emptyList()) }

    LaunchedEffect(historyItems.size) {
        storedFiles = getShareNowReceivedFiles()
    }

    val storedHistoryItems = remember(storedFiles) {
        storedFiles.mapIndexed { index, file ->
            TransferHistoryItem(
                id = -1L - index,
                direction = TransferDirection.Received,
                peerName = "ShareNow Folder",
                fileNames = listOf(file.name),
                totalBytes = file.sizeInBytes,
                status = "Success",
                timestampLabel = formatHistoryTimestamp(file.dateModified),
                receivedFiles = listOf(file),
            )
        }
    }

    val mergedItems = remember(historyItems, storedHistoryItems) {
        val liveReceivedPaths = historyItems
            .flatMap { it.receivedFiles }
            .map { it.savedPath }
            .toSet()
        historyItems + storedHistoryItems.filterNot { item ->
            item.receivedFiles.firstOrNull()?.savedPath in liveReceivedPaths
        }
    }

    val filteredItems = when (selectedTab) {
        1 -> mergedItems.filter { it.direction == TransferDirection.Sent }
        2 -> mergedItems.filter { it.direction == TransferDirection.Received }
        else -> mergedItems
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(top = 24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "History",
                style = MaterialTheme.typography.titleLarge,
                color = TextDark,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = PrimaryBlue,
            divider = {},
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = PrimaryBlue
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTab == index) PrimaryBlue else TextGray
                        )
                    }
                )
            }
        }

        if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No history yet", color = TextGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Your transfers will appear here", color = TextGray.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            // History List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredItems) { item ->
                    HistoryItem(
                        isSent = item.direction == TransferDirection.Sent,
                        fileName = item.fileNames.firstOrNull() ?: "Unknown",
                        size = formatHistoryBytes(item.totalBytes),
                        location = historyLocationLabel(item),
                        date = item.timestampLabel,
                        onClick = {
                            if (item.direction == TransferDirection.Received && item.receivedFiles.isNotEmpty()) {
                                onPreviewFile(item.receivedFiles.first())
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun historyCategoryLabel(fileNames: List<String>): String {
    if (fileNames.isEmpty()) return "Others"
    val labels = fileNames
        .map { fileCategoryFromName(it).label }
        .distinct()
    return if (labels.size == 1) labels.first() else "Mixed"
}

private fun historyLocationLabel(item: TransferHistoryItem): String {
    return if (item.direction == TransferDirection.Sent) {
        "Sent"
    } else {
        "ShareNow/${historyCategoryLabel(item.fileNames)}"
    }
}

private fun formatHistoryBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "${gb.formatOneDecimal()} GB"
        mb >= 1.0 -> "${mb.formatOneDecimal()} MB"
        kb >= 1.0 -> "${kb.formatOneDecimal()} KB"
        else -> "$bytes B"
    }
}

private fun Double.formatOneDecimal(): String {
    val rounded = kotlin.math.round(this * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun formatHistoryTimestamp(modifiedAt: Long): String {
    if (modifiedAt <= 0L) return "Saved"
    val age = (currentTimeMillis() - modifiedAt).coerceAtLeast(0L)
    return when {
        age < 60_000L -> "Just now"
        age < 3_600_000L -> "${age / 60_000L} min ago"
        age < 86_400_000L -> "${age / 3_600_000L} hr ago"
        else -> "${age / 86_400_000L} days ago"
    }
}

@Composable
fun HistoryItem(
    isSent: Boolean,
    fileName: String,
    size: String,
    location: String,
    date: String,
    onClick: () -> Unit = {},
) {
    GlassCard(modifier = Modifier.clickable { onClick() }) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = (if (isSent) PrimaryBlue else SecondaryBlue).copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isSent) Icons.Default.FileUpload else Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = if (isSent) PrimaryBlue else SecondaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    color = TextDark,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Text(
                    text = "$size • $location • $date",
                    color = TextGray,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Text(
                text = "Success",
                color = AccentGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
