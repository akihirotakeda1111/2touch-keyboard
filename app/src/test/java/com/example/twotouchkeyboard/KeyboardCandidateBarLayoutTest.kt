package com.example.twotouchkeyboard

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardCandidateBarLayoutTest {

    private lateinit var keyboardView: View
    private lateinit var candidateBarSlot: LinearLayout
    private lateinit var conversionHint: TextView
    private lateinit var candidateContainer: LinearLayout

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        keyboardView = LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
        candidateBarSlot = keyboardView.findViewById(R.id.candidate_bar_slot)
        conversionHint = keyboardView.findViewById(R.id.conversion_hint)
        candidateContainer = keyboardView.findViewById(R.id.candidate_container)
    }

    @Test
    fun candidateScroll_isVisibleByDefault() {
        val candidateScroll = keyboardView.findViewById<View>(R.id.candidate_scroll)
        assertEquals(View.VISIBLE, candidateScroll.visibility)
    }

    @Test
    fun keyboardHeight_doesNotChangeWhenCandidateBarFillsOrHintShows() {
        measureKeyboard()
        val emptyHeight = keyboardView.measuredHeight
        val emptySlotHeight = candidateBarSlot.measuredHeight

        assertTrue("candidate slot should reserve height while empty", emptySlotHeight > 0)

        conversionHint.visibility = View.VISIBLE
        conversionHint.text = "変換: あいう"
        val candidate = TextView(candidateBarSlot.context).apply {
            text = "候補"
        }
        candidateContainer.addView(candidate)
        candidateBarSlot.setBackgroundColor(
            ContextCompat.getColor(candidateBarSlot.context, R.color.candidate_bar_background),
        )

        measureKeyboard()

        assertEquals(emptyHeight, keyboardView.measuredHeight)
        assertEquals(emptySlotHeight, candidateBarSlot.measuredHeight)
    }

    @Test
    fun candidateBarSlot_isTransparentByDefault() {
        val background = candidateBarSlot.background
        assertTrue("slot should have a transparent background", background != null)
        assertEquals(0, background!!.alpha)
    }

    private fun measureKeyboard() {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        keyboardView.measure(widthSpec, heightSpec)
    }
}
