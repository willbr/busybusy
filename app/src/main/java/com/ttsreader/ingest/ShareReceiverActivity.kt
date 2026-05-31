package com.ttsreader.ingest

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.ttsreader.CurrentArticle
import com.ttsreader.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Transparent activity registered for SEND intents; extracts then launches the reader. */
class ShareReceiverActivity : ComponentActivity() {

    private val extractor by lazy { ArticleExtractor(OkHttpHtmlFetcher()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)

        // lifecycleScope auto-cancels if the activity is destroyed mid-fetch,
        // avoiding an orphaned coroutine touching a dead activity.
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { extractor.extract(shared) }
            when (result) {
                is ExtractResult.Success -> {
                    CurrentArticle.value = result.article
                    CurrentArticle.failure = null
                }
                is ExtractResult.Failure -> {
                    CurrentArticle.value = null
                    CurrentArticle.failure = result
                }
            }
            startActivity(
                Intent(this@ShareReceiverActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
            finish()
        }
    }
}
