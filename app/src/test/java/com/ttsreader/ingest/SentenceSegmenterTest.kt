package com.ttsreader.ingest

import com.ttsreader.data.Sentence
import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceSegmenterTest {

    @Test
    fun `splits two simple sentences`() {
        val out = SentenceSegmenter.segment("Hello world. Goodbye world.")
        assertEquals(2, out.size)
        assertEquals("Hello world.", out[0].text)
        assertEquals("Goodbye world.", out[1].text)
    }

    @Test
    fun `offsets index back into the source string`() {
        val src = "First. Second."
        val out = SentenceSegmenter.segment(src)
        val first = out[0]
        assertEquals("First.", src.substring(first.startOffset, first.endOffset))
    }

    @Test
    fun `does not split on common abbreviation Dr`() {
        val out = SentenceSegmenter.segment("Dr. Smith arrived. He was late.")
        assertEquals(2, out.size)
        assertEquals("Dr. Smith arrived.", out[0].text)
    }

    @Test
    fun `blank input yields no sentences`() {
        assertEquals(0, SentenceSegmenter.segment("   ").size)
    }
}
