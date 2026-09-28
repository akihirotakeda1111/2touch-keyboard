package com.example.twotouchkeyboard

/**
 * 記号パネルの配置と、入力モードごとの文字。
 *
 * 縦表示は4列×6行。横表示は同じ読み順を8列×3行へ広げる。
 * どちらも23マスが記号で、末尾は閉じるキー。段数は通常キーボードと揃える。
 * パネルを開いた時点の入力モードで表示と入力文字を決める。
 * ひらがなは全角、英字と数字は半角。
 * 「、。」は残し、追加する「，．」とは別キーにする。
 * 英字・数字モードの「、。・」は半角の「､｡･」。
 */
object SymbolPanel {
    const val COLUMN_COUNT = 4
    const val ROW_COUNT = 6
    const val CLOSE_COLUMN = 3
    const val CLOSE_ROW = 5
    const val LANDSCAPE_COLUMN_COUNT = 8
    const val LANDSCAPE_ROW_COUNT = 3
    const val LANDSCAPE_CLOSE_COLUMN = 7
    const val LANDSCAPE_CLOSE_ROW = 2

    data class Key(
        val viewId: Int,
        val column: Int,
        val row: Int,
        val fullWidth: String,
        val halfWidth: String,
    ) {
        fun characterFor(mode: InputMode): String = when (mode) {
            InputMode.HIRAGANA -> fullWidth
            InputMode.ALPHABET, InputMode.NUMBER -> halfWidth
        }

        /** 縦の読み順を保ったまま、横表示の8列へ割り当てた列。 */
        val landscapeColumn: Int
            get() = linearIndex % LANDSCAPE_COLUMN_COUNT

        /** 縦の読み順を保ったまま、横表示の3段へ割り当てた行。 */
        val landscapeRow: Int
            get() = linearIndex / LANDSCAPE_COLUMN_COUNT

        private val linearIndex: Int
            get() = row * COLUMN_COUNT + column
    }

    val keys: List<Key> = listOf(
        key(R.id.symbol_key_ideographic_comma, 0, 0, "\u3001", "\uFF64"), // 、 ､
        key(R.id.symbol_key_ideographic_period, 1, 0, "\u3002", "\uFF61"), // 。 ｡
        key(R.id.symbol_key_comma, 2, 0, "\uFF0C", "\u002C"), // ， ,
        key(R.id.symbol_key_period, 3, 0, "\uFF0E", "\u002E"), // ． .
        key(R.id.symbol_key_exclamation, 0, 1, "\uFF01", "\u0021"), // ！ !
        key(R.id.symbol_key_question, 1, 1, "\uFF1F", "\u003F"), // ？ ?
        key(R.id.symbol_key_colon, 2, 1, "\uFF1A", "\u003A"), // ： :
        key(R.id.symbol_key_semicolon, 3, 1, "\uFF1B", "\u003B"), // ； ;
        key(R.id.symbol_key_left_parenthesis, 0, 2, "\uFF08", "\u0028"), // （ (
        key(R.id.symbol_key_right_parenthesis, 1, 2, "\uFF09", "\u0029"), // ） )
        key(R.id.symbol_key_left_brace, 2, 2, "\uFF5B", "\u007B"), // ｛ {
        key(R.id.symbol_key_right_brace, 3, 2, "\uFF5D", "\u007D"), // ｝ }
        key(R.id.symbol_key_hyphen, 0, 3, "\uFF0D", "\u002D"), // － -
        key(R.id.symbol_key_underscore, 1, 3, "\uFF3F", "\u005F"), // ＿ _
        key(R.id.symbol_key_plus, 2, 3, "\uFF0B", "\u002B"), // ＋ +
        key(R.id.symbol_key_equals, 3, 3, "\uFF1D", "\u003D"), // ＝ =
        key(R.id.symbol_key_at, 0, 4, "\uFF20", "\u0040"), // ＠ @
        key(R.id.symbol_key_hash, 1, 4, "\uFF03", "\u0023"), // ＃ #
        key(R.id.symbol_key_ampersand, 2, 4, "\uFF06", "\u0026"), // ＆ &
        key(R.id.symbol_key_dollar, 3, 4, "\uFF04", "\u0024"), // ＄ $
        key(R.id.symbol_key_asterisk, 0, 5, "\uFF0A", "\u002A"), // ＊ *
        key(R.id.symbol_key_slash, 1, 5, "\uFF0F", "\u002F"), // ／ /
        key(R.id.symbol_key_middle_dot, 2, 5, "\u30FB", "\uFF65"), // ・ ･
    )

