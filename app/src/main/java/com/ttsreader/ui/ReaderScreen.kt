package com.ttsreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ttsreader.data.ArticleBlock
import com.ttsreader.data.ParsedArticle

@Composable
fun ReaderScreen(article: ParsedArticle, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item {
            Text(
                text = article.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        items(article.blocks) { block -> BlockView(block) }
    }
}

@Composable
private fun BlockView(block: ArticleBlock) {
    when (block) {
        is ArticleBlock.Heading -> Text(
            text = block.text,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )

        is ArticleBlock.Paragraph -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 6.dp),
        )

        is ArticleBlock.Quote -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
        )

        is ArticleBlock.Image -> Column(Modifier.padding(vertical = 8.dp)) {
            AsyncImage(
                model = block.url,
                contentDescription = block.caption,
                modifier = Modifier.fillMaxWidth(),
            )
            block.caption?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        is ArticleBlock.CodeBlock -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
        )

        is ArticleBlock.Table -> Column(
            Modifier
                .padding(vertical = 8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            block.rows.forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { cell ->
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(8.dp),
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
