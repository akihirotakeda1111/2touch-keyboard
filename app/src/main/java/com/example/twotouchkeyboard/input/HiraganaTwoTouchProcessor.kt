package com.example.twotouchkeyboard.input

import com.example.twotouchkeyboard.KeyboardKey
import com.example.twotouchkeyboard.KeyboardMappings

class HiraganaTwoTouchProcessor(
    host: InputProcessorHost,
) : BaseInputProcessor(host) {

    enum class State { IDLE, WAITING_VOWEL }

    private sealed class ActiveRow {
        data class Hiragana(val row: Int) : ActiveRow()
    }

    var state: State = State.IDLE
        private set

    private var activeRow: ActiveRow? = null

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
            else -> super.getKeyLabel(key)
        }
    }

    override fun hasPartialTwoTouchInput(): Boolean = state == State.WAITING_VOWEL

    override fun resetPartialInput() {
        super.resetPartialInput()
        transitionTo(State.IDLE, activeRow = null)
    }

    override fun resetInputSession() {
        super.resetInputSession()
        transitionTo(State.IDLE, activeRow = null)
    }

    private fun handleIdle(key: KeyboardKey) {
        when (key) {
            is KeyboardKey.Digit -> {
                if (key.number !in 1..9) return
                transitionTo(State.WAITING_VOWEL, ActiveRow.Hiragana(key.number))
            }
            KeyboardKey.Zero -> {
                transitionTo(State.WAITING_VOWEL, ActiveRow.Hiragana(0))
            }
            else -> Unit
        }
    }

    private fun handleWaitingSecondKey(key: KeyboardKey) {
        val row = (activeRow as? ActiveRow.Hiragana)?.row ?: return
        val appended = TwoTouchExtensionSupport.appendFromSecondKey(
            row = row,
            key = key,
            primaryRows = KeyboardMappings.hiraganaRows,
            extensionRows = KeyboardMappings.hiraganaExtensionRows,
            append = host::appendConfirmedCharacter,
        )
        if (appended) {
            transitionTo(State.IDLE, activeRow = null)
        }
    }

    private fun labelForDigit(number: Int): String {
        if (state == State.WAITING_VOWEL) {
            activeRow?.let { active ->
                if (active is ActiveRow.Hiragana) {
                    TwoTouchExtensionSupport.waitingLabel(
                        row = active.row,
                        key = KeyboardKey.Digit(number),
                        primaryRows = KeyboardMappings.hiraganaRows,
                        extensionRows = KeyboardMappings.hiraganaExtensionRows,
                    )?.let { return it }
                }
            }
        }

        if (number !in 1..9) return number.toString()
        val head = KeyboardMappings.hiraganaRowHeadLabels[number] ?: return number.toString()
        return "$number\n$head"
    }

    private fun labelForZero(): String {
        if (state == State.WAITING_VOWEL) {
            activeRow?.let { active ->
                if (active is ActiveRow.Hiragana) {
                    TwoTouchExtensionSupport.waitingLabel(
                        row = active.row,
                        key = KeyboardKey.Zero,
                        primaryRows = KeyboardMappings.hiraganaRows,
                        extensionRows = KeyboardMappings.hiraganaExtensionRows,
                    )?.let { return it }
                }
            }
        }

        val head = KeyboardMappings.hiraganaExtensionHeadLabels[0] ?: "0"
        return "0\n$head"
    }

    private fun transitionTo(newState: State, activeRow: ActiveRow?) {
        state = newState
        this.activeRow = if (newState == State.IDLE) null else activeRow
        host.onProcessorStateChanged()
    }

    companion object {
        private const val MODE_LABEL = "あ"
    }
}
