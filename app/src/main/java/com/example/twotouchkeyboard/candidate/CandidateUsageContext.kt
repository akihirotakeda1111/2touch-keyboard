package com.example.twotouchkeyboard.candidate

import com.example.twotouchkeyboard.InputMode

/**
 * Context for a committed conversion/prediction candidate.
 *
 * [contextKey] is the substring being converted (e.g. hiragana reading or English prefix).
 */
data class CandidateUsageContext(
    val mode: InputMode,
    val contextKey: String,
    val candidate: String,
)
