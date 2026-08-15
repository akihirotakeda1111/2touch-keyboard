package com.example.twotouchkeyboard.input

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnterBehaviorResolverTest {

    private val labels = EnterKeyLabels(
        newline = "↵",
        close = "閉じる",
        go = "Go",
        search = "検索",
        next = "次へ",
        previous = "前へ",
    )

    @Test
    fun isNewlineAllowed_returnsTrue_forMultilineText() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        assertTrue(EnterBehaviorResolver.isNewlineAllowed(info))
    }

    @Test
    fun isNewlineAllowed_returnsTrue_whenNoEnterActionFlagSet() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        }

        assertTrue(EnterBehaviorResolver.isNewlineAllowed(info))
    }

    @Test
    fun isNewlineAllowed_returnsFalse_forSingleLineEmail() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            imeOptions = EditorInfo.IME_ACTION_DONE
        }

        assertFalse(EnterBehaviorResolver.isNewlineAllowed(info))
    }

    @Test
    fun resolveEnterBehavior_performsGo_forUriField() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
        }

        assertEquals(
            EnterBehavior.PerformEditorAction(EditorInfo.IME_ACTION_GO),
            EnterBehaviorResolver.resolveEnterBehavior(info),
        )
    }

    @Test
    fun resolveEnterBehavior_insertsNewline_forMultilineSend() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND
        }

        assertEquals(
            EnterBehavior.InsertNewline,
            EnterBehaviorResolver.resolveEnterBehavior(info),
        )
    }

    @Test
    fun resolveEnterBehavior_hidesKeyboard_forSingleLineSend() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            imeOptions = EditorInfo.IME_ACTION_SEND
        }

        assertEquals(
            EnterBehavior.HideKeyboard,
            EnterBehaviorResolver.resolveEnterBehavior(info),
        )
    }

    @Test
    fun resolveEnterBehavior_hidesKeyboard_forSingleLineDone() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
        }

        assertEquals(
            EnterBehavior.HideKeyboard,
            EnterBehaviorResolver.resolveEnterBehavior(info),
        )
    }

    @Test
    fun getEnterKeyLabel_returnsClose_whenKeyboardWouldHide() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
        }

        assertEquals("閉じる", EnterBehaviorResolver.getEnterKeyLabel(info, labels))
    }

    @Test
    fun getEnterKeyLabel_returnsNewline_forMultilineChat() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND
        }

        assertEquals("↵", EnterBehaviorResolver.getEnterKeyLabel(info, labels))
    }

    @Test
    fun getEnterKeyLabel_returnsGo_forUriField() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
        }

        assertEquals("Go", EnterBehaviorResolver.getEnterKeyLabel(info, labels))
    }
}
