package com.ttsreader.ingest

import com.ttsreader.data.ArticleBlock
import com.ttsreader.data.ParsedArticle
import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object ArticleParser {

    private val BLOCK_TAGS = setOf(
        "h1", "h2", "h3", "h4", "h5", "h6",
        "p", "blockquote", "figure", "img", "pre", "table", "li",
    )

    fun parse(html: String, url: String?): ParsedArticle {
        val readability = Readability4J(url ?: "https://localhost/", html)
        val parsed = readability.parse()
        val title = parsed.title?.takeIf { it.isNotBlank() } ?: extractTitle(html) ?: "Untitled"

        val contentHtml = parsed.articleContent?.outerHtml() ?: html
        val doc = Jsoup.parse(contentHtml, url ?: "")
        val blocks = mutableListOf<ArticleBlock>()
        walk(doc.body() ?: doc, blocks)
        return ParsedArticle(title = title, sourceUrl = url, blocks = blocks)
    }

    private fun extractTitle(html: String): String? =
        Jsoup.parse(html).title().takeIf { it.isNotBlank() }

    private fun walk(root: Element, out: MutableList<ArticleBlock>) {
        for (el in root.children()) {
            when (el.tagName().lowercase()) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> addText(el, out, ::heading)
                "p", "li" -> addText(el, out, ::paragraph)
                "blockquote" -> addText(el, out, ::quote)
                "pre" -> {
                    val code = el.wholeText().trimEnd()
                    if (code.isNotBlank()) out.add(ArticleBlock.CodeBlock(code))
                }
                "img" -> addImage(el, null, out)
                "figure" -> addFigure(el, out)
                "table" -> addTable(el, out)
                else -> if (el.children().isNotEmpty()) walk(el, out)
            }
        }
    }

    private inline fun addText(
        el: Element,
        out: MutableList<ArticleBlock>,
        make: (String) -> ArticleBlock,
    ) {
        val text = el.text().trim()
        if (text.isNotEmpty()) out.add(make(text))
    }

    private fun heading(text: String) =
        ArticleBlock.Heading(text, SentenceSegmenter.segment(text))

    private fun paragraph(text: String) =
        ArticleBlock.Paragraph(text, SentenceSegmenter.segment(text))

    private fun quote(text: String) =
        ArticleBlock.Quote(text, SentenceSegmenter.segment(text))

    private fun addFigure(figure: Element, out: MutableList<ArticleBlock>) {
        val img = figure.selectFirst("img")
        val caption = figure.selectFirst("figcaption")?.text()?.trim()?.takeIf { it.isNotEmpty() }
        if (img != null) addImage(img, caption, out)
    }

    private fun addImage(img: Element, caption: String?, out: MutableList<ArticleBlock>) {
        val src = img.absUrl("src").ifBlank { img.attr("src") }
        if (src.isNotBlank()) out.add(ArticleBlock.Image(src, caption))
    }

    private fun addTable(table: Element, out: MutableList<ArticleBlock>) {
        val rows = table.select("tr").map { tr ->
            tr.select("th, td").map { it.text().trim() }
        }.filter { it.isNotEmpty() }
        if (rows.isNotEmpty()) out.add(ArticleBlock.Table(rows))
    }
}
