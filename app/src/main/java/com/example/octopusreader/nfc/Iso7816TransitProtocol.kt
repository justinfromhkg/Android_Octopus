package com.example.octopusreader.nfc

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime

internal object Iso7816TransitProtocol {
    fun selectApplication(aid: ByteArray): ByteArray = command(
        cla = 0x00,
        ins = 0xA4,
        p1 = 0x04,
        p2 = 0x00,
        data = aid,
        expectedLength = 0,
    )

    fun selectFile(fileId: Int): ByteArray = command(
        cla = 0x00,
        ins = 0xA4,
        p1 = 0x00,
        p2 = 0x00,
        data = byteArrayOf((fileId shr 8).toByte(), fileId.toByte()),
        expectedLength = 0,
    )

    fun getCepasPurse(purseId: Int = 3): ByteArray = command(
        cla = 0x90,
        ins = 0x32,
        p1 = purseId,
        p2 = 0x00,
        expectedLength = 0,
    )

    fun getChinaBalance(balanceIndex: Int = 0): ByteArray = command(
        cla = 0x80,
        ins = 0x5C,
        p1 = balanceIndex,
        p2 = 0x02,
        expectedLength = 4,
    )

    fun readBinaryBySfi(sfi: Int): ByteArray {
        require(sfi in 1..31) { "SFI must be between 1 and 31." }
        return command(
            cla = 0x00,
            ins = 0xB0,
            p1 = 0x80 or sfi,
            p2 = 0x00,
            expectedLength = 0,
        )
    }

    fun readRecordBySfi(sfi: Int, recordNumber: Int): ByteArray {
        require(sfi in 1..31) { "SFI must be between 1 and 31." }
        require(recordNumber in 1..255) { "Record number must be between 1 and 255." }
        return command(
            cla = 0x00,
            ins = 0xB2,
            p1 = recordNumber,
            p2 = (sfi shl 3) or 0x04,
            expectedLength = 0,
        )
    }

    fun correctedLengthCommand(command: ByteArray, response: ByteArray): ByteArray? {
        if (command.isEmpty() || response.size < 2) return null
        val sw1 = response[response.lastIndex - 1].toInt() and 0xFF
        val sw2 = response[response.lastIndex].toInt() and 0xFF
        if (sw1 != 0x6C) return null
        return command.copyOf().apply { this[lastIndex] = sw2.toByte() }
    }

    fun isRecordNotFound(response: ByteArray): Boolean {
        if (response.size < 2) return false
        return (response[response.lastIndex - 1].toInt() and 0xFF) == 0x6A &&
            (response[response.lastIndex].toInt() and 0xFF) == 0x83
    }

    fun command(
        cla: Int,
        ins: Int,
        p1: Int,
        p2: Int,
        data: ByteArray = byteArrayOf(),
        expectedLength: Int,
    ): ByteArray {
        val header = byteArrayOf(cla.toByte(), ins.toByte(), p1.toByte(), p2.toByte())
        return if (data.isEmpty()) {
            header + expectedLength.toByte()
        } else {
            header + data.size.toByte() + data + expectedLength.toByte()
        }
    }

    fun unwrap(response: ByteArray): ByteArray {
        if (response.size < 2) {
            throw Iso7816ProtocolException("The smart card returned an incomplete response.")
        }

        val sw1 = response[response.lastIndex - 1].toInt() and 0xFF
        val sw2 = response[response.lastIndex].toInt() and 0xFF
        if (sw1 != 0x90 || sw2 != 0x00) {
            throw Iso7816ProtocolException(
                "The smart card rejected the command (status %02X%02X).".format(sw1, sw2),
            )
        }
        return response.copyOfRange(0, response.size - 2)
    }

    fun parseCepasBalanceCents(purse: ByteArray): Long {
        require(purse.size >= 5) { "The CEPAS purse response is too short." }
        val value = ((purse[2].toInt() and 0xFF) shl 16) or
            ((purse[3].toInt() and 0xFF) shl 8) or
            (purse[4].toInt() and 0xFF)
        return if (value and 0x800000 != 0) {
            (value or -0x1000000).toLong()
        } else {
            value.toLong()
        }
    }

    fun parseCepasCardNumber(purse: ByteArray): String? =
        if (purse.size >= 16) purse.copyOfRange(8, 16).toUpperHex() else null

    fun parseChinaBalanceCents(data: ByteArray): Long {
        require(data.size >= 4) { "The China transit balance response is too short." }
        val raw = ((data[0].toInt() and 0xFF) shl 24) or
            ((data[1].toInt() and 0xFF) shl 16) or
            ((data[2].toInt() and 0xFF) shl 8) or
            (data[3].toInt() and 0xFF)
        val value = raw and 0x7FFFFFFF
        return if (value and 0x40000000 != 0) {
            value.toLong() - 0x80000000L
        } else {
            value.toLong()
        }
    }

