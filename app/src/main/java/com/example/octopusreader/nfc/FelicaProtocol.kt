package com.example.octopusreader.nfc

internal object FelicaProtocol {
    private const val POLLING = 0x00
    private const val POLLING_RESPONSE = 0x01
    private const val READ_WITHOUT_ENCRYPTION = 0x06
    private const val READ_WITHOUT_ENCRYPTION_RESPONSE = 0x07
    private const val BLOCK_SIZE = 16

    fun buildPollingCommand(systemCode: Int): ByteArray {
        require(systemCode in 0..0xFFFF) { "A FeliCa system code must contain 2 bytes." }
        return byteArrayOf(
            0x06,
            POLLING.toByte(),
            (systemCode shr 8).toByte(),
            systemCode.toByte(),
            0x01,
            0x00,
        )
    }

    fun parsePollingResponse(
        response: ByteArray,
        expectedSystemCode: Int,
    ): FelicaPollingData {
        if (response.size < 18 || response[0].unsigned != response.size) {
            throw FelicaProtocolException("The card returned an invalid polling response.")
        }
        if (response[1].unsigned != POLLING_RESPONSE) {
            throw FelicaProtocolException("The card returned an unexpected polling response.")
        }
        val returnedSystemCode = if (response.size >= 20) {
            (response[18].unsigned shl 8) or response[19].unsigned
        } else {
            expectedSystemCode
        }
        if (returnedSystemCode != expectedSystemCode) {
            throw FelicaProtocolException("The requested FeliCa system was not selected.")
        }
        return FelicaPollingData(
            idm = response.copyOfRange(2, 10),
            manufacturerParameters = response.copyOfRange(10, 18),
            systemCode = returnedSystemCode,
        )
    }

    fun buildReadCommand(idm: ByteArray, serviceCode: Int, blockNumber: Int = 0): ByteArray {
        require(idm.size == 8) { "A FeliCa IDm must contain 8 bytes." }
        require(serviceCode in 0..0xFFFF) { "The service code must contain 2 bytes." }
        require(blockNumber in 0..0xFF) { "The block number must contain 1 byte." }

        return byteArrayOf(
            0x10,
            READ_WITHOUT_ENCRYPTION.toByte(),
            *idm,
            0x01,
            (serviceCode and 0xFF).toByte(),
            ((serviceCode shr 8) and 0xFF).toByte(),
            0x01,
            0x80.toByte(),
            blockNumber.toByte(),
        )
    }

    fun parseReadResponse(response: ByteArray, expectedIdm: ByteArray): ByteArray {
        if (response.size < 13 + BLOCK_SIZE) {
            throw FelicaProtocolException("The card returned a response that was too short.")
        }

        if (response[0].unsigned != response.size) {
            throw FelicaProtocolException("The card returned an invalid response length.")
        }

        if (response[1].unsigned != READ_WITHOUT_ENCRYPTION_RESPONSE) {
            throw FelicaProtocolException("The card returned an unexpected FeliCa response.")
        }

        if (!response.copyOfRange(2, 10).contentEquals(expectedIdm)) {
            throw FelicaProtocolException("The card identifier changed during the scan.")
        }

        val statusFlag1 = response[10].unsigned
        val statusFlag2 = response[11].unsigned
        if (statusFlag1 != 0 || statusFlag2 != 0) {
            throw FelicaProtocolException(
                "The card declined the read (status %02X%02X).".format(statusFlag1, statusFlag2),
            )
        }

        val blockCount = response[12].unsigned
        val expectedLength = 13 + blockCount * BLOCK_SIZE
        if (blockCount < 1 || response.size < expectedLength) {
            throw FelicaProtocolException("The card did not return the requested block.")
        }

        return response.copyOfRange(13, 13 + BLOCK_SIZE)
    }

    private val Byte.unsigned: Int
        get() = toInt() and 0xFF
}

internal data class FelicaPollingData(
    val idm: ByteArray,
    val manufacturerParameters: ByteArray,
    val systemCode: Int,
)

internal class FelicaProtocolException(message: String) : Exception(message)

internal object JapaneseIcProtocol {
    const val SYSTEM_CODE = 0x0003
    const val HISTORY_SERVICE_CODE = 0x090F

    fun decodeLatestBalanceYen(historyBlock: ByteArray): Long {
        require(historyBlock.size >= 12) { "A Japanese IC history block must contain 16 bytes." }
        return (historyBlock[10].toInt() and 0xFF).toLong() or
            ((historyBlock[11].toInt() and 0xFF).toLong() shl 8)
    }
}
