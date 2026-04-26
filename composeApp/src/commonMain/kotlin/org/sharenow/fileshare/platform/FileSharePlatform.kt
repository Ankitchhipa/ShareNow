package org.sharenow.fileshare.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import org.sharenow.fileshare.model.PermissionRequestPurpose
import org.sharenow.fileshare.model.PermissionUiState
import org.sharenow.fileshare.model.ReceivedFile
import org.sharenow.fileshare.model.SharedFile

expect fun currentDeviceName(): String

expect suspend fun localIpAddress(): String

expect fun readFileChunk(path: String, offset: Long, size: Int): ByteArray?

expect suspend fun streamFileChunks(
    path: String,
    chunkSize: Int,
    onChunk: suspend (ByteArray) -> Unit,
)

expect fun saveChunkToFile(path: String, chunk: ByteArray, append: Boolean)

expect fun getTemporaryFilePath(fileName: String): String

expect fun generateQrBitmap(content: String, size: Int): ImageBitmap?

@Composable
expect fun rememberPermissionRequester(
    onPermissionStateChanged: (PermissionUiState) -> Unit,
): (PermissionRequestPurpose) -> Unit

@Composable
expect fun rememberFilePickerLauncher(
    onFilesSelected: (List<SharedFile>) -> Unit,
): () -> Unit

expect suspend fun getFilesByCategory(category: org.sharenow.fileshare.model.FileCategory): List<SharedFile>

expect suspend fun getShareNowReceivedFiles(): List<ReceivedFile>

fun ByteArray.toImageBitmap(): ImageBitmap? = decodeImageBitmap(this)

expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

@Composable
expect fun QrScannerView(
    modifier: Modifier,
    onQrScanned: (String) -> Unit,
    onPermissionChanged: (Boolean) -> Unit,
    onError: (String) -> Unit,
)

expect fun getStorageStats(): StorageStats

data class StorageStats(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long
)

expect fun getFilesInDirectory(path: String): List<SharedFile>

expect fun getExternalStoragePath(): String

@Composable
expect fun VideoPlayer(modifier: Modifier, url: String)

@Composable
expect fun AudioPlayer(modifier: Modifier, url: String)

@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

expect fun currentTimeMillis(): Long

expect fun startLocalHotspot(onSuccess: (String, String) -> Unit, onFailure: (String) -> Unit)

expect fun stopLocalHotspot()

expect fun connectToWifi(ssid: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit)

expect fun isWifiEnabled(): Boolean

expect fun isSystemHotspotEnabled(): Boolean

expect fun openWifiSettings()

expect fun openHotspotSettings()

expect fun isAppInBackground(): Boolean

expect fun updateTransferNotification(
    title: String,
    message: String,
    progressPercent: Int,
)

expect fun showTransferCompleteNotification(
    title: String,
    message: String,
)

expect fun cancelTransferNotification()

expect fun beginTransferKeepAlive()

expect fun endTransferKeepAlive()


expect fun getAppVersion(): String
