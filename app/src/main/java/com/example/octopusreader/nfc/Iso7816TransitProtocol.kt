package com.example.octopusreader.nfc

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

    private fun parseChinaUnsignedBalanceCents(data: ByteArray): Long {
        require(data.size >= 4) { "The China transit balance response is too short." }
        val raw = ((data[0].toLong() and 0xFF) shl 24) or
            ((data[1].toLong() and 0xFF) shl 16) or
            ((data[2].toLong() and 0xFF) shl 8) or
            (data[3].toLong() and 0xFF)
        return raw and 0x7FFFFFFF
    }
}

internal class Iso7816ProtocolException(message: String) : Exception(message)
