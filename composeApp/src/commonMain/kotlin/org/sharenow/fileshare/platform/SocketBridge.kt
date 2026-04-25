package org.sharenow.fileshare.platform

expect class PlatformServerSocket() {
    suspend fun start(port: Int = 0): Int
    suspend fun accept(): PlatformSocket
    fun close()
}

expect class PlatformSocket() {
    suspend fun connect(host: String, port: Int)
    suspend fun readExactly(byteCount: Int): ByteArray
    suspend fun writeFully(bytes: ByteArray)
    suspend fun flush()
    fun close()
}
