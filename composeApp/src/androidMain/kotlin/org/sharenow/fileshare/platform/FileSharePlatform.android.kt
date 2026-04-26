package org.sharenow.fileshare.platform

import android.Manifest
import org.sharenow.BuildConfig
import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.sharenow.fileshare.model.FileCategory
import org.sharenow.fileshare.model.PermissionRequestPurpose
import org.sharenow.fileshare.model.PermissionUiState
import org.sharenow.fileshare.model.ReceivedFile
import org.sharenow.fileshare.model.SharedFile
import org.sharenow.fileshare.model.fileCategoryFromName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.EnumMap
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private object AndroidFileShareContext {
    lateinit var context: Context
}

internal const val TRANSFER_NOTIFICATION_CHANNEL_ID = "share_now_transfer"
internal const val TRANSFER_NOTIFICATION_ID = 4011

private var localOnlyHotspot: android.net.wifi.WifiManager.LocalOnlyHotspotReservation? = null
private var activeWifiNetwork: Network? = null
private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null
private var transferWakeLock: android.os.PowerManager.WakeLock? = null
private var transferWifiLock: android.net.wifi.WifiManager.WifiLock? = null
private var startedActivityCount: Int = 0
private var lifecycleCallbacksRegistered = false

fun initializeAndroidFileShare(context: Context) {
    AndroidFileShareContext.context = context.applicationContext
    ensureTransferNotificationChannel(context.applicationContext)
    registerAppLifecycleCallbacks(context.applicationContext)
}

actual fun currentDeviceName(): String {
    val context = AndroidFileShareContext.context
    
    // 1. Try Global Device Name (This is what usually holds "Vivo T2 Pro 5G")
    try {
        val globalName = android.provider.Settings.Global.getString(context.contentResolver, "device_name")
        if (!globalName.isNullOrBlank()) return globalName
    } catch (_: Exception) {}

    // 2. Try Bluetooth Name (User's friendly name)
    try {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter != null) {
            // Only read if we have permission or if on older version where permission isn't required for name
            val btName = bluetoothAdapter.name
            if (!btName.isNullOrBlank() && !btName.startsWith("Unknown", ignoreCase = true)) {
                return btName
            }
        }
    } catch (_: Exception) {}

    // 3. Try Secure Settings bluetooth_name
    try {
        val btNameSetting = android.provider.Settings.Secure.getString(context.contentResolver, "bluetooth_name")
        if (!btNameSetting.isNullOrBlank()) return btNameSetting
    } catch (_: Exception) {}

    // 4. Fallback: Formatted Manufacturer + Model
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val model = Build.MODEL
    return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
}

actual suspend fun localIpAddress(): String = withContext(Dispatchers.IO) {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces().toList()
        
        // Priority 1: Check for active Hotspot interfaces (ap0, softap, etc)
        val hotspotAddr = interfaces
            .filter { i -> i.name.lowercase().let { it.contains("ap") || it.contains("wlan1") } }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
        
        if (hotspotAddr != null) return@withContext hotspotAddr

        // Priority 2: Any non-loopback IPv4 (WiFi/Ethernet)
        interfaces.flatMap { it.inetAddresses.toList() }
            .filter { it is java.net.Inet4Address && !it.isLoopbackAddress }
            .map { it.hostAddress }
            .firstOrNull { it != null && it != "127.0.0.1" }
            ?: "127.0.0.1"
    } catch (e: Exception) {
        "127.0.0.1"
    }
}

actual fun readFileChunk(path: String, offset: Long, size: Int): ByteArray? {
    return try {
        if (path.startsWith("content://")) {
            val uri = Uri.parse(path)
            return AndroidFileShareContext.context.contentResolver.openInputStream(uri)?.use { input ->
                input.skip(offset)
                val bytes = ByteArray(size)
                val read = input.read(bytes)
                if (read == -1) null
                else if (read < size) bytes.copyOf(read)
                else bytes
            }
        }
        
        val file = File(path)
        if (!file.exists()) return null
        
        val bytes = ByteArray(size)
        val randomAccessFile = java.io.RandomAccessFile(file, "r")
        randomAccessFile.seek(offset)
        val read = randomAccessFile.read(bytes)
        randomAccessFile.close()
        if (read < size) bytes.copyOf(read) else bytes
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

actual suspend fun streamFileChunks(
    path: String,
    chunkSize: Int,
    onChunk: suspend (ByteArray) -> Unit,
) = withContext(Dispatchers.IO) {
    if (path.startsWith("content://")) {
        val uri = Uri.parse(path)
        AndroidFileShareContext.context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(chunkSize)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                onChunk(if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read))
            }
        }
        return@withContext
    }

    val file = File(path)
    if (!file.exists()) return@withContext
    file.inputStream().buffered(chunkSize).use { input ->
        val buffer = ByteArray(chunkSize)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            onChunk(if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read))
        }
    }
}

