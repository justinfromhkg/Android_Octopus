package com.example.octopusreader.nfc

import java.time.Instant

enum class TransitCardProfile(
    val displayName: String,
    val region: String,
    val technologyHint: String,
    val capability: String,
) {
    OCTOPUS(
        displayName = "Octopus",
        region = "Hong Kong",
        technologyHint = "FeliCa / NFC-F",
        capability = "Estimated balance and card identifier",
    ),
    EASYCARD(
        displayName = "EasyCard",
        region = "Taiwan",
        technologyHint = "MIFARE Classic / NFC-A",
        capability = "Card identifier; balance sectors require issuer keys",
    ),
    IPASS(
        displayName = "iPASS",
        region = "Taiwan",
        technologyHint = "NFC-A smart card",
        capability = "Card identifier; protected stored value is not decoded",
    ),
    EZLINK(
        displayName = "EZ-Link / CEPAS",
        region = "Singapore",
        technologyHint = "ISO-DEP / CEPAS",
        capability = "Balance and card number on compatible legacy CEPAS cards",
    ),
    JAPAN_IC(
        displayName = "Suica / PASMO / ICOCA",
        region = "Japan",
        technologyHint = "FeliCa / NFC-F",
        capability = "Latest stored balance and card identifier",
    ),
    YANGCHENGTONG(
        displayName = "Yangchengtong",
        region = "Guangzhou, Guangdong",
        technologyHint = "ISO-DEP or MIFARE Classic",
        capability = "Balance on compatible CPU/T-Union cards; ID-only fallback",
    ),
    SHENZHENTONG(
        displayName = "Shenzhentong",
        region = "Shenzhen, Guangdong",
        technologyHint = "ISO-DEP or MIFARE Classic",
        capability = "Balance on compatible CPU/T-Union cards; ID-only fallback",
    ),
    T_UNION(
        displayName = "China T-Union",
        region = "China",
        technologyHint = "ISO-DEP / PBOC",
        capability = "Stored balance on compatible T-Union cards",
    ),
    CLIPPER(
        displayName = "Clipper",
        region = "San Francisco Bay Area",
        technologyHint = "MIFARE DESFire / ISO-DEP",
        capability = "Balance on cards with the public classic Clipper application",
    ),
    MACAU_PASS(
        displayName = "Macau Pass",
        region = "Macau",
        technologyHint = "NFC-A smart card",
        capability = "Card identifier; protected stored value is not decoded",
    ),
    TOUCH_N_GO(
        displayName = "Touch ’n Go",
        region = "Malaysia",
        technologyHint = "MIFARE Classic / NFC-A",
        capability = "Card identifier; balance sectors require issuer keys",
    ),
}

data class TransitBalance(
    val currencyCode: String,
    val amountMinor: Long,
    val fractionDigits: Int,
    val isEstimated: Boolean = false,
)

data class TransitCardScan(
    val selectedProfile: TransitCardProfile,
    val detectedName: String,
    val cardId: String,
    val technology: String,
    val protocol: String,
    val balance: TransitBalance?,
    val cardNumber: String? = null,
    val systemCode: String? = null,
    val rawDataHex: String? = null,
    val note: String,
    val scannedAt: Instant,
)

sealed interface CardReadResult {
    data class Success(val scan: TransitCardScan) : CardReadResult
    data class Failure(val message: String) : CardReadResult
}
