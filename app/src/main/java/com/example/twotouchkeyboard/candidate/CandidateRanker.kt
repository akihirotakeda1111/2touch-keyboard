package com.example.twotouchkeyboard.candidate

import com.example.mozcengine.AlphabetPredictionSupport
import com.example.mozcengine.ConversionCandidate

/** Reorders eligible English candidates by usage, retaining readings and stable ties. */
object CandidateRanker {
    fun rankByUsage(
        contextKey: String,
        candidates: List<ConversionCandidate>,
        getUsageCount: (contextKey: String, candidate: String) -> Int,
    ): List<ConversionCandidate> {
        if (candidates.size <= 1) return candidates
        val lookupKey = AlphabetPredictionSupport.lookupInput(contextKey)
        return candidates.sortedByDescending { getUsageCount(lookupKey, it.value) }
    }
}
