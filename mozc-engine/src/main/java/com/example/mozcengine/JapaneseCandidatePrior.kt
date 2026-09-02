package com.example.mozcengine

import android.content.Context
import android.util.Log
import java.io.InputStream

/**
 * Offline general-frequency table for Japanese conversion candidates.
 *
 * Mozc still generates candidates; this table only reranks values it returned.
 * The parser has no Android [Context] dependency so it can be unit-tested with
 * strings or streams. Asset loading is one-shot and never runs while ranking.
 */
class JapaneseCandidatePrior private constructor(
    private val priorities: Map<String, Int>,
) {
    fun isEmpty(): Boolean = priorities.isEmpty()

    fun priorityOf(reading: String, candidate: String): Int {
        if (priorities.isEmpty() || reading.isEmpty() || candidate.isEmpty()) return 0
        return priorities[key(reading, candidate)] ?: 0
    }

    companion object {
        const val ASSET_NAME = "japanese_candidate_prior.tsv"
        const val MIN_PRIORITY = 0
        const val MAX_PRIORITY = 3

        val EMPTY = JapaneseCandidatePrior(emptyMap())

        private const val TAG = "JapaneseCandidatePrior"
        private const val KEY_SEPARATOR = '\u0001'

        @Volatile
        private var cached: JapaneseCandidatePrior? = null
        private val loadLock = Any()

        fun parse(text: String): JapaneseCandidatePrior {
            return parseLines(text.removePrefix("\uFEFF").lineSequence())
        }

        fun parseLines(lines: Sequence<String>): JapaneseCandidatePrior {
            val table = HashMap<String, Int>()
            for (line in lines) {
                val parsed = parseLine(line) ?: continue
                val mapKey = key(parsed.reading, parsed.candidate)
                val existing = table[mapKey] ?: MIN_PRIORITY
                if (parsed.priority > existing) {
                    table[mapKey] = parsed.priority
                }
            }
            return if (table.isEmpty()) EMPTY else JapaneseCandidatePrior(table)
        }

        fun loadOrEmpty(openStream: () -> InputStream): JapaneseCandidatePrior {
            return try {
                openStream().bufferedReader().use { reader ->
                    parse(reader.readText())
                }
            } catch (_: Exception) {
                EMPTY
            }
        }

        fun current(): JapaneseCandidatePrior = cached ?: EMPTY

        fun loadOnce(context: Context): JapaneseCandidatePrior {
            cached?.let { return it }
            return synchronized(loadLock) {
                cached ?: loadFromAssets(context.applicationContext).also { cached = it }
            }
        }

        internal fun parseLine(line: String): PriorEntry? {
            if (line.isBlank()) return null
            val content = line.trimStart('\uFEFF', ' ', '\t')
            if (content.isEmpty() || content.startsWith("#")) return null

            val parts = line.split('\t')
            if (parts.size < 3) return null

            val reading = parts[0].trim().trimStart('\uFEFF')
            val candidate = parts[1].trim()
            val priority = parts[2].trim().toIntOrNull() ?: return null
            if (reading.isEmpty() || candidate.isEmpty()) return null

            return PriorEntry(
                reading = reading,
                candidate = candidate,
                priority = priority.coerceIn(MIN_PRIORITY, MAX_PRIORITY),
            )
        }

        private fun loadFromAssets(context: Context): JapaneseCandidatePrior {
            return try {
                context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
                    parse(reader.readText())
                }
            } catch (error: Exception) {
                Log.w(TAG, "japanese_candidate_prior.tsv unavailable; using empty table", error)
                EMPTY
            }
        }

        private fun key(reading: String, candidate: String): String {
            return "$reading$KEY_SEPARATOR$candidate"
        }
    }

    internal data class PriorEntry(
        val reading: String,
        val candidate: String,
        val priority: Int,
    )
}
