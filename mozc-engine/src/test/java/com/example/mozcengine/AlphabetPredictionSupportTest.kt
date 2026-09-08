package com.example.mozcengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphabetPredictionSupportTest {

    @Test
    fun prepareEnglishCandidates_prefersCompletionsToCorrections_andKeepsInputLast() {
        val prepared = AlphabetPredictionSupport.prepareEnglishCandidates(
            listOf("HEL", "heap", "help", "こんにちは", "hello", "help", "hel"),
            "Hel",
        )

        assertEquals(listOf("help", "hello", "HEL", "hel"), prepared)
    }

    @Test
    fun prepareEnglishCandidates_deduplicatesCorrections_withoutChangingOrderOrCase() {
        val prepared = AlphabetPredictionSupport.prepareEnglishCandidates(
            listOf("HLEP", "help", "heap", "help", "Help", "変換"),
            "hlep",
        )

        assertEquals(listOf("help", "heap", "Help"), prepared)
    }

    @Test
    fun prepareEnglishCandidates_preservesAllowedPunctuationAndDigits() {
        val prepared = AlphabetPredictionSupport.prepareEnglishCandidates(
            listOf("a", "a1", "a-b", "a.b", "a'b", "a b", "a_b", "a!", "aあ", ""),
            "a",
        )

        assertEquals(listOf("a1", "a-b", "a.b", "a'b", "a"), prepared)
    }

    @Test
    fun prepareEnglishCandidates_usesOriginalInputCase_whenOnlyExactMatchesRemain() {
        assertEquals(
            listOf("Hel"),
            AlphabetPredictionSupport.prepareEnglishCandidates(listOf("hel", "HEL"), "Hel"),
        )
    }

    @Test
    fun prepareEnglishCandidates_doesNotFallbackToInvalidInput() {
        for (input in listOf("", "こんにちは", "two words")) {
            assertEquals(
                "input=$input",
                emptyList<String>(),
                AlphabetPredictionSupport.prepareEnglishCandidates(listOf("変換", ""), input),
            )
        }
    }

    @Test
    fun prepareEnglishCandidates_isStableWhenAppliedAgainByService() {
        val cases = listOf(
            "hel" to listOf("hel", "help", "heap", "hello", "help"),
            "hlep" to listOf("hlep", "help", "help", "heap"),
            "Hel" to emptyList(),
        )
        for ((input, candidates) in cases) {
            val prepared = AlphabetPredictionSupport.prepareEnglishCandidates(candidates, input)
            assertEquals(
                "input=$input",
                prepared,
                AlphabetPredictionSupport.prepareEnglishCandidates(prepared, input),
            )
        }
    }

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

    @Test
    fun filterEnglishCandidates_matchesUppercaseInput() {
        val filtered = AlphabetPredictionSupport.filterEnglishCandidates(
            candidates = listOf("hello", "help", "world"),
            input = "HE",
        )

        assertEquals(listOf("hello", "help"), filtered)
    }

    @Test
    fun lookupInput_normalizesToLowercase() {
        assertEquals("hel", AlphabetPredictionSupport.lookupInput("HEL"))
    }

    @Test
    fun prepareEnglishCandidates_returnsCorrections_whenNoPrefixMatchesExist() {
        val prepared = AlphabetPredictionSupport.prepareEnglishCandidates(
            candidates = listOf("help", "hlep"),
            input = "hlep",
        )

        assertEquals(listOf("help"), prepared)
    }

    @Test
    fun hasConversionCandidates_returnsTrue_forSpellCorrections() {
        assertTrue(
            AlphabetPredictionSupport.hasConversionCandidates(
                candidates = listOf("help"),
                input = "hlep",
            ),
        )
    }

    @Test
    fun hasConversionCandidates_returnsFalse_whenOnlyInputIsPresent() {
        assertFalse(
            AlphabetPredictionSupport.hasConversionCandidates(
                candidates = listOf("hlep"),
                input = "hlep",
            ),
        )
    }

    @Test
    fun hasConversionCandidates_returnsFalse_forShortInputCorrections() {
        assertFalse(
            AlphabetPredictionSupport.hasConversionCandidates(
                candidates = listOf("help"),
                input = "hep",
            ),
        )
    }
}
