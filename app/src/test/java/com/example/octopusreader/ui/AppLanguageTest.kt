package com.example.octopusreader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `supported language tags resolve to the correct app language`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en-US"))
        assertEquals(
            AppLanguage.TRADITIONAL_CHINESE,
            AppLanguage.fromLanguageTag("zh-Hant-HK"),
        )
        assertEquals(
            AppLanguage.SIMPLIFIED_CHINESE,
            AppLanguage.fromLanguageTag("zh-CN"),
        )
        assertEquals(AppLanguage.JAPANESE, AppLanguage.fromLanguageTag("ja-JP"))
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromLanguageTag("ko-KR"))
        assertEquals(AppLanguage.MALAY, AppLanguage.fromLanguageTag("ms-MY"))
    }

    @Test
    fun `missing or unsupported language tags fall back to English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag(null))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("fr-FR"))
    }
}
