package com.example.mozcengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HiraganaPredictionSupportTest {

    @Test
    fun rankCandidates_keepsAcquisitionOrder_whenReadingsMatchInput() {
        val candidates = listOf("わたし", "ワタシ", "私")

        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = candidates,
            input = "わたし",
            readings = listOf("わたし", "わたし", "わたし"),
        )

        assertEquals(candidates, ranked)
    }

    @Test
    fun rankCandidates_defaultsMissingReadingsToInput() {
        val candidates = listOf("わたし", "ワタシ", "私")

        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = candidates,
            input = "わたし",
        )

        assertEquals(candidates, ranked)
    }

    @Test
    fun rankCandidates_prioritizesReadingsMatchingInputLength() {
        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = listOf("感謝しています", "漢", "漢字", "感", "感じ"),
            input = "かん",
            readings = listOf("かんしゃしています", "かん", "かんじ", "かん", "かんじ"),
        )

        assertEquals(listOf("漢", "感", "感謝しています", "漢字", "感じ"), ranked)
    }

    @Test
    fun rankCandidates_keepsEngineOrder_withinSameReadingLength() {
        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = listOf("愛", "合い", "藍", "相手", "挨拶"),
            input = "あい",
            readings = listOf("あい", "あい", "あい", "あいて", "あいさつ"),
        )

        assertEquals(listOf("愛", "合い", "藍", "相手", "挨拶"), ranked)
    }

    @Test
    fun rankCandidates_treatsEmptyReadingAsInput() {
        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = listOf("私は", "私", "わたし"),
            input = "わたし",
            readings = listOf("わたしは", "", "わたし"),
        )

        assertEquals(listOf("私", "わたし", "私は"), ranked)
    }

    @Test
    fun rankCandidates_ignoresCandidateDisplayLength() {
        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = listOf("𠮷野", "𠮷", "吉"),
            input = "よし",
            readings = listOf("よしの", "よし", "よし"),
        )

        assertEquals(listOf("𠮷", "吉", "𠮷野"), ranked)
    }

    @Test
    fun rankCandidates_preservesOrder_whenNoReadingMatchesInputLength() {
        val candidates = listOf("桜", "佐倉")

        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = candidates,
            input = "さくら",
            readings = listOf("さくらんぼ", "さくらんぼ"),
        )

        assertEquals(candidates, ranked)
    }

    @Test
    fun rankCandidates_hidesReadingsShorterThanInput() {
        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = listOf("漢", "漢字", "感", "感じ"),
            input = "かんじ",
            readings = listOf("かん", "かんじ", "かん", "かんじ"),
        )

        assertEquals(listOf("漢字", "感じ"), ranked)
    }

    @Test
    fun rankCandidates_keepsLongerReadingsAfterHidingShorterOnes() {
        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = listOf("愛", "亜", "相手", "合い", "挨拶"),
            input = "あい",
            readings = listOf("あい", "あ", "あいて", "あい", "あいさつ"),
        )

        assertEquals(listOf("愛", "合い", "相手", "挨拶"), ranked)
    }

    @Test
    fun rankCandidates_hidesSoleCandidate_whenReadingIsShorterThanInput() {
        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = listOf("漢"),
            input = "かんじ",
            readings = listOf("かん"),
        )

        assertEquals(emptyList<String>(), ranked)
    }

    @Test
    fun rankCandidates_returnsOriginal_whenInputIsEmpty() {
        val candidates = listOf("を", "が", "に")

        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = candidates,
            input = "",
        )

        assertEquals(candidates, ranked)
    }

    @Test
    fun rankCandidates_countsSupplementaryPlaneCharactersInReadingAsOne() {
        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = listOf("予測", "一致", "別候補"),
            input = "𠮷",
            readings = listOf("𠮷野", "𠮷", "よし"),
        )

        assertEquals(listOf("一致", "予測", "別候補"), ranked)
    }

    @Test
    fun rankCandidates_hidesSupplementaryPlaneReadingShorterThanInput() {
        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = listOf("𠮷", "𠮷野", "吉"),
            input = "𠮷野",
            readings = listOf("𠮷", "𠮷野", "よし"),
        )

        assertEquals(listOf("𠮷野", "吉"), ranked)
    }

    @Test
    fun rankCandidates_hidesBoostedCandidate_whenReadingIsShorterThanInput() {
        val ranked = rankWithPrior(
            candidates = listOf("漢", "漢字", "感", "感じ"),
            input = "かんじ",
            readings = listOf("かん", "かんじ", "かん", "かんじ"),
            tsv = "かん\t漢\t3\n",
        )

        assertEquals(listOf("漢字", "感じ"), ranked)
    }

    @Test
    fun rankCandidates_keepsOriginalOrder_whenPriorTableIsEmpty() {
        val candidates = listOf("感謝しています", "漢", "漢字", "感", "感じ")
        val readings = listOf("かんしゃしています", "かん", "かんじ", "かん", "かんじ")

        val withoutPrior = HiraganaPredictionSupport.rankCandidates(
            candidates = candidates,
            input = "かん",
            readings = readings,
        )
        val withEmptyPrior = HiraganaPredictionSupport.rankCandidates(
            candidates = candidates,
            input = "かん",
            readings = readings,
            getPriority = JapaneseCandidatePrior.EMPTY::priorityOf,
        )

        assertEquals(listOf("漢", "感", "感謝しています", "漢字", "感じ"), withoutPrior)
        assertEquals(withoutPrior, withEmptyPrior)
    }

    @Test
    fun rankCandidates_advancesCandidateByAdvertisedPriorityInSameGroup() {
        val candidates = listOf("零", "一", "二", "三", "四", "五", "六")

        for (priority in 1..3) {
            val ranked = rankWithPrior(
                candidates = candidates,
                input = "あ",
                readings = List(7) { "あ" },
                tsv = "あ\t六\t$priority\n",
            )

            assertEquals(
                "priority $priority must move the candidate forward by $priority slots",
                priority,
                candidates.indexOf("六") - ranked.indexOf("六"),
            )
        }
    }

    @Test
    fun rankCandidates_doesNotLetBoostedLongerPredictionOvertakeExactReadingMatch() {
        val ranked = rankWithPrior(
            candidates = listOf("感謝しています", "漢", "漢字", "感", "感じ"),
            input = "かん",
            readings = listOf("かんしゃしています", "かん", "かんじ", "かん", "かんじ"),
            tsv = "かんじ\t感じ\t3\n",
        )

        assertEquals(listOf("漢", "感", "感じ", "感謝しています", "漢字"), ranked)
        assertTrue(ranked.indexOf("漢") < ranked.indexOf("感じ"))
        assertTrue(ranked.indexOf("感") < ranked.indexOf("感じ"))
    }

    @Test
    fun rankCandidates_prefersHigherPriority_whenAdjustedRanksTie() {
        val ranked = rankWithPrior(
            candidates = listOf("愛", "合い", "藍", "相"),
            input = "あい",
            readings = List(4) { "あい" },
            tsv = "あい\t合い\t1\nあい\t藍\t2\n",
        )

        assertEquals(listOf("藍", "合い", "愛", "相"), ranked)
    }

    @Test
    fun rankCandidates_usesInputAsReading_whenReadingIsEmpty_forPriorLookup() {
        val ranked = rankWithPrior(
            candidates = listOf("私は", "わたし", "私"),
            input = "わたし",
            readings = listOf("わたしは", "わたし", ""),
            tsv = "わたし\t私\t3\n",
        )

        assertEquals(listOf("私", "わたし", "私は"), ranked)
    }

    @Test
    fun rankCandidates_countsSupplementaryPlaneCharactersAsOne_withPriorityApplied() {
        val ranked = rankWithPrior(
            candidates = listOf("予測", "一致", "別候補"),
            input = "𠮷",
            readings = listOf("𠮷野", "𠮷", "よし"),
            tsv = "𠮷野\t予測\t3\n",
        )

        assertEquals(listOf("一致", "予測", "別候補"), ranked)
    }

    private fun rankWithPrior(
        candidates: List<String>,
        input: String,
        readings: List<String>,
        tsv: String,
    ): List<String> {
        val prior = JapaneseCandidatePrior.parse(tsv)
        return HiraganaPredictionSupport.rankCandidates(
            candidates = candidates,
            input = input,
            readings = readings,
            getPriority = prior::priorityOf,
        )
    }
}
