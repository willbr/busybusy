package com.ttsreader.ingest

import com.ttsreader.data.ArticleBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleExtractorTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("fixtures/$name"))
            .bufferedReader().readText()

    @Test
    fun `url input is fetched then parsed`() {
        val html = fixture("simple-article.html")
        val fetcher = HtmlFetcher { reqUrl ->
            assertEquals("https://example.com/a", reqUrl)
            html
        }
        val extractor = ArticleExtractor(fetcher)
        val result = extractor.extract("https://example.com/a")
        assertTrue(result is ExtractResult.Success)
        result as ExtractResult.Success
        assertEquals("Simple Title", result.article.title)
    }

    @Test
    fun `plain text input becomes a single paragraph article without fetching`() {
        val fetcher = HtmlFetcher { error("should not fetch for text input") }
        val extractor = ArticleExtractor(fetcher)
        val result = extractor.extract("Just some shared prose. Two sentences here.")
        assertTrue(result is ExtractResult.Success)
        result as ExtractResult.Success
        val paragraphs = result.article.blocks.filterIsInstance<ArticleBlock.Paragraph>()
        assertEquals(1, paragraphs.size)
        assertEquals(2, paragraphs[0].sentences.size)
    }

    @Test
    fun `fetch failure returns Failure carrying the raw input`() {
        val fetcher = HtmlFetcher { throw RuntimeException("no network") }
        val extractor = ArticleExtractor(fetcher)
        val result = extractor.extract("https://example.com/a")
        assertTrue(result is ExtractResult.Failure)
        result as ExtractResult.Failure
        assertEquals("https://example.com/a", result.rawInput)
    }

    @Test
    fun `empty input returns Failure`() {
        val extractor = ArticleExtractor { "" }
        assertTrue(extractor.extract("   ") is ExtractResult.Failure)
    }
}
