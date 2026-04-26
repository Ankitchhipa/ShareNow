package org.sharenow.fileshare.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.russhwolf.settings.Settings
import org.sharenow.fileshare.data.ConnectionManager
import org.sharenow.fileshare.data.HistoryManager
import org.sharenow.fileshare.data.TransferManager
import org.sharenow.fileshare.model.*
import org.sharenow.fileshare.platform.beginTransferKeepAlive
import org.sharenow.fileshare.platform.endTransferKeepAlive
import org.sharenow.fileshare.platform.PlatformBackHandler
import org.sharenow.fileshare.platform.cancelTransferNotification
import org.sharenow.fileshare.platform.currentDeviceName
import org.sharenow.fileshare.platform.generateQrBitmap
import org.sharenow.fileshare.platform.isSystemHotspotEnabled
import org.sharenow.fileshare.platform.isWifiEnabled
import org.sharenow.fileshare.platform.openHotspotSettings
import org.sharenow.fileshare.platform.openWifiSettings
import org.sharenow.fileshare.platform.showTransferCompleteNotification
import org.sharenow.fileshare.platform.updateTransferNotification
import org.sharenow.fileshare.ui.components.FuturisticBackground
import org.sharenow.fileshare.ui.components.FuturisticBottomBar
import org.sharenow.fileshare.ui.components.PermissionBottomSheet
import org.sharenow.fileshare.ui.screens.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FileShareApp() {
    val settings: Settings = remember { Settings() }
    val isOnboardingFinished = remember { settings.getBoolean("onboarding_finished", false) }
    
    var currentScreen by remember { mutableStateOf(AppScreen.Splash) }
    var deviceName by remember { mutableStateOf(currentDeviceName()) }
    var selectedFiles by remember { mutableStateOf<List<SharedFile>>(emptyList()) }
    var sendUiState by remember { mutableStateOf(SendUiState()) }
    var receiveUiState by remember { mutableStateOf(ReceiveUiState()) }
    var previewFile by remember { mutableStateOf<ReceivedFile?>(null) }
    var previewSharedFile by remember { mutableStateOf<SharedFile?>(null) }
    var sentSuccessFiles by remember { mutableStateOf<List<SharedFile>>(emptyList()) }
    var transferDisconnectRequested by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val connectionManager = remember { ConnectionManager() }
    val transferManager = remember { TransferManager() }

    var permissionState by remember { mutableStateOf(PermissionUiState()) }
    var showPermissionSheet by remember { mutableStateOf(false) }
    var pendingPermissionPurpose by remember { mutableStateOf<PermissionRequestPurpose?>(null) }
    var receivePreflightIssue by remember { mutableStateOf<String?>(null) }

    val requestPermission = org.sharenow.fileshare.platform.rememberPermissionRequester { state ->
        permissionState = state
        pendingPermissionPurpose?.let { purpose ->
            if (state.hasRequiredPermissionsFor(purpose)) {
                showPermissionSheet = false
                when (purpose) {
                    PermissionRequestPurpose.Send -> currentScreen = AppScreen.Selection
                    PermissionRequestPurpose.Receive -> {
                        currentScreen = AppScreen.Scanning
                        receiveUiState = ReceiveUiState()
                        sendUiState = SendUiState()
                    }
                    PermissionRequestPurpose.Check -> Unit
                }
                pendingPermissionPurpose = null
            } else {
                showPermissionSheet = true
            }
        }
    }

    LaunchedEffect(Unit) {
        requestPermission(PermissionRequestPurpose.Check)
    }

    val activeTransferProgress = sendUiState.transferProgress ?: receiveUiState.transferProgress
    val activeTransferTitle = if (sendUiState.transferProgress != null || sendUiState.isConnected) {
        "Sending files"
    } else {
        "Receiving files"
    }

    LaunchedEffect(
        currentScreen,
        activeTransferProgress?.bytesTransferred,
        activeTransferProgress?.totalBytes,
        activeTransferProgress?.currentFileName,
    ) {
        if (currentScreen == AppScreen.Transfer && activeTransferProgress != null) {
            updateTransferNotification(
                title = activeTransferTitle,
                message = buildTransferNotificationMessage(
                    fileName = activeTransferProgress.currentFileName,
                    transferredBytesLabel = formatBytes(activeTransferProgress.bytesTransferred),
                    totalBytesLabel = formatBytes(activeTransferProgress.totalBytes),
                    speed = activeTransferProgress.speedFormatted
                ),
                progressPercent = (activeTransferProgress.fraction * 100).toInt()
            )
        } else if (currentScreen != AppScreen.Success) {
            cancelTransferNotification()
        }
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen == AppScreen.Transfer) {
            beginTransferKeepAlive()
        } else {
            endTransferKeepAlive()
        }
    }

    if (showPermissionSheet) {
        PermissionBottomSheet(
            purpose = pendingPermissionPurpose,
            onGrant = {
                pendingPermissionPurpose?.let { requestPermission(it) }
            },
            onExit = {
                showPermissionSheet = false
                pendingPermissionPurpose = null
            }
        )
    }

    if (receivePreflightIssue != null) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (receivePreflightIssue.isHotspotIssue()) {
                            openHotspotSettings()
                        } else {
                            openWifiSettings()
                        }
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { receivePreflightIssue = null }) {
                    Text("Close")
                }
            },
            title = { Text("Receiver Setup Required", color = Color.Black) },
            text = { Text(receivePreflightIssue ?: "") }
        )
    }

    LaunchedEffect(receivePreflightIssue) {
        if (receivePreflightIssue == null) return@LaunchedEffect
        while (true) {
            val nextIssue = when {
                !isWifiEnabled() -> "Turn on Wi-Fi before receiving files."
                isSystemHotspotEnabled() -> "Turn off system Hotspot/Tethering before receiving files."
                else -> null
            }
            receivePreflightIssue = nextIssue
            if (nextIssue == null) break
            delay(700)
        }
    }

    fun startPermissionAwareFlow(purpose: PermissionRequestPurpose) {
        pendingPermissionPurpose = purpose
        if (permissionState.hasRequiredPermissionsFor(purpose)) {
            when (purpose) {
                PermissionRequestPurpose.Send -> currentScreen = AppScreen.Selection
                PermissionRequestPurpose.Receive -> {
                    currentScreen = AppScreen.Scanning
                    receiveUiState = ReceiveUiState()
                    sendUiState = SendUiState()
                }
                PermissionRequestPurpose.Check -> Unit
            }
            pendingPermissionPurpose = null
            showPermissionSheet = false
        } else {
            showPermissionSheet = true
            requestPermission(purpose)
        }
    }

    // Hardware Back Button Handling
    PlatformBackHandler(enabled = currentScreen != AppScreen.Home && currentScreen != AppScreen.Splash && currentScreen != AppScreen.Onboarding) {
        when (currentScreen) {
            AppScreen.Selection -> currentScreen = AppScreen.Home
            AppScreen.History -> currentScreen = AppScreen.Home
            AppScreen.Profile -> currentScreen = AppScreen.Home
            AppScreen.Scanning -> {
                transferDisconnectRequested = true
                scope.launch { connectionManager.stopServer() }
                receiveUiState = ReceiveUiState()
                currentScreen = AppScreen.Home
            }
            AppScreen.Transfer -> {
                transferDisconnectRequested = true
                scope.launch { connectionManager.stopServer() }
                receiveUiState = receiveUiState.copy(isConnecting = false)
                endTransferKeepAlive()
                cancelTransferNotification()
                currentScreen = AppScreen.Home
            }
            AppScreen.Success -> {
                transferDisconnectRequested = true
                scope.launch { connectionManager.stopServer() }
                receiveUiState = ReceiveUiState()
                sendUiState = SendUiState()
                sentSuccessFiles = emptyList()
                endTransferKeepAlive()
                cancelTransferNotification()
                currentScreen = AppScreen.Home
            }
            AppScreen.Preview -> {
                if (previewFile != null) {
                    previewFile = null
                    currentScreen = AppScreen.Success
                } else if (previewSharedFile != null) {
                    previewSharedFile = null
                    currentScreen = AppScreen.Selection
                }
            }
            else -> {}
        }
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            if (currentScreen in listOf(AppScreen.Home, AppScreen.History, AppScreen.Profile)) {
                Box(modifier = Modifier.navigationBarsPadding()) {
                    FuturisticBottomBar(
                        selectedTab = when(currentScreen) {
                            AppScreen.Home -> 0
                            AppScreen.History -> 1
                            AppScreen.Profile -> 2
                            else -> 0
                        },
                        onTabSelected = { tabIndex ->
                            currentScreen = when(tabIndex) {
                                0 -> AppScreen.Home
                                1 -> AppScreen.History
                                2 -> AppScreen.Profile
                                else -> AppScreen.Home
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            FuturisticBackground {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                ) {
                    Crossfade(targetState = currentScreen) { screen ->
                        when (screen) {
                            AppScreen.Splash -> SplashScreen(onSplashFinished = { 
                                currentScreen = if (isOnboardingFinished) AppScreen.Home else AppScreen.Onboarding 
                            })
                            AppScreen.Onboarding -> OnboardingScreen(onFinished = { 
                                settings.putBoolean("onboarding_finished", true)
                                currentScreen = AppScreen.Home 
                            })
                            AppScreen.Home -> HomeScreen(
                                deviceName = deviceName,
                                onSendClick = { startPermissionAwareFlow(PermissionRequestPurpose.Send) },
                                onReceiveClick = { 
                                    receivePreflightIssue = when {
                                        !isWifiEnabled() -> "Turn on Wi-Fi before receiving files."
                                        isSystemHotspotEnabled() -> "Turn off system Hotspot/Tethering before receiving files."
                                        else -> null
                                    }
                                    if (receivePreflightIssue == null) {
                                        startPermissionAwareFlow(PermissionRequestPurpose.Receive)
                                    }
                                }
                            )
                            AppScreen.Selection -> FileSelectionScreen(
                                onBack = { currentScreen = AppScreen.Home },
                                onFilesSelected = { files ->
                                    transferDisconnectRequested = false
                                    selectedFiles = files
                                    currentScreen = AppScreen.Scanning
                                    receiveUiState = ReceiveUiState()
                                    sendUiState = SendUiState(
                                        connectionStatus = "Starting hotspot and preparing QR…",
                                        selectedFiles = files
                                    )
                                    scope.launch {
                                        val payload = connectionManager.startServer(
                                            onStatus = { sendUiState = sendUiState.copy(connectionStatus = it) },
                                            onClientConnected = { socket ->
                                                try {
                                                    sendUiState = sendUiState.copy(
                                                        connectionStatus = "Connection complete. Preparing files…",
                                                        transferProgress = TransferProgress(
                                                            currentFileName = selectedFiles.firstOrNull()?.name.orEmpty(),
                                                            bytesTransferred = 0L,
                                                            totalBytes = selectedFiles.sumOf { it.sizeInBytes },
                                                            currentIndex = 1,
                                                            totalCount = selectedFiles.size,
                                                            startTime = org.sharenow.fileshare.platform.currentTimeMillis(),
                                                            currentFileBytesTransferred = 0L,
                                                            currentFileSize = selectedFiles.firstOrNull()?.sizeInBytes ?: 0L
                                                        ),
                                                        isConnected = true,
                                                        errorMessage = null
                                                    )
                                                    currentScreen = AppScreen.Transfer
                                                    transferManager.sendFiles(socket, selectedFiles) { progress ->
                                                        sendUiState = sendUiState.copy(transferProgress = progress)
                                                    }
                                                    sentSuccessFiles = selectedFiles
                                                    HistoryManager.addHistoryItem(
                                                        TransferHistoryItem(
                                                            id = 0,
                                                            direction = TransferDirection.Sent,
                                                            peerName = "Receiver",
                                                            fileNames = selectedFiles.map { it.name },
                                                            totalBytes = selectedFiles.sumOf { it.sizeInBytes },
                                                            status = "Completed",
                                                            timestampLabel = "Just now"
                                                        )
                                                    )
                                                    showTransferCompleteNotification(
                                                        title = "Send complete",
                                                        message = "Transferred ${selectedFiles.size} files successfully."
                                                    )
                                                    currentScreen = AppScreen.Success
                                                } catch (e: Exception) {
                                                    if (!transferDisconnectRequested) {
                                                        sendUiState = sendUiState.copy(
                                                            errorMessage = buildTransferErrorMessage(e),
                                                            connectionStatus = "Transfer interrupted",
                                                            isConnected = false
                                                        )
                                                        cancelTransferNotification()
                                                        currentScreen = AppScreen.Scanning
                                                    }
                                                } finally {
                                                    connectionManager.closeConnection(socket)
                                                    connectionManager.stopServer()
                                                    endTransferKeepAlive()
                                                    transferDisconnectRequested = false
                                                }
                                            }
                                        )
                                        sendUiState = sendUiState.copy(
                                            qrBitmap = generateQrBitmap(payload.encode(), 512),
                                            qrValue = payload.encode()
                                        )
                                    }
                                },
                                onPreviewFile = { file ->
                                    previewSharedFile = file
                                    currentScreen = AppScreen.Preview
                                }
                            )
                            AppScreen.Scanning -> ScanningScreen(
                                qrBitmap = sendUiState.qrBitmap,
                                connectionStatus = if (sendUiState.qrBitmap != null) sendUiState.connectionStatus else receiveUiState.connectionStatus,
                                errorMessage = receiveUiState.errorMessage ?: sendUiState.errorMessage,
                                isConnecting = receiveUiState.isConnecting,
                                onOpenWifiSettings = if (sendUiState.qrBitmap == null) ({
                                    if (isSystemHotspotEnabled()) {
                                        openHotspotSettings()
                                    } else {
                                        openWifiSettings()
                                    }
                                }) else null,
                                onClose = { 
                                    transferDisconnectRequested = true
                                    scope.launch { connectionManager.stopServer() }
                                    receiveUiState = ReceiveUiState()
                                    sendUiState = SendUiState()
                                    cancelTransferNotification()
                                    currentScreen = AppScreen.Home 
                                },
                                onDeviceSelected = { payload ->
                                    transferDisconnectRequested = false
                                    receiveUiState = ReceiveUiState(
                                        scannedPayload = payload,
                                        isConnecting = true,
                                        connectionStatus = "Joining hotspot ${payload.ssid ?: payload.deviceName}…"
                                    )
                                    scope.launch {
                                        try {
                                            val socket = connectionManager.connect(payload) { status ->
                                                receiveUiState = receiveUiState.copy(connectionStatus = status, isConnecting = true, errorMessage = null)
                                            }
                                            try {
                                                receiveUiState = receiveUiState.copy(
                                                    connectionStatus = "Connection complete. Preparing files…",
                                                    isConnecting = false,
                                                    errorMessage = null
                                                )
                                                currentScreen = AppScreen.Transfer
                                                val received = transferManager.receiveFiles(
                                                    socket = socket,
                                                    onMetadataReceived = { meta ->
                                                        receiveUiState = receiveUiState.copy(receivedFiles = meta)
                                                    },
                                                    onProgress = { progress ->
                                                        receiveUiState = receiveUiState.copy(transferProgress = progress)
                                                    }
                                                )
                                                HistoryManager.addHistoryItem(
                                                    TransferHistoryItem(
                                                        id = 0,
                                                        direction = TransferDirection.Received,
                                                        peerName = payload.deviceName,
                                                        fileNames = received.map { it.name },
                                                        totalBytes = received.sumOf { it.sizeInBytes },
                                                        status = "Completed",
                                                        timestampLabel = "Just now",
                                                        receivedFiles = received
                                                    )
                                                )
                                                receiveUiState = receiveUiState.copy(receivedFiles = received)
                                                showTransferCompleteNotification(
                                                    title = "Receive complete",
                                                    message = "Saved ${received.size} files to ShareNow."
                                                )
                                                currentScreen = AppScreen.Success
                                            } finally {
                                                connectionManager.closeConnection(socket)
                                                connectionManager.stopServer()
                                                endTransferKeepAlive()
                                            }
                                        } catch (e: Exception) {
                                            connectionManager.stopServer()
                                            if (!transferDisconnectRequested) {
                                                receiveUiState = receiveUiState.copy(errorMessage = buildTransferErrorMessage(e), isConnecting = false)
                                                cancelTransferNotification()
                                                currentScreen = AppScreen.Scanning
                                            }
                                        } finally {
                                            transferDisconnectRequested = false
                                        }
                                    }
                                }
                            )
                            AppScreen.Transfer -> TransferProgressScreen(
                                connectionLabel = if (sendUiState.transferProgress != null || sendUiState.isConnected) {
                                    "Connected to receiver"
                                } else {
                                    "Connected to sender"
                                },
                                fileName = if (sendUiState.transferProgress != null) sendUiState.transferProgress?.currentFileName ?: "" else receiveUiState.transferProgress?.currentFileName ?: "",
                                progress = if (sendUiState.transferProgress != null) sendUiState.transferProgress?.fraction ?: 0f else receiveUiState.transferProgress?.fraction ?: 0f,
                                currentFileProgress = if (sendUiState.transferProgress != null) sendUiState.transferProgress?.currentFileFraction ?: 0f else receiveUiState.transferProgress?.currentFileFraction ?: 0f,
                                speed = if (sendUiState.transferProgress != null) sendUiState.transferProgress?.speedFormatted ?: "..." else receiveUiState.transferProgress?.speedFormatted ?: "...",
                                timeRemaining = if (sendUiState.transferProgress != null) sendUiState.transferProgress?.etaFormatted ?: "..." else receiveUiState.transferProgress?.etaFormatted ?: "...",
                                currentIndex = if (sendUiState.transferProgress != null) sendUiState.transferProgress?.currentIndex ?: 0 else receiveUiState.transferProgress?.currentIndex ?: 0,
                                totalCount = if (sendUiState.transferProgress != null) sendUiState.transferProgress?.totalCount ?: 0 else receiveUiState.transferProgress?.totalCount ?: 0,
                                transferredBytesLabel = formatBytes(if (sendUiState.transferProgress != null) sendUiState.transferProgress?.bytesTransferred ?: 0L else receiveUiState.transferProgress?.bytesTransferred ?: 0L),
                                totalBytesLabel = formatBytes(if (sendUiState.transferProgress != null) sendUiState.transferProgress?.totalBytes ?: 0L else receiveUiState.transferProgress?.totalBytes ?: 0L),
                                currentFileTransferredLabel = formatBytes(if (sendUiState.transferProgress != null) sendUiState.transferProgress?.currentFileBytesTransferred ?: 0L else receiveUiState.transferProgress?.currentFileBytesTransferred ?: 0L),
                                currentFileSizeLabel = formatBytes(if (sendUiState.transferProgress != null) sendUiState.transferProgress?.currentFileSize ?: 0L else receiveUiState.transferProgress?.currentFileSize ?: 0L),
                                transferItems = if (sendUiState.transferProgress != null || sendUiState.isConnected) {
                                    buildTransferItems(
                                        files = sendUiState.selectedFiles.map { it.name to it.sizeInBytes },
                                        progress = sendUiState.transferProgress,
                                        sideLabel = "Sending"
                                    )
                                } else {
                                    buildTransferItems(
                                        files = receiveUiState.receivedFiles.map { it.name to it.sizeInBytes },
                                        progress = receiveUiState.transferProgress,
                                        sideLabel = "Receiving"
                                    )
                                },
                                onCancel = { 
                                    transferDisconnectRequested = true
                                    scope.launch { connectionManager.stopServer() }
                                    endTransferKeepAlive()
                                    cancelTransferNotification()
                                    currentScreen = AppScreen.Home 
                                }
                            )
                            AppScreen.Success -> SuccessScreen(
                                onDone = { 
                                    transferDisconnectRequested = true
                                    scope.launch { connectionManager.stopServer() }
                                    receiveUiState = ReceiveUiState()
                                    sendUiState = SendUiState()
                                    sentSuccessFiles = emptyList()
                                    endTransferKeepAlive()
                                    cancelTransferNotification()
                                    currentScreen = AppScreen.Home 
                                },
                                onSendMore = { 
                                    transferDisconnectRequested = true
                                    scope.launch { connectionManager.stopServer() }
                                    receiveUiState = ReceiveUiState()
                                    sendUiState = SendUiState()
                                    selectedFiles = emptyList()
                                    endTransferKeepAlive()
                                    cancelTransferNotification()
                                    currentScreen = AppScreen.Selection 
                                },
                                receivedFiles = receiveUiState.receivedFiles,
                                sentFiles = sentSuccessFiles,
                                onPreviewFile = { file ->
                                    previewFile = file
                                    currentScreen = AppScreen.Preview
                                }
                            )
                            AppScreen.Preview -> {
                                val received = previewFile
                                val shared = previewSharedFile
                                when {
                                    received != null -> {
                                        PreviewScreen(
                                            fileName = received.name,
                                            filePath = received.savedPath,
                                            thumbnail = received.thumbnail,
                                            onBack = { 
                                                previewFile = null
                                                currentScreen = AppScreen.Success 
                                            }
                                        )
                                    }
                                    shared != null -> {
                                        PreviewScreen(
                                            fileName = shared.name,
                                            filePath = shared.path ?: "",
                                            thumbnail = shared.thumbnail,
                                            onBack = { 
                                                previewSharedFile = null
                                                currentScreen = AppScreen.Selection 
                                            }
                                        )
                                    }
                                }
                            }
                            AppScreen.History -> HistoryScreen(onPreviewFile = { file ->
                                previewFile = file
                                currentScreen = AppScreen.Preview
                            })
                            AppScreen.Profile -> ProfileScreen(deviceName = deviceName)
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
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

private fun buildTransferItems(
    files: List<Pair<String, Long>>,
    progress: TransferProgress?,
    sideLabel: String,
): List<TransferFileItem> {
    if (files.isEmpty()) return emptyList()
    return files.mapIndexed { index, (name, size) ->
        val status = when {
            progress == null -> TransferFileStatus.Pending
            index + 1 < progress.currentIndex -> TransferFileStatus.Completed
            index + 1 == progress.currentIndex -> {
                if (progress.currentFileFraction >= 1f) TransferFileStatus.Completed else TransferFileStatus.Transferring
            }
            else -> TransferFileStatus.Pending
        }
        val itemProgress = when (status) {
            TransferFileStatus.Completed -> 1f
            TransferFileStatus.Transferring -> progress?.currentFileFraction ?: 0f
            TransferFileStatus.Pending -> 0f
        }
        TransferFileItem(
            name = name,
            sizeInBytes = size,
            sideLabel = sideLabel,
            progress = itemProgress,
            status = status
        )
    }
}

private fun buildTransferErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    return when {
        "Software caused connection abort" in message -> "Connection closed while the network was switching. Reconnect both devices and try again."
        "Connection closed" in message -> "Connection closed. Start a fresh connection and try again."
        "Broken pipe" in message -> "Transfer was interrupted because the connection dropped. Keep both phones awake and retry."
        "Connection reset" in message -> "Connection was lost during transfer. Reconnect both devices and try again."
        "timed out" in message.lowercase() -> "Transfer timed out. Keep both devices unlocked and close to each other, then retry."
        else -> message.ifBlank { "Transfer failed. Please reconnect and try again." }
    }
}

private fun String?.isHotspotIssue(): Boolean {
    return this?.contains("Hotspot", ignoreCase = true) == true ||
        this?.contains("Tethering", ignoreCase = true) == true
}

private fun buildTransferNotificationMessage(
    fileName: String,
    transferredBytesLabel: String,
    totalBytesLabel: String,
    speed: String,
): String {
    val safeFileName = if (fileName.isBlank()) "Preparing files" else fileName
    return "$safeFileName • $transferredBytesLabel / $totalBytesLabel • $speed"
}
