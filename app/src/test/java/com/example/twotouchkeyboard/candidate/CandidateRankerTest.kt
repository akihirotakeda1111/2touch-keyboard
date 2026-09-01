package com.example.twotouchkeyboard.candidate

import com.example.twotouchkeyboard.InputMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateRankerTest {

    @Test
    fun rankByUsage_movesFrequentlyUsedCandidatesFirst() {
        val usageCounts = mapOf(
            "hel" to mapOf(
                "help" to 5,
                "hello" to 2,
            ),
        )

        val ranked = CandidateRanker.rankByUsage(
            contextKey = "hel",
            candidates = listOf("held", "help", "hello", "hel"),
            getUsageCount = { prefix, candidate ->
                usageCounts[prefix]?.get(candidate.lowercase()) ?: 0
            },
        )

        assertEquals(listOf("help", "hello", "held", "hel"), ranked)
    }

    @Test
    fun rankByUsage_preservesEngineOrder_whenUsageCountsAreEqual() {
        val ranked = CandidateRanker.rankByUsage(
            contextKey = "hel",
            candidates = listOf("held", "help", "hello"),
            getUsageCount = { _, _ -> 0 },
        )

        assertEquals(listOf("held", "help", "hello"), ranked)
    }

    @Test
    fun rank_keepsJapaneseCandidatesInAcquisitionOrder_whenReadingsAreAbsent() {
        val candidates = listOf("わたし", "ワタシ", "私")

        val ranked = CandidateRanker.rank(
            mode = InputMode.HIRAGANA,
            contextKey = "わたし",
            candidates = candidates,
            getUsageCount = { _, _ -> 100 },
        )

        assertEquals(candidates, ranked)
    }

    @Test
    fun rankByUsage_isCaseInsensitiveForUsageLookup() {
        val ranked = CandidateRanker.rankByUsage(
            contextKey = "Hel",
            candidates = listOf("Held", "Help", "Hello"),
            getUsageCount = { prefix, candidate ->
                if (prefix == "hel" && candidate.equals("Help", ignoreCase = true)) 3 else 0
            },
        )

        assertEquals(listOf("Help", "Held", "Hello"), ranked)
    }
}
