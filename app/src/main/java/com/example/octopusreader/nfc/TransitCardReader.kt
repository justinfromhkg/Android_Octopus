package com.example.octopusreader.nfc

import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.NfcF
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter

object TransitCardReader {
    private val tUnionAid = "A000000632010105".hexToByteArray()
    private val cityUnionAid = "A00000000386980701".hexToByteArray()
    private val shenzhenAid = "PAY.SZT".encodeToByteArray()

    fun read(tag: Tag, request: TransitReadRequest): CardReadResult = try {
        val profile = request.profile
        when (profile) {
            TransitCardProfile.AUTOMATIC -> readAutomatically(tag, request.octopusBalanceBasis)
            TransitCardProfile.OCTOPUS -> readOctopus(tag, request.octopusBalanceBasis)
            TransitCardProfile.JAPAN_TRANSIT_IC -> readJapaneseIc(tag)
            TransitCardProfile.EZLINK -> readCepas(tag)
            TransitCardProfile.T_MONEY -> readTMoney(tag)
            TransitCardProfile.T_UNION,
            TransitCardProfile.YANGCHENGTONG,
            TransitCardProfile.SHENZHENTONG,
            -> readChinaTransit(tag, profile)

            TransitCardProfile.CLIPPER -> readClipper(tag)
            TransitCardProfile.OYSTER -> readOyster(tag)
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

    private fun readAutomatically(
        tag: Tag,
        octopusBalanceBasis: OctopusBalanceBasis,
    ): CardReadResult {
        val scans = mutableListOf<TransitCardScan>()

        if (NfcF.get(tag) != null) {
            scans += probe { readOctopus(tag, octopusBalanceBasis) }
            scans += probe { readJapaneseIc(tag) }
        }
        if (IsoDep.get(tag) != null) {
            scans += probe { readTMoney(tag, allowIdentificationFallback = false) }
            scans += probe { readChinaTransit(tag, TransitCardProfile.AUTOMATIC) }
            scans += probe { readCepas(tag, allowIdentificationFallback = false) }
            scans += probe { readClipper(tag, allowIdentificationFallback = false) }
        }

        val distinctScans = scans.distinctBy { scan ->
            listOf(scan.selectedProfile.name, scan.protocol, scan.cardNumber.orEmpty())
        }
        return if (distinctScans.isNotEmpty()) {
            CardReadResult.Success(distinctScans)
        } else {
            readIdentificationOnly(
                tag = tag,
                profile = TransitCardProfile.AUTOMATIC,
                detectedName = "Unidentified NFC transit card",
                extraNote = "No uniquely identifiable public transit application was found. Choose a manual profile if you know the card type.",
            )
        }
    }

    private inline fun probe(block: () -> CardReadResult): List<TransitCardScan> = try {
        when (val result = block()) {
            is CardReadResult.Success -> result.scans
            is CardReadResult.Failure -> emptyList()
        }
    } catch (error: TagLostException) {
        throw error
    } catch (error: SecurityException) {
        throw error
    } catch (_: IOException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }

    private fun readOctopus(
        tag: Tag,
        balanceBasis: OctopusBalanceBasis,
    ): CardReadResult {
        val nfcF = NfcF.get(tag)
            ?: return CardReadResult.Failure("Octopus requires an NFC-F (FeliCa) card.")
        val idm = tag.id
        if (idm.size != 8) {
            return CardReadResult.Failure("The card returned an invalid FeliCa identifier.")
        }

        val discoverySystemCode = nfcF.systemCode
        var availableSystemCodes = listOf(discoverySystemCode)
        var octopusPolling: OctopusPollingData? = null

        return try {
            nfcF.connect()
            nfcF.timeout = 3_000
            availableSystemCodes = try {
                OctopusProtocol.parseRequestSystemCodesResponse(
                    response = nfcF.transceive(
                        OctopusProtocol.buildRequestSystemCodesCommand(idm),
                    ),
                    expectedIdm = idm,
                )
            } catch (_: OctopusProtocolException) {
                availableSystemCodes
            } catch (_: IOException) {
                availableSystemCodes
            }
            val polling = OctopusProtocol.parsePollingResponse(
                nfcF.transceive(OctopusProtocol.buildPollingCommand()),
            )
            octopusPolling = polling
            if (availableSystemCodes.none { it.contentEquals(polling.systemCode) }) {
                availableSystemCodes = availableSystemCodes + listOf(polling.systemCode)
            }
            val response = nfcF.transceive(
                OctopusProtocol.buildBalanceReadCommand(polling.idm),
            )
            val balance = OctopusProtocol.parseBalanceReadResponse(
                response,
                polling.idm,
                balanceBasis,
            )
            success(
                tag = tag,
                profile = TransitCardProfile.OCTOPUS,
                detectedName = "Octopus",
                cardId = polling.idm,
                protocol = "FeliCa Read Without Encryption",
                balance = TransitBalance(
                    currencyCode = "HKD",
                    amountMinor = (balance.rawBalance - balanceBasis.rawOffsetTenths) * 10L,
                    fractionDigits = 2,
                    isEstimated = true,
                ),
                systemCode = polling.systemCode.toUpperHex(),
                rawData = balance.rawBlock,
                details = listOf(
                    TransitCardDetail(
                        type = TransitCardDetailType.MANUFACTURER_PARAMETERS,
                        value = polling.manufacturerParameters.toUpperHex(),
                        monospace = true,
                    ),
                    TransitCardDetail(
                        type = TransitCardDetailType.ANDROID_DISCOVERY_SYSTEM,
                        value = discoverySystemCode.toUpperHex(),
                        monospace = true,
                    ),
                    TransitCardDetail(
                        type = TransitCardDetailType.FELICA_SYSTEM_CODES,
                        value = availableSystemCodes.joinToString(" · ") { it.toUpperHex() },
                        monospace = true,
                    ),
                    TransitCardDetail(
                        type = TransitCardDetailType.RAW_BALANCE_UNITS,
                        value = balance.rawBalance.toString(),
                        monospace = true,
                    ),
                    TransitCardDetail(
                        type = TransitCardDetailType.OCTOPUS_BALANCE_BASIS,
                        value = balanceBasis.name,
                    ),
                ),
                note = "The selected Octopus convenience-limit basis was applied to this read-only community-decoded estimate.",
            )
        } catch (error: OctopusProtocolException) {
            if (
                octopusPolling == null &&
                availableSystemCodes.none { it.contentEquals(OctopusProtocol.systemCode) }
            ) {
                return CardReadResult.Failure(
                    error.message ?: "The Octopus FeliCa system was not found.",
                )
            }
            success(
                tag = tag,
                profile = TransitCardProfile.OCTOPUS,
                detectedName = "Octopus",
                cardId = octopusPolling?.idm ?: tag.id,
                protocol = "FeliCa Read Without Encryption",
                balance = null,
                systemCode = if (
                    availableSystemCodes.any { it.contentEquals(OctopusProtocol.systemCode) }
                ) {
                    OctopusProtocol.systemCode.toUpperHex()
                } else {
                    discoverySystemCode.toUpperHex()
                },
                details = listOf(
                    TransitCardDetail(
                        type = TransitCardDetailType.MANUFACTURER_PARAMETERS,
                        value = (octopusPolling?.manufacturerParameters ?: nfcF.manufacturer)
                            .toUpperHex(),
                        monospace = true,
                    ),
                    TransitCardDetail(
                        type = TransitCardDetailType.ANDROID_DISCOVERY_SYSTEM,
                        value = discoverySystemCode.toUpperHex(),
                        monospace = true,
                    ),
                    TransitCardDetail(
                        type = TransitCardDetailType.FELICA_SYSTEM_CODES,
                        value = availableSystemCodes.joinToString(" · ") { it.toUpperHex() },
                        monospace = true,
                    ),
                ),
                note = error.message ?: "The Octopus balance record was not exposed.",
            )
        } finally {
            nfcF.closeQuietly()
        }
    }

    private fun readJapaneseIc(tag: Tag): CardReadResult {
        val nfcF = NfcF.get(tag)
            ?: return CardReadResult.Failure("Japanese IC cards require NFC-F (FeliCa).")
        val discoverySystemCode = nfcF.systemCode
        var japanesePolling: FelicaPollingData? = null

        return try {
            nfcF.connect()
            nfcF.timeout = 3_000
            val polling = FelicaProtocol.parsePollingResponse(
                response = nfcF.transceive(
                    FelicaProtocol.buildPollingCommand(JapaneseIcProtocol.SYSTEM_CODE),
                ),
                expectedSystemCode = JapaneseIcProtocol.SYSTEM_CODE,
            )
            japanesePolling = polling
            val command = FelicaProtocol.buildReadCommand(
                idm = polling.idm,
                serviceCode = JapaneseIcProtocol.HISTORY_SERVICE_CODE,
            )
            val block = FelicaProtocol.parseReadResponse(nfcF.transceive(command), polling.idm)
            success(
                tag = tag,
                profile = TransitCardProfile.JAPAN_TRANSIT_IC,
                detectedName = "Japanese interoperable transit IC",
                cardId = polling.idm,
                protocol = "FeliCa service 090F",
                balance = TransitBalance(
                    currencyCode = "JPY",
                    amountMinor = JapaneseIcProtocol.decodeLatestBalanceYen(block),
                    fractionDigits = 0,
                ),
                systemCode = "%04X".format(polling.systemCode),
                rawData = block,
                details = listOf(
                    TransitCardDetail(
                        type = TransitCardDetailType.MANUFACTURER_PARAMETERS,
                        value = polling.manufacturerParameters.toUpperHex(),
                        monospace = true,
                    ),
                    TransitCardDetail(
                        type = TransitCardDetailType.ANDROID_DISCOVERY_SYSTEM,
                        value = discoverySystemCode.toUpperHex(),
                        monospace = true,
                    ),
                ),
                note = "Japanese interoperable transit cards share FeliCa system 0003, so the card may not publicly confirm whether it is Suica, PASMO, ICOCA, or another compatible brand.",
            )
        } catch (error: FelicaProtocolException) {
            japanesePolling?.let { polling ->
                success(
                    tag = tag,
                    profile = TransitCardProfile.JAPAN_TRANSIT_IC,
                    detectedName = "Japanese interoperable transit IC",
                    cardId = polling.idm,
                    protocol = "FeliCa system 0003",
                    balance = null,
                    systemCode = "%04X".format(polling.systemCode),
                    details = listOf(
                        TransitCardDetail(
                            type = TransitCardDetailType.MANUFACTURER_PARAMETERS,
                            value = polling.manufacturerParameters.toUpperHex(),
                            monospace = true,
                        ),
                        TransitCardDetail(
                            type = TransitCardDetailType.ANDROID_DISCOVERY_SYSTEM,
                            value = discoverySystemCode.toUpperHex(),
                            monospace = true,
                        ),
                    ),
                    note = error.message
                        ?: "The Japanese transit IC application was found, but no stored balance was exposed.",
                )
            } ?: CardReadResult.Failure(
                error.message ?: "A compatible Japanese transit IC application was not found.",
            )
        } finally {
            nfcF.closeQuietly()
        }
    }

    private fun readCepas(
        tag: Tag,
        allowIdentificationFallback: Boolean = true,
    ): CardReadResult {
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
            if (allowIdentificationFallback) {
                readIdentificationOnly(
                    tag = tag,
                    profile = TransitCardProfile.EZLINK,
                    extraNote = error.message ?: "No compatible legacy CEPAS purse was exposed.",
                )
            } else {
                CardReadResult.Failure(
                    error.message ?: "A compatible legacy CEPAS application was not found.",
                )
            }
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
            TransitCardProfile.AUTOMATIC -> listOf(
                "Shenzhentong CPU card" to shenzhenAid,
                "China T-Union" to tUnionAid,
                "China City Union" to cityUnionAid,
            )

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
                if (profile == TransitCardProfile.AUTOMATIC) {
                    CardReadResult.Failure("No compatible public China transit application was found.")
                } else {
                    readIdentificationOnly(
                        tag = tag,
                        profile = profile,
                        extraNote = "No compatible public PBOC/T-Union application was found; balance data may be protected.",
                    )
                }
            } else {
                val isTUnion = selected.second.contentEquals(tUnionAid)
                val detectedProfile = if (profile == TransitCardProfile.AUTOMATIC) {
                    if (selected.second.contentEquals(shenzhenAid)) {
                        TransitCardProfile.SHENZHENTONG
                    } else {
                        TransitCardProfile.T_UNION
                    }
                } else {
                    profile
                }
                var balanceIndex = 0
                var balanceData = try {
                    transceiveIso7816(
                        isoDep,
                        Iso7816TransitProtocol.getChinaBalance(),
                    )
                } catch (_: Iso7816ProtocolException) {
                    null
                }
                if (isTUnion && balanceData == null) {
                    balanceIndex = 3
                    balanceData = try {
                        transceiveIso7816(
                            isoDep,
                            Iso7816TransitProtocol.getChinaBalance(balanceIndex = balanceIndex),
                        )
                    } catch (_: Iso7816ProtocolException) {
                        null
                    }
                }
                val debtData = if (isTUnion && balanceData != null) {
                    try {
                        transceiveIso7816(
                            isoDep,
                            Iso7816TransitProtocol.getChinaBalance(
                                balanceIndex = if (balanceIndex == 0) 1 else 2,
                            ),
                        )
                    } catch (_: Iso7816ProtocolException) {
                        null
                    }
                } else {
                    null
                }
                val cardInfoData = if (isTUnion) {
                    try {
                        transceiveIso7816(
                            isoDep,
                            Iso7816TransitProtocol.readBinaryBySfi(0x15),
                        )
                    } catch (_: Iso7816ProtocolException) {
                        null
                    }
                } else {
                    null
                }
                val cardInfo = cardInfoData?.let { data ->
                    runCatching { Iso7816TransitProtocol.parseTUnionCardInfo(data) }.getOrNull()
                }
                val transactionRecords = if (isTUnion) {
                    readTUnionTransactionRecords(isoDep)
                } else {
                    emptyList()
                }
                val transactions = transactionRecords.mapNotNull { record ->
                    Iso7816TransitProtocol.parseTUnionTransaction(record)
                }
                val details = buildList {
                    cardInfo?.let { info ->
                        add(
                            TransitCardDetail(
                                TransitCardDetailType.APPLICATION_VERSION,
                                info.applicationVersion.toString(),
                            ),
                        )
                        add(
                            TransitCardDetail(
                                TransitCardDetailType.ISSUER_CODE,
                                info.issuerCode,
                                monospace = true,
                            ),
                        )
                        info.validFrom?.let {
                            add(
                                TransitCardDetail(
                                    TransitCardDetailType.VALID_FROM,
                                    it.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                ),
                            )
                        }
                        info.validUntil?.let {
                            add(
                                TransitCardDetail(
                                    TransitCardDetailType.VALID_UNTIL,
                                    it.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                ),
                            )
                        }
                    }
                    if (isTUnion) {
                        if (balanceData != null) {
                            add(
                                TransitCardDetail(
                                    TransitCardDetailType.BALANCE_PURSE_LAYOUT,
                                    if (balanceIndex == 0) "0 / 1" else "3 / 2",
                                    monospace = true,
                                ),
                            )
                        }
                        add(
                            TransitCardDetail(
                                TransitCardDetailType.TRANSACTION_RECORDS_READ,
                                transactions.size.toString(),
                            ),
                        )
                    }
                }
                success(
                    tag = tag,
                    profile = detectedProfile,
                    detectedName = selected.first,
                    protocol = "PBOC transit purse over ISO-DEP",
                    balance = balanceData?.let {
                        TransitBalance(
                            currencyCode = "CNY",
                            amountMinor = if (isTUnion) {
                                Iso7816TransitProtocol.parseChinaTUnionBalanceCents(it, debtData)
                            } else {
                                Iso7816TransitProtocol.parseChinaBalanceCents(it)
                            },
                            fractionDigits = 2,
                        )
                    },
                    cardNumber = cardInfo?.serialNumber,
                    rawData = balanceData,
                    details = details,
                    transactions = transactions,
                    note = if (balanceData == null) {
                        "The transit application was identified, but this card did not expose its stored balance. Public card details and history are shown when available."
                    } else if (transactions.isNotEmpty()) {
                        "Read from the public transit purse and history records. Route and station names require issuer-specific databases, so the card's stored codes are preserved when a reliable name is unavailable."
                    } else {
                        "Read from the card's public transit purse. No write command was sent."
                    },
                )
            }
        } finally {
            isoDep.closeQuietly()
        }
    }

