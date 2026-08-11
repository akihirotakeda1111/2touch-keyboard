package com.example.twotouchkeyboard.candidate

import com.example.mozcengine.AlphabetPredictionSupport
import com.example.twotouchkeyboard.InputMode

/**
 * Ranks conversion candidates using usage frequency.
 *
 * This is the unified entry point for Option 4. Japanese mode currently passes
 * candidates through unchanged because Mozc handles ranking via user history.
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
            else -> candidates
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
