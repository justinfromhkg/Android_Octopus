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
                "EZ-Link / CEPAS",
                "Suica / PASMO / ICOCA",
                "Yangchengtong",
                "Shenzhentong",
                "China T-Union",
                "Clipper",
                "Macau Pass",
                "Touch ’n Go",
            ),
            TransitCardProfile.entries.map(TransitCardProfile::displayName),
        )
    }
}
