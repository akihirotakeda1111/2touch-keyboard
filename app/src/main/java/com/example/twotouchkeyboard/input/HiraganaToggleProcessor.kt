package com.example.twotouchkeyboard.input

import com.example.twotouchkeyboard.KeyboardKey
import com.example.twotouchkeyboard.KeyboardMappings

class HiraganaToggleProcessor(
    host: InputProcessorHost,
) : BaseInputProcessor(host) {

    private sealed class ActiveKey {
        data class Hiragana(val row: Int) : ActiveKey()
    }

    private var activeKey: ActiveKey? = null
    private var activeIndex: Int = 0

    override fun onKeyPressed(key: KeyboardKey) {
        when (key) {
            is KeyboardKey.Digit -> {
                if (key.number !in 1..9) return
                handleToggle(ActiveKey.Hiragana(key.number), KeyboardMappings.hiraganaRows[key.number] ?: return)
            }
            KeyboardKey.Zero -> {
                handleToggle(ActiveKey.Hiragana(0), KeyboardMappings.hiraganaRows[0] ?: return)
            }
            else -> Unit
        }
    }

    override fun getKeyLabel(key: KeyboardKey): String {
        return when (key) {
            is KeyboardKey.Digit -> {
                val rowChars = if (activeKey is ActiveKey.Hiragana &&
                    (activeKey as ActiveKey.Hiragana).row == key.number
                ) {
                    KeyboardMappings.hiraganaRows[key.number]
                } else {
                    null
                }
                getToggleDigitLabel(
                    number = key.number,
                    rowChars = rowChars,
                    idleHeadLabels = KeyboardMappings.hiraganaRowHeadLabels,
                    validIdleRange = 1..9,
                )
            }
            KeyboardKey.Zero -> {
                val rowChars = if (activeKey is ActiveKey.Hiragana &&
                    (activeKey as ActiveKey.Hiragana).row == 0
                ) {
                    KeyboardMappings.hiraganaRows[0]
                } else {
                    null
                }
                rowChars ?: KeyboardMappings.hiraganaRowHeadLabels[0] ?: "わ"
            }
            KeyboardKey.Star -> MODE_LABEL
            else -> super.getKeyLabel(key)
        }
    }

    override fun clearToggleState() {
        super.clearToggleState()
        activeKey = null
        activeIndex = 0
    }

    override fun resetPartialInput() {
        clearToggleState()
        refreshComposingPreview()
        host.onProcessorStateChanged()
    }

    override fun resetInputSession() {
        pendingChar = null
        activeKey = null
        activeIndex = 0
        refreshComposingPreview()
        host.onProcessorStateChanged()
    }

    override fun confirmPendingInput() {
        super.confirmPendingInput()
        activeKey = null
        activeIndex = 0
        host.onProcessorStateChanged()
    }

    private fun handleToggle(key: ActiveKey, chars: String) {
        if (activeKey == key) {
            activeIndex = (activeIndex + 1) % chars.length
        } else {
            confirmPendingInput()
            activeKey = key
            activeIndex = 0
        }
        pendingChar = chars[activeIndex].toString()
        refreshComposingPreview()
        host.onProcessorStateChanged()
        scheduleToggleAutoCommit()
    }

    companion object {
        private const val MODE_LABEL = "あ"
    }
}
