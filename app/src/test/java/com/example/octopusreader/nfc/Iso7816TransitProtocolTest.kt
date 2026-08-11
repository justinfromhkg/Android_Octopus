package com.example.octopusreader.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Iso7816TransitProtocolTest {
    @Test
    fun `CEPAS commands select file 4000 and purse 3`() {
        assertArrayEquals(
            byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x00, 0x02, 0x40, 0x00, 0x00),
            Iso7816TransitProtocol.selectFile(0x4000),
        )
        assertArrayEquals(
            byteArrayOf(0x90.toByte(), 0x32, 0x03, 0x00, 0x00),
            Iso7816TransitProtocol.getCepasPurse(),
        )
    }

    @Test
    fun `CEPAS purse decodes signed balance and card number`() {
        val purse = ByteArray(16).apply {
            this[2] = 0xFF.toByte()
            this[3] = 0xFB.toByte()
            this[4] = 0x2E
            byteArrayOf(0x10, 0x01, 0x23, 0x45, 0x67, 0x01, 0x23, 0x45)
                .copyInto(this, destinationOffset = 8)
        }

        assertEquals(-1_234L, Iso7816TransitProtocol.parseCepasBalanceCents(purse))
        assertEquals("1001234567012345", Iso7816TransitProtocol.parseCepasCardNumber(purse))
    }

    @Test
    fun `China transit command and balance use big endian cents`() {
        assertArrayEquals(
            byteArrayOf(0x80.toByte(), 0x5C, 0x00, 0x02, 0x04),
            Iso7816TransitProtocol.getChinaBalance(),
        )
        assertEquals(
            12_345L,
            Iso7816TransitProtocol.parseChinaBalanceCents(
                byteArrayOf(0x00, 0x00, 0x30, 0x39),
            ),
        )
    }

    @Test
    fun `China balance ignores the upper flag and decodes signed 31 bit cents`() {
        assertEquals(
            -1_234L,
            Iso7816TransitProtocol.parseChinaBalanceCents(
                byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFB.toByte(), 0x2E),
            ),
        )
    }

    @Test
    fun `T Union debt purse is used when positive purse is zero`() {
        assertEquals(
            -1_234L,
            Iso7816TransitProtocol.parseChinaTUnionBalanceCents(
                positivePurse = byteArrayOf(0x00, 0x00, 0x00, 0x00),
                debtPurse = byteArrayOf(0x00, 0x00, 0x04, 0xD2.toByte()),
            ),
        )
        assertEquals(
            12_345L,
            Iso7816TransitProtocol.parseChinaTUnionBalanceCents(
                positivePurse = byteArrayOf(0x00, 0x00, 0x30, 0x39),
                debtPurse = byteArrayOf(0x00, 0x00, 0x04, 0xD2.toByte()),
            ),
        )
    }

    @Test(expected = Iso7816ProtocolException::class)
    fun `non-success status is rejected`() {
        Iso7816TransitProtocol.unwrap(byteArrayOf(0x6A, 0x82.toByte()))
    }
}
