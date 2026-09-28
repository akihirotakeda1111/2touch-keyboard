package com.example.twotouchkeyboard.candidate

import com.example.mozcengine.ConversionCandidate
import com.example.twotouchkeyboard.InputMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidatePipelineTest {
    private val pipeline = CandidatePipeline(getJapanesePriority = { _, _ -> 0 })

    @Test
    fun japanese_ranksByReadingLength_whileKeepingShorterReadings() {
        val candidates = listOf(
            ConversionCandidate("漢字語", "かんじご"), ConversionCandidate("漢", "かん"),
            ConversionCandidate("漢字", "かんじ"), ConversionCandidate("感じ", ""),
        )
        assertEquals(
            listOf(candidates[2], candidates[3], candidates[0], candidates[1]),
            pipeline.prepare(InputMode.HIRAGANA, "かんじ", candidates),
        )
    }

    @Test
    fun japanese_fallsBackToInput_onlyWhenCandidatesAreMissing() {
        assertEquals(
            listOf(ConversionCandidate("かんじ", "かんじ")),
            pipeline.prepare(InputMode.HIRAGANA, "かんじ", emptyList()),
        )
        assertEquals(
            listOf(ConversionCandidate("漢", "かん")),
            pipeline.prepare(InputMode.HIRAGANA, "かんじ", listOf(ConversionCandidate("漢", "かん"))),
        )
    }

    @Test
    fun japanese_keepsItteHomophones_withShorterContentReadings() {
        val iku = ConversionCandidate("行って", "いって")
        val literal = ConversionCandidate("いって", "いって")
        val katakana = ConversionCandidate("イッテ", "いって")
        val iu = ConversionCandidate("言って", "いっ")
        val iuVariant = ConversionCandidate("云って", "いっ")
        assertEquals(
            listOf(iku, literal, katakana, iu, iuVariant),
            pipeline.prepare(
                InputMode.HIRAGANA,
                "いって",
                listOf(iku, literal, katakana, iu, iuVariant),
            ),
        )
    }

    @Test
    fun japanese_deduplicatesAfterRanking_andRetainsWinningReading() {
        val candidates = listOf(
            ConversionCandidate("漢字", "かんじご"), ConversionCandidate("感じ", "かんじ"),
            ConversionCandidate("漢字", "かんじ"),
        )
        assertEquals(
            listOf(candidates[1], candidates[2]),
            pipeline.prepare(InputMode.HIRAGANA, "かんじ", candidates),
        )
    }

    @Test
    fun japanese_boostedShortReadingStaysBehindExactGroup() {
        val pipeline = CandidatePipeline(getJapanesePriority = { _, value ->
            if (value == "漢" || value == "漢字語") 3 else 0
        })
        val kanjiGo = ConversionCandidate("漢字語", "かんじご")
        val kan = ConversionCandidate("漢", "かん")
        val kanji = ConversionCandidate("漢字", "かんじ")
        assertEquals(
            listOf(kanji, kanjiGo, kan),
            pipeline.prepare(InputMode.HIRAGANA, "かんじ", listOf(kanjiGo, kan, kanji)),
        )
    }

    @Test
    fun english_selectsCompletions_beforeUsageRanking_andRetainsReadings() {
        val candidates = listOf(
            ConversionCandidate("hel", "hel"), ConversionCandidate("heap", "heap"),
            ConversionCandidate("help", "help"), ConversionCandidate("hello", "hello"),
        )
        val pipeline = CandidatePipeline(getUsageCount = { _, value ->
            when (value) { "heap" -> 100; "hello" -> 5; else -> 0 }
        })
        assertEquals(listOf(candidates[3], candidates[2], candidates[0]),
            pipeline.prepare(InputMode.ALPHABET, "hel", candidates))
    }

    @Test
    fun english_keepsCorrections_withoutExactInputOrDuplicates() {
        val help = ConversionCandidate("help", "help")
        assertEquals(listOf(help), pipeline.prepare(InputMode.ALPHABET, "hlep", listOf(
            ConversionCandidate("hlep", "hlep"), help, help, ConversionCandidate("変換"),
        )))
    }

    @Test
    fun english_fallsBackUsingOriginalCase_andRejectsInvalidInput() {
        assertEquals(listOf(ConversionCandidate("Hel", "Hel")),
            pipeline.prepare(InputMode.ALPHABET, "Hel", emptyList()))
        assertEquals(emptyList<ConversionCandidate>(),
            pipeline.prepare(InputMode.ALPHABET, "あ", emptyList()))
    }

    @Test
    fun nextInput_keepsShortCandidatesAndEngineOrder_withoutApplyingRankers() {
        val pipeline = CandidatePipeline(
            getUsageCount = { _, _ -> error("No usage ranking for next input") },
            getJapanesePriority = { _, _ -> error("No reading ranking for next input") },
        )
        val candidates = listOf(ConversionCandidate("を", "を"), ConversionCandidate("が", "が"))
        assertEquals(candidates, pipeline.prepare(InputMode.HIRAGANA, "かんじ", candidates, CandidateRequestKind.NEXT_INPUT))
        assertEquals(emptyList<ConversionCandidate>(),
            pipeline.prepare(InputMode.HIRAGANA, "かんじ", emptyList(), CandidateRequestKind.NEXT_INPUT))
    }

    @Test
    fun conversionWithEmptyInput_hasNoFallback_inAnyMode() {
        for (mode in InputMode.entries) {
            assertEquals(emptyList<ConversionCandidate>(), pipeline.prepare(mode, "", emptyList()))
        }
    }

    @Test
    fun number_preservesCandidates_andFallsBackOnlyWhenEmpty() {
        val candidates = listOf(ConversionCandidate("１２", "12"), ConversionCandidate("12", "12"))
        assertEquals(candidates, pipeline.prepare(InputMode.NUMBER, "12", candidates))
        assertEquals(listOf(ConversionCandidate("12", "12")), pipeline.prepare(InputMode.NUMBER, "12", emptyList()))
    }
}
