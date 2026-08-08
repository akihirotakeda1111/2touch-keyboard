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
            State.WAITING_VOWEL -> handleWaitingVowel(key)
        }
    }

    override fun getKeyLabel(key: KeyboardKey): String {
        return when (key) {
            is KeyboardKey.Digit -> getTwoTouchDigitLabel(
                number = key.number,
                waiting = state == State.WAITING_VOWEL,
                activeChars = activeRowChars(),
                idleHeadLabels = KeyboardMappings.hiraganaRowHeadLabels,
                validIdleRange = 1..9,
            )
            KeyboardKey.Star -> MODE_LABEL
            KeyboardKey.Zero -> "0"
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
            else -> Unit
        }
    }

    private fun handleWaitingVowel(key: KeyboardKey) {
        when (key) {
            is KeyboardKey.Digit -> {
                if (key.number !in 1..5) return
                val row = activeRow as? ActiveRow.Hiragana ?: return
                val index = key.number - 1
                val chars = KeyboardMappings.hiraganaRows[row.row] ?: return
                if (index >= chars.length) return
                host.appendConfirmedCharacter(chars[index].toString())
                transitionTo(State.IDLE, activeRow = null)
            }
            else -> Unit
        }
    }

    private fun activeRowChars(): String? {
        return when (val row = activeRow) {
            is ActiveRow.Hiragana -> KeyboardMappings.hiraganaRows[row.row]
            null -> null
        }
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
