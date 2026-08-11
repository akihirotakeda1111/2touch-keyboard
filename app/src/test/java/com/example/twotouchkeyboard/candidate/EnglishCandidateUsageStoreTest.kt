package com.example.twotouchkeyboard.candidate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnglishCandidateUsageStoreTest {

    private lateinit var context: Context
    private lateinit var store: EnglishCandidateUsageStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        EnglishCandidateUsageStore(context).clear()
        store = EnglishCandidateUsageStore(context)
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun record_incrementsUsageCount() {
        store.record("hel", "help")
        store.record("hel", "help")
        store.record("hel", "hello")

        assertEquals(2, store.getCount("hel", "help"))
        assertEquals(1, store.getCount("hel", "hello"))
        assertEquals(0, store.getCount("hel", "held"))
    }

    @Test
    fun record_persistsAcrossInstances() {
        store.record("hel", "help")

        val reloaded = EnglishCandidateUsageStore(context)

        assertEquals(1, reloaded.getCount("hel", "help"))
    }

    @Test
    fun clear_removesStoredCounts() {
        store.record("hel", "help")
        store.clear()

        assertEquals(0, store.getCount("hel", "help"))
        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun buildKey_normalizesCase() {
        assertEquals(
            "hel\thelp",
            EnglishCandidateUsageStore.buildKey("HEL", "Help"),
        )
    }
}
