package com.ttsreader.ingest

import com.ttsreader.data.ArticleBlock
import com.ttsreader.data.ParsedArticle

sealed interface ExtractResult {
    data class Success(val article: ParsedArticle) : ExtractResult
    /** Extraction failed; rawInput is preserved so the UI can offer to read it as-is. */
    data class Failure(val rawInput: String, val reason: String) : ExtractResult
}

class ArticleExtractor(
    private val fetcher: HtmlFetcher,
) {
    fun extract(raw: String?): ExtractResult {
        return when (val input = ShareInput.classify(raw)) {
            is ShareInput.Empty -> ExtractResult.Failure(raw.orEmpty(), "Nothing was shared.")
            is ShareInput.Text -> ExtractResult.Success(textArticle(input.text))
            is ShareInput.Url -> extractUrl(input.url)
        }
    }

    private fun extractUrl(url: String): ExtractResult {
        return try {
            val html = fetcher.fetch(url)
            ExtractResult.Success(ArticleParser.parse(html, url))
        } catch (e: Exception) {
            ExtractResult.Failure(url, e.message ?: "Could not fetch the page.")
        }
    }

    private fun textArticle(text: String): ParsedArticle {
        val paragraph = ArticleBlock.Paragraph(text, SentenceSegmenter.segment(text))
        val title = text.take(60).substringBefore('.').trim().ifBlank { "Shared text" }
        return ParsedArticle(title = title, sourceUrl = null, blocks = listOf(paragraph))
    }
}
