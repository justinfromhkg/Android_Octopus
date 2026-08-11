package com.example.octopusreader.nfc

internal object OctopusProtocol {
    private const val READ_WITHOUT_ENCRYPTION = 0x06
    private const val READ_WITHOUT_ENCRYPTION_RESPONSE = 0x07
    private const val BALANCE_SERVICE_CODE = 0x0117
    private const val BALANCE_BLOCK_NUMBER = 0
    private const val BLOCK_SIZE = 16

    val systemCode: ByteArray
        get() = byteArrayOf(0x80.toByte(), 0x08)

    fun buildBalanceReadCommand(idm: ByteArray): ByteArray {
        require(idm.size == 8) { "A FeliCa IDm must contain 8 bytes." }

        return byteArrayOf(
            0x10,
            READ_WITHOUT_ENCRYPTION.toByte(),
            *idm,
            0x01,
            (BALANCE_SERVICE_CODE and 0xFF).toByte(),
            ((BALANCE_SERVICE_CODE shr 8) and 0xFF).toByte(),
            0x01,
            0x80.toByte(),
            BALANCE_BLOCK_NUMBER.toByte(),
        )
    }

    fun parseBalanceReadResponse(response: ByteArray, expectedIdm: ByteArray): BalanceData {
        if (response.size < 13 + BLOCK_SIZE) {
            throw OctopusProtocolException("The card returned a response that was too short.")
        }

        val declaredLength = response[0].unsigned
        if (declaredLength != response.size) {
            throw OctopusProtocolException("The card returned an invalid response length.")
        }

        if (response[1].unsigned != READ_WITHOUT_ENCRYPTION_RESPONSE) {
            throw OctopusProtocolException("The card returned an unexpected FeliCa response.")
        }

        val responseIdm = response.copyOfRange(2, 10)
        if (!responseIdm.contentEquals(expectedIdm)) {
            throw OctopusProtocolException("The card identifier changed during the scan.")
        }

        val statusFlag1 = response[10].unsigned
        val statusFlag2 = response[11].unsigned
        if (statusFlag1 != 0 || statusFlag2 != 0) {
            throw OctopusProtocolException(
                "The card declined the read (status %02X%02X).".format(statusFlag1, statusFlag2),
            )
        }

        val blockCount = response[12].unsigned
        val expectedLength = 13 + blockCount * BLOCK_SIZE
        if (blockCount < 1 || response.size < expectedLength) {
            throw OctopusProtocolException("The card did not return the requested balance block.")
        }

        val rawBalance = (response[13].unsigned.toLong() shl 24) or
            (response[14].unsigned.toLong() shl 16) or
            (response[15].unsigned.toLong() shl 8) or
            response[16].unsigned.toLong()

        return BalanceData(
            rawBalance = rawBalance,
            estimatedBalanceHkd = decodeEstimatedBalance(rawBalance),
            rawBlock = response.copyOfRange(13, 13 + BLOCK_SIZE),
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
