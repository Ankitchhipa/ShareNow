package org.sharenow.fileshare.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sharenow.fileshare.model.FileCategory
import org.sharenow.fileshare.model.SharedFile
import org.sharenow.fileshare.model.ensureUriScheme
import org.sharenow.fileshare.platform.*
import org.sharenow.fileshare.ui.components.NeonButton
import org.sharenow.fileshare.ui.components.ShimmerEffect
import org.sharenow.fileshare.ui.theme.*
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kotlinx.datetime.*
import org.sharenow.fileshare.model.category as getFileCategory

@Composable
fun FileSelectionScreen(
    onBack: () -> Unit, 
    onFilesSelected: (List<SharedFile>) -> Unit,
    onPreviewFile: (SharedFile) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val categories = listOf(
        FileCategory.Images,
        FileCategory.Videos,
        FileCategory.Audio,
        FileCategory.InternalStorage
    )
    val selectedFiles = remember { mutableStateListOf<SharedFile>() }
    var filesInSelectedCategory by remember { mutableStateOf(emptyList<SharedFile>()) }
    val fileCache = remember { mutableStateMapOf<String, List<SharedFile>>() }
    val rootPath = remember { getExternalStoragePath() }
    var currentPath by remember { mutableStateOf(rootPath) }
    val pathHistory = remember { mutableStateListOf<String>() }
    var isSelectionMode by remember { mutableStateOf(false) }
    var isLoadingFiles by remember { mutableStateOf(false) }

    val storageStats = remember { getStorageStats() }

    val handleBack = {
        if (categories[selectedTab] == FileCategory.InternalStorage && currentPath != rootPath) {
            currentPath = pathHistory.removeAt(pathHistory.size - 1)
        } else if (isSelectionMode && categories[selectedTab] != FileCategory.InternalStorage) {
            isSelectionMode = false
            selectedFiles.clear()
        } else {
            onBack()
        }
    }

    // Handle hardware back button for folder navigation
    PlatformBackHandler(enabled = true) {
        handleBack()
    }

    LaunchedEffect(selectedTab, currentPath) {
        val category = categories[selectedTab]
        val cacheKey = if (category == FileCategory.InternalStorage) currentPath else category.name
        
        if (fileCache.containsKey(cacheKey)) {
            filesInSelectedCategory = fileCache[cacheKey]!!
        } else {
            isLoadingFiles = true
            val files = if (category == FileCategory.InternalStorage) {
                getFilesInDirectory(currentPath)
            } else {
                getFilesByCategory(category)
            }
            fileCache[cacheKey] = files
            filesInSelectedCategory = files
            isLoadingFiles = false
        }
    }

    val groupedFiles = remember(filesInSelectedCategory, selectedTab) {
        if (categories[selectedTab] in listOf(FileCategory.Images, FileCategory.Videos, FileCategory.Audio)) {
            filesInSelectedCategory.groupBy { file ->
                try {
                    val instant = Instant.fromEpochSeconds(file.dateModified)
                    val localDate = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    localDate
                } catch (_: Exception) {
                    LocalDate(1970, 1, 1)
                }
            }
        } else {
            emptyMap<LocalDate, List<SharedFile>>()
        }
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
            IconButton(onClick = handleBack) {
                Icon(
                    if (isSelectionMode && categories[selectedTab] != FileCategory.InternalStorage) Icons.Default.Close else Icons.Default.ArrowBack, 
                    contentDescription = "Back", 
                    tint = TextDark
                )
            }
            Text(
                text = if (isSelectionMode) "${selectedFiles.size} Selected" else "Select Files",
                style = MaterialTheme.typography.titleLarge,
                color = TextDark,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Modern Tab Design
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(categories) { index, category ->
                val isSelected = selectedTab == index
                val icon = when(category) {
                    FileCategory.Images -> Icons.Default.Image
                    FileCategory.Videos -> Icons.Default.Movie
                    FileCategory.Audio -> Icons.Default.MusicNote
                    FileCategory.InternalStorage -> Icons.Default.Folder
                    else -> Icons.Default.Description
                }
                
                Surface(
                    onClick = {
                        selectedTab = index
                        if (category != FileCategory.InternalStorage) {
                            currentPath = rootPath
                            pathHistory.clear()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) PrimaryBlue.copy(alpha = 0.1f) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.5.dp, PrimaryBlue) else BorderStroke(1.dp, SoftGray.copy(alpha = 0.5f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) PrimaryBlue else TextGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = category.label,
                            color = if (isSelected) PrimaryBlue else TextGray,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Storage Info Bar & Breadcrumb
        if (categories[selectedTab] == FileCategory.InternalStorage) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = SurfaceWhite.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        val totalGB = storageStats.totalBytes / (1024 * 1024 * 1024)
                        val availableGB = storageStats.availableBytes / (1024 * 1024 * 1024)
                        Text(
                            "Internal Storage",
                            color = TextDark,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "$availableGB GB free of $totalGB GB",
                            color = TextGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // Modern Breadcrumb
            Breadcrumb(
                rootPath = rootPath,
                currentPath = currentPath,
                onPathClick = { path ->
                    if (path != currentPath) {
                        val parts = path.removePrefix(rootPath).split("/").filter { it.isNotEmpty() }
                        pathHistory.clear()
                        var p = rootPath
                        parts.forEach { part ->
                            pathHistory.add(p)
                            p = "$p/$part"
                        }
                        currentPath = path
                    }
                }
            )
        }

        // File Grid
        Box(modifier = Modifier.weight(1f)) {
            val isListView = categories[selectedTab] == FileCategory.InternalStorage
            val hasSelection = selectedFiles.isNotEmpty()
            
            LazyVerticalGrid(
                columns = if (isListView) GridCells.Fixed(1) else GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 16.dp, 
                    top = 16.dp, 
                    end = 16.dp, 
                    bottom = if (hasSelection) 100.dp else 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLoadingFiles) {
                    items(12) {
                        ShimmerEffect(
                            modifier = Modifier
                                .aspectRatio(if (isListView) 5f else 1f)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }
                } else if (groupedFiles.isNotEmpty()) {
                    groupedFiles.forEach { (date: LocalDate, files: List<SharedFile>) ->
                        val isAllSelectedForDate = files.all { f -> selectedFiles.any { it.path == f.path } }
                        
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            DateHeader(
                                date = date,
                                isAllSelected = isAllSelectedForDate,
                                onSelectAll = { select ->
                                    files.forEach { file ->
                                        val isSelected = selectedFiles.any { it.path == file.path }
                                        if (select && !isSelected) {
                                            selectedFiles.add(file)
                                            isSelectionMode = true
                                        } else if (!select && isSelected) {
                                            selectedFiles.removeAll { it.path == file.path }
                                        }
                                    }
                                    if (selectedFiles.isEmpty()) isSelectionMode = false
                                }
                            )
                        }
                        items(files) { file ->
                            FileItem(
                                file = file,
                                isSelected = selectedFiles.any { it.path == file.path },
                                isSelectionMode = isSelectionMode,
                                isListView = isListView,
                                onToggle = {
                                    val existing = selectedFiles.find { it.path == file.path }
                                    if (existing != null) {
                                        selectedFiles.remove(existing)
                                        if (selectedFiles.isEmpty()) isSelectionMode = false
                                    } else {
                                        selectedFiles.add(file)
                                        isSelectionMode = true
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedFiles.add(file)
                                    }
                                },
                                onPlayClick = {
                                    onPreviewFile(file)
                                }
                            )
                        }
                    }
                } else {
                    items(filesInSelectedCategory) { file ->
                        FileItem(
                            file = file,
                            isSelected = selectedFiles.any { it.path == file.path },
                            isSelectionMode = isSelectionMode,
                            isListView = isListView,
                            onToggle = {
                                if (file.isDirectory) {
                                    pathHistory.add(currentPath)
                                    currentPath = file.path ?: currentPath
                                } else {
                                    val existing = selectedFiles.find { it.path == file.path }
                                    if (existing != null) {
                                        selectedFiles.remove(existing)
                                        if (selectedFiles.isEmpty()) isSelectionMode = false
                                    } else {
                                        selectedFiles.add(file)
                                        isSelectionMode = true
                                    }
                                }
                            },
                            onLongClick = {
                                if (!file.isDirectory && !isSelectionMode) {
                                    isSelectionMode = true
                                    selectedFiles.add(file)
                                }
                            },
                            onPlayClick = {
                                if (!file.isDirectory) onPreviewFile(file)
                            }
                        )
                    }
                }
            }

            // Bottom Send Button (Overlay)
            if (selectedFiles.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                ) {
                    NeonButton(
                        text = "SEND ${selectedFiles.size} ${if (selectedFiles.size == 1) "FILE" else "FILES"}",
                        onClick = { onFilesSelected(selectedFiles.toList()) }
                    )
                }
            }
        }
    }
}

@Composable
fun Breadcrumb(rootPath: String, currentPath: String, onPathClick: (String) -> Unit) {
    val relativePath = currentPath.removePrefix(rootPath).trim('/')
    val parts = if (relativePath.isEmpty()) emptyList() else relativePath.split("/")
    
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                text = "Storage",
                color = if (parts.isEmpty()) PrimaryBlue else TextGray,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onPathClick(rootPath) }
            )
        }
        
        itemsIndexed(parts) { index, part ->
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGray, modifier = Modifier.size(14.dp))
            val path = rootPath + "/" + parts.take(index + 1).joinToString("/")
            Text(
                text = part,
                color = if (index == parts.size - 1) PrimaryBlue else TextGray,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onPathClick(path) }
            )
        }
    }
}

