package org.sharenow.fileshare.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import org.sharenow.fileshare.platform.decodeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sharenow.fileshare.model.ReceivedFile
import org.sharenow.fileshare.model.SharedFile
import org.sharenow.fileshare.ui.components.NeonButton
import org.sharenow.fileshare.ui.components.NeonOutlineButton
import org.sharenow.fileshare.ui.theme.*

@Composable
fun SuccessScreen(
    onDone: () -> Unit,
    onSendMore: () -> Unit,
    receivedFiles: List<ReceivedFile> = emptyList(),
    sentFiles: List<SharedFile> = emptyList(),
    onPreviewFile: (ReceivedFile) -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(90.dp),
                shape = CircleShape,
                color = AccentGreen,
                shadowElevation = 8.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    modifier = Modifier.padding(20.dp),
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Transfer Completed",
            style = MaterialTheme.typography.headlineMedium,
            color = TextDark,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = when {
                receivedFiles.isNotEmpty() -> "Received ${receivedFiles.size} files"
                sentFiles.isNotEmpty() -> "Sent ${sentFiles.size} files"
                else -> "Your files have been shared successfully."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = TextGray,
            modifier = Modifier.padding(top = 8.dp)
        )

        if (receivedFiles.isNotEmpty() || sentFiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                items(receivedFiles) { file ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(80.dp)
                            .clickable { onPreviewFile(file) }
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(70.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceWhite,
                            shadowElevation = 4.dp
                        ) {
                            val bitmap = remember(file.thumbnail) {
                                file.thumbnail?.let { decodeImageBitmap(it) }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("📄", fontSize = 28.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = file.name,
                            color = TextDark,
                            fontSize = 11.sp,
                            maxLines = 1,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                items(sentFiles) { file ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(80.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(70.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceWhite,
                            shadowElevation = 4.dp
                        ) {
                            val bitmap = remember(file.thumbnail) {
                                file.thumbnail?.let { decodeImageBitmap(it) }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("\uD83D\uDCC4", fontSize = 28.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = file.name,
                            color = TextDark,
                            fontSize = 11.sp,
                            maxLines = 1,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(56.dp))

        NeonButton(
            text = "Send More Files",
            onClick = onSendMore
        )

        Spacer(modifier = Modifier.height(16.dp))

        NeonOutlineButton(
            text = "Back to Home",
            onClick = onDone,
            color = TextGray
        )
    }
}