actual fun saveChunkToFile(path: String, chunk: ByteArray, append: Boolean) {
    try {
        if (path.startsWith("content://")) {
            val uri = Uri.parse(path)
            val mode = if (append) "wa" else "w"
            AndroidFileShareContext.context.contentResolver.openOutputStream(uri, mode)?.use { out ->
                out.write(chunk)
            }
            return
        }
        val file = File(path)
        val out = java.io.FileOutputStream(file, append)
        out.write(chunk)
        out.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun getTemporaryFilePath(fileName: String): String {
    val context = AndroidFileShareContext.context
    val categoryFolder = shareNowCategoryFolder(fileCategoryFromName(fileName))
    val resolver = context.contentResolver
    val contentValues = android.content.ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, uniqueFileName(fileName))
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeTypeForFileName(fileName))
        put(
            android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
            "${android.os.Environment.DIRECTORY_DOWNLOADS}/ShareNow/$categoryFolder"
        )
    }
    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) return uri.toString()

    val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
        ?.resolve("ShareNow")
        ?.resolve(categoryFolder)
        ?.also { it.mkdirs() }
        ?: context.cacheDir
    return File(dir, uniqueFileName(fileName)).absolutePath
}

private fun shareNowCategoryFolder(category: FileCategory): String = when (category) {
    FileCategory.Images -> "Images"
    FileCategory.Videos -> "Videos"
    FileCategory.Audio -> "Audio"
    FileCategory.Documents -> "Documents"
    FileCategory.InternalStorage -> "Storage"
    FileCategory.All -> "All"
    FileCategory.Others -> "Others"
}

private fun mimeTypeForFileName(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: "application/octet-stream"
}

private fun uniqueFileName(fileName: String): String {
    val baseName = fileName.substringBeforeLast('.', fileName)
    val extension = fileName.substringAfterLast('.', "")
    val timestamp = System.currentTimeMillis()
    return if (extension.isBlank()) {
        "${baseName}_$timestamp"
    } else {
        "${baseName}_$timestamp.$extension"
    }
}

actual fun generateQrBitmap(content: String, size: Int): ImageBitmap? {
    val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
        put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
        put(EncodeHintType.MARGIN, 1)
    }
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size) { index ->
        val x = index % size
        val y = index / size
        if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable
actual fun rememberPermissionRequester(
    onPermissionStateChanged: (PermissionUiState) -> Unit,
): (PermissionRequestPurpose) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        onPermissionStateChanged(readPermissionState(context))
    }

    return remember(launcher, context) {
        { purpose ->
            val state = readPermissionState(context)
            onPermissionStateChanged(state)

            if (purpose == PermissionRequestPurpose.Check) return@remember

            val toRequest = when (purpose) {
                PermissionRequestPurpose.Send -> buildSendPermissionsToRequest(context)
                PermissionRequestPurpose.Receive -> buildReceivePermissionsToRequest(context)
                PermissionRequestPurpose.Check -> emptyList()
            }

            if (toRequest.isNotEmpty()) {
                launcher.launch(toRequest.toTypedArray())
            }
        }
    }
}

private fun readPermissionState(context: Context): PermissionUiState {
    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val storagePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val storageGranted = storagePermissions.all { ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
    val networkPermissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        networkPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    val networkGranted = networkPermissions.all { ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
    val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    return PermissionUiState(
        cameraGranted = cameraGranted,
        storageGranted = storageGranted,
        networkGranted = networkGranted,
        allFilesGranted = true,
        notificationsGranted = notificationsGranted,
    )
}

private fun buildSendPermissionsToRequest(context: Context): List<String> {
    val requested = mutableListOf<String>()
    val storagePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    requested += storagePermissions.filter {
        ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val networkPermissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        networkPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    requested += networkPermissions.filter {
        ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        requested += Manifest.permission.POST_NOTIFICATIONS
    }
    return requested.distinct()
}

private fun buildReceivePermissionsToRequest(context: Context): List<String> {
    val requested = mutableListOf<String>()
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requested += Manifest.permission.CAMERA
    }
    val networkPermissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        networkPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    requested += networkPermissions.filter {
        ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        requested += Manifest.permission.POST_NOTIFICATIONS
    }
    return requested.distinct()
}

@Composable
actual fun rememberFilePickerLauncher(
    onFilesSelected: (List<SharedFile>) -> Unit,
): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> context.readSharedFile(uri) }
            }
            onFilesSelected(files)
        }
    }
    return remember(launcher) {
        { launcher.launch(arrayOf("*/*")) }
    }
}

