package com.example.twotouchkeyboard.dictionary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.mozcengine.ConversionCandidate
import com.example.twotouchkeyboard.ConversionSession
import com.example.twotouchkeyboard.InputMode
import com.example.twotouchkeyboard.candidate.CandidatePipeline
import com.example.twotouchkeyboard.candidate.CandidateRequestKind
import com.example.twotouchkeyboard.candidate.EnglishCandidateUsageStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserDictionaryTest {
    @Test
    fun registrations_persist_edit_delete_andSurviveEnglishHistoryClear() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = UserDictionaryRepository(context)
        repository.snapshot.first().entries.forEach { repository.delete(it.id) }
        repository.save(null, " よろ ", "よろしく\nお願いします。")
        val reloaded = UserDictionaryRepository(context)
        val entry = reloaded.snapshot.first().entries.single()
        assertEquals("よろ", entry.reading)
        assertEquals("よろしく\nお願いします。", entry.value)
        try {
            repository.save(null, "よろ", entry.value)
            fail("Duplicate should be rejected")
        } catch (_: IllegalArgumentException) { }
        repository.save(entry.id, "おつ", "お疲れさまです。")
        // The Android-only native Mozc history cannot run in a desktop JVM.
        EnglishCandidateUsageStore(context).clear()
        val updated = reloaded.snapshot.first()
        assertEquals(entry.id, updated.entries.single().id)
        assertEquals("おつ", updated.entries.single().reading)
        repository.delete(entry.id)
        assertTrue(reloaded.snapshot.first().entries.isEmpty())
    }

    @Test
    fun validation_rejectsEmptyOrOversizedEntries() {
        assertNotNull(UserDictionaryRepository.validationError("", "文"))
        assertNotNull(UserDictionaryRepository.validationError("abc", "文"))
        assertNotNull(UserDictionaryRepository.validationError("あ".repeat(65), "文"))
        assertNotNull(UserDictionaryRepository.validationError("あ", "  "))
        assertNotNull(UserDictionaryRepository.validationError("あ", "文".repeat(1001)))
        assertNull(UserDictionaryRepository.validationError("めーる", "a@example.com"))
    }

    @Test
    fun candidates_mergeUserThenEngine_withoutDuplicatesOrPrefixMatching() {
        val user = DictionarySnapshot(listOf(DictionaryEntry("1", "よろ", "よろしくお願いします。")))
        val pipeline = CandidatePipeline(
            getJapanesePriority = { _, _ -> 0 },
            getDictionaryCandidates = user::candidatesFor,
        )
        val engine = listOf(ConversionCandidate("ヨロ", "よろ"), ConversionCandidate("よろしくお願いします。", "よろ"))
        assertEquals(
            listOf("よろしくお願いします。", "ヨロ"),
            pipeline.prepare(InputMode.HIRAGANA, "よろ", engine).map { it.value },
        )
        assertEquals(listOf(ConversionCandidate("よろしくお願いします。", "よろ")),
            pipeline.prepare(InputMode.HIRAGANA, "よろ", emptyList()))
        assertEquals(listOf(ConversionCandidate("よ", "よ")), pipeline.prepare(InputMode.HIRAGANA, "よ", emptyList()))
        assertTrue(pipeline.prepare(InputMode.HIRAGANA, "よろ", engine, conversionEnabled = false).isEmpty())
        assertEquals(engine, pipeline.prepare(InputMode.HIRAGANA, "よろ", engine, CandidateRequestKind.NEXT_INPUT))
        assertEquals(listOf(ConversionCandidate("12", "12")), pipeline.prepare(InputMode.NUMBER, "12", emptyList()))
    }

    @Test
    fun emptyUserDictionary_addsNoPresetPhrases() {
        val pipeline = CandidatePipeline(getDictionaryCandidates = DictionarySnapshot()::candidatesFor)
        for (reading in listOf("おつ", "よろ", "あり", "かく", "あと")) {
            assertEquals(listOf(ConversionCandidate(reading, reading)),
                pipeline.prepare(InputMode.HIRAGANA, reading, emptyList()))
        }
    }

    @Test
    fun phraseExpansion_preservesPartialConversionSuffix() {
        val session = ConversionSession()
        session.setCandidates(listOf("よろしくお願いします。"))
        session.activate(5)
        session.moveConversionEnd(-3, 5)
        assertEquals("よろ", session.getConversionTarget("よろそのご"))
        assertEquals("そのご", session.getRemainingSuffix("よろそのご"))
    }
}
