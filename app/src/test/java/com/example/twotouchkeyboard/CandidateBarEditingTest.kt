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
class CandidateBarEditingTest {
    @Test
    fun selectCopyCutAndPaste_preserveTextAndReplaceSelection() {
        val editor = EditText(ApplicationProvider.getApplicationContext<Context>())
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        activity.setContentView(editor)
        editor.requestFocus()
        editor.setText("あいうえお")
        val connection = editor.onCreateInputConnection(EditorInfo())!!
        performCandidateBarEdit(connection, CandidateBarAction.SELECT_ALL)
        assertEquals("あいうえお", connection.getSelectedText(0).toString())
        performCandidateBarEdit(connection, CandidateBarAction.COPY)
        editor.setSelection(1, 3)
        performCandidateBarEdit(connection, CandidateBarAction.PASTE)
        assertEquals("ああいうえおえお", editor.text.toString())
        editor.setSelection(1, 6)
        performCandidateBarEdit(connection, CandidateBarAction.CUT)
        assertEquals("あえお", editor.text.toString())
        performCandidateBarEdit(connection, CandidateBarAction.PASTE)
        assertEquals("ああいうえおえお", editor.text.toString())
    }
}
