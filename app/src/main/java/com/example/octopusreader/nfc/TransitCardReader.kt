package com.example.octopusreader.nfc

import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.nfc.tech.NfcF
import java.io.IOException
import java.time.Instant

object TransitCardReader {
    private val tUnionAid = "A000000632010105".hexToByteArray()
    private val cityUnionAid = "A00000000386980701".hexToByteArray()
    private val shenzhenAid = "PAY.SZT".encodeToByteArray()

    fun read(tag: Tag, profile: TransitCardProfile): CardReadResult = try {
        when (profile) {
            TransitCardProfile.OCTOPUS -> readOctopus(tag)
            TransitCardProfile.JAPAN_IC -> readJapaneseIc(tag)
            TransitCardProfile.EZLINK -> readCepas(tag)
            TransitCardProfile.T_UNION,
            TransitCardProfile.YANGCHENGTONG,
            TransitCardProfile.SHENZHENTONG,
            -> readChinaTransit(tag, profile)

            TransitCardProfile.CLIPPER -> readClipper(tag)
            TransitCardProfile.EASYCARD,
            TransitCardProfile.IPASS,
            TransitCardProfile.MACAU_PASS,
            TransitCardProfile.TOUCH_N_GO,
            -> readIdentificationOnly(tag, profile)
        }
    } catch (_: TagLostException) {
        CardReadResult.Failure("The card moved away too soon. Hold it still and try again.")
    } catch (_: SecurityException) {
        CardReadResult.Failure("Android did not allow NFC access for this app.")
    } catch (_: IOException) {
        CardReadResult.Failure("The card could not be read. Keep it against the phone and try again.")
    } catch (error: IllegalArgumentException) {
        CardReadResult.Failure(error.message ?: "The card returned data in an unexpected format.")
    }

    private fun readOctopus(tag: Tag): CardReadResult {
        val nfcF = NfcF.get(tag)
            ?: return CardReadResult.Failure("Octopus requires an NFC-F (FeliCa) card.")
        val idm = tag.id
        if (idm.size != 8) {
            return CardReadResult.Failure("The card returned an invalid FeliCa identifier.")
        }

        val systemCode = nfcF.systemCode
        if (!systemCode.contentEquals(OctopusProtocol.systemCode)) {
            return CardReadResult.Failure(
                "This is not an Octopus card (FeliCa system ${systemCode.toUpperHex()}).",
            )
        }

        return try {
            nfcF.connect()
            nfcF.timeout = 3_000
            val response = nfcF.transceive(OctopusProtocol.buildBalanceReadCommand(idm))
            val balance = OctopusProtocol.parseBalanceReadResponse(response, idm)
            success(
                tag = tag,
                profile = TransitCardProfile.OCTOPUS,
                detectedName = "Octopus",
                protocol = "FeliCa Read Without Encryption",
                balance = TransitBalance(
                    currencyCode = "HKD",
                    amountMinor = (balance.rawBalance - 500L) * 10L,
                    fractionDigits = 2,
                    isEstimated = true,
                ),
                systemCode = systemCode.toUpperHex(),
                rawData = balance.rawBlock,
                note = "Community-decoded estimate. Verify important amounts with an official Octopus reader.",
            )
        } catch (error: OctopusProtocolException) {
            CardReadResult.Failure(error.message ?: "The Octopus response could not be decoded.")
        } finally {
            nfcF.closeQuietly()
        }
    }

    private fun readJapaneseIc(tag: Tag): CardReadResult {
        val nfcF = NfcF.get(tag)
            ?: return CardReadResult.Failure("Japanese IC cards require NFC-F (FeliCa).")
        val idm = tag.id
        if (idm.size != 8) {
            return CardReadResult.Failure("The card returned an invalid FeliCa identifier.")
        }

        val systemCode = nfcF.systemCode
        val expectedSystem = byteArrayOf(0x00, JapaneseIcProtocol.SYSTEM_CODE.toByte())
        if (!systemCode.contentEquals(expectedSystem)) {
            return CardReadResult.Failure(
                "This is not a compatible Japanese transit IC card (system ${systemCode.toUpperHex()}).",
            )
        }

        return try {
            nfcF.connect()
            nfcF.timeout = 3_000
            val command = FelicaProtocol.buildReadCommand(
                idm = idm,
                serviceCode = JapaneseIcProtocol.HISTORY_SERVICE_CODE,
            )
            val block = FelicaProtocol.parseReadResponse(nfcF.transceive(command), idm)
            success(
                tag = tag,
                profile = TransitCardProfile.JAPAN_IC,
                detectedName = "Japanese IC card (Suica / PASMO / ICOCA family)",
                protocol = "FeliCa service 090F",
                balance = TransitBalance(
                    currencyCode = "JPY",
                    amountMinor = JapaneseIcProtocol.decodeLatestBalanceYen(block),
                    fractionDigits = 0,
                ),
                systemCode = systemCode.toUpperHex(),
                rawData = block,
                note = "These interoperable cards share a common NFC system; the exact brand is not always distinguishable.",
            )
        } catch (error: FelicaProtocolException) {
            CardReadResult.Failure(error.message ?: "The Japanese IC response could not be decoded.")
        } finally {
            nfcF.closeQuietly()
        }
    }

