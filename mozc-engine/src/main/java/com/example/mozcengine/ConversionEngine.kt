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
 * Retrieves candidates and their readings in backend order.
 * Display filtering, ranking and raw-input fallback belong to the app's candidate pipeline.
 */
interface ConversionEngine {
    val isMozc: Boolean get() = false

    /** An empty result is valid; callers decide whether to display the input itself. */
    suspend fun convert(input: String, mode: ConversionMode): List<ConversionCandidate>

    /**
     * Returns next-input (zero-query) suggestions after a commit.
     * [selectedCandidate] is the conversion candidate the user picked; null when the
     * raw composing text was committed via Enter.
     */
    suspend fun suggestNext(
        mode: ConversionMode,
        selectedCandidate: String? = null,
    ): List<ConversionCandidate> = emptyList()

    fun resetSession()

    fun close()

    /** Records a committed candidate for usage-based learning (Mozc user history, etc.). */
    fun recordCandidateSelection(contextKey: String, candidate: String, mode: ConversionMode) = Unit

    /** Clears learned candidate usage. When [mode] is null, all modes are cleared. */
    fun clearCandidateUsageHistory(mode: ConversionMode? = null) = Unit
}
