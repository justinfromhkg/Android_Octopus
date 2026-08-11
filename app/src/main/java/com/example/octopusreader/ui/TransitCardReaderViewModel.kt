package com.example.octopusreader.ui

import androidx.lifecycle.ViewModel
import com.example.octopusreader.nfc.CardReadResult
import com.example.octopusreader.nfc.TransitCardProfile
import com.example.octopusreader.nfc.TransitCardScan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TransitCardReaderUiState(
    val selectedProfile: TransitCardProfile = TransitCardProfile.OCTOPUS,
    val nfcSupported: Boolean = true,
    val nfcEnabled: Boolean = true,
    val isWaitingForCard: Boolean = false,
    val isReading: Boolean = false,
    val status: String = "Choose a card system, then tap Scan.",
    val lastScan: TransitCardScan? = null,
)

class TransitCardReaderViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TransitCardReaderUiState())
    val uiState: StateFlow<TransitCardReaderUiState> = _uiState.asStateFlow()

    fun selectProfile(profile: TransitCardProfile) {
        val previous = _uiState.value
        if (previous.isReading) return
        _uiState.value = previous.copy(
            selectedProfile = profile,
            isWaitingForCard = false,
            status = "${profile.displayName} selected. Tap Scan when ready.",
            lastScan = null,
        )
    }

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
                recovered -> "NFC is ready. Choose a card system and tap Scan."
                else -> previous.status
            },
        )
    }

    fun requestScan() {
        val previous = _uiState.value
        if (!previous.nfcSupported || !previous.nfcEnabled || previous.isReading) return

        _uiState.value = previous.copy(
            isWaitingForCard = true,
            status = "Hold the ${previous.selectedProfile.displayName} card flat against the back of the phone.",
        )
    }

    @Synchronized
    fun beginRead(): TransitCardProfile? {
        val previous = _uiState.value
        if (!previous.isWaitingForCard || previous.isReading) return null

        _uiState.value = previous.copy(
            isWaitingForCard = false,
            isReading = true,
            status = "Reading ${previous.selectedProfile.displayName}…",
        )
        return previous.selectedProfile
    }

    fun completeRead(result: CardReadResult) {
        val previous = _uiState.value
        _uiState.value = when (result) {
            is CardReadResult.Success -> previous.copy(
                isWaitingForCard = false,
                isReading = false,
                status = "${result.scan.detectedName} read successfully.",
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
