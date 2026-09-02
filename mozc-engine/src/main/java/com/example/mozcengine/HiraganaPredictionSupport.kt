package com.example.mozcengine

/**
 * 日本語の予測変換候補を、読み方の文字数が入力と同じものを優先して並べ替える。
 *
 * 読み方が空の候補は入力全体を読み方とみなす。読み方の文字数が同じなら取得順を維持する。
 * 任意の一般優先度は同じ読み長グループ内だけで最大3枠まで前進させる。
 */
object HiraganaPredictionSupport {

    fun rankCandidates(
        candidates: List<String>,
        input: String,
        readings: List<String> = emptyList(),
        getPriority: (reading: String, candidate: String) -> Int = NO_PRIORITY,
    ): List<String> {
        if (input.isEmpty() || candidates.size <= 1) return candidates

        val inputLength = characterCount(input)
        val groupCounters = IntArray(READING_GROUP_COUNT)
        return candidates
            .withIndex()
            .map { (index, value) ->
                val reading = readingFor(readings, index, input)
                val group = if (characterCount(reading) == inputLength) {
                    EXACT_READING_LENGTH_GROUP
                } else {
                    OTHER_READING_LENGTH_GROUP
                }
                val groupOriginalRank = groupCounters[group]++
                val priority = getPriority(reading, value).coerceIn(
                    JapaneseCandidatePrior.MIN_PRIORITY,
                    JapaneseCandidatePrior.MAX_PRIORITY,
                )
                RankedCandidate(
                    originalIndex = index,
                    value = value,
                    readingLengthGroup = group,
                    adjustedRank = groupOriginalRank - priority,
                )
            }
            .sortedWith(
                compareBy<RankedCandidate> { it.readingLengthGroup }
                    .thenBy { it.adjustedRank }
                    .thenBy { it.originalIndex },
            )
            .map { it.value }
            .distinct()
    }

    private fun readingFor(readings: List<String>, index: Int, input: String): String {
        return readings.getOrNull(index).orEmpty().ifEmpty { input }
    }

    private fun characterCount(text: String): Int = text.codePointCount(0, text.length)

    private data class RankedCandidate(
        val originalIndex: Int,
        val value: String,
        val readingLengthGroup: Int,
        val adjustedRank: Int,
    )

    private val NO_PRIORITY: (String, String) -> Int = { _, _ -> 0 }
    private const val READING_GROUP_COUNT = 2
    private const val EXACT_READING_LENGTH_GROUP = 0
    private const val OTHER_READING_LENGTH_GROUP = 1
}
