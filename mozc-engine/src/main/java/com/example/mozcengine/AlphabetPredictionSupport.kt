package com.example.mozcengine

/**
 * 英字入力の予測候補を前後関係つきで並べ替える。
 */
object AlphabetPredictionSupport {

    fun isEnglishWordCandidate(candidate: String): Boolean {
        if (candidate.isEmpty()) return false
        return candidate.all { isEnglishCandidateChar(it) }
    }

    fun filterEnglishCandidates(candidates: List<String>, input: String): List<String> {
        if (input.isEmpty()) return emptyList()

        val lowerInput = input.lowercase()
        return candidates.filter { candidate ->
            isEnglishWordCandidate(candidate) &&
                candidate.lowercase().startsWith(lowerInput)
        }
    }

    fun lookupInput(input: String): String = input.lowercase()

    fun prepareEnglishCandidates(candidates: List<String>, input: String): List<String> {
        val englishCandidates = candidates.filter { isEnglishWordCandidate(it) }
        val lowerInput = input.lowercase()
        val hasPredictivePrefixMatches = englishCandidates.any { candidate ->
            val lowerCandidate = candidate.lowercase()
            lowerCandidate.startsWith(lowerInput) && lowerCandidate.length > lowerInput.length
        }
        if (hasPredictivePrefixMatches) {
            val prefixCandidates = englishCandidates.filter { candidate ->
                candidate.lowercase().startsWith(lowerInput)
            }
            return rankCandidates(prefixCandidates, input)
        }

        val corrections = englishCandidates.filter { candidate ->
            !candidate.equals(input, ignoreCase = true)
        }
        if (corrections.isNotEmpty()) {
            return corrections.distinct()
        }

        return if (isEnglishWordCandidate(input)) listOf(input) else emptyList()
    }

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
            isEnglishWordCandidate(candidate) &&
                candidate.lowercase().startsWith(lowerInput) &&
                candidate.length > input.length
        }
    }

    private fun isEnglishCandidateChar(char: Char): Boolean {
        return char in 'a'..'z' ||
            char in 'A'..'Z' ||
            char in '0'..'9' ||
            char == '\'' ||
            char == '-' ||
            char == '.'
    }
}
