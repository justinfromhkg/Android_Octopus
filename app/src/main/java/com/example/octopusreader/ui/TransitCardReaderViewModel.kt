package com.example.octopusreader.ui

import androidx.lifecycle.ViewModel
import com.example.octopusreader.nfc.CardReadResult
import com.example.octopusreader.nfc.OctopusBalanceBasis
import com.example.octopusreader.nfc.TransitCardProfile
import com.example.octopusreader.nfc.TransitCardScan
import com.example.octopusreader.nfc.TransitReadRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TransitCardReaderUiState(
    val selectedProfile: TransitCardProfile? = TransitCardProfile.AUTOMATIC,
    val octopusBalanceBasis: OctopusBalanceBasis = OctopusBalanceBasis.PHYSICAL_PRE_2017,
    val nfcSupported: Boolean = true,
    val nfcEnabled: Boolean = true,
    val isWaitingForCard: Boolean = false,
    val isReading: Boolean = false,
    val status: ReaderStatus = ReaderStatus.CARD_SELECTED,
    val lastScans: List<TransitCardScan> = emptyList(),
)

enum class ReaderStatus {
    SELECT_CARD,
    CARD_SELECTED,
    NFC_UNSUPPORTED,
    NFC_DISABLED,
    NFC_READY,
    SELECT_REQUIRED,
    HOLD_CARD,
    READING,
    READ_SUCCESS,
    READ_FAILED,
}

class TransitCardReaderViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TransitCardReaderUiState())
    val uiState: StateFlow<TransitCardReaderUiState> = _uiState.asStateFlow()

    fun selectProfile(profile: TransitCardProfile) {
        val previous = _uiState.value
        if (previous.isReading) return
        _uiState.value = previous.copy(
            selectedProfile = profile,
            isWaitingForCard = false,
            status = ReaderStatus.CARD_SELECTED,
            lastScans = emptyList(),
        )
    }

    fun selectOctopusBalanceBasis(basis: OctopusBalanceBasis) {
        val previous = _uiState.value
        if (previous.isReading || previous.isWaitingForCard) return
        _uiState.value = previous.copy(
            octopusBalanceBasis = basis,
            lastScans = emptyList(),
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
                !supported -> ReaderStatus.NFC_UNSUPPORTED
                !enabled -> ReaderStatus.NFC_DISABLED
                recovered -> ReaderStatus.NFC_READY
                else -> previous.status
            },
        )
    }

    fun requestScan() {
        val previous = _uiState.value
        if (!previous.nfcSupported || !previous.nfcEnabled || previous.isReading) return
        val profile = previous.selectedProfile
        if (profile == null) {
            _uiState.value = previous.copy(
                isWaitingForCard = false,
                status = ReaderStatus.SELECT_REQUIRED,
            )
            return
        }

        _uiState.value = previous.copy(
            isWaitingForCard = true,
            status = ReaderStatus.HOLD_CARD,
        )
    }

    @Synchronized
    fun beginRead(): TransitReadRequest? {
        val previous = _uiState.value
        if (!previous.isWaitingForCard || previous.isReading) return null
        val profile = previous.selectedProfile ?: return null

        _uiState.value = previous.copy(
            isWaitingForCard = false,
            isReading = true,
            status = ReaderStatus.READING,
        )
        return TransitReadRequest(
            profile = profile,
            octopusBalanceBasis = previous.octopusBalanceBasis,
        )
    }

    fun completeRead(result: CardReadResult) {
        val previous = _uiState.value
        _uiState.value = when (result) {
            is CardReadResult.Success -> previous.copy(
                isWaitingForCard = false,
                isReading = false,
                status = ReaderStatus.READ_SUCCESS,
                lastScans = result.scans,
            )

            is CardReadResult.Failure -> previous.copy(
                isWaitingForCard = false,
                isReading = false,
                status = ReaderStatus.READ_FAILED,
                lastScans = emptyList(),
            )
        }
    }
}
