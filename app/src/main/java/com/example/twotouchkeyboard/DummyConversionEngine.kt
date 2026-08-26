package com.example.twotouchkeyboard

import android.content.Context
import android.util.Log
import com.example.mozcengine.ConversionEngine
import com.example.mozcengine.ConversionMode
import com.example.mozcengine.MozcConversionEngine
import com.example.mozcengine.MozcSession
import com.example.twotouchkeyboard.english.EnglishDictionaryConversionEngine
import com.example.twotouchkeyboard.english.RoutingConversionEngine
import kotlinx.coroutines.delay

object ConversionEngineProvider {
    private const val TAG = "ConversionEngineProvider"

    fun create(context: Context): ConversionEngine {
        val englishEngine = EnglishDictionaryConversionEngine(context)
        val japaneseEngine = MozcConversionEngine.tryCreate(context)?.let { engine ->
            if (MozcSession.hasDictionaryData(context.applicationContext)) {
                Log.i(
                    TAG,
                    "Using Mozc conversion engine (dataVersion=${MozcSession.getDataVersion()})",
                )
                engine
            } else {
                Log.w(TAG, "Mozc dictionary unavailable; falling back to dummy conversion engine")
                engine.close()
                DummyConversionEngine()
            }
        } ?: run {
            Log.i(TAG, "Falling back to dummy conversion engine")
            DummyConversionEngine()
        }
        return RoutingConversionEngine(japaneseEngine, englishEngine)
    }
}

fun InputMode.toConversionMode(): ConversionMode = when (this) {
    InputMode.HIRAGANA -> ConversionMode.HIRAGANA
    InputMode.ALPHABET -> ConversionMode.ALPHABET
    InputMode.NUMBER -> ConversionMode.NUMBER
}

/**
 * ダミー変換エンジン。
 * libmozc.so / mozc.data が無い環境向けのフォールバック。
 */
class DummyConversionEngine : ConversionEngine {

    override suspend fun convert(input: String, mode: ConversionMode): List<String> {
        delay(100)
        if (input.isEmpty()) return emptyList()
        return when (mode) {
            ConversionMode.HIRAGANA -> lookupHiraganaCandidates(input)
            ConversionMode.ALPHABET -> emptyList()
            ConversionMode.NUMBER -> listOf(input)
        }
    }

    override fun close() = Unit

    override fun resetSession() = Unit

    override suspend fun suggestNext(
        mode: ConversionMode,
        selectedCandidate: String?,
    ): List<String> {
        if (mode != ConversionMode.HIRAGANA) return emptyList()
        return listOf("を", "が", "に")
    }

    private fun lookupHiraganaCandidates(input: String): List<String> {
        CANDIDATE_MAP[input]?.let { return it }

        val fallback = listOf(
            input,
            toFakeKanji(input),
            "${input}語",
        )
        return fallback.distinct()
    }

    private fun toFakeKanji(input: String): String {
        return input.map { char -> KANA_TO_KANJI[char] ?: char }.joinToString("")
    }

    companion object {
        private val KANA_TO_KANJI: Map<Char, Char> = mapOf(
            'あ' to '亜', 'い' to '以', 'う' to '宇', 'え' to '恵', 'お' to '於',
            'か' to '可', 'き' to '木', 'く' to '九', 'け' to '計', 'こ' to '子',
            'さ' to '佐', 'し' to '四', 'す' to '巣', 'せ' to '世', 'そ' to '曾',
            'た' to '太', 'ち' to '知', 'つ' to '津', 'て' to '天', 'と' to '戸',
            'な' to '奈', 'に' to '二', 'ぬ' to '奴', 'ね' to '根', 'の' to '野',
            'は' to '波', 'ひ' to '日', 'ふ' to '不', 'へ' to '部', 'ほ' to '保',
            'ま' to '真', 'み' to '美', 'む' to '無', 'め' to '目', 'も' to '模',
            'や' to '屋', 'ゆ' to '由', 'よ' to '与',
            'わ' to '和', 'を' to '緒', 'ん' to 'ン',
        )

        private val CANDIDATE_MAP: Map<String, List<String>> = mapOf(
            "あ" to listOf("あ", "亜", "阿", "安"),
            "あい" to listOf("あい", "愛", "合い", "藍", "相"),
            "か" to listOf("か", "可", "加", "科", "夏"),
            "かき" to listOf("かき", "書き", "牡蠣", "夏季"),
            "さく" to listOf("さく", "作", "咲", "策"),
            "さくら" to listOf("さくら", "桜", "佐倉"),
            "にほん" to listOf("にほん", "日本", "二本"),
            "てすと" to listOf("てすと", "テスト", "試験"),
        )
    }
}
