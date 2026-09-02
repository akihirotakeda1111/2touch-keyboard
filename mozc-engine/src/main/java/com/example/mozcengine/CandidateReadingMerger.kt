package com.example.mozcengine

/**
 * Mozc の候補読みを合成する。
 *
 * `all_candidate_words` は候補内容が変わったときだけ埋まる。再描画では
 * `candidate_window` のみが返り、読みが空になる。空の読みは入力全体とみなし、
 * 直前の読みを候補値で引き継ぐ。
 */
internal object CandidateReadingMerger {

    fun merge(
        allCandidateWordReadings: List<Pair<String, String>>,
        candidateWindowValues: List<String>,
        previousReadings: Map<String, String>,
        emptyReadingFallback: String,
    ): List<Pair<String, String>> {
        val result = linkedMapOf<String, String>()

        for ((value, key) in allCandidateWordReadings) {
            if (value.isEmpty()) continue
            result.putIfAbsent(value, key.ifEmpty { emptyReadingFallback })
        }

        for (value in candidateWindowValues) {
            if (value.isEmpty()) continue
            val reading = result[value]
                ?: previousReadings[value]
                ?: emptyReadingFallback
            result.putIfAbsent(value, reading)
        }

        return result.toList()
    }
}
