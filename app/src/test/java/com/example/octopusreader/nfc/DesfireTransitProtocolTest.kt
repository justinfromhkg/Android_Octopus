package com.example.octopusreader.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DesfireTransitProtocolTest {
    @Test
    fun `Clipper application and balance-file commands have expected bytes`() {
        assertArrayEquals(
            byteArrayOf(
                0x90.toByte(),
                0x5A,
                0x00,
                0x00,
                0x03,
                0x90.toByte(),
                0x11,
                0xF2.toByte(),
                0x00,
            ),
            DesfireTransitProtocol.selectApplication(DesfireTransitProtocol.CLIPPER_APP_ID),
        )
        assertArrayEquals(
            byteArrayOf(
                0x90.toByte(),
                0xBD.toByte(),
                0x00,
                0x00,
                0x07,
                0x02,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
            ),
            DesfireTransitProtocol.readData(0x02),
        )
    }

    @Test
    fun `Clipper balance is signed big endian cents`() {
        val positive = ByteArray(20).apply {
            this[18] = 0x04
            this[19] = 0xD2.toByte()
        }
        val negative = ByteArray(20).apply {
            this[18] = 0xFB.toByte()
            this[19] = 0x2E
        }

        assertEquals(1_234L, DesfireTransitProtocol.parseClipperBalanceCents(positive))
        assertEquals(-1_234L, DesfireTransitProtocol.parseClipperBalanceCents(negative))
    }

    @Test
    fun `DESFire frame separates payload and continuation status`() {
        val frame = DesfireTransitProtocol.parseFrame(
            byteArrayOf(0x01, 0x02, 0x91.toByte(), 0xAF.toByte()),
        )

        assertArrayEquals(byteArrayOf(0x01, 0x02), frame.data)
        assertEquals(0xAF, frame.status)
    }
}
