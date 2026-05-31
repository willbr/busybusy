package com.ttsreader.ingest

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Fetches raw HTML for a URL. Abstracted so the extractor is unit-testable offline.
 *  `fun interface` enables SAM lambdas in tests, e.g. `HtmlFetcher { url -> "..." }`. */
fun interface HtmlFetcher {
    fun fetch(url: String): String
}

class OkHttpHtmlFetcher(
    private val client: OkHttpClient = defaultClient(),
) : HtmlFetcher {

    override fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            return response.body?.string() ?: error("Empty body for $url")
        }
    }

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
