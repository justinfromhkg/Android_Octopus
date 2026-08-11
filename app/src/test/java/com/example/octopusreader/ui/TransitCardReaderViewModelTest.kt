package com.example.octopusreader.ui

import com.example.octopusreader.nfc.TransitCardProfile
import com.example.octopusreader.nfc.OctopusBalanceBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitCardReaderViewModelTest {
    @Test
    fun `scan cannot start until user selects a card`() {
        val viewModel = TransitCardReaderViewModel()

        viewModel.requestScan()

        assertNull(viewModel.uiState.value.selectedProfile)
        assertFalse(viewModel.uiState.value.isWaitingForCard)
        assertNull(viewModel.beginRead())
        assertEquals(ReaderStatus.SELECT_REQUIRED, viewModel.uiState.value.status)
    }

    @Test
    fun `selected profile is returned for the NFC read session`() {
        val viewModel = TransitCardReaderViewModel()

        viewModel.selectProfile(TransitCardProfile.PASMO)
        viewModel.requestScan()

        assertTrue(viewModel.uiState.value.isWaitingForCard)
        assertEquals(TransitCardProfile.PASMO, viewModel.beginRead()?.profile)
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
}
