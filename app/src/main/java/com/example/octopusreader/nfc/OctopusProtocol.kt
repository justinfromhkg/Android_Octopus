package com.example.octopusreader.nfc

internal object OctopusProtocol {
    private const val BALANCE_SERVICE_CODE = 0x0117
    private const val POLLING_RESPONSE = 0x01
    private const val REQUEST_SYSTEM_CODE_RESPONSE = 0x0D

    val systemCode: ByteArray
        get() = byteArrayOf(0x80.toByte(), 0x08)

    fun buildPollingCommand(): ByteArray = byteArrayOf(
        0x06,
        0x00,
        *systemCode,
        0x01,
        0x00,
    )

    fun parsePollingResponse(response: ByteArray): OctopusPollingData {
        if (response.size < 18 || response[0].unsigned != response.size) {
            throw OctopusProtocolException("The Octopus system returned an invalid polling response.")
        }
        if (response[1].unsigned != POLLING_RESPONSE) {
            throw OctopusProtocolException("The card returned an unexpected polling response.")
        }
        val returnedSystemCode = if (response.size >= 20) {
            response.copyOfRange(18, 20)
        } else {
            null
        }
        if (returnedSystemCode != null && !returnedSystemCode.contentEquals(systemCode)) {
            throw OctopusProtocolException("The requested Octopus system was not selected.")
        }
        return OctopusPollingData(
            idm = response.copyOfRange(2, 10),
            manufacturerParameters = response.copyOfRange(10, 18),
            systemCode = returnedSystemCode ?: systemCode,
        )
    }

    fun buildRequestSystemCodesCommand(idm: ByteArray): ByteArray {
        require(idm.size == 8) { "A FeliCa IDm must contain 8 bytes." }
        return byteArrayOf(0x0A, 0x0C, *idm)
    }

    fun parseRequestSystemCodesResponse(
        response: ByteArray,
        expectedIdm: ByteArray,
    ): List<ByteArray> {
        if (response.size < 11 || response[0].unsigned != response.size) {
            throw OctopusProtocolException("The card returned an invalid system-code response.")
        }
        if (response[1].unsigned != REQUEST_SYSTEM_CODE_RESPONSE) {
            throw OctopusProtocolException("The card returned an unexpected system-code response.")
        }
        if (!response.copyOfRange(2, 10).contentEquals(expectedIdm)) {
            throw OctopusProtocolException("The card identifier changed during system discovery.")
        }
        val count = response[10].unsigned
        if (count < 1 || response.size < 11 + count * 2) {
            throw OctopusProtocolException("The card did not return any FeliCa system codes.")
        }
        return (0 until count).map { index ->
            val offset = 11 + index * 2
            response.copyOfRange(offset, offset + 2)
        }
    }

    fun buildBalanceReadCommand(idm: ByteArray): ByteArray {
        return FelicaProtocol.buildReadCommand(idm, BALANCE_SERVICE_CODE)
    }

    fun parseBalanceReadResponse(
        response: ByteArray,
        expectedIdm: ByteArray,
        balanceBasis: OctopusBalanceBasis,
    ): BalanceData {
        val block = try {
            FelicaProtocol.parseReadResponse(response, expectedIdm)
        } catch (error: FelicaProtocolException) {
            throw OctopusProtocolException(error.message ?: "The FeliCa response could not be decoded.")
        }
        val rawBalance = (block[0].unsigned.toLong() shl 24) or
            (block[1].unsigned.toLong() shl 16) or
            (block[2].unsigned.toLong() shl 8) or
            block[3].unsigned.toLong()
        if (rawBalance > 100_000L) {
            throw OctopusProtocolException("The Octopus balance record was outside the expected range.")
        }

        return BalanceData(
            rawBalance = rawBalance,
            estimatedBalanceHkd = decodeEstimatedBalance(rawBalance, balanceBasis),
            rawBlock = block,
        )
    }

    fun decodeEstimatedBalance(
        rawBalance: Long,
        balanceBasis: OctopusBalanceBasis,
    ): Double = (rawBalance - balanceBasis.rawOffsetTenths) / 10.0

    private val Byte.unsigned: Int
        get() = toInt() and 0xFF
}

internal data class BalanceData(
    val rawBalance: Long,
    val estimatedBalanceHkd: Double,
    val rawBlock: ByteArray,
)

internal data class OctopusPollingData(
    val idm: ByteArray,
    val manufacturerParameters: ByteArray,
    val systemCode: ByteArray,
)

internal class OctopusProtocolException(message: String) : Exception(message)

internal fun ByteArray.toUpperHex(separator: String = ""): String =
    joinToString(separator) { byte -> "%02X".format(byte.toInt() and 0xFF) }
