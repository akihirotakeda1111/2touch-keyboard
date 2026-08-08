package com.example.twotouchkeyboard.english

/**
 * Damerau-Levenshtein edit distance for spell correction scoring.
 */
internal object EnglishSpellingSupport {

    fun damerauLevenshtein(first: String, second: String): Int {
        if (first == second) return 0
        if (first.isEmpty()) return second.length
        if (second.isEmpty()) return first.length

        val rows = first.length + 1
        val cols = second.length + 1
        val dp = Array(rows) { IntArray(cols) }

        for (row in 0 until rows) {
            dp[row][0] = row
        }
        for (col in 0 until cols) {
            dp[0][col] = col
        }

        for (row in 1 until rows) {
            for (col in 1 until cols) {
                val cost = if (first[row - 1] == second[col - 1]) 0 else 1
                dp[row][col] = minOf(
                    dp[row - 1][col] + 1,
                    dp[row][col - 1] + 1,
                    dp[row - 1][col - 1] + cost,
                )
                if (row > 1 && col > 1 &&
                    first[row - 1] == second[col - 2] &&
                    first[row - 2] == second[col - 1]
                ) {
                    dp[row][col] = minOf(dp[row][col], dp[row - 2][col - 2] + 1)
                }
            }
        }

        return dp[first.length][second.length]
    }
}
