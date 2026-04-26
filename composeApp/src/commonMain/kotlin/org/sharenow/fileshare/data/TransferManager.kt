package org.sharenow.fileshare.data

import org.sharenow.fileshare.model.ReceivedFile
import org.sharenow.fileshare.model.SharedFile
import org.sharenow.fileshare.model.TransferProgress
import org.sharenow.fileshare.platform.PlatformSocket
import org.sharenow.fileshare.platform.closeSavedFile
import org.sharenow.fileshare.platform.getTemporaryFilePath
import org.sharenow.fileshare.platform.saveChunkToFile
import org.sharenow.fileshare.platform.streamFileChunks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TransferManager {
    suspend fun sendFiles(
        socket: PlatformSocket,
        files: List<SharedFile>,
        onProgress: (TransferProgress) -> Unit,
    ) = withContext(Dispatchers.Default) {
        val totalBytes = files.sumOf { it.sizeInBytes }
        var totalSentBytes = 0L
        val startTime = org.sharenow.fileshare.platform.currentTimeMillis()
        var lastProgressAt = 0L
        
        // 1. Send file count and total size
        socket.writeLong(totalBytes)
        socket.writeInt(files.size)
        
        // 2. Send metadata for all files first
        files.forEach { file ->
            val nameBytes = file.name.encodeToByteArray()
            socket.writeInt(nameBytes.size)
            socket.writeFully(nameBytes)
            socket.writeLong(file.sizeInBytes)

            val thumb = file.thumbnail
            if (thumb != null) {
                socket.writeInt(thumb.size)
                socket.writeFully(thumb)
            } else {
                socket.writeInt(0)
            }
        }
        socket.flush()

        // 3. Send file contents one by one
        files.forEachIndexed { index, file ->
            var fileSentBytes = 0L
            var nextSyncAt = nextChunkSyncAt(file.sizeInBytes)
            if (file.bytes != null && file.bytes.isNotEmpty()) {
                val data = file.bytes
                var offset = 0
                while (offset < data.size) {
                    val nextChunkSize = minOf(CHUNK_SIZE, data.size - offset)
                    val packet = data.copyOfRange(offset, offset + nextChunkSize)
                    socket.writeFramedChunk(packet)
                    totalSentBytes += packet.size
                    fileSentBytes += packet.size
                    offset += packet.size
                    nextSyncAt = syncWithReceiverIfNeeded(socket, fileSentBytes, file.sizeInBytes, nextSyncAt)
                    lastProgressAt = emitProgressIfNeeded(
                        onProgress = onProgress,
                        currentFileName = file.name,
                        bytesTransferred = totalSentBytes,
                        totalBytes = totalBytes,
                        currentIndex = index + 1,
                        totalCount = files.size,
                        startTime = startTime,
                        currentFileBytesTransferred = fileSentBytes,
                        currentFileSize = file.sizeInBytes,
                        lastProgressAt = lastProgressAt,
                        force = offset >= data.size
                    )
                }
            } else if (file.path != null) {
                streamFileChunks(file.path, CHUNK_SIZE) { packet ->
                    socket.writeFramedChunk(packet)
                    totalSentBytes += packet.size
                    fileSentBytes += packet.size
                    nextSyncAt = syncWithReceiverIfNeeded(socket, fileSentBytes, file.sizeInBytes, nextSyncAt)
                    lastProgressAt = emitProgressIfNeeded(
                        onProgress = onProgress,
                        currentFileName = file.name,
                        bytesTransferred = totalSentBytes,
                        totalBytes = totalBytes,
                        currentIndex = index + 1,
                        totalCount = files.size,
                        startTime = startTime,
                        currentFileBytesTransferred = fileSentBytes,
                        currentFileSize = file.sizeInBytes,
                        lastProgressAt = lastProgressAt,
                        force = fileSentBytes >= file.sizeInBytes
                    )
                }
            } else {
                var offset = 0L
                while (offset < file.sizeInBytes) {
                    val nextChunkSize = minOf(CHUNK_SIZE.toLong(), file.sizeInBytes - offset).toInt()
                    socket.writeFramedChunk(ByteArray(nextChunkSize))
                    totalSentBytes += nextChunkSize
                    fileSentBytes += nextChunkSize
                    offset += nextChunkSize
                    nextSyncAt = syncWithReceiverIfNeeded(socket, fileSentBytes, file.sizeInBytes, nextSyncAt)
                    lastProgressAt = emitProgressIfNeeded(
                        onProgress = onProgress,
                        currentFileName = file.name,
                        bytesTransferred = totalSentBytes,
                        totalBytes = totalBytes,
                        currentIndex = index + 1,
                        totalCount = files.size,
                        startTime = startTime,
                        currentFileBytesTransferred = fileSentBytes,
                        currentFileSize = file.sizeInBytes,
                        lastProgressAt = lastProgressAt,
                        force = offset >= file.sizeInBytes
                    )
                }
            }
            socket.writeInt(FILE_END_MARKER)
            socket.flush()
            val fileAck = socket.readInt()
            if (fileAck != FILE_COMPLETE_ACK) {
                error("File transfer confirmation failed. Please reconnect both devices and try again.")
            }
        }

        socket.flush()
        val ack = socket.readInt()
        if (ack != TRANSFER_COMPLETE_ACK) {
            error("Transfer finalization failed")
        }
    }

    suspend fun receiveFiles(
        socket: PlatformSocket,
        onMetadataReceived: (List<ReceivedFile>) -> Unit,
        onProgress: (TransferProgress) -> Unit,
    ): List<ReceivedFile> = withContext(Dispatchers.Default) {
        val totalBytes = socket.readLong()
        val fileCount = socket.readInt()
        val receivedFiles = mutableListOf<ReceivedFile>()
        var transferred = 0L
        val startTime = org.sharenow.fileshare.platform.currentTimeMillis()
        var lastProgressAt = 0L

        // 1. Receive all metadata first
        val metadataList = mutableListOf<ReceivedFile>()
        repeat(fileCount) {
            val nameLength = socket.readInt()
            val name = socket.readExactly(nameLength).decodeToString()
            val size = socket.readLong()
            
            val thumbLength = socket.readInt()
            val thumbnail = if (thumbLength > 0) socket.readExactly(thumbLength) else null
            
            metadataList.add(ReceivedFile(name, size, "", thumbnail))
        }
        onMetadataReceived(metadataList)

        // 2. Receive file contents one by one
        metadataList.forEachIndexed { index, meta ->
            val tempPath = getTemporaryFilePath(meta.name)
            var offset = 0L
            var nextSyncAt = nextChunkSyncAt(meta.sizeInBytes)
            try {
                while (true) {
                    val chunkLength = socket.readInt()
                    if (chunkLength == FILE_END_MARKER) break
                    if (chunkLength !in 1..MAX_INCOMING_CHUNK_BYTES) {
                        error("Invalid transfer packet received. Please reconnect both devices and try again.")
                    }
                    val chunk = socket.readExactly(chunkLength)
                    saveChunkToFile(tempPath, chunk, append = offset > 0)
                    offset += chunk.size
                    transferred += chunk.size
                    nextSyncAt = acknowledgeSenderIfNeeded(socket, offset, meta.sizeInBytes, nextSyncAt)
                    lastProgressAt = emitProgressIfNeeded(
                        onProgress = onProgress,
                        currentFileName = meta.name,
                        bytesTransferred = transferred,
                        totalBytes = totalBytes,
                        currentIndex = index + 1,
                        totalCount = metadataList.size,
                        startTime = startTime,
                        currentFileBytesTransferred = offset,
                        currentFileSize = meta.sizeInBytes,
                        lastProgressAt = lastProgressAt,
                        force = offset >= meta.sizeInBytes
                    )
                }
            } finally {
                closeSavedFile(tempPath)
            }
            socket.writeInt(FILE_COMPLETE_ACK)
            socket.flush()
            receivedFiles += meta.copy(sizeInBytes = offset, savedPath = tempPath)
        }
        socket.writeInt(TRANSFER_COMPLETE_ACK)
        socket.flush()
        receivedFiles
    }

    private suspend fun PlatformSocket.writeFramedChunk(bytes: ByteArray) {
        writeInt(bytes.size)
        writeFully(bytes)
    }

    private suspend fun syncWithReceiverIfNeeded(
        socket: PlatformSocket,
        fileSentBytes: Long,
        fileSize: Long,
        nextSyncAt: Long,
    ): Long {
        if (fileSize <= 0L || fileSentBytes < nextSyncAt) return nextSyncAt
        socket.flush()
        val ack = socket.readInt()
        if (ack != CHUNK_SYNC_ACK) {
            error("Receiver sync failed. Please reconnect both devices and try again.")
        }
        return nextChunkSyncAt(fileSize, fileSentBytes)
    }

    private suspend fun acknowledgeSenderIfNeeded(
        socket: PlatformSocket,
        fileReceivedBytes: Long,
        fileSize: Long,
        nextSyncAt: Long,
    ): Long {
        if (fileSize <= 0L || fileReceivedBytes < nextSyncAt) return nextSyncAt
        socket.writeInt(CHUNK_SYNC_ACK)
        socket.flush()
        return nextChunkSyncAt(fileSize, fileReceivedBytes)
    }

    private fun nextChunkSyncAt(fileSize: Long, currentBytes: Long = 0L): Long {
        if (fileSize <= 0L) return Long.MAX_VALUE
        val next = ((currentBytes / CHUNK_SYNC_INTERVAL_BYTES) + 1) * CHUNK_SYNC_INTERVAL_BYTES
        return minOf(next, fileSize)
    }

    private suspend fun PlatformSocket.writeInt(value: Int) {
        val bytes = byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte(),
        )
        writeFully(bytes)
    }

    private suspend fun PlatformSocket.readInt(): Int {
        val bytes = readExactly(4)
        return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }

    private suspend fun PlatformSocket.writeLong(value: Long) {
        val bytes = byteArrayOf(
            ((value shr 56) and 0xFF).toByte(),
            ((value shr 48) and 0xFF).toByte(),
            ((value shr 40) and 0xFF).toByte(),
            ((value shr 32) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte(),
        )
        writeFully(bytes)
    }

    private suspend fun PlatformSocket.readLong(): Long {
        val bytes = readExactly(8)
        return ((bytes[0].toLong() and 0xFF) shl 56) or
            ((bytes[1].toLong() and 0xFF) shl 48) or
            ((bytes[2].toLong() and 0xFF) shl 40) or
            ((bytes[3].toLong() and 0xFF) shl 32) or
            ((bytes[4].toLong() and 0xFF) shl 24) or
            ((bytes[5].toLong() and 0xFF) shl 16) or
            ((bytes[6].toLong() and 0xFF) shl 8) or
            (bytes[7].toLong() and 0xFF)
    }

    private fun emitProgressIfNeeded(
        onProgress: (TransferProgress) -> Unit,
        currentFileName: String,
        bytesTransferred: Long,
        totalBytes: Long,
        currentIndex: Int,
        totalCount: Int,
        startTime: Long,
        currentFileBytesTransferred: Long,
        currentFileSize: Long,
        lastProgressAt: Long,
        force: Boolean,
    ): Long {
        val now = org.sharenow.fileshare.platform.currentTimeMillis()
        if (!force && now - lastProgressAt < PROGRESS_UPDATE_INTERVAL_MS) return lastProgressAt
        onProgress(
            TransferProgress(
                currentFileName = currentFileName,
                bytesTransferred = bytesTransferred,
                totalBytes = totalBytes,
                currentIndex = currentIndex,
                totalCount = totalCount,
                startTime = startTime,
                currentFileBytesTransferred = currentFileBytesTransferred,
                currentFileSize = currentFileSize
            )
        )
        return now
    }

    private companion object {
        const val CHUNK_SIZE = 256 * 1024
        const val MAX_INCOMING_CHUNK_BYTES = 4 * 1024 * 1024
        const val CHUNK_SYNC_INTERVAL_BYTES = 1024 * 1024L
        const val CHUNK_SYNC_ACK = 0x53484E4B
        const val FILE_END_MARKER = 0
        const val FILE_COMPLETE_ACK = 0x5348464C
        const val PROGRESS_UPDATE_INTERVAL_MS = 120L
        const val TRANSFER_COMPLETE_ACK = 0x53484F4B
    }
}
