package com.example.mozcengine

/**
 * 英字入力の予測候補を前後関係つきで並べ替える。
 */
object AlphabetPredictionSupport {

    fun rankCandidates(candidates: List<String>, input: String): List<String> {
        if (input.isEmpty()) return candidates

        val lowerInput = input.lowercase()
        val longerPrefixMatches = candidates.filter { candidate ->
            val lowerCandidate = candidate.lowercase()
            lowerCandidate.startsWith(lowerInput) && lowerCandidate.length > lowerInput.length
        }
        val sameLengthMatches = candidates.filter { candidate ->
            candidate.equals(input, ignoreCase = true)
        }
        val remaining = candidates.filter { candidate ->
            candidate !in longerPrefixMatches && candidate !in sameLengthMatches
        }

        return (longerPrefixMatches + remaining + sameLengthMatches).distinct()
    }

    fun hasPredictiveCandidates(candidates: List<String>, input: String): Boolean {
        if (input.isEmpty() || candidates.isEmpty()) return false
        val lowerInput = input.lowercase()
        return candidates.any { candidate ->
            val lowerCandidate = candidate.lowercase()
            lowerCandidate.startsWith(lowerInput) && lowerCandidate.length > lowerInput.length
        }
    }
}
