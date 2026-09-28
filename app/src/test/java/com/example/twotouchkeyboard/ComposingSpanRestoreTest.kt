package com.example.twotouchkeyboard

import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComposingSpanRestoreTest {

    @Test
    fun plan_leavesEmptyComposingUntouched() {
        val field = observation("あ")
        assertEquals(
            ComposingSpanRestore.Action.None,
            ComposingSpanRestore.plan("", field, field),
        )
        assertEquals(
            ComposingSpanRestore.Action.None,
            ComposingSpanRestore.plan(
                "",
                ComposingSpanRestore.FieldObservation.Unavailable,
                ComposingSpanRestore.FieldObservation.Unavailable,
            ),
        )
    }

    @Test
    fun apply_marksMatchingTextWithoutRewritingIt() {
        val connection = connectionWith("下書きあい")
        val before = ComposingSpanRestore.observe(connection)

        val action = ComposingSpanRestore.apply(connection, "あい", before)

        assertEquals(ComposingSpanRestore.Action.MarkExisting(3, 5), action)
        val text = connection.editable!!
        assertEquals("下書きあい", text.toString())
        assertEquals(3, BaseInputConnection.getComposingSpanStart(text))
        assertEquals(5, BaseInputConnection.getComposingSpanEnd(text))
    }

    @Test
    fun apply_marksMatchingTextFromExtractedText() {
        val connection = extractedConnectionWith("下書きあい")
        val before = ComposingSpanRestore.observe(connection)

        val action = ComposingSpanRestore.apply(connection, "あい", before)

        assertEquals(ComposingSpanRestore.Action.MarkExisting(3, 5), action)
        val text = connection.editable!!
        assertEquals("下書きあい", text.toString())
        assertEquals(3, BaseInputConnection.getComposingSpanStart(text))
        assertEquals(5, BaseInputConnection.getComposingSpanEnd(text))
    }

    @Test
    fun apply_doesNotDuplicateCapitalizedText() {
        val connection = connectionWith("A")
        val before = ComposingSpanRestore.observe(connection)

        val action = ComposingSpanRestore.apply(connection, "a", before)

        assertEquals(ComposingSpanRestore.Action.AbandonInternal, action)
        val text = connection.editable!!
        assertEquals("A", text.toString())
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(text))
    }

    @Test
    fun apply_doesNotInsertWhenTextWasTruncated() {
        val connection = connectionWith("ab")
        val before = ComposingSpanRestore.observe(connection)

        val action = ComposingSpanRestore.apply(connection, "abc", before)

        assertEquals(ComposingSpanRestore.Action.AbandonInternal, action)
        val text = connection.editable!!
        assertEquals("ab", text.toString())
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(text))
    }

    @Test
    fun apply_doesNotEditWhenTextCannotBeRead() {
        val connection = unavailableConnection()

        val action = ComposingSpanRestore.apply(
            connection,
            "か",
            ComposingSpanRestore.FieldObservation.Unavailable,
        )

        assertEquals(ComposingSpanRestore.Action.AbandonInternal, action)
        assertFalse(connection.wroteText)
    }

    @Test
    fun apply_doesNotEditWhenSelectionChanges() {
        val connection = connectionWith("あい")
        val before = ComposingSpanRestore.observe(connection)
        Selection.setSelection(connection.editable!!, 0)

        val action = ComposingSpanRestore.apply(connection, "あい", before)

        assertEquals(ComposingSpanRestore.Action.AbandonInternal, action)
        val text = connection.editable!!
        assertEquals("あい", text.toString())
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(text))
    }

    @Test
    fun apply_doesNotEditWhenSelectionIsExpanded() {
        val connection = connectionWith("あい")
        val text = connection.editable!!
        Selection.setSelection(text, 0, text.length)
        val selected = ComposingSpanRestore.observe(connection)

        val action = ComposingSpanRestore.apply(connection, "あい", selected)

        assertEquals(ComposingSpanRestore.Action.AbandonInternal, action)
        assertEquals("あい", connection.editable.toString())
    }

    @Test
    fun apply_doesNotEditWhenComposingRangeDiffers() {
        val connection = extractedConnectionWith("あい")
        connection.setComposingRegion(0, 1)
        val before = ComposingSpanRestore.observe(connection)

        val action = ComposingSpanRestore.apply(connection, "あい", before)

        assertEquals(ComposingSpanRestore.Action.AbandonInternal, action)
        val text = connection.editable!!
        assertEquals("あい", text.toString())
        assertEquals(0, BaseInputConnection.getComposingSpanStart(text))
        assertEquals(1, BaseInputConnection.getComposingSpanEnd(text))
    }

    @Test
    fun apply_ignoresEmptyComposingText() {
        val connection = connectionWith("確定")
        val action = ComposingSpanRestore.apply(
            connection,
            "",
            ComposingSpanRestore.FieldObservation.Unavailable,
        )
        assertEquals(ComposingSpanRestore.Action.None, action)
        assertEquals("確定", connection.editable.toString())
    }

    private fun observation(text: String): ComposingSpanRestore.FieldObservation {
        return ComposingSpanRestore.observe(connectionWith(text))
    }

    private fun connectionWith(text: String): BaseInputConnection {
        val editable = SpannableStringBuilder(text)
        Selection.setSelection(editable, editable.length)
        val view = View(ApplicationProvider.getApplicationContext())
        return object : BaseInputConnection(view, true) {
            override fun getEditable() = editable
        }
    }

    private fun extractedConnectionWith(text: String): BaseInputConnection {
        val editable = SpannableStringBuilder(text)
        Selection.setSelection(editable, editable.length)
        val view = View(ApplicationProvider.getApplicationContext())
        return object : BaseInputConnection(view, true) {
            override fun getEditable() = editable

            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = null

            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = null

            override fun getExtractedText(
                request: ExtractedTextRequest?,
                flags: Int,
            ): ExtractedText {
                return ExtractedText().apply {
                    this.text = editable
                    startOffset = 0
                    partialStartOffset = -1
                    partialEndOffset = -1
                    selectionStart = Selection.getSelectionStart(editable)
                    selectionEnd = Selection.getSelectionEnd(editable)
                }
            }
        }
    }

    private fun unavailableConnection(): RecordingConnection {
        val view = View(ApplicationProvider.getApplicationContext())
        return RecordingConnection(view)
    }

    private class RecordingConnection(view: View) : BaseInputConnection(view, true) {
        var wroteText: Boolean = false

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = null

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = null

        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
            return null
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            wroteText = true
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            wroteText = true
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            wroteText = true
            return true
        }

        override fun setComposingRegion(start: Int, end: Int): Boolean {
            wroteText = true
            return true
        }
    }
}
