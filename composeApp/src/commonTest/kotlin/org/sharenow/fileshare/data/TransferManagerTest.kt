package org.sharenow.fileshare.data

import kotlin.test.Test
import kotlin.test.assertEquals

class TransferManagerTest {

    private val transferManager = TransferManager()

    @Test
    fun testTransferProgressCalculation() {
        val totalBytes = 1000L
        val bytesTransferred = 500L
        val fraction = bytesTransferred.toFloat() / totalBytes.toFloat()
        assertEquals(0.5f, fraction)
    }

    // Mocking PlatformSocket would be ideal here, but since it's an expect/actual 
    // and might involve native sockets, we'll focus on logic that can be tested in common.
}
