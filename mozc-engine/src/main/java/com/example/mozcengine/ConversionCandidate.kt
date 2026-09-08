package com.example.mozcengine

/** A candidate and its reading travel together through filtering and ranking. */
data class ConversionCandidate(
    val value: String,
    val reading: String = "",
)
