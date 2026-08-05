package com.example.twotouchkeyboard.input

import com.example.twotouchkeyboard.KeyboardKey
import com.example.twotouchkeyboard.KeyboardMappings

class AlphabetToggleProcessor(
    host: InputProcessorHost,
) : BaseInputProcessor(host) {

    private var activeRow: Int? = null
    private var activeIndex: Int = 0

    override fun onKeyPressed(key: KeyboardKey) {
        when (key) {
            is KeyboardKey.Digit -> {
                if (key.number !in 2..9) return
                val chars = KeyboardMappings.alphabetRows[key.number] ?: return
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
            }
            KeyboardKey.Star, KeyboardKey.Zero, KeyboardKey.Hash -> Unit
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
                    validIdleRange = 2..9,
                )
            }
            KeyboardKey.Star -> MODE_LABEL
            KeyboardKey.Zero -> "0"
            KeyboardKey.Hash -> "#"
        }
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
        refreshComposingPreview()
        host.onProcessorStateChanged()
    }

    override fun confirmPendingInput() {
        super.confirmPendingInput()
        activeRow = null
        activeIndex = 0
        host.onProcessorStateChanged()
    }

    companion object {
        private const val MODE_LABEL = "A"
    }
}