    private fun readCepas(tag: Tag): CardReadResult {
        val isoDep = IsoDep.get(tag)
            ?: return CardReadResult.Failure("This EZ-Link profile requires an ISO-DEP CEPAS card.")

        return try {
            isoDep.connect()
            isoDep.timeout = 3_000
            Iso7816TransitProtocol.unwrap(
                isoDep.transceive(Iso7816TransitProtocol.selectFile(0x4000)),
            )
            val purse = Iso7816TransitProtocol.unwrap(
                isoDep.transceive(Iso7816TransitProtocol.getCepasPurse()),
            )
            val cardNumber = Iso7816TransitProtocol.parseCepasCardNumber(purse)
            val detectedName = when {
                cardNumber?.startsWith("100") == true -> "EZ-Link"
                cardNumber?.startsWith("111") == true -> "NETS FlashPay"
                else -> "CEPAS transit card"
            }
            success(
                tag = tag,
                profile = TransitCardProfile.EZLINK,
                detectedName = detectedName,
                protocol = "CEPAS purse 3 over ISO-DEP",
                balance = TransitBalance(
                    currencyCode = "SGD",
                    amountMinor = Iso7816TransitProtocol.parseCepasBalanceCents(purse),
                    fractionDigits = 2,
                ),
                cardNumber = cardNumber,
                rawData = purse,
                note = "Compatible with legacy CEPAS stored-value cards. Account-based SimplyGo cards may not expose a local balance.",
            )
        } catch (error: Iso7816ProtocolException) {
            CardReadResult.Failure(error.message ?: "The CEPAS purse could not be read.")
        } finally {
            isoDep.closeQuietly()
        }
    }

    private fun readChinaTransit(tag: Tag, profile: TransitCardProfile): CardReadResult {
        val isoDep = IsoDep.get(tag) ?: return readIdentificationOnly(
            tag = tag,
            profile = profile,
            extraNote = "This appears to be an older non-CPU variant, so only the NFC identifier is available.",
        )

        val candidates = when (profile) {
            TransitCardProfile.SHENZHENTONG -> listOf(
                "Shenzhentong CPU card" to shenzhenAid,
                "China T-Union" to tUnionAid,
                "China City Union" to cityUnionAid,
            )

            TransitCardProfile.YANGCHENGTONG -> listOf(
                "China City Union / Yangchengtong" to cityUnionAid,
                "China T-Union" to tUnionAid,
            )

            else -> listOf("China T-Union" to tUnionAid)
        }

        return try {
            isoDep.connect()
            isoDep.timeout = 3_000
            val selected = candidates.firstOrNull { (_, aid) ->
                try {
                    Iso7816TransitProtocol.unwrap(
                        isoDep.transceive(Iso7816TransitProtocol.selectApplication(aid)),
                    )
                    true
                } catch (_: Iso7816ProtocolException) {
                    false
                }
            }

            if (selected == null) {
                readIdentificationOnly(
                    tag = tag,
                    profile = profile,
                    extraNote = "No compatible public PBOC/T-Union application was found; balance data may be protected.",
                )
            } else {
                val balanceData = try {
                    Iso7816TransitProtocol.unwrap(
                        isoDep.transceive(Iso7816TransitProtocol.getChinaBalance()),
                    )
                } catch (_: Iso7816ProtocolException) {
                    null
                }
                success(
                    tag = tag,
                    profile = profile,
                    detectedName = selected.first,
                    protocol = "PBOC transit purse over ISO-DEP",
                    balance = balanceData?.let {
                        TransitBalance(
                            currencyCode = "CNY",
                            amountMinor = Iso7816TransitProtocol.parseChinaBalanceCents(it),
                            fractionDigits = 2,
                        )
                    },
                    rawData = balanceData,
                    note = if (balanceData == null) {
                        "The transit application was identified, but this card did not expose its stored balance."
                    } else {
                        "Read from the card's public transit purse. No write command was sent."
                    },
                )
            }
        } finally {
            isoDep.closeQuietly()
        }
    }

