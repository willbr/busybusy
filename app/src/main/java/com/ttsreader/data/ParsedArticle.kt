package com.ttsreader.data

data class ParsedArticle(
    val title: String,
    val sourceUrl: String?,
    val blocks: List<ArticleBlock>,
)
