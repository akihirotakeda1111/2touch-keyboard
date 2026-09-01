package com.example.mozcengine

import org.junit.Assert.assertEquals
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
}
