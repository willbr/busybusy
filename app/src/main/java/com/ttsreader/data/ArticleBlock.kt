package com.ttsreader.data

/** A single sentence within a spoken block, with offsets into the block's text. */
data class Sentence(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
)

/**
 * One unit of article content in document order.
 * Spoken blocks (Heading, Paragraph, Quote) carry sentences for future TTS.
 * Visual blocks (Image, Table, CodeBlock) are displayed only.
 */
sealed interface ArticleBlock {
    data class Heading(val text: String, val sentences: List<Sentence>) : ArticleBlock
    data class Paragraph(val text: String, val sentences: List<Sentence>) : ArticleBlock
    data class Quote(val text: String, val sentences: List<Sentence>) : ArticleBlock
    data class Image(val url: String, val caption: String?) : ArticleBlock
    data class Table(val rows: List<List<String>>) : ArticleBlock
    data class CodeBlock(val text: String) : ArticleBlock
}
