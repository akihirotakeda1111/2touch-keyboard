package com.example.twotouchkeyboard

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SymbolPanelLayoutTest {

    private lateinit var context: Context
    private lateinit var symbolGrid: GridLayout
    private lateinit var mainGrid: GridLayout
    private lateinit var mainKey: Button

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val keyboard = LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
        symbolGrid = keyboard.findViewById(R.id.symbol_keyboard_grid)
        mainGrid = keyboard.findViewById(R.id.keyboard_grid)
        mainKey = keyboard.findViewById(R.id.key_1)
        layout(symbolGrid, KEYBOARD_WIDTH)
        layout(mainGrid, KEYBOARD_WIDTH)
    }

    @Test
    fun symbolGrid_placesFullWidthSymbolsAndCloseInFourBySix() {
        assertEquals(SymbolPanel.COLUMN_COUNT, symbolGrid.columnCount)
        assertEquals(SymbolPanel.ROW_COUNT, symbolGrid.rowCount)
        assertEquals(SymbolPanel.keys.size + 1, symbolGrid.childCount)

        SymbolPanel.keys.forEach { key ->
            val button = symbolGrid.findViewById<Button>(key.viewId)
            assertEquals(key.column to key.row, cellOf(button))
            assertEquals(key.fullWidth, button.text.toString())
            assertEquals(key.fullWidth.codePointAt(0), button.text.toString().codePointAt(0))
        }

        val close = symbolGrid.findViewById<Button>(R.id.symbol_key_close)
        assertEquals(SymbolPanel.CLOSE_COLUMN to SymbolPanel.CLOSE_ROW, cellOf(close))
        assertEquals(context.getString(R.string.key_symbol_close), close.text.toString())
    }

    @Test
    fun symbolKeys_keepMainKeyboardKeySizeAndGrowByTwoRows() {
        val keyHeight = context.resources.getDimensionPixelSize(R.dimen.keyboard_key_height)
        val margin = context.resources.getDimensionPixelSize(R.dimen.keyboard_key_margin)
        val rowPitch = keyHeight + margin * 2
        val mainParams = mainKey.layoutParams as GridLayout.LayoutParams

        assertTrue(mainKey.measuredWidth > 0)
        (SymbolPanel.keys.map { it.viewId } + R.id.symbol_key_close).forEach { viewId ->
            val button = symbolGrid.findViewById<Button>(viewId)
            val params = button.layoutParams as GridLayout.LayoutParams
            assertEquals(mainKey.measuredWidth, button.measuredWidth)
            assertEquals(mainKey.measuredHeight, button.measuredHeight)
            assertEquals(mainParams.width, params.width)
            assertEquals(mainParams.height, params.height)
            assertEquals(mainKey.leftMargin(), button.leftMargin())
        }
        assertEquals(SymbolPanel.ROW_COUNT * rowPitch, symbolGrid.measuredHeight)
    }

    private fun layout(grid: GridLayout, width: Int) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        grid.measure(widthSpec, heightSpec)
        grid.layout(0, 0, grid.measuredWidth, grid.measuredHeight)
    }

    private fun cellOf(button: Button): Pair<Int, Int> {
        val column = (button.left + button.width / 2) / (symbolGrid.width / SymbolPanel.COLUMN_COUNT)
        val row = (button.top + button.height / 2) / (symbolGrid.height / SymbolPanel.ROW_COUNT)
        return column to row
    }

    private fun View.leftMargin(): Int {
        return (layoutParams as ViewGroup.MarginLayoutParams).leftMargin
    }

    private companion object {
        const val KEYBOARD_WIDTH = 1080
    }
}
