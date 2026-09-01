package com.example.twotouchkeyboard

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
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

    private lateinit var context: Context
    private lateinit var keyboardView: View
    private lateinit var candidateBarSlot: LinearLayout
    private lateinit var conversionHint: TextView
    private lateinit var candidateScroll: View
    private lateinit var candidateContainer: LinearLayout

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        keyboardView = inflateKeyboard(context)
        bindViews(keyboardView)
    }

    @Test
    fun candidateScroll_isVisibleByDefault() {
        assertEquals(View.VISIBLE, candidateScroll.visibility)
    }

    @Test
    fun conversionHint_isInvisibleByDefault() {
        assertEquals(View.INVISIBLE, conversionHint.visibility)
    }

    @Test
    fun candidateScroll_keepsFixedHeightWhenHintShows() {
        measureKeyboard()
        val hiddenScrollHeight = candidateScroll.measuredHeight
        val hiddenKeyboardHeight = keyboardView.measuredHeight

        assertTrue("candidate row should reserve its own height", hiddenScrollHeight > 0)

        conversionHint.visibility = View.VISIBLE
        conversionHint.text = "変換: あいう"
        measureKeyboard()

        assertEquals(hiddenScrollHeight, candidateScroll.measuredHeight)
        assertEquals(hiddenKeyboardHeight, keyboardView.measuredHeight)
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
    fun conversionHintHeight_scalesWithFontScale() {
        val defaultHintHeight = measuredHintHeight(fontScale = 1f)
        val largeContext = contextForFontScale(2f)
        val largeHintHeight = measuredHintHeight(largeContext)
        val scaledTextPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            13f,
            largeContext.resources.displayMetrics,
        ).toInt()

        assertTrue(
            "hint slot should grow with font scale instead of clipping 13sp text",
            largeHintHeight > defaultHintHeight,
        )
        assertTrue(
            "13sp hint text should fit in the reserved slot at 2x font scale",
            largeHintHeight >= scaledTextPx,
        )
    }

    @Test
    fun candidateBarSlot_isTransparentByDefault() {
        val background = candidateBarSlot.background
        assertTrue("slot should have a transparent background", background != null)
        assertEquals(0, background!!.alpha)
    }

    private fun measuredHintHeight(fontScale: Float): Int {
        return measuredHintHeight(contextForFontScale(fontScale))
    }

    private fun measuredHintHeight(scaledContext: Context): Int {
        val view = inflateKeyboard(scaledContext)
        bindViews(view)
        conversionHint.visibility = View.VISIBLE
        conversionHint.text = "変換: あいう"
        measure(view)
        return conversionHint.measuredHeight
    }

    private fun contextForFontScale(fontScale: Float): Context {
        return context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                this.fontScale = fontScale
            },
        )
    }

    private fun inflateKeyboard(context: Context): View {
        return LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
    }

    private fun bindViews(root: View) {
        keyboardView = root
        candidateBarSlot = root.findViewById(R.id.candidate_bar_slot)
        conversionHint = root.findViewById(R.id.conversion_hint)
        candidateScroll = root.findViewById(R.id.candidate_scroll)
        candidateContainer = root.findViewById(R.id.candidate_container)
    }

    private fun measureKeyboard() {
        measure(keyboardView)
    }

    private fun measure(view: View) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
    }
}
