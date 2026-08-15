package com.example.twotouchkeyboard.input

import android.text.InputType
import android.view.inputmethod.EditorInfo

internal sealed class EnterBehavior {
    data object InsertNewline : EnterBehavior()
    data class PerformEditorAction(val action: Int) : EnterBehavior()
    data object HideKeyboard : EnterBehavior()
}

internal data class EnterKeyLabels(
    val newline: String,
    val close: String,
    val go: String,
    val search: String,
    val next: String,
    val previous: String,
)

internal object EnterBehaviorResolver {

    fun isNewlineAllowed(info: EditorInfo?): Boolean {
        if (info == null) return false

        val inputType = info.inputType
        val typeClass = inputType and InputType.TYPE_MASK_CLASS
        if (typeClass != InputType.TYPE_CLASS_TEXT) return false

        if (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) return true
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return true

        return false
    }

    fun resolveEditorAction(info: EditorInfo?): Int {
        if (info == null) return EditorInfo.IME_ACTION_NONE
        return info.imeOptions and EditorInfo.IME_MASK_ACTION
    }

    fun resolveEnterBehavior(info: EditorInfo?): EnterBehavior {
        val action = resolveEditorAction(info)
        if (isSafeEditorAction(action)) {
            return EnterBehavior.PerformEditorAction(action)
        }
        if (isNewlineAllowed(info)) {
            return EnterBehavior.InsertNewline
        }
        return EnterBehavior.HideKeyboard
    }

    fun getEnterKeyLabel(info: EditorInfo?, labels: EnterKeyLabels): String {
        return when (val behavior = resolveEnterBehavior(info)) {
            EnterBehavior.InsertNewline -> labels.newline
            EnterBehavior.HideKeyboard -> labels.close
            is EnterBehavior.PerformEditorAction -> labelForSafeAction(behavior.action, labels)
        }
    }

    private fun isSafeEditorAction(action: Int): Boolean {
        return when (action) {
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_PREVIOUS,
            -> true
            else -> false
        }
    }

    private fun labelForSafeAction(action: Int, labels: EnterKeyLabels): String {
        return when (action) {
            EditorInfo.IME_ACTION_GO -> labels.go
            EditorInfo.IME_ACTION_SEARCH -> labels.search
            EditorInfo.IME_ACTION_NEXT -> labels.next
            EditorInfo.IME_ACTION_PREVIOUS -> labels.previous
            else -> labels.newline
        }
    }
}
