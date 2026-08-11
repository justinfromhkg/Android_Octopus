package com.example.octopusreader.nfc

import java.time.Instant
import java.time.LocalDateTime

enum class TransitCardProfile(
    val displayName: String,
    val localName: String?,
    val region: String,
    val technologyHint: String,
    val balanceReadSupport: BalanceReadSupport,
) {
    OCTOPUS(
        displayName = "Octopus",
        localName = "八達通",
        region = "Hong Kong",
        technologyHint = "FeliCa / NFC-F",
        balanceReadSupport = BalanceReadSupport.ESTIMATED,
    ),
    EASYCARD(
        displayName = "EasyCard",
        localName = "悠遊卡",
        region = "Taiwan",
        technologyHint = "MIFARE Classic / NFC-A",
        balanceReadSupport = BalanceReadSupport.ISSUER_KEYS,
    ),
    IPASS(
        displayName = "iPASS",
        localName = "一卡通",
        region = "Taiwan",
        technologyHint = "NFC-A smart card",
        balanceReadSupport = BalanceReadSupport.PROTECTED_FORMAT,
    ),
    EZLINK(
        displayName = "EZ-Link",
        localName = null,
        region = "Singapore",
        technologyHint = "ISO-DEP / CEPAS",
        balanceReadSupport = BalanceReadSupport.LEGACY_CEPAS,
    ),
    SUICA(
        displayName = "Suica",
        localName = "スイカ",
        region = "Japan",
        technologyHint = "FeliCa / NFC-F",
        balanceReadSupport = BalanceReadSupport.PUBLIC_FELICA,
    ),
    PASMO(
        displayName = "PASMO",
        localName = "パスモ",
        region = "Japan",
        technologyHint = "FeliCa / NFC-F",
        balanceReadSupport = BalanceReadSupport.PUBLIC_FELICA,
    ),
    ICOCA(
        displayName = "ICOCA",
        localName = "イコカ",
        region = "Japan",
        technologyHint = "FeliCa / NFC-F",
        balanceReadSupport = BalanceReadSupport.PUBLIC_FELICA,
    ),
    YANGCHENGTONG(
        displayName = "Yangchengtong",
        localName = "羊城通",
        region = "Guangzhou, Guangdong",
        technologyHint = "ISO-DEP or MIFARE Classic",
        balanceReadSupport = BalanceReadSupport.CHINA_CARD_VARIANT,
    ),
    SHENZHENTONG(
        displayName = "Shenzhentong",
        localName = "深圳通",
        region = "Shenzhen, Guangdong",
        technologyHint = "ISO-DEP or MIFARE Classic",
        balanceReadSupport = BalanceReadSupport.CHINA_CARD_VARIANT,
    ),
    T_UNION(
        displayName = "China T-Union",
        localName = "交通联合",
        region = "China",
        technologyHint = "ISO-DEP / PBOC",
        balanceReadSupport = BalanceReadSupport.CHINA_PUBLIC_PURSE,
    ),
    CLIPPER(
        displayName = "Clipper",
        localName = null,
        region = "San Francisco Bay Area",
        technologyHint = "MIFARE DESFire / ISO-DEP",
        balanceReadSupport = BalanceReadSupport.CLASSIC_CLIPPER,
    ),
    MACAU_PASS(
        displayName = "Macau Pass",
        localName = "澳門通",
        region = "Macau",
        technologyHint = "NFC-A smart card",
        balanceReadSupport = BalanceReadSupport.PROTECTED_FORMAT,
    ),
    TOUCH_N_GO(
        displayName = "Touch ’n Go",
        localName = null,
        region = "Malaysia",
        technologyHint = "MIFARE Classic / NFC-A",
        balanceReadSupport = BalanceReadSupport.ISSUER_KEYS,
    ),
}

enum class BalanceReadSupport {
    ESTIMATED,
    ISSUER_KEYS,
    PROTECTED_FORMAT,
    LEGACY_CEPAS,
    PUBLIC_FELICA,
    CHINA_CARD_VARIANT,
    CHINA_PUBLIC_PURSE,
    CLASSIC_CLIPPER,
}

enum class OctopusBalanceBasis(val rawOffsetTenths: Long) {
    PHYSICAL_PRE_2017(rawOffsetTenths = 350L),
    NEW_OR_MOBILE(rawOffsetTenths = 500L),
}

data class TransitReadRequest(
    val profile: TransitCardProfile,
    val octopusBalanceBasis: OctopusBalanceBasis = OctopusBalanceBasis.PHYSICAL_PRE_2017,
)

data class TransitBalance(
    val currencyCode: String,
    val amountMinor: Long,
    val fractionDigits: Int,
    val isEstimated: Boolean = false,
)

enum class TransitCardDetailType {
    NFC_ID_LENGTH,
    MANUFACTURER_PARAMETERS,
    RAW_BALANCE_UNITS,
    OCTOPUS_BALANCE_BASIS,
    APPLICATION_VERSION,
    ISSUER_CODE,
    VALID_FROM,
    VALID_UNTIL,
    BALANCE_PURSE_LAYOUT,
    TRANSACTION_RECORDS_READ,
}

data class TransitCardDetail(
    val type: TransitCardDetailType,
    val value: String,
    val monospace: Boolean = false,
)

enum class TransitTransactionType {
    TOP_UP,
    BUS,
    METRO,
    PURCHASE,
    UNKNOWN,
}

data class TransitTransaction(
    val type: TransitTransactionType,
    val timestamp: LocalDateTime?,
    val amountMinor: Long,
    val transactionCode: Int,
    val sequenceCounter: Int,
    val overdraftMinor: Long,
    val terminalCode: String,
    val routeCode: String? = null,
    val boardingStationCode: String? = null,
    val alightingStationCode: String? = null,
    val gateCode: String? = null,
    val rawDataHex: String,
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
    val details: List<TransitCardDetail> = emptyList(),
    val transactions: List<TransitTransaction> = emptyList(),
    val note: String,
    val scannedAt: Instant,
)

sealed interface CardReadResult {
    data class Success(val scan: TransitCardScan) : CardReadResult
    data class Failure(val message: String) : CardReadResult
}
