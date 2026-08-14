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

    override fun getTwoTouchWaitingRowKey(): KeyboardKey? {
        if (state != State.WAITING_VOWEL) return null
        val row = activeRow ?: return null
        return rowKey(row)
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
                if (key.number !in 1..9) return
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
            maxPrimarySecondKey = 6,
            preferExtensionOnConflict = true,
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
                if (row == number) {
                    return idleLabelForRow(number)
                }
                return TwoTouchExtensionSupport.waitingLabel(
                    row = row,
                    key = KeyboardKey.Digit(number),
                    primaryRows = KeyboardMappings.alphabetRows,
                    extensionRows = KeyboardMappings.alphabetExtensionRows,
                    maxPrimarySecondKey = 6,
                    preferExtensionOnConflict = true,
                ).orEmpty()
            }
        }

        return idleLabelForRow(number)
    }

    private fun labelForZero(): String {
        if (state == State.WAITING_VOWEL) {
            activeRow?.let { row ->
                if (row == 0) {
                    return idleLabelForRow(0)
                }
                return TwoTouchExtensionSupport.waitingLabel(
                    row = row,
                    key = KeyboardKey.Zero,
                    primaryRows = KeyboardMappings.alphabetRows,
                    extensionRows = KeyboardMappings.alphabetExtensionRows,
                    maxPrimarySecondKey = 6,
                    preferExtensionOnConflict = true,
                ).orEmpty()
            }
        }

        return idleLabelForRow(0)
    }

    private fun idleLabelForRow(row: Int): String {
        if (row !in 1..9) return "0"
        return KeyboardMappings.alphabetTwoTouchIdleLabel(row) ?: row.toString()
    }

    private fun rowKey(row: Int): KeyboardKey {
        return if (row == 0) KeyboardKey.Zero else KeyboardKey.Digit(row)
    }

    private fun toggleCase(char: Char): Char {
        return if (char.isLowerCase()) char.uppercaseChar() else char.lowercaseChar()
    }

    companion object {
        private const val MODE_LABEL = "A"
    }
}
