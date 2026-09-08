package com.example.twotouchkeyboard.candidate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.mozcengine.ConversionCandidate
import com.example.mozcengine.ConversionEngine
import com.example.mozcengine.ConversionMode
import com.example.twotouchkeyboard.InputMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CandidateLearningCoordinatorTest {
    private lateinit var store: EnglishCandidateUsageStore
    private lateinit var coordinator: CandidateLearningCoordinator
    private lateinit var pipeline: CandidatePipeline

    @Before
    fun setUp() {
        store = EnglishCandidateUsageStore(ApplicationProvider.getApplicationContext<Context>())
        store.clear()
        val engine = object : ConversionEngine {
            override suspend fun convert(input: String, mode: ConversionMode): List<ConversionCandidate> =
                error("Ranking must not request conversion")
            override fun resetSession() = Unit
            override fun close() = Unit
        }
        coordinator = CandidateLearningCoordinator(engine, store)
        pipeline = CandidatePipeline(getUsageCount = coordinator::getUsageCount)
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun english_learningReordersSurvivors_withoutRestoringFilteredCandidates() {
        repeat(3) { store.record("hel", "heap") }
        store.record("hel", "hello")
        val candidates = listOf("hel", "help", "heap", "hello").map { ConversionCandidate(it) }

        assertEquals(
            listOf("hello", "help", "hel"),
            pipeline.prepare(InputMode.ALPHABET, "hel", candidates).map { it.value },
        )
    }

    @Test
    fun english_disabledLearning_preservesPreparedOrder_despiteStoredUsage() {
        store.record("hel", "hello")
        coordinator.learningEnabled = false
        val candidates = listOf("hel", "heap", "help", "hello").map { ConversionCandidate(it) }

        assertEquals(
            listOf("help", "hello", "hel"),
            pipeline.prepare(InputMode.ALPHABET, "hel", candidates).map { it.value },
        )
    }

    @Test
    fun japanese_preservesEngineOrder_withLearningEnabledOrDisabled() {
        val candidates = listOf("漢字", "感じ", "漢字語")
        for (enabled in listOf(true, false)) {
            coordinator.learningEnabled = enabled
            assertEquals(candidates, pipeline.prepare(InputMode.HIRAGANA, "かんじ", candidates.map { ConversionCandidate(it) }).map { it.value })
        }
    }
}
