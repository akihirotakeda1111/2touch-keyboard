package com.example.twotouchkeyboard.candidate

import com.example.mozcengine.ConversionEngine
import com.example.mozcengine.ConversionMode
import com.example.twotouchkeyboard.InputMode

/**
 * Coordinates candidate usage learning and ranking across input modes.
 *
 * Japanese learning is delegated to Mozc user history (Option 3).
 * Japanese predictive conversion always prefers candidates whose length matches the input.
 * English learning uses [EnglishCandidateUsageStore] (Option 2).
 * [CandidateRanker] is the unified ranking entry point.
 */
class CandidateLearningCoordinator(
    private val conversionEngine: ConversionEngine,
    private val englishUsageStore: EnglishCandidateUsageStore,
) {
    @Volatile
    var learningEnabled: Boolean = true

    fun recordCommit(context: CandidateUsageContext) {
        if (!learningEnabled) return

        when (context.mode) {
            InputMode.HIRAGANA -> {
                conversionEngine.recordCandidateSelection(
                    contextKey = context.contextKey,
                    candidate = context.candidate,
                    mode = ConversionMode.HIRAGANA,
                )
            }
            InputMode.ALPHABET -> {
                englishUsageStore.record(
                    prefix = context.contextKey,
                    candidate = context.candidate,
                )
            }
            InputMode.NUMBER -> Unit
        }
    }

    fun rank(
        mode: InputMode,
        contextKey: String,
        candidates: List<String>,
    ): List<String> {
        if (!learningEnabled && mode != InputMode.HIRAGANA) return candidates

        return CandidateRanker.rank(
            mode = mode,
            contextKey = contextKey,
            candidates = candidates,
            getUsageCount = { prefix, candidate ->
                englishUsageStore.getCount(prefix, candidate)
            },
        )
    }

    fun clearHistory(mode: InputMode? = null) {
        when (mode) {
            null -> {
                conversionEngine.clearCandidateUsageHistory(null)
                englishUsageStore.clear()
            }
            InputMode.HIRAGANA -> {
                conversionEngine.clearCandidateUsageHistory(ConversionMode.HIRAGANA)
            }
            InputMode.ALPHABET -> {
                englishUsageStore.clear()
            }
            InputMode.NUMBER -> Unit
        }
    }
}
