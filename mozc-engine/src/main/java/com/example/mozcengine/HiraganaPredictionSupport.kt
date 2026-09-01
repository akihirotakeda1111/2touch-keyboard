package com.example.mozcengine

/**
 * 日本語の予測変換候補を、入力文字数と同じ長さのものを優先して並べ替える。
 */
object HiraganaPredictionSupport {

    fun rankCandidates(candidates: List<String>, input: String): List<String> {
        if (input.isEmpty() || candidates.size <= 1) return candidates

        val inputLength = characterCount(input)
        val sameLength = ArrayList<String>(candidates.size)
        val remaining = ArrayList<String>(candidates.size)
        for (candidate in candidates) {
            if (characterCount(candidate) == inputLength) {
                sameLength.add(candidate)
            } else {
                remaining.add(candidate)
            }
        }
        if (sameLength.isEmpty() || remaining.isEmpty()) return candidates

        return (sameLength + remaining).distinct()
    }

    private fun characterCount(text: String): Int = text.codePointCount(0, text.length)
}
