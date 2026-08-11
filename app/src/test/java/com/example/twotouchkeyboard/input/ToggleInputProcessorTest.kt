package com.example.twotouchkeyboard.input

import android.view.inputmethod.InputConnection
import com.example.twotouchkeyboard.InputMode
import com.example.twotouchkeyboard.KeyboardKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ToggleInputProcessorTest {

    @Test
    fun resetPartialInput_clearsPendingWithoutConfirming() {
        val host = RecordingInputProcessorHost()
        val processor = HiraganaToggleProcessor(host)

        processor.onKeyPressed(KeyboardKey.Digit(1))
        processor.resetPartialInput()

        assertEquals("", host.confirmedBuffer.toString())
        assertEquals("", host.composingPreview)
    }

    @Test
    fun delete_withOnlyPendingChar_removesComposingWithoutConfirming() {
        val host = RecordingInputProcessorHost()
        val processor = HiraganaToggleProcessor(host)

        processor.onKeyPressed(KeyboardKey.Digit(1))
        assertEquals("あ", host.composingPreview)

        processor.onDelete(FakeInputConnection())

        assertEquals("", host.confirmedBuffer.toString())
        assertEquals("", host.composingPreview)
    }

    @Test
    fun delete_withConfirmedBuffer_removesLastCharacter() {
        val host = RecordingInputProcessorHost()
        val processor = HiraganaToggleProcessor(host)

        processor.onKeyPressed(KeyboardKey.Digit(1))
        processor.confirmPendingInput()
        assertEquals("あ", host.confirmedBuffer.toString())

        processor.onDelete(FakeInputConnection())

        assertEquals("", host.confirmedBuffer.toString())
        assertEquals("", host.composingPreview)
    }

    @Test
    fun delete_withPendingCharAndConfirmedBuffer_clearsPendingOnly() {
        val host = RecordingInputProcessorHost()
        val processor = HiraganaToggleProcessor(host)

        processor.onKeyPressed(KeyboardKey.Digit(1))
        processor.confirmPendingInput()
        processor.onKeyPressed(KeyboardKey.Digit(2))

        assertEquals("あ", host.confirmedBuffer.toString())
        assertEquals("あd", host.composingPreview)

        processor.onDelete(FakeInputConnection())

        assertEquals("あ", host.confirmedBuffer.toString())
        assertEquals("あ", host.composingPreview)
    }

    private class RecordingInputProcessorHost : InputProcessorHost {
        val confirmedBuffer = StringBuilder()
        var composingPreview: String = ""

        override fun appendConfirmedCharacter(character: String) {
            confirmedBuffer.append(character)
        }

        override fun setComposingPreview(text: String) {
            composingPreview = text
        }

        override fun onProcessorStateChanged() = Unit

        override fun getConfirmedBuffer(): String = confirmedBuffer.toString()

        override fun getComposingPreview(): String = composingPreview

        override fun deleteLastConfirmedCharacter() {
            if (confirmedBuffer.isEmpty()) return
            confirmedBuffer.deleteCharAt(confirmedBuffer.lastIndex)
            composingPreview = confirmedBuffer.toString()
        }

        override fun replaceLastConfirmedCharacter(newChar: Char) = Unit

        override fun commitComposingText(ic: InputConnection) = Unit

        override fun clearComposingState() {
            confirmedBuffer.clear()
            composingPreview = ""
        }

        override fun getInputMode(): InputMode = InputMode.HIRAGANA

        override fun requestConversion() = Unit

        override fun commitDirectText(text: String) = Unit

        override fun scheduleToggleAutoCommit(onTimeout: () -> Unit) = Unit

        override fun cancelToggleAutoCommit() = Unit
    }

    private class FakeInputConnection : InputConnection {
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) = true

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int) = true

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = null

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = null

        override fun getSelectedText(flags: Int): CharSequence? = null

        override fun getCursorCapsMode(reqModes: Int) = 0

        override fun getExtractedText(
            request: android.view.inputmethod.ExtractedTextRequest?,
            flags: Int,
        ) = null

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int) = true

        override fun setComposingRegion(start: Int, end: Int) = true

        override fun finishComposingText() = true

        override fun commitText(text: CharSequence?, newCursorPosition: Int) = true

        override fun commitCompletion(text: android.view.inputmethod.CompletionInfo?) = true

        override fun commitCorrection(correctionInfo: android.view.inputmethod.CorrectionInfo?) = true

        override fun setSelection(start: Int, end: Int) = true

        override fun performEditorAction(editorAction: Int) = true

        override fun performContextMenuAction(id: Int) = true

        override fun beginBatchEdit() = true

        override fun endBatchEdit() = true

        override fun sendKeyEvent(event: android.view.KeyEvent?) = true

        override fun clearMetaKeyStates(states: Int) = true

        override fun reportFullscreenMode(enabled: Boolean) = true

        override fun performPrivateCommand(action: String?, data: android.os.Bundle?) = true

        override fun requestCursorUpdates(cursorUpdateMode: Int) = true

        override fun requestCursorUpdates(cursorUpdateMode: Int, executionMode: Int) = true

        override fun commitContent(
            inputContentInfo: android.view.inputmethod.InputContentInfo,
            flags: Int,
            opts: android.os.Bundle?,
        ) = true

        override fun performSpellCheck() = true

        override fun setImeConsumesInput(imeConsumesInput: Boolean) = true

        override fun closeConnection() = Unit

        override fun getHandler(): android.os.Handler? = null

        override fun revokeSelfPermissionOnKill(permission: String) = true

        override fun setComposingText(
            text: CharSequence?,
            newCursorPosition: Int,
            textAttribute: android.view.inputmethod.TextAttribute?,
        ) = true

        override fun commitText(
            text: CharSequence?,
            newCursorPosition: Int,
            textAttribute: android.view.inputmethod.TextAttribute?,
        ) = true
    }
}
