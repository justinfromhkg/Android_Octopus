package com.example.octopusreader.nfc

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime

internal object TMoneyProtocol {
    val applicationId: ByteArray = "D4100000030001".hexToByteArray()

    fun getBalanceCommand(): ByteArray = Iso7816TransitProtocol.command(
        cla = 0x90,
        ins = 0x4C,
        p1 = 0x00,
        p2 = 0x00,
        expectedLength = 4,
    )

    fun parseBalanceWon(data: ByteArray): Long {
        require(data.size >= 4) { "The T-money balance response is too short." }
        return data.readSignedInt(0).toLong()
    }

    fun parseCardInfo(selectResponseData: ByteArray): TMoneyCardInfo? {
        val purseInfo = findBerTlv(selectResponseData, 0xB0) ?: return null
        if (purseInfo.size < 25) return null
        return TMoneyCardInfo(
            cardTypeCode = purseInfo[0].toInt() and 0xFF,
            issuerCode = "%02X".format(purseInfo[3].toInt() and 0xFF),
            serialNumber = purseInfo.copyOfRange(4, 12).toUpperHex(),
            issueDate = parseBcdDate(purseInfo, 17),
            validUntil = parseBcdDate(purseInfo, 21),
            maximumBalanceWon = purseInfo.readUnsignedLongOrNull(27, 4),
            rawPurseInfo = purseInfo,
        )
    }

    fun parseTransaction(data: ByteArray): TransitTransaction? {
        if (data.size < TRANSACTION_RECORD_LENGTH) return null
        val record = data.copyOfRange(0, TRANSACTION_RECORD_LENGTH)
        val typeCode = record[0].toInt() and 0xFF
        val amount = record.readSignedInt(10).toLong()
        val timestamp = parseBcdDateTime(record, 26)
        if (amount == 0L && timestamp == null) return null
        val type = when (typeCode) {
            0x02 -> TransitTransactionType.TOP_UP
            0x01 -> TransitTransactionType.TRANSIT_RIDE
            else -> TransitTransactionType.UNKNOWN
        }

        return TransitTransaction(
            type = type,
            timestamp = timestamp,
            amountMinor = if (type == TransitTransactionType.TOP_UP) amount else -amount,
            currencyCode = "KRW",
            fractionDigits = 0,
            transactionCode = typeCode,
            sequenceCounter = record.readSignedInt(6),
            overdraftMinor = 0L,
            terminalCode = record.copyOfRange(14, 22).toUpperHex(),
            balanceAfterMinor = record.readSignedInt(2).toLong(),
            rawDataHex = record.toUpperHex(" "),
        )
    }

    private fun findBerTlv(data: ByteArray, wantedTag: Int): ByteArray? {
        var offset = 0
        while (offset < data.size) {
            val firstTagByte = data[offset].toInt() and 0xFF
            var tag = firstTagByte
            offset++
            if (firstTagByte and 0x1F == 0x1F) {
                do {
                    if (offset >= data.size) return null
                    val next = data[offset].toInt() and 0xFF
                    tag = (tag shl 8) or next
                    offset++
                } while (next and 0x80 != 0)
            }
            if (offset >= data.size) return null
            val firstLength = data[offset].toInt() and 0xFF
            offset++
            val length = if (firstLength and 0x80 == 0) {
                firstLength
            } else {
                val lengthBytes = firstLength and 0x7F
                if (lengthBytes !in 1..4 || offset + lengthBytes > data.size) return null
                var value = 0
                repeat(lengthBytes) {
                    value = (value shl 8) or (data[offset].toInt() and 0xFF)
                    offset++
                }
                value
            }
            if (length < 0 || offset + length > data.size) return null
            val value = data.copyOfRange(offset, offset + length)
            if (tag == wantedTag) return value
            if (firstTagByte and 0x20 != 0) {
                findBerTlv(value, wantedTag)?.let { return it }
            }
            offset += length
        }
        return null
    }

    private fun parseBcdDate(data: ByteArray, offset: Int): LocalDate? = try {
        if (offset + 4 > data.size) {
            null
        } else {
            LocalDate.of(
                bcd(data[offset]) * 100 + bcd(data[offset + 1]),
                bcd(data[offset + 2]),
                bcd(data[offset + 3]),
            )
        }
    } catch (_: DateTimeException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun parseBcdDateTime(data: ByteArray, offset: Int): LocalDateTime? = try {
        LocalDateTime.of(
            bcd(data[offset]) * 100 + bcd(data[offset + 1]),
            bcd(data[offset + 2]),
            bcd(data[offset + 3]),
            bcd(data[offset + 4]),
            bcd(data[offset + 5]),
            bcd(data[offset + 6]),
        )
    } catch (_: DateTimeException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun bcd(value: Byte): Int {
        val unsigned = value.toInt() and 0xFF
        val high = unsigned shr 4
        val low = unsigned and 0x0F
        require(high <= 9 && low <= 9) { "Invalid BCD value." }
        return high * 10 + low
    }

    private fun ByteArray.readSignedInt(offset: Int): Int {
        require(offset >= 0 && offset + 4 <= size)
        return ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
    }

    private fun ByteArray.readUnsignedLongOrNull(offset: Int, length: Int): Long? {
        if (offset < 0 || length !in 1..8 || offset + length > size) return null
        var value = 0L
        for (index in offset until offset + length) {
            value = (value shl 8) or (this[index].toLong() and 0xFF)
        }
        return value
    }

    private const val TRANSACTION_RECORD_LENGTH = 46
}

internal data class TMoneyCardInfo(
    val cardTypeCode: Int,
    val issuerCode: String,
    val serialNumber: String,
    val issueDate: LocalDate?,
    val validUntil: LocalDate?,
    val maximumBalanceWon: Long?,
    val rawPurseInfo: ByteArray,
)
