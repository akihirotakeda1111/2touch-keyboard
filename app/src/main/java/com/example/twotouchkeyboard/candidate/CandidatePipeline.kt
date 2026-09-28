package com.example.twotouchkeyboard.candidate

import com.example.mozcengine.AlphabetPredictionSupport
import com.example.mozcengine.ConversionCandidate
import com.example.mozcengine.HiraganaPredictionSupport
import com.example.mozcengine.JapaneseCandidatePrior
import com.example.twotouchkeyboard.InputMode

enum class CandidateRequestKind { CONVERSION, NEXT_INPUT }

/** The single entry point for deciding which candidates to display and in what order. */
class CandidatePipeline(
    private val getUsageCount: (String, String) -> Int = { _, _ -> 0 },
    private val getJapanesePriority: (String, String) -> Int = { reading, value ->
        JapaneseCandidatePrior.current().priorityOf(reading, value)
    },
) {
    fun prepare(
        mode: InputMode,
        input: String,
        candidates: List<ConversionCandidate>,
        kind: CandidateRequestKind = CandidateRequestKind.CONVERSION,
    ): List<ConversionCandidate> {
        // Zero-query suggestions retain engine order and never fall back to composing text.
        if (kind == CandidateRequestKind.NEXT_INPUT) return candidates
        if (input.isEmpty()) return emptyList()

        return when (mode) {
            InputMode.HIRAGANA -> {
                HiraganaPredictionSupport.rankEligibleCandidates(
                    candidates,
                    input,
                    getJapanesePriority,
                ).ifEmpty {
                    listOf(ConversionCandidate(input, input))
                }
            }
            InputMode.ALPHABET -> {
                val byValue = candidates.groupBy { it.value }
                val prepared = AlphabetPredictionSupport.prepareEnglishCandidates(
                    candidates.map { it.value }, input,
                ).map { value -> byValue[value]?.first() ?: ConversionCandidate(value, input) }
                CandidateRanker.rankByUsage(input, prepared, getUsageCount)
            }
            InputMode.NUMBER -> candidates.ifEmpty { listOf(ConversionCandidate(input, input)) }
        }
    }
}
