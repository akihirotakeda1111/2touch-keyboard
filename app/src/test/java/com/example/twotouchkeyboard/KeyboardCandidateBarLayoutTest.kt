package com.example.twotouchkeyboard

import android.content.Context
import android.graphics.drawable.ColorDrawable
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
    private lateinit var keyboardPanel: View
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
    fun keyboardHeight_doesNotChangeWhenCandidatesFill() {
        measureKeyboard()
        val emptyHeight = keyboardView.measuredHeight
        val emptyScrollHeight = candidateScroll.measuredHeight

        assertTrue("candidate row should reserve height while empty", emptyScrollHeight > 0)

        candidateContainer.addView(
            TextView(context).apply { text = "候補" },
        )
        measureKeyboard()

        assertEquals(emptyHeight, keyboardView.measuredHeight)
        assertEquals(emptyScrollHeight, candidateScroll.measuredHeight)
    }

    @Test
    fun candidateScroll_isTransparentByDefault() {
        val background = candidateScroll.background
        assertTrue("candidate row should have a transparent background", background != null)
        assertEquals(0, background!!.alpha)
    }

    @Test
    fun keyboardRoot_isTransparent() {
        val background = keyboardView.background
        assertTrue("keyboard root should be transparent outside the key panel", background != null)
        assertEquals(0, background!!.alpha)
    }

    @Test
    fun keyboardPanel_usesOpaqueKeyboardBackground() {
        val expected = ContextCompat.getColor(context, R.color.keyboard_background)
        val background = keyboardPanel.background
        assertTrue(background is ColorDrawable)
        assertEquals(expected, (background as ColorDrawable).color)
    }

    @Test
    fun candidateScroll_isAdjacentToKeyboardPanel() {
        layoutKeyboard()
        assertEquals(candidateScroll.bottom, keyboardPanel.top)
    }

    private fun inflateKeyboard(context: Context): View {
        return LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
    }

    private fun bindViews(root: View) {
        keyboardView = root
        keyboardPanel = root.findViewById(R.id.keyboard_panel)
        candidateScroll = root.findViewById(R.id.candidate_scroll)
        candidateContainer = root.findViewById(R.id.candidate_container)
    }

    private fun measureKeyboard() {
        measure(keyboardView)
    }

    private fun layoutKeyboard() {
        measureKeyboard()
        keyboardView.layout(0, 0, keyboardView.measuredWidth, keyboardView.measuredHeight)
    }

    private fun measure(view: View) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
    }
}
