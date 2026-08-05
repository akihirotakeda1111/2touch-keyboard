package com.example.twotouchkeyboard.input

import com.example.twotouchkeyboard.KeyboardKey
import com.example.twotouchkeyboard.KeyboardMappings

abstract class BaseInputProcessor(
    protected val host: InputProcessorHost,
) : InputProcessor {

    protected var pendingChar: String? = null

    override fun resetPartialInput() {
        pendingChar = null
        refreshComposingPreview()
    }

    override fun resetInputSession() {
        resetPartialInput()
    }

    override fun confirmPendingInput() {
        pendingChar?.let { host.appendConfirmedCharacter(it) }
        pendingChar = null
        refreshComposingPreview()
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
            if (number !in 1..5) return number.toString()
            val index = number - 1
            return if (index < activeChars.length) activeChars[index].toString() else number.toString()
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
