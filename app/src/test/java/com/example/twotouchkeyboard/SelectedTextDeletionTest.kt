package com.example.twotouchkeyboard

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SelectedTextDeletionTest {
    @Test
    fun deleteSelection_inEveryMode_preservesSurroundingText() {
        for (mode in InputMode.entries) {
            for ((start, end, expected) in listOf(
                Triple(0, 5, ""),
                Triple(1, 4, "あお"),
                Triple(4, 1, "あお"),
                Triple(2, 2, "あうえお"),
            )) {
                val editor = EditText(ApplicationProvider.getApplicationContext<Context>())
                val connection = editor.onCreateInputConnection(EditorInfo())!!
                val coordinator = coordinator(editor)
                coordinator.bindInputConnection(connection)
                while (coordinator.getInputMode() != mode) coordinator.cycleInputMode()
                editor.setText("あいうえお")
                editor.setSelection(start, end)

                coordinator.onDelete()

                assertEquals("mode=$mode selection=$start..$end", expected, editor.text.toString())
                assertEquals(editor.selectionStart, editor.selectionEnd)
            }
        }
    }

    @Test
    fun deleteSelection_afterEnterCommitsInput() {
        val editor = EditText(ApplicationProvider.getApplicationContext<Context>())
        val coordinator = coordinator(editor)
        coordinator.bindInputConnection(editor.onCreateInputConnection(EditorInfo())!!)
        coordinator.onKeyPressed(KeyboardKey.Digit(1))
        coordinator.onKeyPressed(KeyboardKey.Digit(1))
        coordinator.onKeyPressed(KeyboardKey.Digit(1))
        coordinator.onKeyPressed(KeyboardKey.Digit(2))
        coordinator.onEnter()
        assertEquals("あい", editor.text.toString())
        assertEquals("", coordinator.getComposingText())
        editor.setSelection(0, 2)

        coordinator.onDelete()

        assertEquals("", editor.text.toString())
    }

    @Test
    fun deleteDuringComposing_stillRemovesLastCharacter() {
        val editor = EditText(ApplicationProvider.getApplicationContext<Context>())
        val coordinator = coordinator(editor)
        coordinator.bindInputConnection(editor.onCreateInputConnection(EditorInfo())!!)
        coordinator.onKeyPressed(KeyboardKey.Digit(1))
        coordinator.onKeyPressed(KeyboardKey.Digit(1))
        coordinator.onKeyPressed(KeyboardKey.Digit(1))
        coordinator.onKeyPressed(KeyboardKey.Digit(2))

        coordinator.onDelete()

        assertEquals("あ", coordinator.getComposingText())
        assertEquals("あ", editor.text.toString())
    }

    private fun coordinator(editor: EditText): KeyboardInputCoordinator {
        val connection = editor.onCreateInputConnection(EditorInfo())!!
        return KeyboardInputCoordinator(object : KeyboardInputCoordinator.Listener {
            override fun onStateChanged() = Unit
            override fun onComposingTextUpdated(composingText: String) {
                connection.setComposingText(composingText, 1)
            }
            override fun onInputModeChanged(mode: InputMode) = Unit
            override fun scheduleToggleAutoCommit(onTimeout: () -> Unit) = Unit
            override fun cancelToggleAutoCommit() = Unit
            override fun requestHideSoftInput() = Unit
        })
    }
}
