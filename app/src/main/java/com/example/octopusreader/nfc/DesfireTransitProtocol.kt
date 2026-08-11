package com.example.octopusreader.nfc

internal object DesfireTransitProtocol {
    const val CLIPPER_APP_ID = 0x9011F2

    fun selectApplication(appId: Int): ByteArray = wrap(
        command = 0x5A,
        parameters = byteArrayOf(
            (appId shr 16).toByte(),
            (appId shr 8).toByte(),
            appId.toByte(),
        ),
    )

    fun readData(fileNumber: Int): ByteArray = wrap(
        command = 0xBD,
        parameters = byteArrayOf(
            fileNumber.toByte(),
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
        ),
    )

    fun additionalFrame(): ByteArray = wrap(0xAF, byteArrayOf())

    fun wrap(command: Int, parameters: ByteArray): ByteArray =
        if (parameters.isEmpty()) {
            byteArrayOf(0x90.toByte(), command.toByte(), 0x00, 0x00, 0x00)
        } else {
            byteArrayOf(0x90.toByte(), command.toByte(), 0x00, 0x00, parameters.size.toByte()) +
                parameters + 0x00.toByte()
        }

    fun parseFrame(response: ByteArray): DesfireFrame {
        if (response.size < 2 || response[response.lastIndex - 1] != 0x91.toByte()) {
            throw DesfireProtocolException("The card returned an invalid DESFire response.")
        }
        return DesfireFrame(
            data = response.copyOfRange(0, response.size - 2),
            status = response.last().toInt() and 0xFF,
        )
    }

    fun parseClipperBalanceCents(fileData: ByteArray): Long {
        require(fileData.size >= 20) { "The Clipper balance file is too short." }
        val signed = (((fileData[18].toInt() and 0xFF) shl 8) or
            (fileData[19].toInt() and 0xFF)).toShort()
        return signed.toLong()
    }
}

internal data class DesfireFrame(val data: ByteArray, val status: Int)

internal class DesfireProtocolException(message: String) : Exception(message)
