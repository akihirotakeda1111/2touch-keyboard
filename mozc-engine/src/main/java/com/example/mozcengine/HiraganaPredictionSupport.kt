package com.example.mozcengine

/**
 * 日本語の予測変換候補を、読み方の文字数が入力と同じものを優先して並べ替える。
 *
 * 読み方が空の候補は入力全体を読み方とみなす。読み方の文字数が同じなら取得順を維持する。
 */
object HiraganaPredictionSupport {

    fun rankCandidates(
        candidates: List<String>,
        input: String,
        readings: List<String> = emptyList(),
    ): List<String> {
        if (input.isEmpty() || candidates.size <= 1) return candidates

        val inputLength = characterCount(input)
        return candidates
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<String>> { (index, _) ->
                    val reading = readingFor(readings, index, input)
                    if (characterCount(reading) == inputLength) 0 else 1
                }.thenBy { it.index },
            )
            .map { it.value }
            .distinct()
    }

    private fun readingFor(readings: List<String>, index: Int, input: String): String {
        return readings.getOrNull(index).orEmpty().ifEmpty { input }
    }

    private fun characterCount(text: String): Int = text.codePointCount(0, text.length)
}
