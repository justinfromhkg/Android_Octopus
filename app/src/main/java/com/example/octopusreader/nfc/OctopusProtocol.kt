package com.example.octopusreader.nfc

internal object OctopusProtocol {
    private const val BALANCE_SERVICE_CODE = 0x0117

    val systemCode: ByteArray
        get() = byteArrayOf(0x80.toByte(), 0x08)

    fun buildBalanceReadCommand(idm: ByteArray): ByteArray {
        return FelicaProtocol.buildReadCommand(idm, BALANCE_SERVICE_CODE)
    }

    fun parseBalanceReadResponse(response: ByteArray, expectedIdm: ByteArray): BalanceData {
        val block = try {
            FelicaProtocol.parseReadResponse(response, expectedIdm)
        } catch (error: FelicaProtocolException) {
            throw OctopusProtocolException(error.message ?: "The FeliCa response could not be decoded.")
        }
        val rawBalance = (block[0].unsigned.toLong() shl 24) or
            (block[1].unsigned.toLong() shl 16) or
            (block[2].unsigned.toLong() shl 8) or
            block[3].unsigned.toLong()

        return BalanceData(
            rawBalance = rawBalance,
            estimatedBalanceHkd = decodeEstimatedBalance(rawBalance),
            rawBlock = block,
        )
    }

    fun decodeEstimatedBalance(rawBalance: Long): Double = (rawBalance - 500L) / 10.0

    private val Byte.unsigned: Int
        get() = toInt() and 0xFF
}

internal data class BalanceData(
    val rawBalance: Long,
    val estimatedBalanceHkd: Double,
    val rawBlock: ByteArray,
)

internal class OctopusProtocolException(message: String) : Exception(message)

internal fun ByteArray.toUpperHex(separator: String = ""): String =
    joinToString(separator) { byte -> "%02X".format(byte.toInt() and 0xFF) }
