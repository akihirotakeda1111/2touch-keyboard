package com.example.twotouchkeyboard.candidate

import com.example.mozcengine.AlphabetPredictionSupport
import com.example.mozcengine.HiraganaPredictionSupport
import com.example.twotouchkeyboard.InputMode

/**
 * Ranks conversion candidates using usage frequency and Japanese reading-length priority.
 *
 * Japanese predictive conversion prefers candidates whose reading length matches the input.
 * Candidates without an explicit reading are treated as matching the input, so acquisition
 * order is preserved. English ranking uses learned usage counts.
 */
object CandidateRanker {

    fun rank(
        mode: InputMode,
        contextKey: String,
        candidates: List<String>,
        getUsageCount: (contextKey: String, candidate: String) -> Int,
    ): List<String> {
        return when (mode) {
            InputMode.ALPHABET -> rankByUsage(contextKey, candidates, getUsageCount)
            InputMode.HIRAGANA -> HiraganaPredictionSupport.rankCandidates(candidates, contextKey)
            InputMode.NUMBER -> candidates
        }
    }

    fun rankByUsage(
        contextKey: String,
        candidates: List<String>,
        getUsageCount: (contextKey: String, candidate: String) -> Int,
    ): List<String> {
        if (candidates.size <= 1) return candidates

        val lookupKey = AlphabetPredictionSupport.lookupInput(contextKey)
        return candidates
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<String>> { (_, candidate) ->
                    getUsageCount(lookupKey, candidate)
                }.thenBy { it.index },
            )
            .map { it.value }
    }
}
