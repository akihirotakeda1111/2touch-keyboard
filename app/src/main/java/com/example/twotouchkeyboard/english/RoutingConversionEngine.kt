package com.example.twotouchkeyboard.english

import com.example.mozcengine.ConversionEngine
import com.example.mozcengine.ConversionMode

/**
 * Routes conversion requests to a mode-specific backend engine.
 */
class RoutingConversionEngine(
    private val japaneseEngine: ConversionEngine,
    private val englishEngine: ConversionEngine,
) : ConversionEngine {

    override val isMozc: Boolean
        get() = japaneseEngine.isMozc

    override suspend fun convert(input: String, mode: ConversionMode): List<String> {
        return when (mode) {
            ConversionMode.ALPHABET -> englishEngine.convert(input, mode)
            ConversionMode.HIRAGANA, ConversionMode.NUMBER -> japaneseEngine.convert(input, mode)
        }
    }

    override suspend fun suggestNext(
        mode: ConversionMode,
        selectedCandidate: String?,
    ): List<String> {
        return when (mode) {
            ConversionMode.HIRAGANA -> japaneseEngine.suggestNext(mode, selectedCandidate)
            ConversionMode.ALPHABET, ConversionMode.NUMBER -> emptyList()
        }
    }

    override fun resetSession() {
        japaneseEngine.resetSession()
        englishEngine.resetSession()
    }

    override fun recordCandidateSelection(
        contextKey: String,
        candidate: String,
        mode: ConversionMode,
    ) {
        when (mode) {
            ConversionMode.ALPHABET -> englishEngine.recordCandidateSelection(
                contextKey,
                candidate,
                mode,
            )
            ConversionMode.HIRAGANA, ConversionMode.NUMBER -> japaneseEngine.recordCandidateSelection(
                contextKey,
                candidate,
                mode,
            )
        }
    }

    override fun clearCandidateUsageHistory(mode: ConversionMode?) {
        if (mode == null) {
            japaneseEngine.clearCandidateUsageHistory(null)
            englishEngine.clearCandidateUsageHistory(null)
            return
        }
        when (mode) {
            ConversionMode.ALPHABET -> englishEngine.clearCandidateUsageHistory(mode)
            ConversionMode.HIRAGANA, ConversionMode.NUMBER -> japaneseEngine.clearCandidateUsageHistory(mode)
        }
    }

    override fun close() {
        japaneseEngine.close()
        englishEngine.close()
    }
}
