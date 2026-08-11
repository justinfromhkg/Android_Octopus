package com.example.octopusreader.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class OctopusProtocolTest {
    private val idm = byteArrayOf(
        0x01,
        0x02,
        0x03,
        0x04,
        0x05,
        0x06,
        0x07,
        0x08,
    )

    @Test
    fun `balance command uses Octopus service 0117 and block zero`() {
        val command = OctopusProtocol.buildBalanceReadCommand(idm)

        assertArrayEquals(
            byteArrayOf(
                0x10,
                0x06,
                0x01,
                0x02,
                0x03,
                0x04,
                0x05,
                0x06,
                0x07,
                0x08,
                0x01,
                0x17,
                0x01,
                0x01,
                0x80.toByte(),
                0x00,
            ),
            command,
        )
    }

    @Test
    fun `successful response decodes estimated HKD balance`() {
        val response = byteArrayOf(
            0x1D,
            0x07,
            *idm,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x06,
            0xC6.toByte(),
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
        )

        val data = OctopusProtocol.parseBalanceReadResponse(response, idm)

        assertEquals(1_734L, data.rawBalance)
        assertEquals(123.4, data.estimatedBalanceHkd, 0.001)
    }

    @Test(expected = OctopusProtocolException::class)
    fun `failed Felica status is rejected`() {
        val response = byteArrayOf(
            0x1D,
            0x07,
            *idm,
            0x01,
            0xA2.toByte(),
            0x01,
            *ByteArray(16),
        )

        OctopusProtocol.parseBalanceReadResponse(response, idm)
    }
}