actual suspend fun getFilesByCategory(category: org.sharenow.fileshare.model.FileCategory): List<SharedFile> = withContext(Dispatchers.IO) {
    val context = AndroidFileShareContext.context
    val files = mutableListOf<SharedFile>()
    val contentUri = when (category) {
        org.sharenow.fileshare.model.FileCategory.Images -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        org.sharenow.fileshare.model.FileCategory.Videos -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        org.sharenow.fileshare.model.FileCategory.Audio -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> android.provider.MediaStore.Files.getContentUri("external")
    }

    val projection = arrayOf(
        android.provider.MediaStore.MediaColumns._ID,
        android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
        android.provider.MediaStore.MediaColumns.SIZE,
        android.provider.MediaStore.MediaColumns.DATA,
        android.provider.MediaStore.MediaColumns.DATE_MODIFIED
    )

    context.contentResolver.query(contentUri, projection, null, null, "${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
        val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.SIZE)
        val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
        val dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol)
            val size = cursor.getLong(sizeCol)
            val date = cursor.getLong(dateCol)
            val uri = android.content.ContentUris.withAppendedId(contentUri, id)
            val path = uri.toString()
            
            var thumbnailBytes: ByteArray? = null
            if (category == org.sharenow.fileshare.model.FileCategory.Images || category == org.sharenow.fileshare.model.FileCategory.Videos) {
                try {
                    val uri = android.content.ContentUris.withAppendedId(contentUri, id)
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(uri, android.util.Size(300, 300), null)
                    } else {
                        if (category == org.sharenow.fileshare.model.FileCategory.Images) {
                            android.provider.MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, id, android.provider.MediaStore.Images.Thumbnails.MINI_KIND, null)
                        } else {
                            android.provider.MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, id, android.provider.MediaStore.Video.Thumbnails.MINI_KIND, null)
                        }
                    }
                    if (bitmap != null) {
                        val stream = java.io.ByteArrayOutputStream()
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, stream)
                        thumbnailBytes = stream.toByteArray()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            files.add(SharedFile(name = name, bytes = null, sizeInBytes = size, path = path, thumbnail = thumbnailBytes, dateModified = date))
            if (files.size >= 500) break
        }
    }
    files
}

