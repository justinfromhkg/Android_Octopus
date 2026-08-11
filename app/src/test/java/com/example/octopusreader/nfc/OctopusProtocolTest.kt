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
    fun `polling command explicitly selects Octopus system 8008`() {
        assertArrayEquals(
            byteArrayOf(0x06, 0x00, 0x80.toByte(), 0x08, 0x01, 0x00),
            OctopusProtocol.buildPollingCommand(),
        )
    }

    @Test
    fun `polling response returns Octopus specific identifier and manufacturer data`() {
        val octopusIdm = byteArrayOf(8, 7, 6, 5, 4, 3, 2, 1)
        val pmm = byteArrayOf(0x04, 0x3B, 3, 4, 5, 6, 7, 8)
        val response = byteArrayOf(
            0x14,
            0x01,
            *octopusIdm,
            *pmm,
            0x80.toByte(),
            0x08,
        )

        val result = OctopusProtocol.parsePollingResponse(response)

        assertArrayEquals(octopusIdm, result.idm)
        assertArrayEquals(pmm, result.manufacturerParameters)
        assertArrayEquals(OctopusProtocol.systemCode, result.systemCode)
    }

    @Test
    fun `system code discovery exposes both sides of a dual core card`() {
        assertArrayEquals(
            byteArrayOf(0x0A, 0x0C, *idm),
            OctopusProtocol.buildRequestSystemCodesCommand(idm),
        )
        val response = byteArrayOf(
            0x0F,
            0x0D,
            *idm,
            0x02,
            0x80.toByte(),
            0x05,
            0x80.toByte(),
            0x08,
        )

        val systems = OctopusProtocol.parseRequestSystemCodesResponse(response, idm)

        assertEquals(2, systems.size)
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x05), systems[0])
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x08), systems[1])
    }

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

        val data = OctopusProtocol.parseBalanceReadResponse(
            response,
            idm,
            OctopusBalanceBasis.NEW_OR_MOBILE,
        )

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

        OctopusProtocol.parseBalanceReadResponse(
            response,
            idm,
            OctopusBalanceBasis.NEW_OR_MOBILE,
        )
    }

    @Test
    fun `older physical cards use the 35 dollar convenience limit offset`() {
        assertEquals(
            138.4,
            OctopusProtocol.decodeEstimatedBalance(
                rawBalance = 1_734,
                balanceBasis = OctopusBalanceBasis.PHYSICAL_PRE_2017,
            ),
            0.001,
        )
    }

    @Test(expected = OctopusProtocolException::class)
    fun `unrelated out of range balance data is rejected`() {
        val response = byteArrayOf(
            0x1D,
            0x07,
            *idm,
            0x00,
            0x00,
            0x01,
            0x00,
            0x01,
            0x86.toByte(),
            0xA1.toByte(),
            *ByteArray(12),
        )

        OctopusProtocol.parseBalanceReadResponse(
            response,
            idm,
            OctopusBalanceBasis.PHYSICAL_PRE_2017,
        )
    }
}
