package com.example.twotouchkeyboard.input

import com.example.twotouchkeyboard.InputMode
import com.example.twotouchkeyboard.KeyboardKey
import org.junit.Assert.assertEquals
import org.junit.Test

class AlphabetToggleProcessorTest {

    @Test
    fun getKeyLabel_showsFullAlphabetRowOnIdleKeys() {
        val processor = AlphabetToggleProcessor(StubHost())

        assertEquals("abc", processor.getKeyLabel(KeyboardKey.Digit(1)))
        assertEquals("def", processor.getKeyLabel(KeyboardKey.Digit(2)))
        assertEquals("ghi", processor.getKeyLabel(KeyboardKey.Digit(3)))
        assertEquals("yz", processor.getKeyLabel(KeyboardKey.Digit(9)))
    }

    @Test
    fun getKeyLabel_keepsActiveRowLabelWhileToggling() {
        val processor = AlphabetToggleProcessor(StubHost())

        processor.onKeyPressed(KeyboardKey.Digit(1))

        assertEquals("abc", processor.getKeyLabel(KeyboardKey.Digit(1)))
    }

    private class StubHost : InputProcessorHost {
        override fun appendConfirmedCharacter(character: String) = Unit
        override fun setComposingPreview(text: String) = Unit
        override fun onProcessorStateChanged() = Unit
        override fun getConfirmedBuffer(): String = ""
        override fun getComposingPreview(): String = ""
        override fun deleteLastConfirmedCharacter() = Unit
        override fun replaceLastConfirmedCharacter(newChar: Char) = Unit
        override fun commitComposingText(ic: android.view.inputmethod.InputConnection) = Unit
        override fun clearComposingState() = Unit
        override fun getInputMode(): InputMode = InputMode.ALPHABET
        override fun requestConversion() = Unit
        override fun commitDirectText(text: String) = Unit
        override fun scheduleToggleAutoCommit(onTimeout: () -> Unit) = Unit
        override fun cancelToggleAutoCommit() = Unit
        override fun requestHideSoftInput() = Unit
    }
}