actual suspend fun getShareNowReceivedFiles(): List<ReceivedFile> = withContext(Dispatchers.IO) {
    val context = AndroidFileShareContext.context
    val files = mutableListOf<ReceivedFile>()
    val downloadsUri = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        android.provider.MediaStore.MediaColumns._ID,
        android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
        android.provider.MediaStore.MediaColumns.SIZE,
        android.provider.MediaStore.MediaColumns.DATE_MODIFIED,
        android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
    )
    val shareNowPrefix = "${android.os.Environment.DIRECTORY_DOWNLOADS}/ShareNow/%"
    val selection = "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
    val sortOrder = "${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} DESC"

    try {
        context.contentResolver.query(downloadsUri, projection, selection, arrayOf(shareNowPrefix), sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                val modifiedAt = cursor.getLong(dateCol) * 1000L
                val uri = ContentUris.withAppendedId(downloadsUri, id)
                files.add(
                    ReceivedFile(
                        name = name,
                        sizeInBytes = size,
                        savedPath = uri.toString(),
                        dateModified = modifiedAt,
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    files.addAll(scanShareNowFallbackFolder(context))
    files
        .distinctBy { it.savedPath }
        .sortedByDescending { it.dateModified }
}

private fun scanShareNowFallbackFolder(context: Context): List<ReceivedFile> {
    val root = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
        ?.resolve("ShareNow")
        ?: return emptyList()
    if (!root.exists()) return emptyList()

    return root.walkTopDown()
        .filter { it.isFile }
        .map {
            ReceivedFile(
                name = it.name,
                sizeInBytes = it.length().coerceAtLeast(0L),
                savedPath = it.absolutePath,
                dateModified = it.lastModified(),
            )
        }
        .toList()
}

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    return try {
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

@Composable
actual fun QrScannerView(
    modifier: Modifier,
    onQrScanned: (String) -> Unit,
    onPermissionChanged: (Boolean) -> Unit,
    onError: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        onPermissionChanged(granted)
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            onPermissionChanged(true)
        }
    }

    if (!hasPermission) {
        Box(modifier = modifier)
        return
    }

    val previewView = remember { PreviewView(context) }
    val didScan = remember { AtomicBoolean(false) }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient()
        var cameraProvider: ProcessCameraProvider? = null

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().apply {
                        setAnalyzer(executor) { imageProxy ->
                            @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                            val mediaImage = imageProxy.image
                            if (mediaImage == null || didScan.get()) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val value = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
                                    if (value != null && didScan.compareAndSet(false, true)) {
                                        onQrScanned(value)
                                    }
                                }
                                .addOnFailureListener { error -> onError(error.message ?: "Unable to scan QR code") }
                                .addOnCompleteListener { imageProxy.close() }
                        }
                    }

                provider.unbindAll()
                if (lifecycleOwner.lifecycle.currentState != androidx.lifecycle.Lifecycle.State.DESTROYED) {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }
            } catch (e: Exception) {
                onError("Camera initialization failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProvider?.unbindAll()
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { previewView },
    )
}

actual fun getStorageStats(): StorageStats {
    val path = android.os.Environment.getExternalStorageDirectory()
    val stat = android.os.StatFs(path.path)
    val blockSize = stat.blockSizeLong
    val totalBlocks = stat.blockCountLong
    val availableBlocks = stat.availableBlocksLong
    
    val totalBytes = totalBlocks * blockSize
    val availableBytes = availableBlocks * blockSize
    val usedBytes = totalBytes - availableBytes
    
    return StorageStats(totalBytes, availableBytes, usedBytes)
}

actual fun getFilesInDirectory(path: String): List<SharedFile> {
    val directory = File(path)
    if (!directory.exists() || !directory.isDirectory) return emptyList()
    
    return directory.listFiles()?.map { file ->
        SharedFile(
            name = file.name,
            bytes = null,
            sizeInBytes = if (file.isDirectory) 0 else file.length(),
            path = file.absolutePath,
            thumbnail = null,
            isDirectory = file.isDirectory
        )
    } ?: emptyList()
}

actual fun getExternalStoragePath(): String {
    return android.os.Environment.getExternalStorageDirectory().absolutePath
}

@Composable
actual fun VideoPlayer(modifier: Modifier, url: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
    )
}

@Composable
actual fun AudioPlayer(modifier: Modifier, url: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(150.dp)
        )
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

private fun resetAndroidConnectionState() {
    val context = AndroidFileShareContext.context
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager

    wifiNetworkCallback?.let {
        runCatching { connectivityManager.unregisterNetworkCallback(it) }
    }
    wifiNetworkCallback = null
    activeWifiNetwork = null
    runCatching { connectivityManager.bindProcessToNetwork(null) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        localOnlyHotspot?.close()
        localOnlyHotspot = null
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        runCatching { wifiManager.disconnect() }
    }
}

actual fun startLocalHotspot(onSuccess: (String, String) -> Unit, onFailure: (String) -> Unit) {
    val context = AndroidFileShareContext.context
    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager

    resetAndroidConnectionState()
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            wifiManager.startLocalOnlyHotspot(object : android.net.wifi.WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: android.net.wifi.WifiManager.LocalOnlyHotspotReservation?) {
                    super.onStarted(reservation)
                    localOnlyHotspot = reservation
                    val config = reservation?.wifiConfiguration
                    if (config != null) {
                        onSuccess(config.SSID.removeSurrounding("\""), config.preSharedKey ?: "")
                    } else {
                        onFailure("Hotspot started but configuration is null")
                    }
                }

                override fun onStopped() {
                    super.onStopped()
                    localOnlyHotspot = null
                }

                override fun onFailed(reason: Int) {
                    super.onFailed(reason)
                    val message = when (reason) {
                        1 -> "No Wi-Fi channel available (ERROR_NO_CHANNEL)."
                        2 -> "Generic Hotspot error (ERROR_GENERIC)."
                        3 -> "Incompatible mode. Please turn off your system Wi-Fi Hotspot/Tethering and try again."
                        4 -> "Tethering is disallowed on this device (ERROR_TETHERING_DISALLOWED)."
                        else -> "Hotspot failed with reason: $reason"
                    }
                    onFailure(message)
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        } catch (e: Exception) {
            onFailure("Hotspot exception: ${e.message}")
        }
    } else {
        onFailure("Hotspot API requires Android O or higher")
    }
}

