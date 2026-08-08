package com.example.twotouchkeyboard.input

import com.example.twotouchkeyboard.KeyboardKey
import com.example.twotouchkeyboard.KeyboardMappings

class AlphabetToggleProcessor(
    host: InputProcessorHost,
) : BaseInputProcessor(host), UppercaseToggleSupport {

    private var activeRow: Int? = null
    private var activeIndex: Int = 0
    private var uppercasePreferred: Boolean = false

    override fun onKeyPressed(key: KeyboardKey) {
        when (key) {
            is KeyboardKey.Digit -> {
                if (key.number !in 2..9) return
                val chars = selectableCharacters(key.number) ?: return
                if (activeRow == key.number) {
                    activeIndex = (activeIndex + 1) % chars.length
                } else {
                    confirmPendingInput()
                    activeRow = key.number
                    activeIndex = 0
                }
                pendingChar = chars[activeIndex].toString()
                refreshComposingPreview()
                host.onProcessorStateChanged()
                scheduleToggleAutoCommit()
            }
            else -> Unit
        }
    }

    override fun getKeyLabel(key: KeyboardKey): String {
        return when (key) {
            is KeyboardKey.Digit -> {
                val rowChars = if (activeRow == key.number) {
                    selectableCharacters(key.number)
                } else {
                    null
                }
                getToggleDigitLabel(
                    number = key.number,
                    rowChars = rowChars,
                    idleHeadLabels = KeyboardMappings.alphabetRowHeadLabels,
                    validIdleRange = 2..9,
                )
            }
            KeyboardKey.Star -> "$MODE_SWITCH_LABEL\n$MODE_LABEL"
            KeyboardKey.Zero -> "0"
            KeyboardKey.Hash -> "#"
            else -> super.getKeyLabel(key)
        }
    }

    override fun toggleUppercase() {
        uppercasePreferred = !uppercasePreferred
        if (activeRow != null) {
            val chars = selectableCharacters(activeRow!!) ?: return
            activeIndex = activeIndex.coerceIn(0, chars.lastIndex)
            pendingChar = chars[activeIndex].toString()
            refreshComposingPreview()
        }
        host.onProcessorStateChanged()
    }

    override fun isUppercasePreferred(): Boolean = uppercasePreferred

    override fun clearToggleState() {
        super.clearToggleState()
        activeRow = null
        activeIndex = 0
    }

    override fun resetPartialInput() {
        confirmPendingInput()
        activeRow = null
        activeIndex = 0
        host.onProcessorStateChanged()
    }

    override fun resetInputSession() {
        pendingChar = null
        activeRow = null
        activeIndex = 0
        uppercasePreferred = false
        refreshComposingPreview()
        host.onProcessorStateChanged()
    }

    override fun confirmPendingInput() {
        super.confirmPendingInput()
        activeRow = null
        activeIndex = 0
        host.onProcessorStateChanged()
    }

    private fun selectableCharacters(row: Int): String? {
        val chars = KeyboardMappings.alphabetRows[row] ?: return null
        if (!uppercasePreferred) return chars
        return chars.filter { it.isUpperCase() }.ifEmpty { chars }
    }

    companion object {
        private const val MODE_LABEL = "A"
        private const val MODE_SWITCH_LABEL = "切替"
    }
}
