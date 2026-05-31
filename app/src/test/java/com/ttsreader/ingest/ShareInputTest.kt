package com.ttsreader.ingest

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareInputTest {

    @Test
    fun `bare http url is classified as url`() {
        assertEquals(
            ShareInput.Url("https://example.com/a"),
            ShareInput.classify("https://example.com/a"),
        )
    }

    @Test
    fun `url with surrounding whitespace is trimmed`() {
        assertEquals(
            ShareInput.Url("https://example.com/a"),
            ShareInput.classify("  https://example.com/a \n"),
        )
    }

    @Test
    fun `text containing a url but also prose is treated as text`() {
        val input = "Check this out https://example.com/a it is great"
        assertEquals(ShareInput.Text(input), ShareInput.classify(input))
    }

    @Test
    fun `plain text is classified as text`() {
        assertEquals(ShareInput.Text("just some words"), ShareInput.classify("just some words"))
    }

    @Test
    fun `blank input is Empty`() {
        assertEquals(ShareInput.Empty, ShareInput.classify("   "))
    }
}
