package com.example.twotouchkeyboard

import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComposingSpanRestoreTest {

    @Test
    fun plan_leavesEmptyComposingUntouched() {
        assertEquals(
            ComposingSpanRestore.Action.None,
            ComposingSpanRestore.plan("", "あ"),
        )
    }

    @Test
    fun plan_reusesSuffixAlreadyBeforeCursor() {
        assertEquals(
            ComposingSpanRestore.Action.ReplaceSuffix(2, "あい"),
            ComposingSpanRestore.plan("あい", "下書きあい"),
        )
    }

    @Test
    fun plan_insertsWhenComposingTextIsMissing() {
        assertEquals(
            ComposingSpanRestore.Action.Insert("か"),
            ComposingSpanRestore.plan("か", "あ"),
        )
        assertEquals(
            ComposingSpanRestore.Action.Insert("か"),
            ComposingSpanRestore.plan("か", null),
        )
    }

    @Test
    fun apply_doesNotDuplicateTextAlreadyBeforeCursor() {
        val connection = connectionWith("確定あい")
        ComposingSpanRestore.apply(connection, "あい")

        val text = connection.editable!!
        assertEquals("確定あい", text.toString())
        assertEquals(2, BaseInputConnection.getComposingSpanStart(text))
        assertEquals(4, BaseInputConnection.getComposingSpanEnd(text))
    }

    @Test
    fun apply_insertsComposingTextWhenItWasDropped() {
        val connection = connectionWith("確定")
        ComposingSpanRestore.apply(connection, "か")

        val text = connection.editable!!
        assertEquals("確定か", text.toString())
        assertEquals(2, BaseInputConnection.getComposingSpanStart(text))
        assertEquals(3, BaseInputConnection.getComposingSpanEnd(text))
    }

    @Test
    fun apply_ignoresEmptyComposingText() {
        val connection = connectionWith("確定")
        ComposingSpanRestore.apply(connection, "")
        assertEquals("確定", connection.editable.toString())
    }

    private fun connectionWith(text: String): BaseInputConnection {
        val editable = SpannableStringBuilder(text)
        Selection.setSelection(editable, editable.length)
        val view = View(ApplicationProvider.getApplicationContext())
        return object : BaseInputConnection(view, true) {
            override fun getEditable() = editable
        }
    }
}
