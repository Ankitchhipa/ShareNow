package org.sharenow.fileshare.model

import androidx.compose.ui.graphics.ImageBitmap
import org.sharenow.fileshare.platform.currentTimeMillis

data class ConnectionPayload(
    val host: String,
    val port: Int,
    val deviceName: String,
    val ssid: String? = null,
    val password: String? = null,
) {
    fun encode(): String = listOf(
        host,
        port.toString(),
        deviceName.replace("|", "/"),
        ssid ?: "",
        password ?: ""
    ).joinToString("|")

    companion object {
        fun decode(raw: String): ConnectionPayload? {
            val parts = raw.split("|")
            if (parts.size < 3) return null
            val port = parts[1].toIntOrNull() ?: return null
            val deviceName = parts.drop(2).firstOrNull() ?: ""
            val ssid = parts.getOrNull(3).takeIf { it?.isNotEmpty() == true }
            val password = parts.getOrNull(4).takeIf { it?.isNotEmpty() == true }
            return ConnectionPayload(
                host = parts[0],
                port = port,
                deviceName = deviceName,
                ssid = ssid,
                password = password
            )
        }
    }
}

data class SharedFile(
    val name: String,
    val bytes: ByteArray? = null,
    val sizeInBytes: Long = bytes?.size?.toLong() ?: 0L,
    val path: String? = null,
    val thumbnail: ByteArray? = null,
    val isDirectory: Boolean = false,
    val dateModified: Long = 0L,
)

enum class FileCategory(val label: String) {
    All("All"),
    Images("Images"),
    Videos("Videos"),
    Audio("Audio"),
    Documents("Documents"),
    InternalStorage("Storage"),
    Others("Others"),
}

data class ReceivedFile(
    val name: String,
    val sizeInBytes: Long,
    val savedPath: String,
    val thumbnail: ByteArray? = null,
    val dateModified: Long = 0L,
)

data class TransferProgress(
    val currentFileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val startTime: Long = 0L,
    val currentFileBytesTransferred: Long = 0L,
    val currentFileSize: Long = 0L
) {
    val fraction: Float
        get() = if (totalBytes == 0L) 0f else (bytesTransferred.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)

    val currentFileFraction: Float
        get() = if (currentFileSize == 0L) 0f else (currentFileBytesTransferred.toDouble() / currentFileSize.toDouble()).toFloat().coerceIn(0f, 1f)

    val speedFormatted: String
        get() {
            if (startTime == 0L) return "0 KB/s"
            val durationSeconds = (currentTimeMillis() - startTime) / 1000.0
            if (durationSeconds <= 0) return "0 KB/s"
            val speedBytesPerSec = bytesTransferred / durationSeconds
            return formatSpeed(speedBytesPerSec)
        }

    val etaFormatted: String
        get() {
            if (startTime == 0L || bytesTransferred == 0L) return "--:--"
            val durationSeconds = (currentTimeMillis() - startTime) / 1000.0
            val speedBytesPerSec = bytesTransferred / durationSeconds
            if (speedBytesPerSec <= 0) return "--:--"
            val remainingBytes = totalBytes - bytesTransferred
            val remainingSeconds = (remainingBytes / speedBytesPerSec).toLong()
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        }

    private fun formatSpeed(bytesPerSec: Double): String {
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> "${mb.toString().take(4)} MB/s"
            kb >= 1.0 -> "${kb.toInt()} KB/s"
            else -> "${bytesPerSec.toInt()} B/s"
        }
    }
}

enum class TransferFileStatus {
    Pending,
    Transferring,
    Completed,
}

data class TransferFileItem(
    val name: String,
    val sizeInBytes: Long,
    val sideLabel: String,
    val progress: Float,
    val status: TransferFileStatus,
)

data class SendUiState(
    val deviceName: String = "",
    val qrBitmap: ImageBitmap? = null,
    val qrValue: String = "",
    val selectedFiles: List<SharedFile> = emptyList(),
    val connectionStatus: String = "Starting sender…",
    val transferProgress: TransferProgress? = null,
    val isConnected: Boolean = false,
    val errorMessage: String? = null,
)

data class ReceiveUiState(
    val connectionStatus: String = "Ready to scan",
    val scannedPayload: ConnectionPayload? = null,
    val qrBitmap: ImageBitmap? = null,
    val qrValue: String = "",
    val transferProgress: TransferProgress? = null,
    val receivedFiles: List<ReceivedFile> = emptyList(),
    val isConnecting: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val errorMessage: String? = null,
)

data class PermissionUiState(
    val cameraGranted: Boolean = false,
    val storageGranted: Boolean = false,
    val allFilesGranted: Boolean = false,
    val networkGranted: Boolean = false,
    val notificationsGranted: Boolean = true,
)

enum class PermissionRequestPurpose {
    Check,
    Send,
    Receive,
}

fun PermissionUiState.hasRequiredPermissionsFor(purpose: PermissionRequestPurpose): Boolean {
    return when (purpose) {
        PermissionRequestPurpose.Check -> false
        PermissionRequestPurpose.Send -> storageGranted && networkGranted && notificationsGranted
        PermissionRequestPurpose.Receive -> cameraGranted && networkGranted && notificationsGranted
    }
}

enum class ShareMode(val title: String, val subtitle: String) {
    Send("Send Files", "Pick files, show QR, and share instantly."),
    Receive("Receive Files", "Scan QR and accept transfers automatically."),
}

enum class AppScreen {
    Splash,
    Onboarding,
    Home,
    Selection,
    Scanning,
    Transfer,
    Success,
    History,
    Profile,
    Preview,
}

enum class HistoryFilter(val title: String) {
    All("All"),
    Sent("Sent"),
    Received("Received"),
}

enum class TransferDirection {
    Sent,
    Received,
}

data class TransferHistoryItem(
    val id: Long,
    val direction: TransferDirection,
    val peerName: String,
    val fileNames: List<String>,
    val totalBytes: Long,
    val status: String,
    val timestampLabel: String,
    val receivedFiles: List<ReceivedFile> = emptyList()
)

fun SharedFile.category(): FileCategory {
    return fileCategoryFromName(name)
}

fun ReceivedFile.category(): FileCategory {
    return fileCategoryFromName(name)
}

fun fileCategoryFromName(name: String): FileCategory {
    val extension = name.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "png", "jpg", "jpeg", "gif", "webp", "heic" -> FileCategory.Images
        "mp4", "mkv", "mov", "avi", "webm" -> FileCategory.Videos
        "mp3", "wav", "aac", "m4a", "ogg" -> FileCategory.Audio
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "zip" -> FileCategory.Documents
        else -> FileCategory.Others
    }
}

fun String.ensureUriScheme(): String {
    return if (this.contains("://")) this else "file://$this"
}
