package com.example.twotouchkeyboard

import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/**
 * 回転後も、同じ文字範囲を確認できたときだけ未確定の範囲を戻す。
 *
 * 文字列の書き換えはしない。末尾の不一致や取得失敗では挿入も削除もしない。
 * 入力欄側の変換で内部状態と一致しない場合は、既存の文字を残して内部状態を終了する。
 */
internal object ComposingSpanRestore {
    data class FieldObservation(
        val available: Boolean,
        val fullText: String = "",
        val selectionStart: Int = -1,
        val selectionEnd: Int = -1,
        val composingStart: Int = -1,
        val composingEnd: Int = -1,
    ) {
        val hasCollapsedSelection: Boolean
            get() = available && selectionStart >= 0 && selectionStart == selectionEnd

        companion object {
            val Unavailable = FieldObservation(available = false)
        }
    }

    sealed class Action {
        data object None : Action()
        data class MarkExisting(val start: Int, val end: Int) : Action()
        data object AbandonInternal : Action()
    }

    fun observe(connection: InputConnection?): FieldObservation {
        if (connection == null) return FieldObservation.Unavailable
        return observeExtracted(connection) ?: observeAroundCursor(connection)
    }

    fun plan(
        internal: String,
        before: FieldObservation,
        after: FieldObservation,
    ): Action {
        if (internal.isEmpty()) return Action.None
        if (!before.available || !after.available) return Action.AbandonInternal
        if (!before.hasCollapsedSelection || !after.hasCollapsedSelection) {
            return Action.AbandonInternal
        }
        if (before.fullText != after.fullText) return Action.AbandonInternal
        if (before.selectionStart != after.selectionStart) return Action.AbandonInternal
        val end = after.selectionEnd
        val start = end - internal.length
        if (start < 0 || end > after.fullText.length) return Action.AbandonInternal
        if (after.fullText.substring(start, end) != internal) return Action.AbandonInternal
        if (!composingRangeMatchesOrAbsent(before, start, end)) return Action.AbandonInternal
        if (!composingRangeMatchesOrAbsent(after, start, end)) return Action.AbandonInternal
        return Action.MarkExisting(start, end)
    }

    /**
     * 一致する既存文字に未確定範囲を付ける。テキストの挿入や削除はしない。
     */
    fun apply(
        connection: InputConnection,
        internal: String,
        before: FieldObservation,
    ): Action {
        val action = plan(internal, before, observe(connection))
        if (action is Action.MarkExisting) {
            connection.setComposingRegion(action.start, action.end)
        }
        return action
    }

    private fun observeExtracted(connection: InputConnection): FieldObservation? {
        val request = ExtractedTextRequest().apply {
            flags = InputConnection.GET_TEXT_WITH_STYLES
            hintMaxChars = MAX_VERIFIED_CHARS
            hintMaxLines = MAX_VERIFIED_LINES
        }
        val extracted = connection.getExtractedText(request, 0) ?: return null
        if (extracted.partialStartOffset >= 0 || extracted.startOffset > 0) return null
        val text = extracted.text ?: return null
        val selectionStart = absoluteIndex(extracted.selectionStart, text.length)
        val selectionEnd = absoluteIndex(extracted.selectionEnd, text.length)
        if (selectionStart < 0 || selectionEnd < 0) return null
        val composingStart = composingBound(text, start = true, length = text.length)
        val composingEnd = composingBound(text, start = false, length = text.length)
        return FieldObservation(
            available = true,
            fullText = text.toString(),
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            composingStart = composingStart,
            composingEnd = composingEnd,
        )
    }

    private fun observeAroundCursor(connection: InputConnection): FieldObservation {
        val flags = InputConnection.GET_TEXT_WITH_STYLES
        val limit = MAX_VERIFIED_CHARS + 1
        val before = connection.getTextBeforeCursor(limit, flags) ?: return FieldObservation.Unavailable
        val after = connection.getTextAfterCursor(limit, flags) ?: return FieldObservation.Unavailable
        if (before.length > MAX_VERIFIED_CHARS || after.length > MAX_VERIFIED_CHARS) {
            return FieldObservation.Unavailable
        }
        val selected = connection.getSelectedText(flags)
        if (selected != null && selected.length > MAX_VERIFIED_CHARS) {
            return FieldObservation.Unavailable
        }
        val selectedText = selected?.toString().orEmpty()
        val selectionStart = before.length
        val selectionEnd = before.length + selectedText.length
        val fullText = before.toString() + selectedText + after.toString()
        val (composingStart, composingEnd) = composingRangeAroundCursor(
            before = before,
            selected = selected,
            after = after,
            selectionStart = selectionStart,
            fullLength = fullText.length,
        )
        return FieldObservation(
            available = true,
            fullText = fullText,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            composingStart = composingStart,
            composingEnd = composingEnd,
        )
    }

    private fun composingRangeAroundCursor(
        before: CharSequence,
        selected: CharSequence?,
        after: CharSequence,
        selectionStart: Int,
        fullLength: Int,
    ): Pair<Int, Int> {
        val beforeRange = spanRange(before, absoluteOrigin = 0, fullLength = fullLength)
        if (beforeRange != null) return beforeRange
        val selectedRange = selected?.let {
            spanRange(it, absoluteOrigin = selectionStart, fullLength = fullLength)
        }
        if (selectedRange != null) return selectedRange
        val afterRange = spanRange(
            after,
            absoluteOrigin = selectionStart + (selected?.length ?: 0),
            fullLength = fullLength,
        )
        return afterRange ?: (-1 to -1)
    }

    private fun spanRange(
        text: CharSequence,
        absoluteOrigin: Int,
        fullLength: Int,
    ): Pair<Int, Int>? {
        val start = composingBound(text, start = true, length = text.length)
        val end = composingBound(text, start = false, length = text.length)
        if (start < 0 || end < 0) return null
        val absoluteStart = absoluteOrigin + start
        val absoluteEnd = absoluteOrigin + end
        if (absoluteStart !in 0..fullLength || absoluteEnd !in 0..fullLength) return null
        return absoluteStart to absoluteEnd
    }

    private fun composingBound(text: CharSequence, start: Boolean, length: Int): Int {
        val spannable = when (text) {
            is Spannable -> text
            is Spanned -> SpannableString(text)
            else -> return -1
        }
        val relative = if (start) {
            BaseInputConnection.getComposingSpanStart(spannable)
        } else {
            BaseInputConnection.getComposingSpanEnd(spannable)
        }
        if (relative < 0 || relative > length) return -1
        return relative
    }

    private fun absoluteIndex(index: Int, length: Int): Int {
        if (index < 0 || index > length) return -1
        return index
    }

    private fun composingRangeMatchesOrAbsent(
        observation: FieldObservation,
        start: Int,
        end: Int,
    ): Boolean {
        val hasSpan = observation.composingStart >= 0 || observation.composingEnd >= 0
        if (!hasSpan) return true
        return observation.composingStart == start && observation.composingEnd == end
    }

    private const val MAX_VERIFIED_CHARS = 100_000
    private const val MAX_VERIFIED_LINES = 10_000
}
