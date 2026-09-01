package com.example.mozcengine

import org.junit.Assert.assertEquals
import org.junit.Test

class HiraganaPredictionSupportTest {

    @Test
    fun rankCandidates_prioritizesSameLengthAsInput() {
        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = listOf("感謝しています", "漢", "漢字", "感", "感じ"),
            input = "かん",
        )

        assertEquals(listOf("漢字", "感じ", "感謝しています", "漢", "感"), ranked)
    }

    @Test
    fun rankCandidates_keepsEngineOrder_withinSameLengthGroup() {
        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = listOf("愛", "合い", "藍", "相手", "挨拶"),
            input = "あい",
        )

        assertEquals(listOf("合い", "相手", "挨拶", "愛", "藍"), ranked)
    }

    @Test
    fun rankCandidates_preservesOrder_whenNoSameLengthCandidateExists() {
        val candidates = listOf("桜", "佐倉")

        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = candidates,
            input = "さくら",
        )

        assertEquals(candidates, ranked)
    }

    @Test
    fun rankCandidates_preservesOrder_whenAllCandidatesMatchInputLength() {
        val candidates = listOf("合い", "藍い", "愛い")

        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = candidates,
            input = "あい",
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
    fun rankCandidates_countsSupplementaryPlaneCharactersAsOne() {
        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = listOf("𠮷", "吉", "良し", "由"),
            input = "よし",
        )

        assertEquals(listOf("良し", "𠮷", "吉", "由"), ranked)
    }

    @Test
    fun rankCandidates_matchesSupplementaryPlaneCandidateToSingleCharacterInput() {
        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = listOf("よし", "𠮷", "吉野"),
            input = "よ",
        )

        assertEquals(listOf("𠮷", "よし", "吉野"), ranked)
    }
}
