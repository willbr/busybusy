package com.ttsreader.ingest

sealed interface ShareInput {
    data class Url(val url: String) : ShareInput
    data class Text(val text: String) : ShareInput
    data object Empty : ShareInput

    companion object {
        fun classify(raw: String?): ShareInput {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return Empty
            val isSingleToken = trimmed.none { it.isWhitespace() }
            val looksLikeUrl = trimmed.startsWith("http://") || trimmed.startsWith("https://")
            return if (isSingleToken && looksLikeUrl) Url(trimmed) else Text(trimmed)
        }
    }
}
