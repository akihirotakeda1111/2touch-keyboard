package com.example.twotouchkeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversionSessionTest {

    @Test
    fun selectNextCandidate_cyclesThroughCandidates() {
        val session = ConversionSession()
        session.setCandidates(listOf("あ", "亜", "愛"))
        session.activate(composingLength = 2)

        assertEquals(0, session.getSelectedIndex())
        assertEquals("あ", session.getSelectedCandidate())

        session.selectNextCandidate()
        assertEquals(1, session.getSelectedIndex())
        assertEquals("亜", session.getSelectedCandidate())

        session.selectNextCandidate()
        assertEquals(2, session.getSelectedIndex())
        assertEquals("愛", session.getSelectedCandidate())

        session.selectNextCandidate()
        assertEquals(0, session.getSelectedIndex())
        assertEquals("あ", session.getSelectedCandidate())
    }

    @Test
    fun partialRange_keepsUnconvertedSuffix() {
        val session = ConversionSession()
        val composing = "きょうは"
        session.setCandidates(listOf("今日", "きょう"))
        session.activate(composing.length)
        session.moveConversionEnd(delta = -1, composingLength = composing.length)

        assertEquals("きょう", session.getConversionTarget(composing))
        assertEquals("は", session.getRemainingSuffix(composing))
        assertEquals(true, session.isPartialConversion(composing.length))
    }
}