    private fun readTMoney(
        tag: Tag,
        allowIdentificationFallback: Boolean = true,
    ): CardReadResult {
        val isoDep = IsoDep.get(tag)
            ?: return CardReadResult.Failure("T-money requires an ISO-DEP smart card.")

        return try {
            isoDep.connect()
            isoDep.timeout = 3_000
            val selectData = try {
                transceiveIso7816(
                    isoDep,
                    Iso7816TransitProtocol.selectApplication(TMoneyProtocol.applicationId),
                )
            } catch (error: Iso7816ProtocolException) {
                return if (allowIdentificationFallback) {
                    readIdentificationOnly(
                        tag = tag,
                        profile = TransitCardProfile.T_MONEY,
                        extraNote = error.message ?: "The public T-money application was not found.",
                    )
                } else {
                    CardReadResult.Failure(
                        error.message ?: "The public T-money application was not found.",
                    )
                }
            }
            val cardInfo = runCatching { TMoneyProtocol.parseCardInfo(selectData) }.getOrNull()
            val balanceData = try {
                transceiveIso7816(isoDep, TMoneyProtocol.getBalanceCommand())
            } catch (_: Iso7816ProtocolException) {
                null
            }
            val balanceWon = balanceData?.let { data ->
                runCatching { TMoneyProtocol.parseBalanceWon(data) }.getOrNull()
            }
            val transactions = readIso7816Records(
                isoDep = isoDep,
                sfi = 0x04,
                minimumLength = 46,
            ).mapNotNull(TMoneyProtocol::parseTransaction)
            val details = buildList {
                add(
                    TransitCardDetail(
                        type = TransitCardDetailType.APPLICATION_ID,
                        value = TMoneyProtocol.applicationId.toUpperHex(),
                        monospace = true,
                    ),
                )
                cardInfo?.let { info ->
                    add(
                        TransitCardDetail(
                            type = TransitCardDetailType.CARD_TYPE_CODE,
                            value = "%02X".format(info.cardTypeCode),
                            monospace = true,
                        ),
                    )
                    add(
                        TransitCardDetail(
                            type = TransitCardDetailType.ISSUER_CODE,
                            value = info.issuerCode,
                            monospace = true,
                        ),
                    )
                    info.issueDate?.let {
                        add(
                            TransitCardDetail(
                                type = TransitCardDetailType.ISSUE_DATE,
                                value = it.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            ),
                        )
                    }
                    info.validUntil?.let {
                        add(
                            TransitCardDetail(
                                type = TransitCardDetailType.VALID_UNTIL,
                                value = it.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            ),
                        )
                    }
                    info.maximumBalanceWon?.let {
                        add(
                            TransitCardDetail(
                                type = TransitCardDetailType.MAXIMUM_BALANCE,
                                value = "$it KRW",
                            ),
                        )
                    }
                }
                add(
                    TransitCardDetail(
                        type = TransitCardDetailType.TRANSACTION_RECORDS_READ,
                        value = transactions.size.toString(),
                    ),
                )
            }
            success(
                tag = tag,
                profile = TransitCardProfile.T_MONEY,
                detectedName = "T-money",
                protocol = "KS X 6924 public purse over ISO-DEP",
                balance = balanceWon?.let {
                    TransitBalance(
                        currencyCode = "KRW",
                        amountMinor = it,
                        fractionDigits = 0,
                    )
                },
                cardNumber = cardInfo?.serialNumber,
                rawData = balanceData,
                details = details,
                transactions = transactions,
                note = if (balanceWon == null) {
                    "The public T-money application was identified, but this card did not expose its purse balance. Public card details and history are shown when available."
                } else {
                    "Read from the public T-money purse. No write or value-changing command was sent."
                },
            )
        } finally {
            isoDep.closeQuietly()
        }
    }

