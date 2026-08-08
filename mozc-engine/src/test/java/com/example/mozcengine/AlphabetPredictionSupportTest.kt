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
    fun hasPredictiveCandidates_returnsFalse_whenOnlyExactMatchExists() {
        assertFalse(
            AlphabetPredictionSupport.hasPredictiveCandidates(
                candidates = listOf("hel"),
                input = "hel",
            ),
        )
    }
}
