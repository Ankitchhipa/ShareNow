package org.sharenow.fileshare.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TransferProgressTest {

    @Test
    fun testFractionCalculation() {
        val progress = TransferProgress(
            currentFileName = "test.txt",
            bytesTransferred = 50,
            totalBytes = 100
        )
        assertEquals(0.5f, progress.fraction)
    }

    @Test
    fun testCurrentFileFractionCalculation() {
        val progress = TransferProgress(
            currentFileName = "test.txt",
            bytesTransferred = 50,
            totalBytes = 200,
            currentFileBytesTransferred = 50,
            currentFileSize = 100
        )
        assertEquals(0.5f, progress.currentFileFraction)
    }

    @Test
    fun testZeroTotalBytes() {
        val progress = TransferProgress(
            currentFileName = "test.txt",
            bytesTransferred = 0,
            totalBytes = 0
        )
        assertEquals(0f, progress.fraction)
    }
}
