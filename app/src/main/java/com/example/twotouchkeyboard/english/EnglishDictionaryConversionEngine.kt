package com.example.twotouchkeyboard.english

import android.content.Context
import android.util.Log
import com.example.mozcengine.AlphabetPredictionSupport
import com.example.mozcengine.ConversionEngine
import com.example.mozcengine.ConversionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * English prefix prediction engine backed by a frequency-ranked dictionary.
 *
 * This engine is used instead of Mozc for [ConversionMode.ALPHABET].
 */
class EnglishDictionaryConversionEngine(
    context: Context,
) : ConversionEngine {

    private val appContext = context.applicationContext
    @Volatile
    private var dictionary: EnglishPrefixDictionary? = null

    override suspend fun convert(input: String, mode: ConversionMode): List<String> {
        if (mode != ConversionMode.ALPHABET) return emptyList()
        if (input.isEmpty()) return emptyList()

        return withContext(Dispatchers.Default) {
            val dict = loadDictionary()
            val suggestions = dict.suggest(input, limit = EnglishPrefixDictionary.MAX_CANDIDATES)
            AlphabetPredictionSupport.prepareEnglishCandidates(suggestions + input, input)
        }
    }

    override fun resetSession() = Unit

    override fun close() = Unit

    private fun loadDictionary(): EnglishPrefixDictionary {
        dictionary?.let { return it }
        synchronized(this) {
            dictionary?.let { return it }
            Log.i(TAG, "Loading English prefix dictionary")
            return EnglishPrefixDictionary.load(appContext).also { dictionary = it }
        }
    }

    companion object {
        private const val TAG = "EnglishDictionaryEngine"
    }
}
