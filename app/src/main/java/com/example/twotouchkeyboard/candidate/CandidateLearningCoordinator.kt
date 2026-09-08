package com.example.twotouchkeyboard.candidate

import com.example.mozcengine.ConversionEngine
import com.example.mozcengine.ConversionMode
import com.example.twotouchkeyboard.InputMode

/** Records candidate learning; display policy belongs to CandidatePipeline. */
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

    fun getUsageCount(contextKey: String, candidate: String): Int {
        return if (learningEnabled) englishUsageStore.getCount(contextKey, candidate) else 0
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