@Composable
fun DateHeader(
    date: LocalDate, 
    isAllSelected: Boolean, 
    onSelectAll: (Boolean) -> Unit
) {
    val today = Instant.fromEpochMilliseconds(currentTimeMillis()).toLocalDateTime(TimeZone.currentSystemDefault()).date
    val label = when (date) {
        today -> "Today"
        today.minus(1, DateTimeUnit.DAY) -> "Yesterday"
        else -> "${date.dayOfMonth} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.year}"
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextDark,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onSelectAll(!isAllSelected) }
        ) {
            Text(
                text = if (isAllSelected) "Deselect All" else "Select All",
                color = PrimaryBlue,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isAllSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isAllSelected) PrimaryBlue else TextGray.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun String.capitalize(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

@Composable
fun FileItem(
    file: SharedFile,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isListView: Boolean = false,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val category = file.getFileCategory()
    Surface(
        modifier = Modifier
            .then(if (isListView) Modifier.fillMaxWidth() else Modifier.aspectRatio(1f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { 
                        if (file.isDirectory) onToggle() else onPlayClick()
                    },
                    onLongPress = { if (!file.isDirectory) onLongClick() }
                )
            },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) PrimaryBlue.copy(alpha = 0.1f) else SurfaceWhite,
        shadowElevation = 2.dp
    ) {
        if (isListView) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon/Thumbnail
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val thumbnailBitmap = remember(file.thumbnail) {
                        file.thumbnail?.toImageBitmap()
                    }
                    when {
                        file.isDirectory -> {
                            Box(
                                modifier = Modifier.fillMaxSize().background(PrimaryBlue.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
                            }
                        }
                        thumbnailBitmap != null -> {
                            Image(bitmap = thumbnailBitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        file.path != null && (category == FileCategory.Images || category == FileCategory.Videos) -> {
                            KamelImage(
                                resource = asyncPainterResource(file.path.ensureUriScheme()),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                onLoading = { ShimmerEffect(modifier = Modifier.fillMaxSize()) },
                                onFailure = { 
                                    Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                        Text(if (category == FileCategory.Videos) "🎬" else "🖼️", fontSize = 24.sp)
                                    }
                                }
                            )
                        }
                        else -> {
                            val (icon, color) = when (category) {
                                FileCategory.Audio -> "🎵" to Color(0xFF5856D6)
                                FileCategory.Videos -> "🎬" to Color(0xFFFF9500)
                                FileCategory.Documents -> "📄" to Color(0xFF34C759)
                                else -> "📄" to Color(0xFF007AFF)
                            }
                            Box(modifier = Modifier.fillMaxSize().background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                Text(text = icon, fontSize = 24.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        color = TextDark,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                    if (!file.isDirectory) {
                        val formattedSize = remember(file.sizeInBytes) {
                            val kb = file.sizeInBytes / 1024.0
                            val mb = kb / 1024.0
                            val gb = mb / 1024.0
                            when {
                                gb >= 1 -> "${(gb * 10).toInt() / 10.0} GB"
                                mb >= 1 -> "${(mb * 10).toInt() / 10.0} MB"
                                else -> "${kb.toInt()} KB"
                            }
                        }
                        Text(
                            text = formattedSize,
                            color = TextGray,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = "Folder",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                    }
                }

                if (!file.isDirectory) {
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) PrimaryBlue else Color.Transparent,
                            border = if (isSelected) null else BorderStroke(2.dp, SoftGray),
                            modifier = Modifier.size(22.dp)
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(4.dp))
                            }
                        }
                    }
                } else {
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGray.copy(alpha = 0.5f))
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                val thumbnailBitmap = remember(file.thumbnail) {
                    file.thumbnail?.toImageBitmap()
                }

                // Content
                when {
                    file.isDirectory -> {
                        Column(
                            modifier = Modifier.fillMaxSize().background(PrimaryBlue.copy(alpha = 0.05f)),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(40.dp))
                            Text(
                                text = file.name,
                                color = TextDark,
                                fontSize = 11.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    thumbnailBitmap != null -> {
                        Image(
                            bitmap = thumbnailBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    file.path != null && (category == FileCategory.Images || category == FileCategory.Videos) -> {
                        KamelImage(
                            resource = asyncPainterResource(file.path.ensureUriScheme()),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onLoading = { ShimmerEffect(modifier = Modifier.fillMaxSize()) },
                            onFailure = { FilePlaceholder(file.name, category, onPlayClick) }
                        )
                    }
                    else -> {
                        FilePlaceholder(file.name, category, onPlayClick)
                    }
                }
                
                // Video Play Overlay (Always show for videos)
                if (!file.isDirectory && category == FileCategory.Videos) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                .padding(4.dp)
                        )
                    }
                }

                // Selection Radio Button (Top-Right)
                if (!file.isDirectory) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        IconButton(
                            onClick = onToggle,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) PrimaryBlue else Color.White.copy(alpha = 0.6f),
                                border = if (isSelected) null else BorderStroke(1.dp, Color.White),
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = "Select",
                                    tint = if (isSelected) Color.White else Color.Transparent,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilePlaceholder(name: String, category: FileCategory, onPlayClick: () -> Unit) {
    val (icon, color) = when (category) {
        FileCategory.Audio -> "🎵" to Color(0xFF5856D6)
        FileCategory.Videos -> "🎬" to Color(0xFFFF9500)
        FileCategory.Documents -> "📄" to Color(0xFF34C759)
        else -> "📁" to Color(0xFF007AFF)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(color.copy(alpha = 0.1f)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 32.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                color = TextDark,
                fontSize = 10.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp),
                fontWeight = FontWeight.Medium
            )
        }
        
        if (category == FileCategory.Audio) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = PrimaryBlue,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.8f), CircleShape)
                        .padding(4.dp)
                )
            }
        }
    }
}
