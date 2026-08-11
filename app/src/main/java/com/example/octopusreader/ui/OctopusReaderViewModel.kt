package com.example.octopusreader.ui

import androidx.lifecycle.ViewModel
import com.example.octopusreader.nfc.CardReadResult
import com.example.octopusreader.nfc.OctopusScan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OctopusReaderUiState(
    val nfcSupported: Boolean = true,
    val nfcEnabled: Boolean = true,
    val isWaitingForCard: Boolean = false,
    val isReading: Boolean = false,
    val status: String = "Ready to scan a physical Octopus card.",
    val lastScan: OctopusScan? = null,
)

class OctopusReaderViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(OctopusReaderUiState())
    val uiState: StateFlow<OctopusReaderUiState> = _uiState.asStateFlow()

    fun updateNfcStatus(supported: Boolean, enabled: Boolean) {
        val previous = _uiState.value
        val recovered = (!previous.nfcSupported || !previous.nfcEnabled) && supported && enabled
        _uiState.value = previous.copy(
            nfcSupported = supported,
            nfcEnabled = enabled,
            isWaitingForCard = if (supported && enabled) previous.isWaitingForCard else false,
            isReading = if (supported && enabled) previous.isReading else false,
            status = when {
                !supported -> "This Android phone does not support NFC."
                !enabled -> "NFC is turned off. Enable it in Android settings."
                recovered -> "NFC is ready. Tap the scan button to begin."
                else -> previous.status
            },
        )
    }

    fun requestScan() {
        val previous = _uiState.value
        if (!previous.nfcSupported || !previous.nfcEnabled || previous.isReading) return

        _uiState.value = previous.copy(
            isWaitingForCard = true,
            status = "Hold the Octopus card flat against the back of the phone.",
        )
    }

    @Synchronized
    fun beginRead(): Boolean {
        val previous = _uiState.value
        if (!previous.isWaitingForCard || previous.isReading) return false

        _uiState.value = previous.copy(
            isWaitingForCard = false,
            isReading = true,
            status = "Reading the Octopus card…",
        )
        return true
    }

    fun completeRead(result: CardReadResult) {
        val previous = _uiState.value
        _uiState.value = when (result) {
            is CardReadResult.Success -> previous.copy(
                isWaitingForCard = false,
                isReading = false,
                status = "Octopus card read successfully.",
                lastScan = result.scan,
            )

            is CardReadResult.Failure -> previous.copy(
                isWaitingForCard = false,
                isReading = false,
                status = result.message,
            )
        }
    }
}
