package com.example.twotouchkeyboard.english

import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishSpellingSupportTest {

    @Test
    fun damerauLevenshtein_returnsZero_forIdenticalStrings() {
        assertEquals(0, EnglishSpellingSupport.damerauLevenshtein("help", "help"))
    }

    @Test
    fun damerauLevenshtein_countsTransposition() {
        assertEquals(1, EnglishSpellingSupport.damerauLevenshtein("hlep", "help"))
    }

    @Test
    fun damerauLevenshtein_countsInsertion() {
        assertEquals(1, EnglishSpellingSupport.damerauLevenshtein("helo", "hello"))
    }

    @Test
    fun damerauLevenshtein_countsDeletion() {
        assertEquals(1, EnglishSpellingSupport.damerauLevenshtein("helpp", "help"))
    }
}
