package com.example.twotouchkeyboard

/**
 * Manages next-input (zero-query) suggestion state shown after a commit.
 */
class NextInputSuggestionSession {

    private var candidates: List<String> = emptyList()

    val isActive: Boolean
        get() = candidates.isNotEmpty()

    fun getCandidates(): List<String> = candidates

    fun setCandidates(newCandidates: List<String>) {
        candidates = newCandidates
    }

    fun clear() {
        candidates = emptyList()
    }
}
