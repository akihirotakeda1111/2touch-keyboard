package com.example.twotouchkeyboard.input

import com.example.twotouchkeyboard.KeyboardKey
import com.example.twotouchkeyboard.KeyboardMappings

class AlphabetTwoTouchProcessor(
    host: InputProcessorHost,
) : BaseInputProcessor(host), UppercaseToggleSupport {

    enum class State { IDLE, WAITING_VOWEL }

    var state: State = State.IDLE
        private set

    private var activeRow: Int? = null
    private var uppercasePreferred: Boolean = false

    override fun onKeyPressed(key: KeyboardKey) {
        when (state) {
            State.IDLE -> handleIdle(key)
            State.WAITING_VOWEL -> handleWaitingVowel(key)
        }
    }

    override fun getKeyLabel(key: KeyboardKey): String {
        return when (key) {
            is KeyboardKey.Digit -> getTwoTouchDigitLabel(
                number = key.number,
                waiting = state == State.WAITING_VOWEL,
                activeChars = activeRow?.let { selectableCharacters(it) },
                idleHeadLabels = KeyboardMappings.alphabetRowHeadLabels,
                validIdleRange = 2..9,
            )
            KeyboardKey.Star -> "$MODE_SWITCH_LABEL\n$MODE_LABEL"
            KeyboardKey.Zero -> "0"
            KeyboardKey.Hash -> "#"
            else -> super.getKeyLabel(key)
        }
    }

    override fun toggleUppercase() {
        uppercasePreferred = !uppercasePreferred
        host.onProcessorStateChanged()
    }

    override fun isUppercasePreferred(): Boolean = uppercasePreferred

    override fun hasPartialTwoTouchInput(): Boolean = state == State.WAITING_VOWEL

    override fun resetPartialInput() {
        super.resetPartialInput()
        state = State.IDLE
        activeRow = null
        host.onProcessorStateChanged()
    }

    override fun resetInputSession() {
        super.resetInputSession()
        state = State.IDLE
        activeRow = null
        uppercasePreferred = false
        host.onProcessorStateChanged()
    }

    private fun handleIdle(key: KeyboardKey) {
        when (key) {
            is KeyboardKey.Digit -> {
                if (key.number !in 2..9) return
                activeRow = key.number
                state = State.WAITING_VOWEL
                host.onProcessorStateChanged()
            }
            else -> Unit
        }
    }

    private fun handleWaitingVowel(key: KeyboardKey) {
        when (key) {
            is KeyboardKey.Digit -> {
                val row = activeRow ?: return
                val chars = selectableCharacters(row) ?: return
                if (key.number !in 1..chars.length) return
                val index = key.number - 1
                host.appendConfirmedCharacter(chars[index].toString())
                state = State.IDLE
                activeRow = null
                host.onProcessorStateChanged()
            }
            else -> Unit
        }
    }

    private fun selectableCharacters(row: Int): String? {
        val chars = KeyboardMappings.alphabetRows[row] ?: return null
        if (!uppercasePreferred) return chars
        return buildString {
            chars.forEach { char ->
                append(if (char.isLowerCase()) char.uppercaseChar() else char)
            }
        }
    }

    companion object {
        private const val MODE_LABEL = "A"
        private const val MODE_SWITCH_LABEL = "切替"
    }
}