    private fun readClipper(tag: Tag): CardReadResult {
        val isoDep = IsoDep.get(tag) ?: return readIdentificationOnly(
            tag = tag,
            profile = TransitCardProfile.CLIPPER,
            extraNote = "This card did not expose an ISO-DEP interface for the classic Clipper application.",
        )

        return try {
            isoDep.connect()
            isoDep.timeout = 3_000
            val selected = try {
                exchangeDesfire(
                    isoDep,
                    DesfireTransitProtocol.selectApplication(DesfireTransitProtocol.CLIPPER_APP_ID),
                )
                true
            } catch (_: DesfireProtocolException) {
                false
            }

            if (!selected) {
                readIdentificationOnly(
                    tag = tag,
                    profile = TransitCardProfile.CLIPPER,
                    extraNote = "The classic public Clipper DESFire application was not found on this card.",
                )
            } else {
                val balanceFile = try {
                    exchangeDesfire(isoDep, DesfireTransitProtocol.readData(0x02))
                } catch (_: DesfireProtocolException) {
                    null
                }
                success(
                    tag = tag,
                    profile = TransitCardProfile.CLIPPER,
                    detectedName = "Clipper",
                    protocol = "MIFARE DESFire application 9011F2",
                    balance = balanceFile?.let {
                        TransitBalance(
                            currencyCode = "USD",
                            amountMinor = DesfireTransitProtocol.parseClipperBalanceCents(it),
                            fractionDigits = 2,
                        )
                    },
                    rawData = balanceFile,
                    note = if (balanceFile == null) {
                        "Clipper was identified, but this card did not allow the public balance file to be read."
                    } else {
                        "Compatible with classic Clipper DESFire cards; newer account-based products may differ."
                    },
                )
            }
        } finally {
            isoDep.closeQuietly()
        }
    }

    private fun exchangeDesfire(isoDep: IsoDep, command: ByteArray): ByteArray {
        val output = mutableListOf<Byte>()
        var response = isoDep.transceive(command)
        while (true) {
            val frame = DesfireTransitProtocol.parseFrame(response)
            output.addAll(frame.data.toList())
            when (frame.status) {
                0x00 -> return output.toByteArray()
                0xAF -> response = isoDep.transceive(DesfireTransitProtocol.additionalFrame())
                0x9D, 0xAE -> throw DesfireProtocolException("The DESFire file requires authentication.")
                0xA0 -> throw DesfireProtocolException("The Clipper application was not found.")
                0xF0 -> throw DesfireProtocolException("The Clipper balance file was not found.")
                else -> throw DesfireProtocolException(
                    "The DESFire card returned status %02X.".format(frame.status),
                )
            }
        }
    }

    private fun readIdentificationOnly(
        tag: Tag,
        profile: TransitCardProfile,
        extraNote: String? = null,
    ): CardReadResult = success(
        tag = tag,
        profile = profile,
        detectedName = "${profile.displayName} profile",
        protocol = technologyNames(tag),
        balance = null,
        note = listOfNotNull(
            "Card detected. The selected system's stored-value data is encrypted, keyed, or not publicly documented, so this app only shows safe NFC metadata.",
            extraNote,
        ).joinToString(" "),
    )

    private fun success(
        tag: Tag,
        profile: TransitCardProfile,
        detectedName: String,
        protocol: String,
        balance: TransitBalance?,
        cardNumber: String? = null,
        systemCode: String? = null,
        rawData: ByteArray? = null,
        note: String,
    ) = CardReadResult.Success(
        TransitCardScan(
            selectedProfile = profile,
            detectedName = detectedName,
            cardId = tag.id.toUpperHex(),
            technology = technologyNames(tag),
            protocol = protocol,
            balance = balance,
            cardNumber = cardNumber,
            systemCode = systemCode,
            rawDataHex = rawData?.toUpperHex(" "),
            note = note,
            scannedAt = Instant.now(),
        ),
    )

    private fun technologyNames(tag: Tag): String = tag.techList
        .map { it.substringAfterLast('.') }
        .sorted()
        .joinToString(" • ")
        .ifBlank { "Unknown NFC technology" }
}

private fun NfcF.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
        // The card may already be out of range.
    }
}

private fun IsoDep.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
        // The card may already be out of range.
    }
}

internal fun String.hexToByteArray(): ByteArray {
    require(length % 2 == 0) { "A hexadecimal string must have an even length." }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
