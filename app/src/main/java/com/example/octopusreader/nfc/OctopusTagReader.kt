package com.example.octopusreader.nfc

import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.NfcF
import java.io.IOException
import java.time.Instant

object OctopusTagReader {
    fun read(tag: Tag): CardReadResult {
        val nfcF = NfcF.get(tag)
            ?: return CardReadResult.Failure("This is not an NFC-F (FeliCa) card.")

        val idm = tag.id
        if (idm.size != 8) {
            return CardReadResult.Failure("The card returned an invalid FeliCa identifier.")
        }

        val detectedSystemCode = nfcF.systemCode
        if (!detectedSystemCode.contentEquals(OctopusProtocol.systemCode)) {
            return CardReadResult.Failure(
                "This NFC-F card is not an Octopus card (system ${detectedSystemCode.toUpperHex()}).",
            )
        }

        return try {
            nfcF.connect()
            nfcF.timeout = 3_000
            val command = OctopusProtocol.buildBalanceReadCommand(idm)
            val response = nfcF.transceive(command)
            val balance = OctopusProtocol.parseBalanceReadResponse(response, idm)

            CardReadResult.Success(
                OctopusScan(
                    cardId = idm.toUpperHex(),
                    systemCode = detectedSystemCode.toUpperHex(),
                    rawBalance = balance.rawBalance,
                    estimatedBalanceHkd = balance.estimatedBalanceHkd,
                    rawBlockHex = balance.rawBlock.toUpperHex(" "),
                    scannedAt = Instant.now(),
                ),
            )
        } catch (_: TagLostException) {
            CardReadResult.Failure("The card moved away too soon. Hold it still and try again.")
        } catch (error: OctopusProtocolException) {
            CardReadResult.Failure(error.message ?: "The Octopus response could not be decoded.")
        } catch (_: SecurityException) {
            CardReadResult.Failure("Android did not allow NFC access for this app.")
        } catch (_: IOException) {
            CardReadResult.Failure("The card could not be read. Keep it against the phone and try again.")
        } catch (_: IllegalArgumentException) {
            CardReadResult.Failure("The card returned data in an unexpected format.")
        } finally {
            try {
                nfcF.close()
            } catch (_: IOException) {
                // The tag may already be out of range; there is nothing else to close.
            }
        }
    }
}

data class OctopusScan(
    val cardId: String,
    val systemCode: String,
    val rawBalance: Long,
    val estimatedBalanceHkd: Double,
    val rawBlockHex: String,
    val scannedAt: Instant,
)

sealed interface CardReadResult {
    data class Success(val scan: OctopusScan) : CardReadResult
    data class Failure(val message: String) : CardReadResult
}
