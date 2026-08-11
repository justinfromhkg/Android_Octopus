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
    AUTOMATIC(
        displayName = "Automatic detection",
        localName = null,
        region = "Supported regions",
        technologyHint = "NFC-A / NFC-B / NFC-F / ISO-DEP",
        balanceReadSupport = BalanceReadSupport.AUTOMATIC,
    ),
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
    T_MONEY(
        displayName = "T-money",
        localName = "티머니",
        region = "South Korea",
        technologyHint = "ISO-DEP / KS X 6924",
        balanceReadSupport = BalanceReadSupport.KOREAN_PUBLIC_PURSE,
    ),
    JAPAN_TRANSIT_IC(
        displayName = "Japan Transit IC",
        localName = "交通系ICカード",
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
    OYSTER(
        displayName = "Oyster",
        localName = null,
        region = "London, United Kingdom",
        technologyHint = "MIFARE Classic or DESFire",
        balanceReadSupport = BalanceReadSupport.OYSTER_PROTECTED,
    ),
}

enum class BalanceReadSupport {
    AUTOMATIC,
    ESTIMATED,
    ISSUER_KEYS,
    PROTECTED_FORMAT,
    LEGACY_CEPAS,
    PUBLIC_FELICA,
    CHINA_CARD_VARIANT,
    CHINA_PUBLIC_PURSE,
    CLASSIC_CLIPPER,
    KOREAN_PUBLIC_PURSE,
    OYSTER_PROTECTED,
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
    ANDROID_DISCOVERY_SYSTEM,
    FELICA_SYSTEM_CODES,
    RAW_BALANCE_UNITS,
    OCTOPUS_BALANCE_BASIS,
    APPLICATION_VERSION,
    ISSUER_CODE,
    VALID_FROM,
    VALID_UNTIL,
    BALANCE_PURSE_LAYOUT,
    TRANSACTION_RECORDS_READ,
    APPLICATION_ID,
    CARD_TYPE_CODE,
    ISSUE_DATE,
    MAXIMUM_BALANCE,
    CARD_GENERATION,
    MEMORY_SIZE,
    SECTOR_COUNT,
    BLOCK_COUNT,
    DESFIRE_HARDWARE_VERSION,
    DESFIRE_SOFTWARE_VERSION,
    DESFIRE_CHIP_IDENTIFIER,
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
    TRANSIT_RIDE,
    UNKNOWN,
}

data class TransitTransaction(
    val type: TransitTransactionType,
    val timestamp: LocalDateTime?,
    val amountMinor: Long,
    val currencyCode: String,
    val fractionDigits: Int,
    val transactionCode: Int,
    val sequenceCounter: Int,
    val overdraftMinor: Long,
    val terminalCode: String,
    val routeCode: String? = null,
    val boardingStationCode: String? = null,
    val alightingStationCode: String? = null,
    val gateCode: String? = null,
    val balanceAfterMinor: Long? = null,
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
    data class Success(val scans: List<TransitCardScan>) : CardReadResult {
        init {
            require(scans.isNotEmpty()) { "A successful read must contain at least one scan." }
        }
    }
    data class Failure(val message: String) : CardReadResult
}
