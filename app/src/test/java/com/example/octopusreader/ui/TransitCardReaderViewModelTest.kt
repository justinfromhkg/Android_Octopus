package com.example.octopusreader.ui

import com.example.octopusreader.nfc.TransitCardProfile
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
        assertEquals(
            "Select the card you are using before scanning.",
            viewModel.uiState.value.status,
        )
    }

    @Test
    fun `selected profile is returned for the NFC read session`() {
        val viewModel = TransitCardReaderViewModel()

        viewModel.selectProfile(TransitCardProfile.PASMO)
        viewModel.requestScan()

        assertTrue(viewModel.uiState.value.isWaitingForCard)
        assertEquals(TransitCardProfile.PASMO, viewModel.beginRead())
        assertTrue(viewModel.uiState.value.isReading)
    }
}
