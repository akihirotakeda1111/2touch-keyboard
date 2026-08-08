package com.example.mozcengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphabetPredictionSupportTest {

    @Test
    fun rankCandidates_prioritizesLongerPrefixMatches() {
        val ranked = AlphabetPredictionSupport.rankCandidates(
            candidates = listOf("hel", "help", "hello", "held"),
            input = "hel",
        )

        assertEquals(listOf("help", "hello", "held", "hel"), ranked)
    }

    @Test
    fun hasPredictiveCandidates_returnsTrue_whenLongerMatchExists() {
        assertTrue(
            AlphabetPredictionSupport.hasPredictiveCandidates(
                candidates = listOf("hel", "hello"),
                input = "hel",
            ),
        )
    }

    @Test
    fun filterEnglishCandidates_removesJapaneseCandidates() {
        val filtered = AlphabetPredictionSupport.filterEnglishCandidates(
            candidates = listOf("hello", "こんにちは", "help", "変換"),
            input = "he",
        )

        assertEquals(listOf("hello", "help"), filtered)
    }

    @Test
    fun prepareEnglishCandidates_returnsInput_whenOnlyJapaneseCandidatesExist() {
        val prepared = AlphabetPredictionSupport.prepareEnglishCandidates(
            candidates = listOf("こんにちは", "変換"),
            input = "hel",
        )

        assertEquals(listOf("hel"), prepared)
    }

    @Test
    fun hasPredictiveCandidates_returnsFalse_forJapaneseCandidates() {
        assertFalse(
            AlphabetPredictionSupport.hasPredictiveCandidates(
                candidates = listOf("こんにちは", "変換"),
                input = "hel",
            ),
        )
    }
}
