package com.ttsreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttsreader.data.ParsedArticle
import com.ttsreader.ingest.ExtractResult
import com.ttsreader.ui.ReaderScreen
import com.ttsreader.ui.theme.TtsReaderTheme

/** Process-scoped handoff from ShareReceiverActivity to MainActivity (no persistence yet). */
object CurrentArticle {
    @Volatile var value: ParsedArticle? = null
    @Volatile var failure: ExtractResult.Failure? = null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TtsReaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    val article = CurrentArticle.value
                    val failure = CurrentArticle.failure
                    when {
                        article != null -> ReaderScreen(article, Modifier.padding(padding))
                        failure != null -> FailureView(failure, Modifier.padding(padding))
                        else -> Text(
                            "Share an article here to read it.",
                            modifier = Modifier.padding(padding).padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FailureView(failure: ExtractResult.Failure, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp)) {
        Text("Couldn't extract a readable article.", style = MaterialTheme.typography.titleMedium)
        Text(failure.reason, modifier = Modifier.padding(top = 8.dp))
    }
}
