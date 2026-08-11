package com.example.octopusreader

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.octopusreader.nfc.TransitCardReader
import com.example.octopusreader.ui.TransitCardReaderScreen
import com.example.octopusreader.ui.TransitCardReaderViewModel
import com.example.octopusreader.ui.theme.OctopusReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    private val viewModel: TransitCardReaderViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = getSystemService(NfcManager::class.java)?.defaultAdapter

        setContent {
            OctopusReaderTheme {
                TransitCardReaderScreen(
                    viewModel = viewModel,
                    onOpenNfcSettings = {
                        startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter
        viewModel.updateNfcStatus(
            supported = adapter != null,
            enabled = adapter?.isEnabled == true,
        )

        adapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            Bundle().apply {
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
            },
        )
    }

    override fun onPause() {
        nfcAdapter?.disableReaderMode(this)
        super.onPause()
    }

    override fun onTagDiscovered(tag: Tag) {
        val selectedProfile = viewModel.beginRead() ?: return

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                TransitCardReader.read(tag, selectedProfile)
            }
            viewModel.completeRead(result)
        }
    }
}