    private fun readClipper(
        tag: Tag,
        allowIdentificationFallback: Boolean = true,
    ): CardReadResult {
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
                if (allowIdentificationFallback) {
                    readIdentificationOnly(
                        tag = tag,
                        profile = TransitCardProfile.CLIPPER,
                        extraNote = "The classic public Clipper DESFire application was not found on this card.",
                    )
                } else {
                    CardReadResult.Failure(
                        "The classic public Clipper DESFire application was not found.",
                    )
                }
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

    private fun readOyster(tag: Tag): CardReadResult {
        val mifareClassic = MifareClassic.get(tag)
        if (mifareClassic != null) {
            return try {
                mifareClassic.connect()
                val mifareType = when (mifareClassic.type) {
                    MifareClassic.TYPE_CLASSIC -> "MIFARE Classic"
                    MifareClassic.TYPE_PLUS -> "MIFARE Plus"
                    MifareClassic.TYPE_PRO -> "MIFARE Pro"
                    else -> "Unknown MIFARE Classic-compatible type"
                }
                success(
                    tag = tag,
                    profile = TransitCardProfile.OYSTER,
                    detectedName = "Oyster-compatible card",
                    protocol = "$mifareType metadata (no sector authentication)",
                    balance = null,
                    details = listOf(
                        TransitCardDetail(
                            type = TransitCardDetailType.CARD_GENERATION,
                            value = mifareType,
                        ),
                        TransitCardDetail(
                            type = TransitCardDetailType.MEMORY_SIZE,
                            value = "${mifareClassic.size} bytes",
                        ),
                        TransitCardDetail(
                            type = TransitCardDetailType.SECTOR_COUNT,
                            value = mifareClassic.sectorCount.toString(),
                        ),
                        TransitCardDetail(
                            type = TransitCardDetailType.BLOCK_COUNT,
                            value = mifareClassic.blockCount.toString(),
                        ),
                    ),
                    note = "This matches the technology used by first-generation Oyster cards. Oyster sector keys are not public, so the app does not attempt authentication or show a balance.",
                )
            } finally {
                mifareClassic.closeQuietly()
            }
        }

        val isoDep = IsoDep.get(tag) ?: return readIdentificationOnly(
            tag = tag,
            profile = TransitCardProfile.OYSTER,
            extraNote = "Neither a MIFARE Classic nor an ISO-DEP interface was available.",
        )
        return try {
            isoDep.connect()
            isoDep.timeout = 3_000
            val version = try {
                DesfireTransitProtocol.parseVersion(
                    exchangeDesfire(isoDep, DesfireTransitProtocol.getVersion()),
                )
            } catch (error: DesfireProtocolException) {
                return readIdentificationOnly(
                    tag = tag,
                    profile = TransitCardProfile.OYSTER,
                    extraNote = error.message ?: "A public DESFire version response was unavailable.",
                )
            } catch (_: IllegalArgumentException) {
                return readIdentificationOnly(
                    tag = tag,
                    profile = TransitCardProfile.OYSTER,
                    extraNote = "The DESFire version response was incomplete.",
                )
            }
            success(
                tag = tag,
                profile = TransitCardProfile.OYSTER,
                detectedName = "Oyster-compatible DESFire card",
                protocol = "MIFARE DESFire GetVersion (read-only)",
                balance = null,
                details = listOf(
                    TransitCardDetail(
                        type = TransitCardDetailType.CARD_GENERATION,
                        value = "MIFARE DESFire",
                    ),
                    TransitCardDetail(
                        type = TransitCardDetailType.DESFIRE_HARDWARE_VERSION,
                        value = "%d.%d (vendor %02X, type %02X, storage %02X)".format(
                            version.hardwareMajor,
                            version.hardwareMinor,
                            version.hardwareVendorId,
                            version.hardwareType,
                            version.hardwareStorageCode,
                        ),
                        monospace = true,
                    ),
                    TransitCardDetail(
                        type = TransitCardDetailType.DESFIRE_SOFTWARE_VERSION,
                        value = "${version.softwareMajor}.${version.softwareMinor}",
                        monospace = true,
                    ),
                    TransitCardDetail(
                        type = TransitCardDetailType.DESFIRE_CHIP_IDENTIFIER,
                        value = version.chipIdentifier,
                        monospace = true,
                    ),
                ),
                note = "Newer Oyster cards use DESFire and do not expose a freely readable Oyster balance file. Only standard read-only chip metadata is shown.",
            )
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

    private fun transceiveIso7816(isoDep: IsoDep, command: ByteArray): ByteArray {
        var response = isoDep.transceive(command)
        val corrected = Iso7816TransitProtocol.correctedLengthCommand(command, response)
        if (corrected != null) {
            response = isoDep.transceive(corrected)
        }
        return Iso7816TransitProtocol.unwrap(response)
    }

    private fun readTUnionTransactionRecords(isoDep: IsoDep): List<ByteArray> {
        return readIso7816Records(
            isoDep = isoDep,
            sfi = 0x18,
            minimumLength = 23,
        )
    }

    private fun readIso7816Records(
        isoDep: IsoDep,
        sfi: Int,
        minimumLength: Int,
        maximumRecords: Int = 10,
    ): List<ByteArray> {
        val records = mutableListOf<ByteArray>()
        for (recordNumber in 1..maximumRecords) {
            val command = Iso7816TransitProtocol.readRecordBySfi(
                sfi = sfi,
                recordNumber = recordNumber,
            )
            var response = isoDep.transceive(command)
            val corrected = Iso7816TransitProtocol.correctedLengthCommand(command, response)
            if (corrected != null) {
                response = isoDep.transceive(corrected)
            }
            if (Iso7816TransitProtocol.isRecordNotFound(response)) break
            val record = try {
                Iso7816TransitProtocol.unwrap(response)
            } catch (_: Iso7816ProtocolException) {
                break
            }
            if (record.size < minimumLength) break
            records += record
        }
        return records
    }

    private fun readIdentificationOnly(
        tag: Tag,
        profile: TransitCardProfile,
        detectedName: String = profile.displayName,
        extraNote: String? = null,
    ): CardReadResult = success(
        tag = tag,
        profile = profile,
        detectedName = detectedName,
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
        cardId: ByteArray = tag.id,
        protocol: String,
        balance: TransitBalance?,
        cardNumber: String? = null,
        systemCode: String? = null,
        rawData: ByteArray? = null,
        details: List<TransitCardDetail> = emptyList(),
        transactions: List<TransitTransaction> = emptyList(),
        note: String,
    ) = CardReadResult.Success(
        scans = listOf(
            TransitCardScan(
                selectedProfile = profile,
                detectedName = detectedName,
                cardId = cardId.toUpperHex(),
                technology = technologyNames(tag),
                protocol = protocol,
                balance = balance,
                cardNumber = cardNumber,
                systemCode = systemCode,
                rawDataHex = rawData?.toUpperHex(" "),
                details = listOf(
                    TransitCardDetail(
                        type = TransitCardDetailType.NFC_ID_LENGTH,
                        value = cardId.size.toString(),
                    ),
                ) + details,
                transactions = transactions,
                note = note,
                scannedAt = Instant.now(),
            ),
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

private fun MifareClassic.closeQuietly() {
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
