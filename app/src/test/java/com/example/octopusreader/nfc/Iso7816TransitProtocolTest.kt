package com.example.octopusreader.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

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

    @Test
    fun `T Union public files use SFI 21 and 24 read commands`() {
        assertArrayEquals(
            byteArrayOf(0x00, 0xB0.toByte(), 0x95.toByte(), 0x00, 0x00),
            Iso7816TransitProtocol.readBinaryBySfi(0x15),
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0xB2.toByte(), 0x01, 0xC4.toByte(), 0x00),
            Iso7816TransitProtocol.readRecordBySfi(0x18, 1),
        )
    }

    @Test
    fun `wrong length status updates the APDU expected length`() {
        assertArrayEquals(
            byteArrayOf(0x00, 0xB2.toByte(), 0x01, 0xC4.toByte(), 0x17),
            Iso7816TransitProtocol.correctedLengthCommand(
                command = Iso7816TransitProtocol.readRecordBySfi(0x18, 1),
                response = byteArrayOf(0x6C, 0x17),
            )!!,
        )
        assertNull(
            Iso7816TransitProtocol.correctedLengthCommand(
                command = Iso7816TransitProtocol.readRecordBySfi(0x18, 1),
                response = byteArrayOf(0x90.toByte(), 0x00),
            ),
        )
    }

    @Test
    fun `T Union card information exposes serial version and validity`() {
        val data = ByteArray(30).apply {
            this[9] = 0x03
            byteArrayOf(
                0x12, 0x34, 0x56, 0x78, 0x90.toByte(),
                0x12, 0x34, 0x56, 0x78, 0x90.toByte(),
            ).copyInto(this, destinationOffset = 10)
            byteArrayOf(0x20, 0x24, 0x01, 0x31).copyInto(this, destinationOffset = 20)
            byteArrayOf(0x20, 0x34, 0x12, 0x31).copyInto(this, destinationOffset = 24)
        }

        val info = Iso7816TransitProtocol.parseTUnionCardInfo(data)

        assertEquals("2345678901234567890", info.serialNumber)
        assertEquals("23456789", info.issuerCode)
        assertEquals(3, info.applicationVersion)
        assertEquals(LocalDate.of(2024, 1, 31), info.validFrom)
        assertEquals(LocalDate.of(2034, 12, 31), info.validUntil)
    }

    @Test
    fun `T Union bus record decodes amount time and route code`() {
        val record = transactionRecord(
            transactionCode = 0x06,
            terminalCode = byteArrayOf(0x00, 0x00, 0x30, 0x12, 0x34, 0x56),
        )

        val transaction = Iso7816TransitProtocol.parseTUnionTransaction(record)!!

        assertEquals(TransitTransactionType.BUS, transaction.type)
        assertEquals(-10_000L, transaction.amountMinor)
        assertEquals(LocalDateTime.of(2026, 8, 11, 14, 30, 45), transaction.timestamp)
        assertEquals("000030123456", transaction.routeCode)
        assertEquals("000030123456", transaction.terminalCode)
        assertEquals(18, transaction.sequenceCounter)
    }

    @Test
    fun `T Union metro record exposes exit station and gate but not entry station`() {
        val record = transactionRecord(
            transactionCode = 0x06,
            terminalCode = byteArrayOf(0x00, 0x00, 0x60, 0xAB.toByte(), 0xCD.toByte(), 0x12),
        )

        val transaction = Iso7816TransitProtocol.parseTUnionTransaction(record)!!

        assertEquals(TransitTransactionType.METRO, transaction.type)
        assertNull(transaction.boardingStationCode)
        assertEquals("60ABCD00", transaction.alightingStationCode)
        assertEquals("12", transaction.gateCode)
    }

    @Test
    fun `T Union top up is positive`() {
        val record = transactionRecord(
            transactionCode = 0x02,
            terminalCode = byteArrayOf(0x00, 0x00, 0x30, 0x12, 0x34, 0x56),
        )

        val transaction = Iso7816TransitProtocol.parseTUnionTransaction(record)!!

        assertEquals(TransitTransactionType.TOP_UP, transaction.type)
        assertEquals(10_000L, transaction.amountMinor)
    }

    @Test(expected = Iso7816ProtocolException::class)
    fun `non-success status is rejected`() {
        Iso7816TransitProtocol.unwrap(byteArrayOf(0x6A, 0x82.toByte()))
    }

    private fun transactionRecord(
        transactionCode: Int,
        terminalCode: ByteArray,
    ): ByteArray = ByteArray(23).apply {
        this[1] = 0x12
        byteArrayOf(0x00, 0x00, 0x27, 0x10).copyInto(this, destinationOffset = 5)
        this[9] = transactionCode.toByte()
        terminalCode.copyInto(this, destinationOffset = 10)
        byteArrayOf(0x20, 0x26, 0x08, 0x11, 0x14, 0x30, 0x45)
            .copyInto(this, destinationOffset = 16)
    }
}
