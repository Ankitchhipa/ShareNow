package org.sharenow.fileshare.data

import org.sharenow.fileshare.model.TransferHistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HistoryManager {
    private val _historyItems = MutableStateFlow<List<TransferHistoryItem>>(emptyList())
    val historyItems: StateFlow<List<TransferHistoryItem>> = _historyItems.asStateFlow()

    fun addHistoryItem(item: TransferHistoryItem) {
        _historyItems.value = listOf(item) + _historyItems.value
    }

    // In a real app, this would be persisted to a database
    fun loadHistory() {
        // Initial load could come from a file or database
    }
}
