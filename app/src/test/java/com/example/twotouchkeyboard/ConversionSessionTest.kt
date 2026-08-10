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
}
