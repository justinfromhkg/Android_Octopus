package com.example.octopusreader.ui

enum class AppLanguage(val languageTag: String) {
    ENGLISH("en"),
    TRADITIONAL_CHINESE("zh-Hant"),
    SIMPLIFIED_CHINESE("zh-Hans"),
    JAPANESE("ja"),
    KOREAN("ko"),
    MALAY("ms"),
    ;

    companion object {
        fun fromLanguageTag(languageTag: String?): AppLanguage {
            val normalized = languageTag.orEmpty().lowercase()
            return when {
                normalized.startsWith("zh-hant") ||
                    normalized.startsWith("zh-tw") ||
                    normalized.startsWith("zh-hk") -> TRADITIONAL_CHINESE

                normalized.startsWith("zh-hans") ||
                    normalized.startsWith("zh-cn") ||
                    normalized.startsWith("zh-sg") -> SIMPLIFIED_CHINESE

                normalized.startsWith("ja") -> JAPANESE
                normalized.startsWith("ko") -> KOREAN
                normalized.startsWith("ms") -> MALAY
                else -> ENGLISH
            }
        }
    }
}
