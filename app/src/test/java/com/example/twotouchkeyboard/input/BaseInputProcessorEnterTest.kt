package com.example.twotouchkeyboard.input

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import com.example.twotouchkeyboard.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BaseInputProcessorEnterTest {

    private lateinit var host: FakeInputProcessorHost
    private lateinit var inputConnection: RecordingInputConnection
    private lateinit var processor: NumberInputProcessor

    @Before
    fun setUp() {
        host = FakeInputProcessorHost()
        inputConnection = RecordingInputConnection()
        processor = NumberInputProcessor(host)
    }

    @Test
    fun onEnter_insertsNewline_whenComposingIsEmpty() {
        processor.onEnter(inputConnection, editorInfoWithAction(EditorInfo.IME_ACTION_SEND))

        assertEquals(listOf(Commit("\n", 1)), inputConnection.commits)
        assertFalse(host.composingCommitted)
    }

    @Test
    fun onEnter_commitsComposingWithoutNewline_whenComposingIsNotEmpty() {
        host.preview = "hello"

        processor.onEnter(inputConnection, editorInfoWithAction(EditorInfo.IME_ACTION_SEND))

        assertTrue(host.composingCommitted)
        assertTrue(host.composingCleared)
        assertEquals(emptyList<Commit>(), inputConnection.commits)
        assertEquals(emptyList<Int>(), inputConnection.editorActions)
    }

    @Test
    fun onEnter_insertsNewline_afterComposingWasCommitted() {
        host.preview = "hello"
        processor.onEnter(inputConnection, editorInfoWithAction(EditorInfo.IME_ACTION_SEND))

        host.preview = ""
        processor.onEnter(inputConnection, editorInfoWithAction(EditorInfo.IME_ACTION_SEND))

        assertEquals(listOf(Commit("\n", 1)), inputConnection.commits)
        assertEquals(emptyList<Int>(), inputConnection.editorActions)
    }

    private fun editorInfoWithAction(action: Int): EditorInfo {
        return EditorInfo().apply { imeOptions = action }
    }

    private class FakeInputProcessorHost : InputProcessorHost {
        var preview: String = ""
        var composingCommitted = false
        var composingCleared = false

        override fun appendConfirmedCharacter(character: String) = Unit

        override fun setComposingPreview(text: String) {
            preview = text
        }

        override fun onProcessorStateChanged() = Unit

        override fun getConfirmedBuffer(): String = ""

        override fun getComposingPreview(): String = preview

        override fun deleteLastConfirmedCharacter() = Unit

        override fun replaceLastConfirmedCharacter(newChar: Char) = Unit

        override fun commitComposingText(ic: InputConnection) {
            composingCommitted = true
        }

        override fun clearComposingState() {
            composingCleared = true
            preview = ""
        }

        override fun getInputMode(): InputMode = InputMode.ALPHABET

        override fun requestConversion() = Unit

        override fun commitDirectText(text: String) = Unit

        override fun scheduleToggleAutoCommit(onTimeout: () -> Unit) = Unit

        override fun cancelToggleAutoCommit() = Unit
    }

    private data class Commit(val text: String, val newCursorPosition: Int)

    private class RecordingInputConnection : InputConnection {
        val commits = mutableListOf<Commit>()
        val editorActions = mutableListOf<Int>()

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            commits += Commit(text?.toString().orEmpty(), newCursorPosition)
            return true
        }

        override fun performEditorAction(actionCode: Int): Boolean {
            editorActions += actionCode
            return true
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = null

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = null

        override fun getSelectedText(flags: Int): CharSequence? = null

        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = null

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = true

        override fun setComposingRegion(start: Int, end: Int): Boolean = true

        override fun finishComposingText(): Boolean = true

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean = true

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = true

        override fun setSelection(start: Int, end: Int): Boolean = true

        override fun sendKeyEvent(event: KeyEvent?): Boolean = true

        override fun getCursorCapsMode(reqModes: Int): Int = 0

        override fun clearMetaKeyStates(states: Int): Boolean = true

        override fun reportFullscreenMode(enabled: Boolean): Boolean = true

        override fun performContextMenuAction(id: Int): Boolean = true

        override fun beginBatchEdit(): Boolean = true

        override fun endBatchEdit(): Boolean = true

        override fun closeConnection() = Unit

        override fun getHandler(): Handler = Handler(Looper.getMainLooper())

        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = true

        override fun commitCompletion(text: CompletionInfo?): Boolean = true

        override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = true

        override fun performPrivateCommand(action: String?, data: android.os.Bundle?): Boolean = true

        override fun commitContent(
            inputContentInfo: InputContentInfo,
            flags: Int,
            opts: android.os.Bundle?,
        ): Boolean = true
    }
}