actual fun stopLocalHotspot() {
    resetAndroidConnectionState()
}

actual fun connectToWifi(ssid: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
    val context = AndroidFileShareContext.context
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager

    val cleanSsid = ssid.removeSurrounding("\"")

    resetAndroidConnectionState()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !wifiManager.isWifiEnabled) {
        onFailure("Wi-Fi is disabled. Turn Wi-Fi on, then scan the QR again.")
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val specifier = android.net.wifi.WifiNetworkSpecifier.Builder()
            .setSsid(cleanSsid)
            .setWpa2Passphrase(password)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
            .build()

        wifiNetworkCallback?.let {
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            private var isHandled = false

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                if (!isHandled) {
                    isHandled = true
                    activeWifiNetwork = network
                    connectivityManager.bindProcessToNetwork(network)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        onSuccess()
                    }, 350L)
                }
            }

            override fun onUnavailable() {
                super.onUnavailable()
                if (!isHandled) {
                    isHandled = true
                    resetAndroidConnectionState()
                    onFailure("Wi-Fi hotspot connection timed out. Keep both devices unlocked and close to each other.")
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                if (activeWifiNetwork == network) {
                    activeWifiNetwork = null
                }
                runCatching { connectivityManager.bindProcessToNetwork(null) }
            }
        }
        wifiNetworkCallback = callback
        connectivityManager.requestNetwork(request, callback, 8_000)
    } else {
        // Fallback for older versions (Android 8/9)
        val wifiConfig = android.net.wifi.WifiConfiguration().apply {
            SSID = "\"$cleanSsid\""
            preSharedKey = "\"$password\""
            status = android.net.wifi.WifiConfiguration.Status.ENABLED
            allowedProtocols.set(android.net.wifi.WifiConfiguration.Protocol.RSN)
            allowedProtocols.set(android.net.wifi.WifiConfiguration.Protocol.WPA)
            allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK)
        }
        
        val netId = wifiManager.addNetwork(wifiConfig)
        if (netId == -1) {
            // Try to find existing network
            val existingId = wifiManager.configuredNetworks?.firstOrNull { it.SSID == "\"$cleanSsid\"" }?.networkId
            if (existingId != null) {
                wifiManager.disconnect()
                wifiManager.enableNetwork(existingId, true)
                wifiManager.reconnect()
                onSuccess()
            } else {
                onFailure("Failed to add network configuration")
            }
        } else {
            wifiManager.disconnect()
            wifiManager.enableNetwork(netId, true)
            wifiManager.reconnect()
            onSuccess()
        }
    }
}

actual fun isWifiEnabled(): Boolean {
    val context = AndroidFileShareContext.context
    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
    return wifiManager.isWifiEnabled
}

actual fun isSystemHotspotEnabled(): Boolean {
    if (localOnlyHotspot != null) return false
    val context = AndroidFileShareContext.context
    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
    return runCatching {
        val method = wifiManager.javaClass.methods.firstOrNull { it.name == "isWifiApEnabled" }
        when (val result = method?.invoke(wifiManager)) {
            is Boolean -> result
            else -> false
        }
    }.getOrDefault(false)
}

actual fun openWifiSettings() {
    val context = AndroidFileShareContext.context
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        android.content.Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    } else {
        android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
    }.apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

actual fun openHotspotSettings() {
    val context = AndroidFileShareContext.context
    val intents = listOf(
        android.content.Intent("android.settings.TETHER_SETTINGS"),
        android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS),
        android.content.Intent(android.provider.Settings.ACTION_SETTINGS),
    ).map {
        it.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    intents.firstOrNull { intent ->
        runCatching { context.startActivity(intent) }.isSuccess
    }
}

actual fun isAppInBackground(): Boolean {
    return startedActivityCount <= 0
}

