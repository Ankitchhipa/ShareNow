package org.sharenow.fileshare.data

import org.sharenow.fileshare.model.ConnectionPayload
import org.sharenow.fileshare.platform.PlatformServerSocket
import org.sharenow.fileshare.platform.PlatformSocket
import org.sharenow.fileshare.platform.currentDeviceName
import org.sharenow.fileshare.platform.localIpAddress
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ConnectionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val socketMutex = Mutex()
    private var serverSocket: PlatformServerSocket? = null
    private var acceptJob: Job? = null
    private val activeSockets = mutableListOf<PlatformSocket>()

    suspend fun startServer(
        onStatus: (String) -> Unit,
        onClientConnected: suspend (PlatformSocket) -> Unit,
    ): ConnectionPayload {
        stopServer()
        
        val hotspotCompletable = kotlinx.coroutines.CompletableDeferred<Pair<String, String>>()
        org.sharenow.fileshare.platform.startLocalHotspot(
            onSuccess = { ssid, password ->
                hotspotCompletable.complete(ssid to password)
            },
            onFailure = { error ->
                hotspotCompletable.completeExceptionally(Exception(error))
            }
        )

        val (ssid, password) = try {
            hotspotCompletable.await()
        } catch (e: Exception) {
            onStatus("Hotspot failed: ${e.message}. Using fallback.")
            "" to ""
        }

        val server = PlatformServerSocket()
        val port = server.start()
        val resolvedHost = resolveAdvertisedHost(hasHotspot = ssid.isNotEmpty())
        val payload = ConnectionPayload(
            host = resolvedHost,
            port = port,
            deviceName = currentDeviceName(),
            ssid = ssid,
            password = password
        )
        serverSocket = server
        onStatus("Waiting for receiver on ${payload.host}:${payload.port}")
        acceptJob = scope.launch {
            val socket = try {
                server.accept()
            } catch (_: Throwable) {
                null
            }
            if (socket != null) {
                socketMutex.withLock {
                    activeSockets += socket
                }
                onStatus("Receiver connected. Preparing transfer…")
                onClientConnected(socket)
            }
        }
        return payload
    }

    suspend fun connect(payload: ConnectionPayload, onStatus: (String) -> Unit): PlatformSocket {
        stopServer()
        val ssid = payload.ssid
        val password = payload.password
        
        if (ssid != null && password != null && ssid.isNotEmpty()) {
            onStatus("Connecting to Hotspot: $ssid…")
            val connectionDeferred = kotlinx.coroutines.CompletableDeferred<Unit>()
            org.sharenow.fileshare.platform.connectToWifi(
                ssid = ssid,
                password = password,
                onSuccess = { connectionDeferred.complete(Unit) },
                onFailure = { error -> connectionDeferred.completeExceptionally(Exception(error)) }
            )
            try {
                connectionDeferred.await()
                onStatus("Connected to Hotspot. Establishing socket…")
                delay(700)
            } catch (e: Exception) {
                onStatus("Hotspot connection failed: ${e.message}. Trying direct IP.")
            }
        }

        val hostCandidates = buildHostCandidates(payload.host)
        var lastError: Throwable? = null
        hostCandidates.forEachIndexed { hostIndex, host ->
            val socket = PlatformSocket()
            onStatus("Connecting to ${payload.deviceName} on $host…")
            repeat(3) { attempt ->
                try {
                    socket.connect(host, payload.port)
                    socketMutex.withLock {
                        activeSockets += socket
                    }
                    onStatus("Connected to ${payload.deviceName}")
                    return socket
                } catch (e: Throwable) {
                    lastError = e
                    onStatus(
                        "Connection attempt ${attempt + 1}/3 failed on $host" +
                            if (hostIndex < hostCandidates.lastIndex || attempt < 2) ", retrying…" else ""
                    )
                    delay(450)
                }
            }
        }
        throw lastError ?: Exception("Failed to connect to ${payload.deviceName}")
    }

    suspend fun stopServer() {
        socketMutex.withLock {
            acceptJob?.cancel()
            acceptJob = null
            serverSocket?.close()
            serverSocket = null
            activeSockets.forEach { socket -> runCatching { socket.close() } }
            activeSockets.clear()
            org.sharenow.fileshare.platform.stopLocalHotspot()
        }
    }

    fun dispose() {
        activeSockets.forEach { socket -> runCatching { socket.close() } }
        activeSockets.clear()
        serverSocket?.close()
        acceptJob?.cancel()
        org.sharenow.fileshare.platform.stopLocalHotspot()
        scope.cancel()
    }

    suspend fun closeConnection(socket: PlatformSocket?) {
        if (socket == null) return
        socketMutex.withLock {
            activeSockets.remove(socket)
            runCatching { socket.close() }
        }
    }

    private suspend fun resolveAdvertisedHost(hasHotspot: Boolean): String {
        repeat(if (hasHotspot) 6 else 1) {
            val host = localIpAddress()
            if (host != "127.0.0.1") return host
            delay(500)
        }
        return if (hasHotspot) {
            // Common Android hotspot gateway fallback if interface probing is delayed.
            "192.168.43.1"
        } else {
            localIpAddress()
        }
    }

    private fun buildHostCandidates(primaryHost: String): List<String> {
        val commonGatewayCandidates = listOf(
            primaryHost,
            "192.168.43.1",
            "192.168.49.1",
            "192.168.137.1",
            "192.168.232.1",
        )
        return commonGatewayCandidates.distinct()
    }
}
