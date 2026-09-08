package com.example.mozcengine

/**
 * CandidatePipeline から呼ぶ、日本語候補の除外・並べ替えポリシー。
 *
 * filterCandidates は読み方が入力文字数未満の候補を除外する。
 * rankEligibleCandidates は適格候補のみを受け取り、同じ読み長の候補を優先する。
 * 読み方が空の候補は入力全体を読み方とみなす。同じ読み長・一般優先度なら取得順を維持する。
 * 任意の一般優先度は同じ読み長グループ内だけで最大3枠まで前進させる。
 */
object HiraganaPredictionSupport {

    fun filterCandidates(
        candidates: List<ConversionCandidate>,
        input: String,
    ): List<ConversionCandidate> {
        if (input.isEmpty()) return candidates
        val inputLength = characterCount(input)
        return candidates.filter { characterCount(it.reading.ifEmpty { input }) >= inputLength }
    }

    fun rankEligibleCandidates(
        candidates: List<ConversionCandidate>,
        input: String,
        getPriority: (reading: String, candidate: String) -> Int = NO_PRIORITY,
    ): List<ConversionCandidate> {
        if (input.isEmpty()) return candidates

        val inputLength = characterCount(input)
        if (candidates.size <= 1) return candidates

        val groupCounters = IntArray(READING_GROUP_COUNT)
        return candidates.withIndex()
            .map { (index, value) ->
                val reading = value.reading.ifEmpty { input }
                val group = if (characterCount(reading) == inputLength) {
                    EXACT_READING_LENGTH_GROUP
                } else {
                    OTHER_READING_LENGTH_GROUP
                }
                val groupOriginalRank = groupCounters[group]++
                val priority = getPriority(reading, value.value).coerceIn(
                    JapaneseCandidatePrior.MIN_PRIORITY,
                    JapaneseCandidatePrior.MAX_PRIORITY,
                )
                RankedCandidate(
                    originalIndex = index,
                    value = value,
                    readingLengthGroup = group,
                    adjustedRank = groupOriginalRank - priority,
                    priority = priority,
                )
            }
            .sortedWith(
                compareBy<RankedCandidate> { it.readingLengthGroup }
                    .thenBy { it.adjustedRank }
                    .thenByDescending { it.priority }
                    .thenBy { it.originalIndex },
            )
            .map { it.value }
            .distinctBy { it.value }
    }

    private fun characterCount(text: String): Int = text.codePointCount(0, text.length)

    private data class RankedCandidate(
        val originalIndex: Int,
        val value: ConversionCandidate,
        val readingLengthGroup: Int,
        val adjustedRank: Int,
        val priority: Int,
    )

    private val NO_PRIORITY: (String, String) -> Int = { _, _ -> 0 }
    private const val READING_GROUP_COUNT = 2
    private const val EXACT_READING_LENGTH_GROUP = 0
    private const val OTHER_READING_LENGTH_GROUP = 1
}
