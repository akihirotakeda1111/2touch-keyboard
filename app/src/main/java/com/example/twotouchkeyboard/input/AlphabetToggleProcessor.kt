package com.example.twotouchkeyboard.input

import com.example.twotouchkeyboard.KeyboardKey
import com.example.twotouchkeyboard.KeyboardMappings

class AlphabetToggleProcessor(
    host: InputProcessorHost,
) : BaseInputProcessor(host), AlphabetCaseModifierSupport {

    private var activeRow: Int? = null
    private var activeIndex: Int = 0

    override fun onKeyPressed(key: KeyboardKey) {
        when (key) {
            is KeyboardKey.Digit -> {
                if (key.number !in 1..9) return
                val chars = KeyboardMappings.alphabetRows[key.number] ?: return
                handleToggle(row = key.number, chars = chars)
            }
            KeyboardKey.Zero -> {
                handleToggle(row = DIGIT_ROW, chars = KeyboardMappings.alphabetToggleDigitRow)
            }
            else -> Unit
        }
    }

    override fun getKeyLabel(key: KeyboardKey): String {
        return when (key) {
            is KeyboardKey.Digit -> {
                val rowChars = if (activeRow == key.number) {
                    KeyboardMappings.alphabetRows[key.number]
                } else {
                    null
                }
                getToggleDigitLabel(
                    number = key.number,
                    rowChars = rowChars,
                    idleHeadLabels = KeyboardMappings.alphabetRowHeadLabels,
                    validIdleRange = 1..9,
                )
            }
            KeyboardKey.Star -> MODE_LABEL
            KeyboardKey.Zero -> {
                if (activeRow == DIGIT_ROW) {
                    KeyboardMappings.alphabetToggleDigitRow
                } else {
                    "0"
                }
            }
            KeyboardKey.Hash -> "#"
            else -> super.getKeyLabel(key)
        }
    }

    override fun toggleLastCharacterCase() {
        pendingChar?.singleOrNull()?.takeIf { it.isLetter() }?.let { pending ->
            pendingChar = toggleCase(pending).toString()
            refreshComposingPreview()
            host.onProcessorStateChanged()
            return
        }

        val buffer = host.getConfirmedBuffer()
        val last = buffer.lastOrNull()?.takeIf { it.isLetter() } ?: return
        host.replaceLastConfirmedCharacter(toggleCase(last))
    }

    override fun clearToggleState() {
        super.clearToggleState()
        activeRow = null
        activeIndex = 0
    }

    override fun resetPartialInput() {
        clearToggleState()
        refreshComposingPreview()
        host.onProcessorStateChanged()
    }

    override fun resetInputSession() {
        pendingChar = null
        activeRow = null
        activeIndex = 0
        refreshComposingPreview()
        host.onProcessorStateChanged()
    }

    override fun confirmPendingInput() {
        super.confirmPendingInput()
        activeRow = null
        activeIndex = 0
        host.onProcessorStateChanged()
    }

    private fun handleToggle(row: Int, chars: String) {
        if (activeRow == row) {
            activeIndex = (activeIndex + 1) % chars.length
        } else {
            confirmPendingInput()
            activeRow = row
            activeIndex = 0
        }
        pendingChar = chars[activeIndex].toString()
        refreshComposingPreview()
        host.onProcessorStateChanged()
        scheduleToggleAutoCommit()
    }

    private fun toggleCase(char: Char): Char {
        return if (char.isLowerCase()) char.uppercaseChar() else char.lowercaseChar()
    }

    companion object {
        private const val MODE_LABEL = "A"
        private const val DIGIT_ROW = 0
    }
}
