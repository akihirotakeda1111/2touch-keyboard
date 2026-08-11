package com.example.mozcengine

/**
 * Input mode passed to the conversion engine.
 */
enum class ConversionMode {
    HIRAGANA,
    ALPHABET,
    NUMBER,
}

/**
 * Japanese conversion engine abstraction.
 */
interface ConversionEngine {
    val isMozc: Boolean get() = false

    suspend fun convert(input: String, mode: ConversionMode): List<String>

    fun resetSession()

    fun close()

    /** Records a committed candidate for usage-based learning (Mozc user history, etc.). */
    fun recordCandidateSelection(contextKey: String, candidate: String, mode: ConversionMode) = Unit

    /** Clears learned candidate usage. When [mode] is null, all modes are cleared. */
    fun clearCandidateUsageHistory(mode: ConversionMode? = null) = Unit
}
