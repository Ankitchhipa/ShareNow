package org.sharenow.fileshare.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import org.sharenow.fileshare.model.PermissionUiState
import org.sharenow.fileshare.model.FileCategory
import org.sharenow.fileshare.model.PermissionRequestPurpose
import org.sharenow.fileshare.model.ReceivedFile
import org.sharenow.fileshare.model.SharedFile
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIDevice

actual fun currentDeviceName(): String = UIDevice.currentDevice.name

actual suspend fun localIpAddress(): String = "127.0.0.1"

actual fun readFileChunk(path: String, offset: Long, size: Int): ByteArray? = null

actual suspend fun streamFileChunks(
    path: String,
    chunkSize: Int,
    onChunk: suspend (ByteArray) -> Unit,
) = Unit

actual fun saveChunkToFile(path: String, chunk: ByteArray, append: Boolean) {}

actual fun getTemporaryFilePath(fileName: String): String = ""

actual fun generateQrBitmap(content: String, size: Int): ImageBitmap? = null

actual suspend fun getFilesByCategory(category: FileCategory): List<SharedFile> = emptyList()

actual suspend fun getShareNowReceivedFiles(): List<ReceivedFile> = emptyList()

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = null

@Composable
actual fun rememberPermissionRequester(
    onPermissionStateChanged: (PermissionUiState) -> Unit,
): (PermissionRequestPurpose) -> Unit = remember {
    { purpose ->
        onPermissionStateChanged(
            PermissionUiState(
                cameraGranted = purpose != PermissionRequestPurpose.Receive,
                storageGranted = true,
                allFilesGranted = true,
                networkGranted = true,
                notificationsGranted = true,
            )
        )
    }
}

@Composable
actual fun rememberFilePickerLauncher(
    onFilesSelected: (List<SharedFile>) -> Unit,
): () -> Unit = remember { { onFilesSelected(emptyList()) } }

@Composable
actual fun QrScannerView(
    modifier: Modifier,
    onQrScanned: (String) -> Unit,
    onPermissionChanged: (Boolean) -> Unit,
    onError: (String) -> Unit,
) {
    LaunchedEffect(Unit) {
        onPermissionChanged(false)
        onError("Native iOS camera scanning hook is scaffolded but not fully wired yet.")
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x22000000)),
        contentAlignment = Alignment.Center,
    ) {
        Text("iOS scanner placeholder", color = Color.White)
    }
}

actual fun getStorageStats(): StorageStats = StorageStats(
    totalBytes = 0L,
    availableBytes = 0L,
    usedBytes = 0L,
)

actual fun getFilesInDirectory(path: String): List<SharedFile> = emptyList()

actual fun getExternalStoragePath(): String {
    val directory = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String
    return directory ?: ""
}

@Composable
actual fun VideoPlayer(modifier: Modifier, url: String) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Video preview is not wired on iOS yet.", color = Color.White)
    }
}

@Composable
actual fun AudioPlayer(modifier: Modifier, url: String) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Audio preview is not wired on iOS yet.", color = Color.White)
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual class PlatformServerSocket actual constructor() {
    actual suspend fun start(port: Int): Int = error("iOS server socket bridge is not wired yet")
    actual suspend fun accept(): PlatformSocket = error("iOS server socket bridge is not wired yet")
    actual fun close() = Unit
}

actual fun currentTimeMillis(): Long = (platform.Foundation.NSDate().timeIntervalSince1970() * 1000).toLong()

actual fun startLocalHotspot(onSuccess: (String, String) -> Unit, onFailure: (String) -> Unit) {
    onFailure("Hotspot API not supported on iOS")
}

actual fun stopLocalHotspot() {}

actual fun connectToWifi(ssid: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
    onFailure("Wi-Fi hotspot join is not wired on iOS yet")
}

actual fun isWifiEnabled(): Boolean = true

actual fun isSystemHotspotEnabled(): Boolean = false

actual fun openWifiSettings() = Unit

actual fun isAppInBackground(): Boolean = false

actual fun updateTransferNotification(title: String, message: String, progressPercent: Int) = Unit

actual fun showTransferCompleteNotification(title: String, message: String) = Unit

actual fun cancelTransferNotification() = Unit

actual fun beginTransferKeepAlive() = Unit

actual fun endTransferKeepAlive() = Unit

actual class PlatformSocket actual constructor() {
    actual suspend fun connect(host: String, port: Int) {
        error("iOS socket bridge is not wired yet")
    }
    actual suspend fun readExactly(byteCount: Int): ByteArray = error("iOS socket bridge is not wired yet")
    actual suspend fun writeFully(bytes: ByteArray) {
        error("iOS socket bridge is not wired yet")
    }
    actual suspend fun flush() = Unit
    actual fun close() = Unit
}

actual fun getAppVersion(): String {
    return NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        ?: "1.0"
}