actual fun updateTransferNotification(title: String, message: String, progressPercent: Int) {
    val context = AndroidFileShareContext.context
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) return

    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val pendingIntent = PendingIntent.getActivity(
        context,
        100,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(context, TRANSFER_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle(title)
        .setContentText(message)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setContentIntent(pendingIntent)
        .setProgress(100, progressPercent.coerceIn(0, 100), false)
        .build()

    NotificationManagerCompat.from(context).notify(TRANSFER_NOTIFICATION_ID, notification)
}

actual fun showTransferCompleteNotification(title: String, message: String) {
    val context = AndroidFileShareContext.context
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) return

    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val pendingIntent = PendingIntent.getActivity(
        context,
        101,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(context, TRANSFER_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_upload_done)
        .setContentTitle(title)
        .setContentText(message)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setContentIntent(pendingIntent)
        .build()

    NotificationManagerCompat.from(context).notify(TRANSFER_NOTIFICATION_ID, notification)
}

actual fun cancelTransferNotification() {
    NotificationManagerCompat.from(AndroidFileShareContext.context).cancel(TRANSFER_NOTIFICATION_ID)
}

actual fun beginTransferKeepAlive() {
    val context = AndroidFileShareContext.context
    val serviceIntent = Intent(context, TransferForegroundService::class.java).apply {
        action = TransferForegroundService.ACTION_START
        putExtra(TransferForegroundService.EXTRA_TITLE, "Share Now transfer")
        putExtra(TransferForegroundService.EXTRA_MESSAGE, "Keeping your transfer active")
    }
    ContextCompat.startForegroundService(context, serviceIntent)

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
    if (transferWakeLock?.isHeld != true) {
        transferWakeLock = powerManager
            ?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "${context.packageName}:share_now_transfer")
            ?.apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 60 * 1000L)
            }
    }

    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
    if (transferWifiLock?.isHeld != true) {
        transferWifiLock = wifiManager
            ?.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "${context.packageName}:share_now_wifi")
            ?.apply {
                setReferenceCounted(false)
                acquire()
            }
    }
}

actual fun endTransferKeepAlive() {
    val context = AndroidFileShareContext.context
    val serviceIntent = Intent(context, TransferForegroundService::class.java).apply {
        action = TransferForegroundService.ACTION_STOP
    }
    runCatching { context.startService(serviceIntent) }

    runCatching {
        if (transferWakeLock?.isHeld == true) transferWakeLock?.release()
    }
    transferWakeLock = null

    runCatching {
        if (transferWifiLock?.isHeld == true) transferWifiLock?.release()
    }
    transferWifiLock = null
}

private fun ensureTransferNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val notificationManager = context.getSystemService<NotificationManager>() ?: return
    val channel = NotificationChannel(
        TRANSFER_NOTIFICATION_CHANNEL_ID,
        "Share Now Transfers",
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = "Shows Share Now transfer progress and completion"
    }
    notificationManager.createNotificationChannel(channel)
}

private fun registerAppLifecycleCallbacks(context: Context) {
    if (lifecycleCallbacksRegistered) return
    val application = context as? Application ?: return
    application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivityCount += 1
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    })
    lifecycleCallbacksRegistered = true
}

actual class PlatformServerSocket actual constructor() {
    private var delegate: ServerSocket? = null

    actual suspend fun start(port: Int): Int = withContext(Dispatchers.IO) {
        ServerSocket(port).also { delegate = it }.localPort
    }

    actual suspend fun accept(): PlatformSocket = withContext(Dispatchers.IO) {
        PlatformSocket(delegate?.accept() ?: error("Server socket is not running"))
    }

    actual fun close() {
        delegate?.close()
        delegate = null
    }
}

