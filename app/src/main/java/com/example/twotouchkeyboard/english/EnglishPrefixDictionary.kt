package com.example.twotouchkeyboard.english

import android.content.Context

/**
 * Prefix trie backed by word frequency scores for English prediction.
 */
class EnglishPrefixDictionary private constructor(
    private val root: Node,
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
            .sortedWith(
                compareByDescending<WordEntry> { it.frequency }
                    .thenBy { it.word.length }
                    .thenBy { it.word },
            )
            .take(limit)
            .map { entry ->
                matchInputCase(prefix, entry.word)
            }
            .toList()
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

    private data class WordEntry(
        val word: String,
        val frequency: Int,
    )

    private class Node(
        val children: MutableMap<Char, Node> = mutableMapOf(),
        var entry: WordEntry? = null,
    )

    companion object {
        private const val ASSET_NAME = "english_word_freq.tsv"
        const val MAX_CANDIDATES = 20

        fun load(context: Context): EnglishPrefixDictionary {
            val entries = context.assets.open(ASSET_NAME).bufferedReader().useLines { lines ->
                lines.mapNotNull { line -> parseLine(line) }.toList()
            }
            return fromEntries(entries)
        }

        fun fromEntries(entries: List<Pair<String, Int>>): EnglishPrefixDictionary {
            val root = Node()
            entries.forEach { (word, frequency) ->
                insert(root, word.lowercase(), frequency)
            }
            return EnglishPrefixDictionary(root)
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
