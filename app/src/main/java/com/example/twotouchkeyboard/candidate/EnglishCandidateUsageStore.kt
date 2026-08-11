package com.example.twotouchkeyboard.candidate

import android.content.Context
import com.example.mozcengine.AlphabetPredictionSupport
import org.json.JSONObject
import java.io.File

/**
 * Persists English candidate usage counts keyed by (prefix, word).
 */
class EnglishCandidateUsageStore(
    context: Context,
) {
    private val storageFile = File(context.applicationContext.filesDir, STORAGE_FILE_NAME)
    private val lock = Any()
    private val entries = linkedMapOf<String, UsageEntry>()

    init {
        loadFromDisk()
    }

    fun record(prefix: String, candidate: String) {
        val normalizedPrefix = AlphabetPredictionSupport.lookupInput(prefix)
        if (normalizedPrefix.isEmpty() || candidate.isEmpty()) return

        val key = buildKey(normalizedPrefix, candidate)
        synchronized(lock) {
            val existing = entries[key]
            entries[key] = UsageEntry(
                count = (existing?.count ?: 0) + 1,
                lastUsedAtMs = System.currentTimeMillis(),
            )
            trimIfNeeded()
            saveToDiskLocked()
        }
    }

    fun getCount(prefix: String, candidate: String): Int {
        val normalizedPrefix = AlphabetPredictionSupport.lookupInput(prefix)
        val key = buildKey(normalizedPrefix, candidate)
        synchronized(lock) {
            return entries[key]?.count ?: 0
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            if (storageFile.exists()) {
                storageFile.delete()
            }
        }
    }

    internal fun snapshot(): Map<String, UsageEntry> {
        synchronized(lock) {
            return entries.mapValues { it.value.copy() }
        }
    }

    internal fun restore(entriesByKey: Map<String, UsageEntry>) {
        synchronized(lock) {
            entries.clear()
            entries.putAll(entriesByKey)
            trimIfNeeded()
            saveToDiskLocked()
        }
    }

    private fun trimIfNeeded() {
        if (entries.size <= MAX_ENTRIES) return

        val keysToRemove = entries.entries
            .sortedBy { it.value.lastUsedAtMs }
            .take(entries.size - MAX_ENTRIES)
            .map { it.key }
        keysToRemove.forEach { entries.remove(it) }
    }

    private fun loadFromDisk() {
        if (!storageFile.exists()) return

        runCatching {
            val json = JSONObject(storageFile.readText())
            synchronized(lock) {
                entries.clear()
                json.keys().forEachRemaining { key ->
                    val entryJson = json.getJSONObject(key)
                    entries[key] = UsageEntry(
                        count = entryJson.getInt("count"),
                        lastUsedAtMs = entryJson.getLong("lastUsedAtMs"),
                    )
                }
            }
        }
    }

    private fun saveToDiskLocked() {
        val json = JSONObject()
        entries.forEach { (key, entry) ->
            json.put(
                key,
                JSONObject()
                    .put("count", entry.count)
                    .put("lastUsedAtMs", entry.lastUsedAtMs),
            )
        }
        storageFile.writeText(json.toString())
    }

    data class UsageEntry(
        val count: Int,
        val lastUsedAtMs: Long,
    )

    companion object {
        private const val STORAGE_FILE_NAME = "english_candidate_usage.json"
        private const val MAX_ENTRIES = 2_000

        internal fun buildKey(prefix: String, candidate: String): String {
            return "${prefix.lowercase()}\t${candidate.lowercase()}"
        }
    }
}
