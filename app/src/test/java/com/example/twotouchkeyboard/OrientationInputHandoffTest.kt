package com.example.twotouchkeyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationInputHandoffTest {

    @Test
    fun sameSessionRestart_consumesPendingRestore() {
        val handoff = OrientationInputHandoff()
        handoff.onOrientationChanged(sessionId = 4)

        assertTrue(handoff.isPending)
        assertTrue(handoff.consumeIfSameSession(sessionId = 4, restarting = true))
        assertFalse(handoff.isPending)
        assertFalse(handoff.consumeIfSameSession(sessionId = 4, restarting = true))
    }

    @Test
    fun newSession_doesNotConsumePendingRestore() {
        val handoff = OrientationInputHandoff()
        handoff.onOrientationChanged(sessionId = 4)

        assertFalse(handoff.consumeIfSameSession(sessionId = 4, restarting = false))
        assertFalse(handoff.consumeIfSameSession(sessionId = 9, restarting = true))
        assertTrue(handoff.isPending)

        handoff.clear()
        assertFalse(handoff.isPending)
        assertFalse(handoff.consumeIfSameSession(sessionId = 4, restarting = true))
    }
}
