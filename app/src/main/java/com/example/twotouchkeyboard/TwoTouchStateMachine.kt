package com.example.twotouchkeyboard

/**
 * ポケベル式 2 タッチ入力のステートマシン。
 *
 * 1 打目: キー 1〜9 で行（あ・か・さ…）を選択 → [WAITING_VOWEL]
 * 2 打目: キー 1〜5 で段（あいうえお）を選択 → 文字確定 → [IDLE]
 * 2 打目待ち中に * キー → [IDLE] へリセット
 */
class TwoTouchStateMachine(
    private val listener: Listener,
) {

    enum class State {
        IDLE,
        WAITING_VOWEL,
    }

    interface Listener {
        fun onStateChanged(state: State, rowKey: Int?)
        fun onCharacterConfirmed(character: String)
    }

    private val rows: Map<Int, String> = mapOf(
        1 to "あいうえお",
        2 to "かきくけこ",
        3 to "さしすせそ",
        4 to "たちつてと",
        5 to "なにぬねの",
        6 to "はひふへほ",
        7 to "まみむめも",
        8 to "やゆよ",
        9 to "わをん",
    )

    /** IDLE 状態でキー 1〜9 に表示する行ラベル（先頭文字） */
    private val rowHeadLabels: Map<Int, String> = rows.mapValues { (_, chars) ->
        chars.first().toString()
    }

    var state: State = State.IDLE
        private set

    /** 1 打目で選択された行キー（1〜9） */
    var selectedRowKey: Int? = null
        private set

    fun onKeyPressed(key: Key) {
        when (state) {
            State.IDLE -> handleIdle(key)
            State.WAITING_VOWEL -> handleWaitingVowel(key)
        }
    }

    /** キーに表示するラベルを返す */
    fun getKeyLabel(key: Key): String {
        return when (key) {
            is Key.Digit -> getDigitLabel(key.number)
            Key.Star -> if (state == State.WAITING_VOWEL) "削除" else "*"
            Key.Zero -> "0"
            Key.Hash -> "#"
        }
    }

    fun reset() {
        transitionTo(State.IDLE, rowKey = null)
    }

    private fun handleIdle(key: Key) {
        when (key) {
            is Key.Digit -> {
                if (key.number !in 1..9) return
                selectedRowKey = key.number
                transitionTo(State.WAITING_VOWEL, rowKey = key.number)
            }
            Key.Star, Key.Zero, Key.Hash -> Unit
        }
    }

    private fun handleWaitingVowel(key: Key) {
        when (key) {
            Key.Star -> reset()
            is Key.Digit -> {
                if (key.number !in 1..5) return
                val rowKey = selectedRowKey ?: return
                val row = rows[rowKey] ?: return
                val index = key.number - 1
                if (index >= row.length) return
                listener.onCharacterConfirmed(row[index].toString())
                reset()
            }
            Key.Zero, Key.Hash -> Unit
        }
    }

    private fun getDigitLabel(number: Int): String {
        if (number !in 1..9) return number.toString()

        if (state == State.WAITING_VOWEL && selectedRowKey != null) {
            val row = rows[selectedRowKey] ?: return number.toString()
            return when (number) {
                in 1..5 -> {
                    val index = number - 1
                    if (index < row.length) row[index].toString() else number.toString()
                }
                else -> number.toString()
            }
        }

        val head = rowHeadLabels[number] ?: return number.toString()
        return "$number\n$head"
    }

    private fun transitionTo(newState: State, rowKey: Int?) {
        state = newState
        if (newState == State.IDLE) {
            selectedRowKey = null
        }
        listener.onStateChanged(newState, rowKey)
    }

    sealed class Key {
        data class Digit(val number: Int) : Key()
        data object Star : Key()
        data object Zero : Key()
        data object Hash : Key()
    }
}
