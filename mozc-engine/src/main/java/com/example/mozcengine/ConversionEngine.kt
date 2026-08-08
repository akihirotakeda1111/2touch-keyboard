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
}
