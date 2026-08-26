package com.example.twotouchkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextInputSuggestionSessionTest {

    @Test
    fun isActive_returnsFalseWhenEmpty() {
        val session = NextInputSuggestionSession()
        assertFalse(session.isActive)
    }

    @Test
    fun setCandidates_marksSessionActive() {
        val session = NextInputSuggestionSession()
        session.setCandidates(listOf("を", "が"))
        assertTrue(session.isActive)
        assertEquals(listOf("を", "が"), session.getCandidates())
    }

    @Test
    fun clear_resetsCandidates() {
        val session = NextInputSuggestionSession()
        session.setCandidates(listOf("を"))
        session.clear()
        assertFalse(session.isActive)
        assertEquals(emptyList<String>(), session.getCandidates())
    }
}
