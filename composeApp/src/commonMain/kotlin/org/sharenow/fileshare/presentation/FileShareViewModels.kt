package org.sharenow.fileshare.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.sharenow.fileshare.data.ConnectionManager
import org.sharenow.fileshare.data.TransferManager
import org.sharenow.fileshare.model.ConnectionPayload
import org.sharenow.fileshare.model.ReceiveUiState
import org.sharenow.fileshare.model.SendUiState
import org.sharenow.fileshare.model.SharedFile
import org.sharenow.fileshare.platform.currentDeviceName
import org.sharenow.fileshare.platform.generateQrBitmap
import org.sharenow.fileshare.platform.PlatformSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SendViewModel(
    private val connectionManager: ConnectionManager = ConnectionManager(),
    private val transferManager: TransferManager = TransferManager(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeSocket: PlatformSocket? = null
    private var pendingFiles: List<SharedFile> = emptyList()
    private var hasStartedServer = false

    var uiState by mutableStateOf(
        SendUiState(
            deviceName = currentDeviceName(),
            connectionStatus = "Choose files to get started",
        )
    )
        private set

    fun onFilesSelected(files: List<SharedFile>) {
        pendingFiles = files
        uiState = uiState.copy(selectedFiles = files, errorMessage = null)
        if (uiState.isConnected && files.isNotEmpty()) {
            sendSelectedFiles()
        }
    }

    fun ensureServerStarted() {
        if (hasStartedServer) return
        hasStartedServer = true
        scope.launch {
            try {
                val payload = connectionManager.startServer(
                    onStatus = { status -> uiState = uiState.copy(connectionStatus = status, errorMessage = null) },
                    onClientConnected = { socket ->
                        activeSocket = socket
                        uiState = uiState.copy(isConnected = true, connectionStatus = "Receiver connected")
                        if (pendingFiles.isNotEmpty()) {
                            transferFiles(socket)
                        } else {
                            uiState = uiState.copy(connectionStatus = "Connected. Waiting for file confirmation.")
                        }
                    },
                )
                uiState = uiState.copy(
                    qrValue = payload.encode(),
                    qrBitmap = generateQrBitmap(payload.encode(), 720),
                    connectionStatus = "Waiting for receiver on ${payload.host}:${payload.port}",
                )
            } catch (error: Throwable) {
                hasStartedServer = false
                uiState = uiState.copy(errorMessage = error.message ?: "Unable to start sender")
            }
        }
    }

    fun sendSelectedFiles() {
        if (pendingFiles.isEmpty()) {
            uiState = uiState.copy(errorMessage = "Select at least one file to send")
            return
        }
        val socket = activeSocket
        if (socket == null) {
            uiState = uiState.copy(connectionStatus = "Waiting for receiver to connect")
            return
        }
        scope.launch {
            transferFiles(socket)
        }
    }

    fun resetTransferState() {
        uiState = uiState.copy(transferProgress = null, errorMessage = null)
    }

    fun dispose() {
        activeSocket?.close()
        connectionManager.dispose()
        scope.cancel()
    }

    private suspend fun transferFiles(socket: PlatformSocket) {
        transferManager.sendFiles(
            socket = socket,
            files = pendingFiles,
            onProgress = { progress -> uiState = uiState.copy(transferProgress = progress) },
        )
        uiState = uiState.copy(connectionStatus = "Transfer complete")
    }
}

class ReceiveViewModel(
    private val connectionManager: ConnectionManager = ConnectionManager(),
    private val transferManager: TransferManager = TransferManager(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var hasConsumedScan = false

    var uiState by mutableStateOf(ReceiveUiState())
        private set

    fun onPermissionChanged(granted: Boolean) {
        uiState = uiState.copy(hasCameraPermission = granted)
    }

    fun onQrScanned(rawValue: String) {
        if (hasConsumedScan) return
        val payload = ConnectionPayload.decode(rawValue) ?: run {
            uiState = uiState.copy(errorMessage = "Invalid QR code")
            return
        }
        hasConsumedScan = true
        uiState = uiState.copy(
            scannedPayload = payload,
            isConnecting = true,
            connectionStatus = "Connecting to ${payload.deviceName}",
            errorMessage = null,
        )
        scope.launch {
            try {
                val socket = connectionManager.connect(
                    payload = payload,
                    onStatus = { status -> uiState = uiState.copy(connectionStatus = status) },
                )
                val received = transferManager.receiveFiles(
                    socket = socket,
                    onMetadataReceived = { /* optional: handle pre-transfer metadata if needed */ },
                    onProgress = { progress ->
                        uiState = uiState.copy(
                            transferProgress = progress,
                            connectionStatus = "Receiving ${progress.currentFileName}",
                        )
                    },
                )
                socket.close()
                uiState = uiState.copy(
                    receivedFiles = received,
                    isConnecting = false,
                    connectionStatus = "Transfer complete",
                )
            } catch (error: Throwable) {
                hasConsumedScan = false
                uiState = uiState.copy(
                    isConnecting = false,
                    errorMessage = error.message ?: "Connection failed",
                    connectionStatus = "Ready to scan again",
                )
            }
        }
    }

    fun onScannerError(message: String) {
        uiState = uiState.copy(errorMessage = message)
    }

    fun resetTransferState() {
        hasConsumedScan = false
        uiState = uiState.copy(
            transferProgress = null,
            errorMessage = null,
            connectionStatus = "Ready to scan",
        )
    }

    fun dispose() {
        connectionManager.dispose()
        scope.cancel()
    }
}
