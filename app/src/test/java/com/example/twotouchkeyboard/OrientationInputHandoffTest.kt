package com.example.twotouchkeyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationInputHandoffTest {

    @Test
    fun sameEditor_keepsInputUntilCleared() {
        val handoff = OrientationInputHandoff()
        val editor = editor()

        assertFalse(handoff.isPending)
        handoff.onOrientationChanged(editor)

        assertTrue(handoff.shouldKeepInput(editor))
        assertFalse(handoff.shouldKeepInput(editor.copy(fieldId = 8)))

        handoff.clear()
        assertFalse(handoff.isPending)
        assertFalse(handoff.shouldKeepInput(editor))
    }

    private fun editor(): OrientationInputHandoff.EditorKey {
        return OrientationInputHandoff.EditorKey(
            packageName = "com.example.notes",
            fieldId = 7,
            inputType = 1,
            imeOptions = 2,
        )
    }
}
