package com.example.twotouchkeyboard

/**
 * ポケベル式 2 タッチ入力のステートマシン。
 *
 * HIRAGANA: 1〜9 で行、1〜5 で段。# 1 打目で記号行。
 * ALPHABET: 2〜9 で英字行、1〜5 で段（21=A, 22=B, 23=C…）。
 * NUMBER: 本クラスでは処理せず、サービス側で 1 タッチ即確定。
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
        fun onComposingTextUpdated(composingText: String)
        fun onInputModeChanged(mode: InputMode)
    }

    private sealed class ActiveRow {
        data class Hiragana(val row: Int) : ActiveRow()
        data object Symbol : ActiveRow()
        data class Alphabet(val row: Int) : ActiveRow()
    }

    private val hiraganaRows: Map<Int, String> = mapOf(
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

    private val alphabetRows: Map<Int, String> = mapOf(
        2 to "ABC",
        3 to "DEF",
        4 to "GHI",
        5 to "JKL",
        6 to "MNO",
        7 to "PQRS",
        8 to "TUV",
        9 to "WXYZ",
    )

    private val symbolRow = "、。？！・"

    private val hiraganaRowHeadLabels: Map<Int, String> = hiraganaRows.mapValues { (_, chars) ->
        chars.first().toString()
    }

    private val alphabetRowHeadLabels: Map<Int, String> = alphabetRows.mapValues { (_, chars) ->
        chars.first().toString()
    }

    private val composingText = StringBuilder()

    var state: State = State.IDLE
        private set

    var inputMode: InputMode = InputMode.HIRAGANA
        private set

    private var activeRow: ActiveRow? = null

    fun onKeyPressed(key: Key) {
        if (inputMode == InputMode.NUMBER) return

        when (state) {
            State.IDLE -> handleIdle(key)
            State.WAITING_VOWEL -> handleWaitingVowel(key)
        }
    }

    fun getKeyLabel(key: Key): String {
        return when (inputMode) {
            InputMode.HIRAGANA -> getHiraganaKeyLabel(key)
            InputMode.ALPHABET -> getAlphabetKeyLabel(key)
            InputMode.NUMBER -> getNumberKeyLabel(key)
        }
    }

    fun getComposingText(): String = composingText.toString()

    fun appendConfirmedCharacter(character: String) {
        composingText.append(character)
        listener.onComposingTextUpdated(composingText.toString())
    }

    fun clearComposingText() {
        if (composingText.isEmpty()) return
        composingText.clear()
        listener.onComposingTextUpdated("")
    }

    fun reset() {
        transitionTo(State.IDLE, activeRow = null)
    }

    fun resetInputSession() {
        reset()
        clearComposingText()
    }

    /** HIRAGANA → ALPHABET → NUMBER → HIRAGANA と巡回 */
    fun cycleInputMode(): InputMode {
        if (state == State.WAITING_VOWEL) {
            reset()
        }
        inputMode = when (inputMode) {
            InputMode.HIRAGANA -> InputMode.ALPHABET
            InputMode.ALPHABET -> InputMode.NUMBER
            InputMode.NUMBER -> InputMode.HIRAGANA
        }
        listener.onInputModeChanged(inputMode)
        listener.onStateChanged(state, null)
        return inputMode
    }

    private fun handleIdle(key: Key) {
        when (inputMode) {
            InputMode.HIRAGANA -> handleIdleHiragana(key)
            InputMode.ALPHABET -> handleIdleAlphabet(key)
            InputMode.NUMBER -> Unit
        }
    }

    private fun handleIdleHiragana(key: Key) {
        when (key) {
            is Key.Digit -> {
                if (key.number !in 1..9) return
                transitionTo(State.WAITING_VOWEL, ActiveRow.Hiragana(key.number))
            }
            Key.Hash -> transitionTo(State.WAITING_VOWEL, ActiveRow.Symbol)
            Key.Star, Key.Zero -> Unit
        }
    }

    private fun handleIdleAlphabet(key: Key) {
        when (key) {
            is Key.Digit -> {
                if (key.number !in 2..9) return
                transitionTo(State.WAITING_VOWEL, ActiveRow.Alphabet(key.number))
            }
            Key.Star, Key.Zero, Key.Hash -> Unit
        }
    }

    private fun handleWaitingVowel(key: Key) {
        when (key) {
            is Key.Digit -> {
                if (key.number !in 1..5) return
                val row = activeRow ?: return
                val index = key.number - 1
                val character = when (row) {
                    is ActiveRow.Hiragana -> {
                        val chars = hiraganaRows[row.row] ?: return
                        if (index >= chars.length) return
                        chars[index].toString()
                    }
                    ActiveRow.Symbol -> {
                        if (index >= symbolRow.length) return
                        symbolRow[index].toString()
                    }
                    is ActiveRow.Alphabet -> {
                        val chars = alphabetRows[row.row] ?: return
                        if (index >= chars.length) return
                        chars[index].toString()
                    }
                }
                appendConfirmedCharacter(character)
                reset()
            }
            Key.Star, Key.Zero, Key.Hash -> Unit
        }
    }

    private fun getHiraganaKeyLabel(key: Key): String {
        return when (key) {
            is Key.Digit -> getTwoTouchDigitLabel(
                number = key.number,
                idleHeadLabels = hiraganaRowHeadLabels,
                validIdleRange = 1..9,
            )
            Key.Star -> MODE_LABEL_HIRAGANA
            Key.Zero -> "0"
            Key.Hash -> if (state == State.IDLE) "#\n、" else "#"
        }
    }

    private fun getAlphabetKeyLabel(key: Key): String {
        return when (key) {
            is Key.Digit -> getTwoTouchDigitLabel(
                number = key.number,
                idleHeadLabels = alphabetRowHeadLabels,
                validIdleRange = 2..9,
            )
            Key.Star -> MODE_LABEL_ALPHABET
            Key.Zero -> "0"
            Key.Hash -> "#"
        }
    }

    private fun getNumberKeyLabel(key: Key): String {
        return when (key) {
            is Key.Digit -> key.number.toString()
            Key.Star -> MODE_LABEL_NUMBER
            Key.Zero -> "0"
            Key.Hash -> "#"
        }
    }

    private fun getTwoTouchDigitLabel(
        number: Int,
        idleHeadLabels: Map<Int, String>,
        validIdleRange: IntRange,
    ): String {
        if (state == State.WAITING_VOWEL && activeRow != null) {
            if (number !in 1..5) return number.toString()
            val chars = when (val row = activeRow) {
                is ActiveRow.Hiragana -> hiraganaRows[row.row]
                ActiveRow.Symbol -> symbolRow
                is ActiveRow.Alphabet -> alphabetRows[row.row]
                null -> return number.toString()
            } ?: return number.toString()
            val index = number - 1
            return if (index < chars.length) chars[index].toString() else number.toString()
        }

        if (number !in validIdleRange) return number.toString()
        val head = idleHeadLabels[number] ?: return number.toString()
        return "$number\n$head"
    }

    private fun transitionTo(newState: State, activeRow: ActiveRow?) {
        state = newState
        this.activeRow = if (newState == State.IDLE) null else activeRow
        val rowKey = when (val row = this.activeRow) {
            is ActiveRow.Hiragana -> row.row
            is ActiveRow.Alphabet -> row.row
            ActiveRow.Symbol -> 0
            null -> null
        }
        listener.onStateChanged(newState, rowKey)
    }

    sealed class Key {
        data class Digit(val number: Int) : Key()
        data object Star : Key()
        data object Zero : Key()
        data object Hash : Key()
    }

    companion object {
        private const val MODE_LABEL_HIRAGANA = "あ"
        private const val MODE_LABEL_ALPHABET = "A"
        private const val MODE_LABEL_NUMBER = "123"
    }
}
