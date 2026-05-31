package com.ttsreader.ingest

import com.ttsreader.data.Sentence
import java.text.BreakIterator
import java.util.Locale

object SentenceSegmenter {

    fun segment(text: String, locale: Locale = Locale.getDefault()): List<Sentence> {
        if (text.isBlank()) return emptyList()
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(text)
        val sentences = mutableListOf<Sentence>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val raw = text.substring(start, end)
            val trimmed = raw.trim()
            if (trimmed.isNotEmpty()) {
                val leading = raw.indexOf(trimmed.first())
                val absStart = start + leading
                val absEnd = absStart + trimmed.length
                sentences.add(Sentence(trimmed, absStart, absEnd))
            }
            start = end
            end = iterator.next()
        }
        return sentences
    }
}
