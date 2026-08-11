package com.example.octopusreader.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FelicaProtocolTest {
    private val idm = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

    @Test
    fun `Japanese IC polling explicitly selects shared system 0003`() {
        assertArrayEquals(
            byteArrayOf(0x06, 0x00, 0x00, 0x03, 0x01, 0x00),
            FelicaProtocol.buildPollingCommand(JapaneseIcProtocol.SYSTEM_CODE),
        )
        val pmm = byteArrayOf(8, 7, 6, 5, 4, 3, 2, 1)
        val polling = FelicaProtocol.parsePollingResponse(
            response = byteArrayOf(
                0x14,
                0x01,
                *idm,
                *pmm,
                0x00,
                0x03,
            ),
            expectedSystemCode = JapaneseIcProtocol.SYSTEM_CODE,
        )

        assertArrayEquals(idm, polling.idm)
        assertArrayEquals(pmm, polling.manufacturerParameters)
        assertEquals(JapaneseIcProtocol.SYSTEM_CODE, polling.systemCode)
    }

    @Test
    fun `Japanese IC command uses history service 090F`() {
        val command = FelicaProtocol.buildReadCommand(
            idm = idm,
            serviceCode = JapaneseIcProtocol.HISTORY_SERVICE_CODE,
        )

        assertArrayEquals(
            byteArrayOf(
                0x10,
                0x06,
                *idm,
                0x01,
                0x0F,
                0x09,
                0x01,
                0x80.toByte(),
                0x00,
            ),
            command,
        )
    }

    @Test
    fun `Japanese IC balance is little endian yen`() {
        val block = ByteArray(16).apply {
            this[10] = 0x34
            this[11] = 0x12
        }

        assertEquals(4_660L, JapaneseIcProtocol.decodeLatestBalanceYen(block))
    }

    @Test(expected = FelicaProtocolException::class)
    fun `FeliCa response with another identifier is rejected`() {
        val response = byteArrayOf(
            0x1D,
            0x07,
            *byteArrayOf(8, 7, 6, 5, 4, 3, 2, 1),
            0x00,
            0x00,
            0x01,
            *ByteArray(16),
        )

        FelicaProtocol.parseReadResponse(response, idm)
    }
}
