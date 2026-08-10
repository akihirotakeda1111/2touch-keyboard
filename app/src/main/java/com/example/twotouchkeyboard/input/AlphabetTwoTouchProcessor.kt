package com.example.twotouchkeyboard.input

import com.example.twotouchkeyboard.KeyboardKey
import com.example.twotouchkeyboard.KeyboardMappings

class AlphabetTwoTouchProcessor(
    host: InputProcessorHost,
) : BaseInputProcessor(host), AlphabetCaseModifierSupport {

    enum class State { IDLE, WAITING_VOWEL }

    var state: State = State.IDLE
        private set

    private var activeRow: Int? = null

    override fun onKeyPressed(key: KeyboardKey) {
        when (state) {
            State.IDLE -> handleIdle(key)
            State.WAITING_VOWEL -> handleWaitingSecondKey(key)
        }
    }

    override fun getKeyLabel(key: KeyboardKey): String {
        return when (key) {
            is KeyboardKey.Digit -> labelForDigit(key.number)
            KeyboardKey.Zero -> labelForZero()
            KeyboardKey.Star -> MODE_LABEL
            KeyboardKey.Hash -> "#"
            else -> super.getKeyLabel(key)
        }
    }

    override fun toggleLastCharacterCase() {
        val last = host.getConfirmedBuffer().lastOrNull()?.takeIf { it.isLetter() } ?: return
        host.replaceLastConfirmedCharacter(toggleCase(last))
    }

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
            KeyboardKey.Zero -> {
                activeRow = 0
                state = State.WAITING_VOWEL
                host.onProcessorStateChanged()
            }
            else -> Unit
        }
    }

    private fun handleWaitingSecondKey(key: KeyboardKey) {
        val row = activeRow ?: return
        val appended = TwoTouchExtensionSupport.appendFromSecondKey(
            row = row,
            key = key,
            primaryRows = KeyboardMappings.alphabetRows,
            extensionRows = KeyboardMappings.alphabetExtensionRows,
            append = host::appendConfirmedCharacter,
        )
        if (appended) {
            state = State.IDLE
            activeRow = null
            host.onProcessorStateChanged()
        }
    }

    private fun labelForDigit(number: Int): String {
        if (state == State.WAITING_VOWEL) {
            activeRow?.let { row ->
                TwoTouchExtensionSupport.waitingLabel(
                    row = row,
                    key = KeyboardKey.Digit(number),
                    primaryRows = KeyboardMappings.alphabetRows,
                    extensionRows = KeyboardMappings.alphabetExtensionRows,
                )?.let { return it }
            }
        }

        if (number !in 2..9) return number.toString()
        val head = KeyboardMappings.alphabetRowHeadLabels[number] ?: return number.toString()
        return "$number\n$head"
    }

    private fun labelForZero(): String {
        if (state == State.WAITING_VOWEL) {
            activeRow?.let { row ->
                TwoTouchExtensionSupport.waitingLabel(
                    row = row,
                    key = KeyboardKey.Zero,
                    primaryRows = KeyboardMappings.alphabetRows,
                    extensionRows = KeyboardMappings.alphabetExtensionRows,
                )?.let { return it }
            }
        }

        val head = KeyboardMappings.alphabetExtensionHeadLabels[0] ?: "0"
        return "0\n$head"
    }

    private fun toggleCase(char: Char): Char {
        return if (char.isLowerCase()) char.uppercaseChar() else char.lowercaseChar()
    }

    companion object {
        private const val MODE_LABEL = "A"
    }
}
