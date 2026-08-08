package com.example.twotouchkeyboard.input

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.twotouchkeyboard.CursorDirection
import com.example.twotouchkeyboard.InputMode
import com.example.twotouchkeyboard.KeyboardKey
import com.example.twotouchkeyboard.KeyboardMappings

abstract class BaseInputProcessor(
    protected val host: InputProcessorHost,
) : InputProcessor {

    protected var pendingChar: String? = null

    override fun resetPartialInput() {
        host.cancelToggleAutoCommit()
        pendingChar = null
        refreshComposingPreview()
    }

    override fun resetInputSession() {
        host.cancelToggleAutoCommit()
        resetPartialInput()
    }

    override fun confirmPendingInput() {
        host.cancelToggleAutoCommit()
        pendingChar?.let { host.appendConfirmedCharacter(it) }
        pendingChar = null
        refreshComposingPreview()
    }

    override fun onDelete(ic: InputConnection) {
        when {
            hasPartialTwoTouchInput() -> resetPartialInput()
            pendingChar != null -> {
                clearToggleState()
                refreshComposingPreview()
                host.onProcessorStateChanged()
            }
            host.getConfirmedBuffer().isNotEmpty() -> host.deleteLastConfirmedCharacter()
            else -> ic.deleteSurroundingText(1, 0)
        }
    }

    override fun onEnter(ic: InputConnection, editorInfo: EditorInfo?) {
        val composing = host.getComposingPreview()
        if (composing.isNotEmpty()) {
            host.commitComposingText(ic)
            host.clearComposingState()
            return
        }
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    override fun isMidCharacterInput(): Boolean {
        return hasPartialTwoTouchInput() || pendingChar != null
    }

    override fun onSpace(ic: InputConnection) {
        val space = when (host.getInputMode()) {
            InputMode.HIRAGANA -> "　"
            InputMode.ALPHABET, InputMode.NUMBER -> " "
        }
        ic.commitText(space, 1)
    }

    override fun onCursorMove(ic: InputConnection, direction: Int) {
        if (host.getComposingPreview().isNotEmpty()) {
            host.commitComposingText(ic)
            host.clearComposingState()
        }
        val keyCode = when (direction) {
            CursorDirection.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            CursorDirection.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            else -> return
        }
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    override fun getKeyLabel(key: KeyboardKey): String {
        return when (key) {
            KeyboardKey.Delete -> "削除"
            KeyboardKey.Enter -> "↵"
            KeyboardKey.Space -> "Space"
            KeyboardKey.CursorLeft -> "←"
            KeyboardKey.CursorRight -> "→"
            else -> ""
        }
    }

    protected open fun hasPartialTwoTouchInput(): Boolean = false

    protected open fun clearToggleState() {
        host.cancelToggleAutoCommit()
        pendingChar = null
    }

    protected fun scheduleToggleAutoCommit() {
        if (pendingChar == null) return
        host.scheduleToggleAutoCommit { confirmPendingInput() }
    }

    protected fun refreshComposingPreview() {
        val preview = buildString {
            append(host.getConfirmedBuffer())
            pendingChar?.let { append(it) }
        }
        host.setComposingPreview(preview)
    }

    protected fun getTwoTouchDigitLabel(
        number: Int,
        waiting: Boolean,
        activeChars: String?,
        idleHeadLabels: Map<Int, String>,
        validIdleRange: IntRange,
    ): String {
        if (waiting && activeChars != null) {
            val maxIndex = activeChars.length.coerceAtMost(9)
            if (number !in 1..maxIndex) return number.toString()
            val index = number - 1
            return activeChars[index].toString()
        }

        if (number !in validIdleRange) return number.toString()
        val head = idleHeadLabels[number] ?: return number.toString()
        return "$number\n$head"
    }

    protected fun getToggleDigitLabel(
        number: Int,
        rowChars: String?,
        idleHeadLabels: Map<Int, String>,
        validIdleRange: IntRange,
    ): String {
        if (number !in validIdleRange) return number.toString()
        rowChars?.let { return it }
        val head = idleHeadLabels[number] ?: return number.toString()
        return "$number\n$head"
    }

    protected fun getSymbolToggleLabel(): String = KeyboardMappings.symbolRow
}