    init {
        require(keys.size == COLUMN_COUNT * ROW_COUNT - 1) {
            "Symbol panel must contain 23 symbols."
        }
        require(keys.map { it.viewId }.distinct().size == keys.size) {
            "Symbol keys must use distinct view ids."
        }
        val occupied = keys.map { it.column to it.row }.toSet()
        require(occupied.size == keys.size) {
            "Symbol keys must occupy distinct cells."
        }
        for (row in 0 until ROW_COUNT) {
            for (column in 0 until COLUMN_COUNT) {
                val isCloseCell = column == CLOSE_COLUMN && row == CLOSE_ROW
                val occupiedBySymbol = (column to row) in occupied
                require(occupiedBySymbol != isCloseCell) {
                    "Unexpected symbol cell at column=$column row=$row."
                }
            }
        }
        val closeIndex = CLOSE_ROW * COLUMN_COUNT + CLOSE_COLUMN
        require(LANDSCAPE_CLOSE_COLUMN == closeIndex % LANDSCAPE_COLUMN_COUNT) {
            "Landscape close column must stay at the end of the reading order."
        }
        require(LANDSCAPE_CLOSE_ROW == closeIndex / LANDSCAPE_COLUMN_COUNT) {
            "Landscape close row must stay at the end of the reading order."
        }
        val landscapeOccupied = keys.map { it.landscapeColumn to it.landscapeRow }.toSet()
        require(landscapeOccupied.size == keys.size) {
            "Landscape symbol keys must occupy distinct cells."
        }
        require((LANDSCAPE_CLOSE_COLUMN to LANDSCAPE_CLOSE_ROW) !in landscapeOccupied) {
            "Landscape close cell must not contain a symbol."
        }
        for (row in 0 until LANDSCAPE_ROW_COUNT) {
            for (column in 0 until LANDSCAPE_COLUMN_COUNT) {
                val isCloseCell = column == LANDSCAPE_CLOSE_COLUMN && row == LANDSCAPE_CLOSE_ROW
                val occupiedBySymbol = (column to row) in landscapeOccupied
                require(occupiedBySymbol != isCloseCell) {
                    "Unexpected landscape symbol cell at column=$column row=$row."
                }
            }
        }
    }

    /** パネルを開いた時点のモードで固定する、表示と入力で共通の文字。 */
    fun charactersFor(mode: InputMode): Map<Int, String> {
        return keys.associate { it.viewId to it.characterFor(mode) }
    }

    fun rowsFor(mode: InputMode): List<List<String>> {
        return (0 until ROW_COUNT).map { row ->
            keys.filter { it.row == row }
                .sortedBy { it.column }
                .map { it.characterFor(mode) }
        }
    }

    /** 横表示の段。閉じるキーのマスは含まない。 */
    fun landscapeRowsFor(mode: InputMode): List<List<String>> {
        return (0 until LANDSCAPE_ROW_COUNT).map { row ->
            keys.filter { it.landscapeRow == row }
                .sortedBy { it.landscapeColumn }
                .map { it.characterFor(mode) }
        }
    }

    private fun key(
        viewId: Int,
        column: Int,
        row: Int,
        fullWidth: String,
        halfWidth: String,
    ): Key {
        require(fullWidth.length == 1 && halfWidth.length == 1) {
            "Each symbol must be a single character."
        }
        require(column in 0 until COLUMN_COUNT && row in 0 until ROW_COUNT) {
            "Symbol key is outside the 4x6 grid."
        }
        return Key(viewId, column, row, fullWidth, halfWidth)
    }
}