actual class PlatformSocket actual constructor() {
    private var delegate: Socket? = null
    private var inputStream: java.io.InputStream? = null
    private var outputStream: java.io.OutputStream? = null
    private val closed = AtomicBoolean(false)

    internal constructor(socket: Socket) : this() {
        delegate = socket
        configureSocket(socket)
    }

    actual suspend fun connect(host: String, port: Int) = withContext(Dispatchers.IO) {
        close()
        closed.set(false)
        val network = activeWifiNetwork
        val socket = if (network != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            network.socketFactory.createSocket()
        } else {
            Socket()
        }
        try {
            socket.connect(InetSocketAddress(host, port), SOCKET_CONNECT_TIMEOUT_MS)
            delegate = socket
            configureSocket(socket)
        } catch (error: Throwable) {
            runCatching { socket.close() }
            closed.set(true)
            throw normalizeSocketError(error)
        }
    }

    actual suspend fun readExactly(byteCount: Int): ByteArray = withContext(Dispatchers.IO) {
        if (closed.get()) throw IOException("Connection closed")
        val stream = inputStream ?: error("Socket input is not connected")
        val buffer = ByteArray(byteCount)
        var offset = 0
        try {
            while (offset < byteCount) {
                val bytesRead = stream.read(buffer, offset, byteCount - offset)
                if (bytesRead == -1) throw EOFException("Connection closed while reading")
                offset += bytesRead
            }
        } catch (error: Throwable) {
            if (closed.get()) throw IOException("Connection closed")
            throw normalizeSocketError(error)
        }
        buffer
    }

    actual suspend fun writeFully(bytes: ByteArray) = withContext(Dispatchers.IO) {
        if (closed.get()) throw IOException("Connection closed")
        val stream = outputStream ?: error("Socket output is not connected")
        try {
            stream.write(bytes)
        } catch (error: Throwable) {
            if (closed.get()) throw IOException("Connection closed")
            throw normalizeSocketError(error)
        }
    }

    actual suspend fun flush() = withContext(Dispatchers.IO) {
        if (closed.get()) return@withContext
        try {
            outputStream?.flush()
        } catch (error: Throwable) {
            if (closed.get()) return@withContext
            throw normalizeSocketError(error)
        }
        Unit
    }

    actual fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { outputStream?.flush() }
        val socket = delegate
        outputStream = null
        inputStream = null
        delegate = null
        socket?.let {
            runCatching { socket.shutdownInput() }
            runCatching { socket.shutdownOutput() }
            runCatching { socket.close() }
        }
    }

    private fun configureSocket(socket: Socket) {
        socket.keepAlive = true
        socket.tcpNoDelay = true
        socket.reuseAddress = true
        socket.sendBufferSize = SOCKET_BUFFER_SIZE
        socket.receiveBufferSize = SOCKET_BUFFER_SIZE
        socket.soTimeout = SOCKET_READ_TIMEOUT_MS
        inputStream = java.io.BufferedInputStream(socket.getInputStream(), SOCKET_BUFFER_SIZE)
        outputStream = java.io.BufferedOutputStream(socket.getOutputStream(), SOCKET_BUFFER_SIZE)
    }

    private fun normalizeSocketError(error: Throwable): Throwable {
        val message = error.message.orEmpty()
        if (error is SocketException && message.contains("Software caused connection abort", ignoreCase = true)) {
            return IOException("Connection closed by the device network. Reconnect and try again.", error)
        }
        if (error is SocketException && message.contains("Broken pipe", ignoreCase = true)) {
            return IOException("Connection closed by the other device. Reconnect and try again.", error)
        }
        if (error is SocketException && message.contains("Socket closed", ignoreCase = true)) {
            return IOException("Connection closed", error)
        }
        return error
    }

    private companion object {
        const val SOCKET_CONNECT_TIMEOUT_MS = 6_000
        const val SOCKET_READ_TIMEOUT_MS = 300_000
        const val SOCKET_BUFFER_SIZE = 512 * 1024
    }
}

private fun Context.readSharedFile(uri: Uri): SharedFile? {
    val projection = arrayOf(
        android.provider.OpenableColumns.DISPLAY_NAME,
        android.provider.OpenableColumns.SIZE
    )
    var fileName = "shared-file-${System.currentTimeMillis()}"
    var fileSize = 0L
    
    contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIndex >= 0) fileName = cursor.getString(nameIndex)
            if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex)
        }
    }
    
    var thumbnailBytes: ByteArray? = null
    try {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver.loadThumbnail(uri, android.util.Size(300, 300), null)
        } else {
            null
        }
        if (bitmap != null) {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, stream)
            thumbnailBytes = stream.toByteArray()
        }
    } catch (_: Exception) {}

    return SharedFile(name = fileName, bytes = null, sizeInBytes = fileSize, path = uri.toString(), thumbnail = thumbnailBytes)
}

private fun Context.findLifecycleOwner(): androidx.lifecycle.LifecycleOwner? = when (this) {
    is androidx.lifecycle.LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}

actual fun getAppVersion(): String {
    return BuildConfig.VERSION_NAME
}
