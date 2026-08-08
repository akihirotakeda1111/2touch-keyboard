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

    override fun resetSession() {
        japaneseEngine.resetSession()
        englishEngine.resetSession()
    }

    override fun close() {
        japaneseEngine.close()
        englishEngine.close()
    }
}
