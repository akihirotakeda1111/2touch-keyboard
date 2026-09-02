package com.example.mozcengine

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateReadingMergerTest {

    @Test
    fun merge_usesFallback_whenAllCandidateWordKeyIsEmpty() {
        val merged = CandidateReadingMerger.merge(
            allCandidateWordReadings = listOf("漢" to "", "漢字" to "かんじ"),
            candidateWindowValues = listOf("漢", "漢字"),
            previousReadings = emptyMap(),
            emptyReadingFallback = "かん",
        )

        assertEquals(
            listOf("漢" to "かん", "漢字" to "かんじ"),
            merged,
        )
    }

    @Test
    fun merge_reusesPreviousReadings_whenAllCandidateWordsAreOmitted() {
        val merged = CandidateReadingMerger.merge(
            allCandidateWordReadings = emptyList(),
            candidateWindowValues = listOf("漢", "漢字", "感じ"),
            previousReadings = mapOf(
                "漢" to "かん",
                "漢字" to "かんじ",
                "感じ" to "かんじ",
            ),
            emptyReadingFallback = "かんじ",
        )

        assertEquals(
            listOf("漢" to "かん", "漢字" to "かんじ", "感じ" to "かんじ"),
            merged,
        )
    }

    @Test
    fun merge_usesCurrentInputFallback_whenWindowCandidateHasNoPreviousReading() {
        val merged = CandidateReadingMerger.merge(
            allCandidateWordReadings = emptyList(),
            candidateWindowValues = listOf("漢", "漢字"),
            previousReadings = mapOf("漢字" to "かんじ"),
            emptyReadingFallback = "かんじ",
        )

        assertEquals(
            listOf("漢" to "かんじ", "漢字" to "かんじ"),
            merged,
        )
    }

    @Test
    fun merge_prefersNewAllCandidateWordsOverPreviousReadings() {
        val merged = CandidateReadingMerger.merge(
            allCandidateWordReadings = listOf("漢" to "かん", "漢字" to "かんじ"),
            candidateWindowValues = listOf("漢", "漢字"),
            previousReadings = mapOf("漢" to "か"),
            emptyReadingFallback = "かんじ",
        )

        assertEquals(
            listOf("漢" to "かん", "漢字" to "かんじ"),
            merged,
        )
    }

    @Test
    fun merge_keepsWindowOrder_whenAllCandidateWordsAreOmitted() {
        val merged = CandidateReadingMerger.merge(
            allCandidateWordReadings = emptyList(),
            candidateWindowValues = listOf("感じ", "漢字", "漢"),
            previousReadings = mapOf(
                "漢" to "かん",
                "漢字" to "かんじ",
                "感じ" to "かんじ",
            ),
            emptyReadingFallback = "かんじ",
        )

        assertEquals(
            listOf("感じ" to "かんじ", "漢字" to "かんじ", "漢" to "かん"),
            merged,
        )
    }

    @Test
    fun merge_keepsShorterReadingAcrossRefresh_soRankerCanHideIt() {
        val first = CandidateReadingMerger.merge(
            allCandidateWordReadings = listOf(
                "漢" to "かん",
                "漢字" to "かんじ",
                "感じ" to "かんじ",
            ),
            candidateWindowValues = listOf("漢", "漢字", "感じ"),
            previousReadings = emptyMap(),
            emptyReadingFallback = "かんじ",
        )
        val refresh = CandidateReadingMerger.merge(
            allCandidateWordReadings = emptyList(),
            candidateWindowValues = first.map { it.first },
            previousReadings = first.toMap(),
            emptyReadingFallback = "かんじ",
        )
        val ranked = HiraganaPredictionSupport.rankCandidates(
            candidates = refresh.map { it.first },
            input = "かんじ",
            readings = refresh.map { it.second },
        )

        assertEquals(listOf("漢字", "感じ"), ranked)
    }
}
