package com.example.twotouchkeyboard

import android.view.inputmethod.InputConnection

/**
 * 回転後の入力欄へ、未確定文字列を二重に確定せず戻す。
 *
 * 文字がすでにカーソル直前へ残っていれば、その範囲を未確定へ戻す。
 * 消えていれば、同じ文字列を未確定として挿入する。
 */
internal object ComposingSpanRestore {
    sealed class Action {
        data object None : Action()
        data class ReplaceSuffix(val length: Int, val text: String) : Action()
        data class Insert(val text: String) : Action()
    }

    fun plan(composing: String, textBeforeCursor: String?): Action {
        if (composing.isEmpty()) return Action.None
        if (textBeforeCursor != null && textBeforeCursor.endsWith(composing)) {
            return Action.ReplaceSuffix(composing.length, composing)
        }
        return Action.Insert(composing)
    }

    fun apply(connection: InputConnection, composing: String) {
        val before = if (composing.isEmpty()) {
            null
        } else {
            connection.getTextBeforeCursor(composing.length, 0)?.toString()
        }
        when (val action = plan(composing, before)) {
            Action.None -> Unit
            is Action.Insert -> connection.setComposingText(action.text, 1)
            is Action.ReplaceSuffix -> {
                connection.beginBatchEdit()
                try {
                    connection.deleteSurroundingText(action.length, 0)
                    connection.setComposingText(action.text, 1)
                } finally {
                    connection.endBatchEdit()
                }
            }
        }
    }
}
