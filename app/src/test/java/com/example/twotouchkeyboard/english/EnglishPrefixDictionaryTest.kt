package com.example.twotouchkeyboard.english

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishPrefixDictionaryTest {

    @Test
    fun suggest_prefersCompletion_evenWhenCorrectionHasHigherFrequency() {
        val dictionary = EnglishPrefixDictionary.fromEntries(
            listOf("helper" to 1, "heap" to 1000),
        )

        assertEquals(listOf("heap"), dictionary.correct("help"))
        assertEquals(listOf("helper"), dictionary.suggest("help"))
    }

    @Test
    fun suggest_limitsDefaultResultsToTwenty_inFrequencyOrder() {
        val words = ('a'..'z').mapIndexed { index, suffix -> "hel$suffix" to index + 1 }
        val dictionary = EnglishPrefixDictionary.fromEntries(words)

        assertEquals(
            listOf("helz", "hely", "helx", "helw", "helv", "helu", "helt", "hels", "helr", "helq",
                "help", "helo", "heln", "helm", "hell", "helk", "helj", "heli", "helh", "helg"),
            dictionary.suggest("hel"),
        )
    }

    @Test
    fun suggest_doesNotCorrectThreeCharacterInput_withoutPrefixMatches() {
        val dictionary = EnglishPrefixDictionary.fromEntries(listOf("help" to 500))

        assertEquals(emptyList<String>(), dictionary.suggest("hep"))
        assertEquals(listOf("help"), dictionary.suggest("hlep"))
    }

    @Test
    fun predict_returnsPrefixMatches_sortedByFrequency() {
        val dictionary = EnglishPrefixDictionary.fromEntries(
            listOf(
                "hel" to 100,
                "help" to 500,
                "hello" to 300,
                "held" to 200,
                "he" to 50,
            ),
        )

        val predictions = dictionary.predict("hel", limit = 10)

        assertEquals(listOf("help", "hello", "held"), predictions)
    }

    @Test
    fun predict_excludesExactInputMatch() {
        val dictionary = EnglishPrefixDictionary.fromEntries(
            listOf(
                "hel" to 500,
                "help" to 400,
            ),
        )

        val predictions = dictionary.predict("hel", limit = 10)

        assertEquals(listOf("help"), predictions)
    }

    @Test
    fun predict_returnsEmpty_forUnknownPrefix() {
        val dictionary = EnglishPrefixDictionary.fromEntries(
            listOf("hello" to 100),
        )

        assertTrue(dictionary.predict("xyz", limit = 10).isEmpty())
    }

    @Test
    fun predict_respectsLimit() {
        val dictionary = EnglishPrefixDictionary.fromEntries(
            listOf(
                "hel" to 100,
                "help" to 500,
                "hello" to 400,
                "held" to 300,
            ),
        )

        val predictions = dictionary.predict("hel", limit = 2)

        assertEquals(2, predictions.size)
    }

    @Test
    fun predict_appliesTitleCase_whenInputStartsWithUppercase() {
        val dictionary = EnglishPrefixDictionary.fromEntries(
            listOf("help" to 500),
        )

        val predictions = dictionary.predict("Hel", limit = 10)

        assertEquals(listOf("Help"), predictions)
    }

    @Test
    fun predict_appliesUpperCase_whenInputIsAllUppercase() {
        val dictionary = EnglishPrefixDictionary.fromEntries(
            listOf("help" to 500),
        )

        val predictions = dictionary.predict("HEL", limit = 10)

        assertEquals(listOf("HELP"), predictions)
    }

    @Test
    fun predict_isCaseInsensitive() {
        val dictionary = EnglishPrefixDictionary.fromEntries(
            listOf("help" to 500, "hello" to 400),
        )

        val lower = dictionary.predict("hel", limit = 10)
        val upper = dictionary.predict("HEL", limit = 10)

        assertEquals(listOf("help", "hello"), lower)
        assertEquals(listOf("HELP", "HELLO"), upper)
    }

    @Test
    fun correct_findsTransposition() {
        val dictionary = EnglishPrefixDictionary.fromEntries(
            listOf("help" to 500),
        )

        val corrections = dictionary.correct("hlep", limit = 10)

        assertEquals(listOf("help"), corrections)
    }

    @Test
    fun correct_findsMissingCharacter() {
        val dictionary = EnglishPrefixDictionary.fromEntries(
            listOf("hello" to 500),
        )

        val corrections = dictionary.correct("helo", limit = 10)

        assertEquals(listOf("hello"), corrections)
    }

    @Test
    fun correct_findsExtraCharacter() {
        val dictionary = EnglishPrefixDictionary.fromEntries(
            listOf("help" to 500),
        )

        val corrections = dictionary.correct("helpp", limit = 10)

        assertEquals(listOf("help"), corrections)
    }

    @Test
    fun correct_returnsEmpty_forShortInput() {
        val dictionary = EnglishPrefixDictionary.fromEntries(
            listOf("help" to 500),
        )

        assertTrue(dictionary.correct("hep", limit = 10).isEmpty())
    }

    @Test
    fun correct_prefersCloserMatchByFrequency() {
        val dictionary = EnglishPrefixDictionary.fromEntries(
            listOf(
                "help" to 500,
                "heap" to 100,
            ),
        )

        val corrections = dictionary.correct("helo", limit = 10)

        assertEquals(listOf("help", "heap"), corrections)
    }

    @Test
    fun suggest_returnsPrefixMatches_whenAvailable() {
        val dictionary = EnglishPrefixDictionary.fromEntries(
            listOf(
                "help" to 500,
                "hello" to 400,
                "heap" to 100,
            ),
        )

        val suggestions = dictionary.suggest("hel", limit = 10)

        assertEquals(listOf("help", "hello"), suggestions)
    }

    @Test
    fun suggest_fallsBackToCorrection_whenPrefixMissing() {
        val dictionary = EnglishPrefixDictionary.fromEntries(
            listOf("help" to 500, "hello" to 400),
        )

        val suggestions = dictionary.suggest("hlep", limit = 10)

        assertEquals(listOf("help"), suggestions)
    }
}
