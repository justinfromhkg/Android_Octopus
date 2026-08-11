package com.example.octopusreader.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TMoneyProtocolTest {
    @Test
    fun `T-money application and balance commands use public KS X 6924 values`() {
        assertArrayEquals(
            byteArrayOf(
                0x00,
                0xA4.toByte(),
                0x04,
                0x00,
                0x07,
                0xD4.toByte(),
                0x10,
                0x00,
                0x00,
                0x03,
                0x00,
                0x01,
                0x00,
            ),
            Iso7816TransitProtocol.selectApplication(TMoneyProtocol.applicationId),
        )
        assertArrayEquals(
            byteArrayOf(0x90.toByte(), 0x4C, 0x00, 0x00, 0x04),
            TMoneyProtocol.getBalanceCommand(),
        )
    }

    @Test
    fun `T-money balance is signed big endian won`() {
        assertEquals(
            12_345L,
            TMoneyProtocol.parseBalanceWon(byteArrayOf(0x00, 0x00, 0x30, 0x39)),
        )
        assertEquals(
            -1_000L,
            TMoneyProtocol.parseBalanceWon(
                byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFC.toByte(), 0x18),
            ),
        )
    }

    @Test
    fun `T-money purse information is decoded from nested B0 TLV`() {
        val purse = ByteArray(37).apply {
            this[0] = 0x01
            this[3] = 0x12
            byteArrayOf(0x10, 0x01, 0x23, 0x45, 0x67, 0x01, 0x23, 0x45)
                .copyInto(this, destinationOffset = 4)
            byteArrayOf(0x20, 0x24, 0x08, 0x11).copyInto(this, destinationOffset = 17)
            byteArrayOf(0x20, 0x34, 0x12, 0x31).copyInto(this, destinationOffset = 21)
            byteArrayOf(0x00, 0x07, 0xA1.toByte(), 0x20).copyInto(this, destinationOffset = 27)
        }
        val fci = byteArrayOf(0x6F, 0x27, 0xB0.toByte(), 0x25, *purse)

        val info = TMoneyProtocol.parseCardInfo(fci)!!

        assertEquals(1, info.cardTypeCode)
        assertEquals("12", info.issuerCode)
        assertEquals("1001234567012345", info.serialNumber)
        assertEquals(LocalDate.of(2024, 8, 11), info.issueDate)
        assertEquals(LocalDate.of(2034, 12, 31), info.validUntil)
        assertEquals(500_000L, info.maximumBalanceWon)
    }

    @Test
    fun `T-money transit record exposes fare remaining balance time and terminal`() {
        val record = ByteArray(46).apply {
            this[0] = 0x01
            byteArrayOf(0x00, 0x00, 0x30, 0x39).copyInto(this, destinationOffset = 2)
            byteArrayOf(0x00, 0x00, 0x00, 0x12).copyInto(this, destinationOffset = 6)
            byteArrayOf(0x00, 0x00, 0x04, 0xD2.toByte()).copyInto(this, destinationOffset = 10)
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8).copyInto(this, destinationOffset = 14)
            byteArrayOf(0x20, 0x26, 0x08, 0x11, 0x14, 0x30, 0x45)
                .copyInto(this, destinationOffset = 26)
        }

        val transaction = TMoneyProtocol.parseTransaction(record)!!

        assertEquals(TransitTransactionType.TRANSIT_RIDE, transaction.type)
        assertEquals(-1_234L, transaction.amountMinor)
        assertEquals(12_345L, transaction.balanceAfterMinor)
        assertEquals("KRW", transaction.currencyCode)
        assertEquals(0, transaction.fractionDigits)
        assertEquals(18, transaction.sequenceCounter)
        assertEquals("0102030405060708", transaction.terminalCode)
        assertEquals(LocalDateTime.of(2026, 8, 11, 14, 30, 45), transaction.timestamp)
    }

    @Test
    fun `T-money top-up amount is positive`() {
        val record = ByteArray(46).apply {
            this[0] = 0x02
            byteArrayOf(0x00, 0x00, 0x27, 0x10).copyInto(this, destinationOffset = 10)
            byteArrayOf(0x20, 0x26, 0x08, 0x11, 0x14, 0x30, 0x45)
                .copyInto(this, destinationOffset = 26)
        }

        val transaction = TMoneyProtocol.parseTransaction(record)!!

        assertEquals(TransitTransactionType.TOP_UP, transaction.type)
        assertEquals(10_000L, transaction.amountMinor)
    }
}
