package com.example.octopusreader.ui

import com.example.octopusreader.nfc.TransitCardProfile
import com.example.octopusreader.nfc.OctopusBalanceBasis
import com.example.octopusreader.nfc.CardReadResult
import com.example.octopusreader.nfc.TransitCardScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TransitCardReaderViewModelTest {
    @Test
    fun `automatic detection is selected by default and can scan immediately`() {
        val viewModel = TransitCardReaderViewModel()

        viewModel.requestScan()

        assertEquals(TransitCardProfile.AUTOMATIC, viewModel.uiState.value.selectedProfile)
        assertTrue(viewModel.uiState.value.isWaitingForCard)
        assertEquals(TransitCardProfile.AUTOMATIC, viewModel.beginRead()?.profile)
    }

    @Test
    fun `selected profile is returned for the NFC read session`() {
        val viewModel = TransitCardReaderViewModel()

        viewModel.selectProfile(TransitCardProfile.JAPAN_TRANSIT_IC)
        viewModel.requestScan()

        assertTrue(viewModel.uiState.value.isWaitingForCard)
        assertEquals(TransitCardProfile.JAPAN_TRANSIT_IC, viewModel.beginRead()?.profile)
        assertTrue(viewModel.uiState.value.isReading)
    }

    @Test
    fun `older physical Octopus balance basis is the default and can be changed`() {
        val viewModel = TransitCardReaderViewModel()

        assertEquals(
            OctopusBalanceBasis.PHYSICAL_PRE_2017,
            viewModel.uiState.value.octopusBalanceBasis,
        )

        viewModel.selectOctopusBalanceBasis(OctopusBalanceBasis.NEW_OR_MOBILE)

        assertEquals(
            OctopusBalanceBasis.NEW_OR_MOBILE,
            viewModel.uiState.value.octopusBalanceBasis,
        )
    }

    @Test
    fun `automatic read keeps every detected card application`() {
        val viewModel = TransitCardReaderViewModel()
        val scans = listOf(
            scan(TransitCardProfile.OCTOPUS),
            scan(TransitCardProfile.SHENZHENTONG),
        )
        viewModel.requestScan()
        viewModel.beginRead()

        viewModel.completeRead(CardReadResult.Success(scans))

        assertEquals(scans, viewModel.uiState.value.lastScans)
        assertEquals(ReaderStatus.READ_SUCCESS, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.isReading)
    }

    private fun scan(profile: TransitCardProfile) = TransitCardScan(
        selectedProfile = profile,
        detectedName = profile.displayName,
        cardId = "0102030405060708",
        technology = "NFC",
        protocol = "read-only test",
        balance = null,
        note = "test",
        scannedAt = Instant.EPOCH,
    )
}
