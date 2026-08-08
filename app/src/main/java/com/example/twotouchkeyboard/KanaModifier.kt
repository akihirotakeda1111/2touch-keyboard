package com.example.twotouchkeyboard

/**
 * かな修飾（濁点・半濁点・小文字）の変換表。
 */
object KanaModifier {

    private val DAKUTEN_MAP: Map<Char, Char> = mapOf(
        'か' to 'が', 'き' to 'ぎ', 'く' to 'ぐ', 'け' to 'げ', 'こ' to 'ご',
        'さ' to 'ざ', 'し' to 'じ', 'す' to 'ず', 'せ' to 'ぜ', 'そ' to 'ぞ',
        'た' to 'だ', 'ち' to 'ぢ', 'つ' to 'づ', 'て' to 'で', 'と' to 'ど',
        'は' to 'ば', 'ひ' to 'び', 'ふ' to 'ぶ', 'へ' to 'べ', 'ほ' to 'ぼ',
        'ウ' to 'ヴ',
    )

    private val HANDAKUTEN_MAP: Map<Char, Char> = mapOf(
        'は' to 'ぱ', 'ひ' to 'ぴ', 'ふ' to 'ぷ', 'へ' to 'ぺ', 'ほ' to 'ぽ',
    )

    private val SMALL_KANA_MAP: Map<Char, Char> = mapOf(
        'あ' to 'ぁ', 'い' to 'ぃ', 'う' to 'ぅ', 'え' to 'ぇ', 'お' to 'ぉ',
        'つ' to 'っ', 'や' to 'ゃ', 'ゆ' to 'ゅ', 'よ' to 'ょ', 'わ' to 'ゎ',
        'ア' to 'ァ', 'イ' to 'ィ', 'ウ' to 'ゥ', 'エ' to 'ェ', 'オ' to 'ォ',
        'ツ' to 'ッ', 'ヤ' to 'ャ', 'ユ' to 'ュ', 'ヨ' to 'ョ', 'ワ' to 'ヮ',
    )

    fun applyDakuten(char: Char): Char? = DAKUTEN_MAP[char]

    fun applyHandakuten(char: Char): Char? = HANDAKUTEN_MAP[char]

    fun applySmallKana(char: Char): Char? = SMALL_KANA_MAP[char]
}
