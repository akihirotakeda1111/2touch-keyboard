package com.example.twotouchkeyboard.english

import android.content.Context

/**
 * Prefix trie backed by word frequency scores for English prediction and spell correction.
 */
class EnglishPrefixDictionary private constructor(
    private val root: Node,
    private val allEntries: List<WordEntry>,
) {

    fun predict(prefix: String, limit: Int = MAX_CANDIDATES): List<String> {
        if (prefix.isEmpty() || limit <= 0) return emptyList()

        val lowerPrefix = prefix.lowercase()
        var node = root
        for (char in lowerPrefix) {
            node = node.children[char] ?: return emptyList()
        }

        val matches = mutableListOf<WordEntry>()
        collectWords(node, matches)

        return matches
            .asSequence()
            .filter { entry ->
                entry.word.startsWith(lowerPrefix) &&
                    (entry.word.length > lowerPrefix.length ||
                        !entry.word.equals(prefix, ignoreCase = true))
            }
            .sortedWith(entryComparator())
            .take(limit)
            .map { entry -> matchInputCase(prefix, entry.word) }
            .toList()
    }

    fun correct(
        input: String,
        limit: Int = MAX_CANDIDATES,
        maxDistance: Int = MAX_EDIT_DISTANCE,
    ): List<String> {
        if (input.length < MIN_LENGTH_FOR_CORRECTION || limit <= 0) return emptyList()

        val lowerInput = input.lowercase()
        val minLength = (lowerInput.length - maxDistance).coerceAtLeast(1)
        val maxLength = lowerInput.length + maxDistance

        return allEntries
            .asSequence()
            .filter { entry ->
                entry.word.length in minLength..maxLength &&
                    !entry.word.startsWith(lowerInput) &&
                    !entry.word.equals(lowerInput, ignoreCase = true)
            }
            .mapNotNull { entry ->
                val distance = EnglishSpellingSupport.damerauLevenshtein(lowerInput, entry.word)
                if (distance in 1..maxDistance) {
                    ScoredEntry(entry, distance)
                } else {
                    null
                }
            }
            .sortedWith(
                compareBy<ScoredEntry> { it.distance }
                    .thenByDescending { it.entry.frequency }
                    .thenBy { it.entry.word },
            )
            .take(limit)
            .map { scored -> matchInputCase(input, scored.entry.word) }
            .toList()
    }

    fun suggest(input: String, limit: Int = MAX_CANDIDATES): List<String> {
        if (input.isEmpty() || limit <= 0) return emptyList()

        val prefixMatches = predict(input, limit)
        if (prefixMatches.isNotEmpty()) return prefixMatches

        if (input.length < MIN_LENGTH_FOR_CORRECTION) return emptyList()
        return correct(input, limit)
    }

    private fun matchInputCase(input: String, word: String): String {
        if (input.all { it.isUpperCase() || !it.isLetter() }) {
            return word.uppercase()
        }
        if (input.firstOrNull()?.isUpperCase() == true) {
            return word.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecaseChar() else char
            }
        }
        return word
    }

    private fun collectWords(node: Node, output: MutableList<WordEntry>) {
        node.entry?.let { output.add(it) }
        for (child in node.children.values) {
            collectWords(child, output)
        }
    }

    private fun entryComparator() = compareByDescending<WordEntry> { it.frequency }
        .thenBy { it.word.length }
        .thenBy { it.word }

    private data class WordEntry(
        val word: String,
        val frequency: Int,
    )

    private data class ScoredEntry(
        val entry: WordEntry,
        val distance: Int,
    )

    private class Node(
        val children: MutableMap<Char, Node> = mutableMapOf(),
        var entry: WordEntry? = null,
    )

    companion object {
        private const val ASSET_NAME = "english_word_freq.tsv"
        const val MAX_CANDIDATES = 20
        const val MAX_EDIT_DISTANCE = 2
        const val MIN_LENGTH_FOR_CORRECTION = 4

        fun load(context: Context): EnglishPrefixDictionary {
            val entries = context.assets.open(ASSET_NAME).bufferedReader().useLines { lines ->
                lines.mapNotNull { line -> parseLine(line) }.toList()
            }
            return fromEntries(entries)
        }

        fun fromEntries(entries: List<Pair<String, Int>>): EnglishPrefixDictionary {
            val root = Node()
            val allEntries = mutableListOf<WordEntry>()
            entries.forEach { (word, frequency) ->
                val normalized = word.lowercase()
                insert(root, normalized, frequency)
                allEntries.add(WordEntry(normalized, frequency))
            }
            return EnglishPrefixDictionary(root, allEntries)
        }

        private fun parseLine(line: String): Pair<String, Int>? {
            if (line.isBlank() || line.startsWith("#")) return null
            val parts = line.split('\t')
            if (parts.size < 2) return null
            val word = parts[0].trim()
            val frequency = parts[1].trim().toIntOrNull() ?: return null
            if (word.isEmpty() || !word.all { it.isLetter() }) return null
            return word to frequency
        }

        private fun insert(root: Node, word: String, frequency: Int) {
            var node = root
            for (char in word) {
                node = node.children.getOrPut(char) { Node() }
            }
            val existing = node.entry
            if (existing == null || frequency > existing.frequency) {
                node.entry = WordEntry(word, frequency)
            }
        }
    }
}
