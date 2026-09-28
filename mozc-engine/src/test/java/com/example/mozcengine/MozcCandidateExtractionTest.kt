package com.example.mozcengine

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidateWindow.CandidateWindow
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidateWindow.CandidateWord
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Output

/**
 * Characterizes the existing extraction boundary without starting a native Mozc session.
 * Reflection is confined to the helpers until extraction has a public replacement.
 */
class MozcCandidateExtractionTest {

    @Test
    fun japanese_preservesShortReadings_forPipelineRanking() {
        assertEquals(
            listOf("漢", "感"),
            extract(output("漢" to "かん", "感" to "かん"), "かんじ"),
        )
    }

    @Test
    fun japanese_leavesFallbackToPipeline_whenEngineReturnsNoCandidates() {
        assertEquals(emptyList<String>(), extract(Output.getDefaultInstance(), "かんじ"))
    }

    @Test
    fun japanese_preservesAcquisitionOrder_forPipelineRanking() {
        assertEquals(
            listOf("漢字語", "漢", "漢字", "感じ"),
            extract(
                output("漢字語" to "かんじご", "漢" to "かん", "漢字" to "かんじ", "感じ" to ""),
                "かんじ",
            ),
        )
    }

    @Test
    fun japanese_reusesPreviousReading_whenRedrawContainsOnlyCandidateWindow() {
        val builder = Output.newBuilder()
        val window = CandidateWindow.newBuilder().setSize(3).setPosition(0)
        for ((index, value) in listOf("漢", "漢字", "感じ").withIndex()) {
            window.addCandidate(CandidateWindow.Candidate.newBuilder().setIndex(index).setValue(value))
        }
        builder.setCandidateWindow(window)

        assertEquals(
            listOf(ConversionCandidate("漢", "かん"), ConversionCandidate("漢字", "かんじ"), ConversionCandidate("感じ", "かんじ")),
            extractEntries(builder.build(), "かんじ", previousReadings = mapOf("漢" to "かん", "漢字" to "かんじ")),
        )
    }

    @Test
    fun nextInput_preservesShortCandidatesAndOrder_withoutRawInputFallback() {
        assertEquals(listOf("を", "が", "に"), extractNext(output("を" to "", "が" to "", "に" to "")))
        assertEquals(emptyList<String>(), extractNext(Output.getDefaultInstance()))
    }

    @Test
    fun english_leavesCompletionSelectionToPipeline() {
        assertEquals(
            listOf("hel", "heap", "help", "hello"),
            extract(output("hel" to "", "heap" to "", "help" to "", "hello" to ""), "hel"),
        )
    }

    private fun output(vararg entries: Pair<String, String>): Output {
        val builder = Output.newBuilder()
        val words = builder.allCandidateWords.toBuilder()
        for ((value, reading) in entries) {
            words.addCandidates(
                CandidateWord.newBuilder().setValue(value).setKey(reading),
            )
        }
        return builder.setAllCandidateWords(words).build()
    }

    private fun extract(
        output: Output,
        input: String,
    ): List<String> = extractEntries(output, input).map { it.value }

    private fun extractEntries(
        output: Output,
        input: String,
        previousReadings: Map<String, String> = emptyMap(),
    ): List<ConversionCandidate> {
        val method = MozcSession.Companion::class.java.getDeclaredMethod(
            "extractCandidates", Output::class.java, String::class.java,
            Map::class.java,
        ).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return method.invoke(MozcSession.Companion, output, input, previousReadings) as List<ConversionCandidate>
    }

    private fun extractNext(output: Output): List<String> {
        val method = MozcSession.Companion::class.java.getDeclaredMethod(
            "extractSuggestionCandidates", Output::class.java,
        ).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return (method.invoke(MozcSession.Companion, output) as List<ConversionCandidate>).map { it.value }
    }
}
