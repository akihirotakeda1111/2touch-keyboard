package com.example.twotouchkeyboard

/**
 * かな修飾（小文字・濁点・半濁点）の変換表。
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

    private val CYCLE_MAP: Map<Char, Char> = buildCycleMap()

    fun applyDakuten(char: Char): Char? = DAKUTEN_MAP[char]

    fun applyHandakuten(char: Char): Char? = HANDAKUTEN_MAP[char]

    fun applySmallKana(char: Char): Char? = SMALL_KANA_MAP[char]

    fun cycle(char: Char): Char? = CYCLE_MAP[char]

    private fun buildCycleMap(): Map<Char, Char> {
        val bases = buildSet {
            addAll(DAKUTEN_MAP.keys)
            addAll(HANDAKUTEN_MAP.keys)
            addAll(SMALL_KANA_MAP.keys)
        }
        val cycleMap = mutableMapOf<Char, Char>()
        for (base in bases) {
            val cycle = buildCycleForBase(base)
            if (cycle.size <= 1) continue
            for (index in cycle.indices) {
                cycleMap[cycle[index]] = cycle[(index + 1) % cycle.size]
            }
        }
        return cycleMap
    }

    private fun buildCycleForBase(base: Char): List<Char> {
        val forms = mutableListOf(base)
        applySmallKana(base)?.let { small ->
            if (small !in forms) forms.add(small)
        }
        applyDakuten(base)?.let { dakuten ->
            if (dakuten !in forms) forms.add(dakuten)
        }
        applyHandakuten(base)?.let { handakuten ->
            if (handakuten !in forms) forms.add(handakuten)
        }
        return forms
    }
}