    fun parseChinaTUnionBalanceCents(
        positivePurse: ByteArray,
        debtPurse: ByteArray?,
    ): Long {
        val positive = parseChinaUnsignedBalanceCents(positivePurse)
        val debt = debtPurse?.let(::parseChinaUnsignedBalanceCents) ?: 0L
        return if (positive > 0L) positive else positive - debt
    }

    fun parseTUnionCardInfo(data: ByteArray): TUnionCardInfo {
        require(data.size >= 28) { "The T-Union card information file is too short." }
        val fullSerial = data.copyOfRange(10, 20).toUpperHex()
        val serialNumber = fullSerial.drop(1)
        return TUnionCardInfo(
            serialNumber = serialNumber,
            issuerCode = serialNumber.take(8),
            applicationVersion = data[9].toInt() and 0xFF,
            validFrom = parseBcdDate(data, 20),
            validUntil = parseBcdDate(data, 24),
            rawData = data,
        )
    }

    fun parseTUnionTransaction(data: ByteArray): TransitTransaction? {
        if (data.size < T_UNION_RECORD_LENGTH) return null
        val record = data.copyOfRange(0, T_UNION_RECORD_LENGTH)
        val amount = record.readUnsignedLong(5, 4)
        val timestamp = parseBcdDateTime(record, 16)
        if (amount == 0L && timestamp == null) return null

        val transactionCode = record[9].toInt() and 0xFF
        val terminalValue = record.readUnsignedLong(10, 6)
        val transportCode = (terminalValue shr 28).toInt()
        val type = when {
            transactionCode == 0x02 -> TransitTransactionType.TOP_UP
            transportCode == 0x03 -> TransitTransactionType.BUS
            transportCode == 0x06 -> TransitTransactionType.METRO
            transactionCode == 0x06 || transactionCode == 0x09 -> TransitTransactionType.PURCHASE
            else -> TransitTransactionType.UNKNOWN
        }
        val signedAmount = when (type) {
            TransitTransactionType.TOP_UP -> amount
            else -> -amount
        }
        val terminalHex = record.copyOfRange(10, 16).toUpperHex()
        val stationValue = terminalValue and 0xFFFF_FF00L

        return TransitTransaction(
            type = type,
            timestamp = timestamp,
            amountMinor = signedAmount,
            currencyCode = "CNY",
            fractionDigits = 2,
            transactionCode = transactionCode,
            sequenceCounter = record.readUnsignedLong(0, 2).toInt(),
            overdraftMinor = record.readUnsignedLong(2, 3),
            terminalCode = terminalHex,
            routeCode = if (type == TransitTransactionType.BUS) terminalHex else null,
            boardingStationCode = null,
            alightingStationCode = if (type == TransitTransactionType.METRO) {
                "%08X".format(stationValue)
            } else {
                null
            },
            gateCode = if (type == TransitTransactionType.METRO) {
                "%02X".format(terminalValue and 0xFF)
            } else {
                null
            },
            rawDataHex = record.toUpperHex(" "),
        )
    }

    private fun parseChinaUnsignedBalanceCents(data: ByteArray): Long {
        require(data.size >= 4) { "The China transit balance response is too short." }
        val raw = ((data[0].toLong() and 0xFF) shl 24) or
            ((data[1].toLong() and 0xFF) shl 16) or
            ((data[2].toLong() and 0xFF) shl 8) or
            (data[3].toLong() and 0xFF)
        return raw and 0x7FFFFFFF
    }

    private fun parseBcdDate(data: ByteArray, offset: Int): LocalDate? = try {
        LocalDate.of(
            bcd(data[offset]) * 100 + bcd(data[offset + 1]),
            bcd(data[offset + 2]),
            bcd(data[offset + 3]),
        )
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

    private fun ByteArray.readUnsignedLong(offset: Int, length: Int): Long {
        require(offset >= 0 && length in 1..8 && offset + length <= size)
        var value = 0L
        for (index in offset until offset + length) {
            value = (value shl 8) or (this[index].toLong() and 0xFF)
        }
        return value
    }

    private const val T_UNION_RECORD_LENGTH = 23
}

internal data class TUnionCardInfo(
    val serialNumber: String,
    val issuerCode: String,
    val applicationVersion: Int,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
    val rawData: ByteArray,
)

internal class Iso7816ProtocolException(message: String) : Exception(message)
