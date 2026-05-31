package com.ttsreader.ingest

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
    fun `every sentence's offsets round-trip to its text`() {
        val src = "Alpha beta. Gamma delta epsilon. Zeta."
        val out = SentenceSegmenter.segment(src)
        assertEquals(3, out.size)
        for (s in out) {
            assertEquals(s.text, src.substring(s.startOffset, s.endOffset))
        }
    }

    /**
     * Documents a known limitation: java.text.BreakIterator (the JVM sentence
     * iterator used in host unit tests) has no abbreviation awareness, so it
     * splits "Dr." into its own sentence. Abbreviation-aware segmentation is
     * deferred to Phase 2 speech normalization (per the design spec). This test
     * pins the CURRENT behavior so a future Phase 2 change is a deliberate,
     * visible update rather than a silent regression.
     */
    @Test
    fun `BreakIterator splits on abbreviation period (Phase 2 will refine)`() {
        val out = SentenceSegmenter.segment("Dr. Smith arrived. He was late.")
        assertEquals(3, out.size)
        assertEquals("Dr.", out[0].text)
        assertEquals("Smith arrived.", out[1].text)
        assertEquals("He was late.", out[2].text)
    }

    @Test
    fun `blank input yields no sentences`() {
        assertEquals(0, SentenceSegmenter.segment("   ").size)
    }
}
