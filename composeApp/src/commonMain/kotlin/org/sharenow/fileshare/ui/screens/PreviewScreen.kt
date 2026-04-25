package org.sharenow.fileshare.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.sharenow.fileshare.model.ensureUriScheme
import org.sharenow.fileshare.platform.AudioPlayer
import org.sharenow.fileshare.platform.VideoPlayer
import org.sharenow.fileshare.platform.decodeImageBitmap
import org.sharenow.fileshare.ui.theme.BackgroundLight
import org.sharenow.fileshare.ui.theme.TextDark
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    fileName: String,
    filePath: String,
    thumbnail: ByteArray?,
    onBack: () -> Unit
) {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val isImage = extension in listOf("png", "jpg", "jpeg", "gif", "webp", "heic")
    val isVideo = extension in listOf("mp4", "mkv", "mov", "avi", "webm")
    val isAudio = extension in listOf("mp3", "wav", "aac", "m4a", "ogg")
    val uri = remember(filePath) { filePath.ensureUriScheme() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, style = MaterialTheme.typography.titleMedium, color = if (isVideo) Color.White else TextDark) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = if (isVideo) Color.White else TextDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isVideo) Color.Black else Color.White
                )
            )
        },
        containerColor = if (isVideo) Color.Black else BackgroundLight
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                isImage -> {
                    KamelImage(
                        resource = { asyncPainterResource(uri) },
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        onLoading = {
                            val bitmap = remember(thumbnail) { thumbnail?.let { decodeImageBitmap(it) } }
                            if (bitmap != null) {
                                Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            } else {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                        },
                        onFailure = { GenericFilePreview(fileName) }
                    )
                }
                isVideo -> {
                    VideoPlayer(
                        modifier = Modifier.fillMaxSize(),
                        url = uri
                    )
                }
                isAudio -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(120.dp),
                            tint = Color.Gray.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        AudioPlayer(
                            modifier = Modifier.fillMaxWidth(),
                            url = uri
                        )
                    }
                }
                else -> {
                    GenericFilePreview(fileName)
                }
            }
        }
    }
}

@Composable
fun GenericFilePreview(fileName: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = Color.Gray.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(fileName, color = TextDark)
    }
}
