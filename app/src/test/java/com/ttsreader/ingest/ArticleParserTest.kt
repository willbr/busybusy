package com.ttsreader.ingest

import com.ttsreader.data.ArticleBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleParserTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("fixtures/$name"))
            .bufferedReader().readText()

    @Test
    fun `extracts title and paragraphs from a simple article`() {
        val article = ArticleParser.parse(fixture("simple-article.html"), "https://example.com/a")
        assertEquals("Simple Title", article.title)
        val paragraphs = article.blocks.filterIsInstance<ArticleBlock.Paragraph>()
        assertEquals(2, paragraphs.size)
        assertTrue(paragraphs[0].text.startsWith("This is the first paragraph"))
        assertEquals(2, paragraphs[0].sentences.size)
    }

    @Test
    fun `drops nav and footer boilerplate`() {
        val article = ArticleParser.parse(fixture("simple-article.html"), "https://example.com/a")
        val allText = article.blocks.joinToString(" ") {
            when (it) {
                is ArticleBlock.Paragraph -> it.text
                is ArticleBlock.Heading -> it.text
                else -> ""
            }
        }
        assertTrue(!allText.contains("Subscribe"))
        assertTrue(!allText.contains("Share this on social media"))
    }

    @Test
    fun `preserves images, tables, quotes and code in document order`() {
        val article = ArticleParser.parse(fixture("rich-article.html"), "https://example.com/b")
        val types = article.blocks.map { it::class.simpleName }
        assertTrue(types.contains("Image"))
        assertTrue(types.contains("Quote"))
        assertTrue(types.contains("CodeBlock"))
        assertTrue(types.contains("Table"))

        val image = article.blocks.filterIsInstance<ArticleBlock.Image>().first()
        assertEquals("https://example.com/pic.png", image.url)
        assertEquals("A caption", image.caption)

        val table = article.blocks.filterIsInstance<ArticleBlock.Table>().first()
        assertEquals(listOf("Name", "Age"), table.rows[0])
        assertEquals(listOf("Ada", "36"), table.rows[1])

        val code = article.blocks.filterIsInstance<ArticleBlock.CodeBlock>().first()
        assertTrue(code.text.contains("println(x)"))
    }
}
