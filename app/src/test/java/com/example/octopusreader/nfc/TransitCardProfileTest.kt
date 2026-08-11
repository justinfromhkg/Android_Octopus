package com.example.octopusreader.nfc

import org.junit.Assert.assertEquals
import org.junit.Test

class TransitCardProfileTest {
    @Test
    fun `all requested transit systems are available`() {
        assertEquals(
            listOf(
                "Octopus",
                "EasyCard",
                "iPASS",
                "EZ-Link",
                "T-money",
                "Suica",
                "PASMO",
                "ICOCA",
                "Yangchengtong",
                "Shenzhentong",
                "China T-Union",
                "Clipper",
                "Macau Pass",
                "Touch ’n Go",
                "Oyster",
            ),
            TransitCardProfile.entries.map(TransitCardProfile::displayName),
        )
    }

    @Test
    fun `native card names are included where the brand has one`() {
        assertEquals("八達通", TransitCardProfile.OCTOPUS.localName)
        assertEquals("悠遊卡", TransitCardProfile.EASYCARD.localName)
        assertEquals("一卡通", TransitCardProfile.IPASS.localName)
        assertEquals("スイカ", TransitCardProfile.SUICA.localName)
        assertEquals("羊城通", TransitCardProfile.YANGCHENGTONG.localName)
        assertEquals("澳門通", TransitCardProfile.MACAU_PASS.localName)
        assertEquals("티머니", TransitCardProfile.T_MONEY.localName)
    }
}
