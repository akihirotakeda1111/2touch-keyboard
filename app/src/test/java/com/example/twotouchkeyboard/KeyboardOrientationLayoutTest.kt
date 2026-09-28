package com.example.twotouchkeyboard

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ViewFlipper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class PortraitKeyboardLayoutTest {

    private lateinit var context: Context
    private lateinit var keyboard: View

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        keyboard = LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
        layout(keyboard, KEYBOARD_WIDTH)
    }

    @Test
    fun portrait_keepsFourByFiveAndTallModeAndEnterKeys() {
        val grid = keyboard.findViewById<GridLayout>(R.id.keyboard_grid)
        assertEquals(4, grid.columnCount)
        assertEquals(5, grid.rowCount)
        assertCells(
            grid,
            mapOf(
                R.id.key_1 to (0 to 0),
                R.id.key_2 to (1 to 0),
                R.id.key_3 to (2 to 0),
                R.id.key_delete to (3 to 0),
                R.id.key_4 to (0 to 1),
                R.id.key_5 to (1 to 1),
                R.id.key_6 to (2 to 1),
                R.id.key_cursor_left to (3 to 1),
                R.id.key_7 to (0 to 2),
                R.id.key_8 to (1 to 2),
                R.id.key_9 to (2 to 2),
                R.id.key_cursor_right to (3 to 2),
                R.id.key_star to (0 to 3),
                R.id.key_0 to (1 to 3),
                R.id.key_hash to (2 to 3),
                R.id.key_enter to (3 to 3),
                R.id.key_text_modifier to (1 to 4),
                R.id.key_space to (2 to 4),
            ),
        )

        val keyHeight = context.resources.getDimensionPixelSize(R.dimen.keyboard_key_height)
        val tallHeight = context.resources.getDimensionPixelSize(R.dimen.keyboard_tall_key_height)
        assertEquals(keyHeight, keyboard.findViewById<Button>(R.id.key_1).measuredHeight)
        assertEquals(tallHeight, keyboard.findViewById<Button>(R.id.key_star).measuredHeight)
        assertEquals(tallHeight, keyboard.findViewById<Button>(R.id.key_enter).measuredHeight)
        assertEquals(expectedKeyboardHeight(context, rowCount = 5), keyboard.measuredHeight)
    }

    private fun assertCells(grid: GridLayout, expected: Map<Int, Pair<Int, Int>>) {
        expected.forEach { (viewId, cell) ->
            assertEquals(cell, cellOf(grid.findViewById(viewId), grid))
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "+land")
class LandscapeKeyboardLayoutTest {

    private lateinit var context: Context
    private lateinit var keyboard: View

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        keyboard = LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
        layout(keyboard, KEYBOARD_WIDTH)
    }

    @Test
    fun landscape_usesSixByThreeWithEqualKeys() {
        val grid = keyboard.findViewById<GridLayout>(R.id.keyboard_grid)
        assertEquals(6, grid.columnCount)
        assertEquals(3, grid.rowCount)
        assertEquals(18, grid.childCount)
        assertCells(
            grid,
            mapOf(
                R.id.key_star to (0 to 0),
                R.id.key_1 to (1 to 0),
                R.id.key_2 to (2 to 0),
                R.id.key_3 to (3 to 0),
                R.id.key_0 to (4 to 0),
                R.id.key_delete to (5 to 0),
                R.id.key_hash to (0 to 1),
                R.id.key_4 to (1 to 1),
                R.id.key_5 to (2 to 1),
                R.id.key_6 to (3 to 1),
                R.id.key_cursor_left to (4 to 1),
                R.id.key_space to (5 to 1),
                R.id.key_text_modifier to (0 to 2),
                R.id.key_7 to (1 to 2),
                R.id.key_8 to (2 to 2),
                R.id.key_9 to (3 to 2),
                R.id.key_cursor_right to (4 to 2),
                R.id.key_enter to (5 to 2),
            ),
        )

        val keyHeight = context.resources.getDimensionPixelSize(R.dimen.keyboard_key_height)
        val widths = mutableListOf<Int>()
        grid.children().forEach { button ->
            assertEquals(keyHeight, button.measuredHeight)
            widths += button.measuredWidth
        }
        assertTrue(widths.max() - widths.min() <= 1)
        assertEquals(expectedKeyboardHeight(context, rowCount = 3), keyboard.measuredHeight)
    }

    @Test
    fun landscape_isTwoRowsShorterThanPortraitFootprint() {
        val portrait = expectedKeyboardHeight(context, rowCount = 5)
        val landscape = expectedKeyboardHeight(context, rowCount = 3)
        val rowPitch = context.resources.getDimensionPixelSize(R.dimen.keyboard_key_height) +
            context.resources.getDimensionPixelSize(R.dimen.keyboard_key_margin) * 2
        assertEquals(rowPitch * 2, portrait - landscape)
        assertEquals(landscape, keyboard.measuredHeight)
    }

    @Test
    fun landscape_symbolPanelMatchesMainKeyboardHeight() {
        val mainGrid = keyboard.findViewById<GridLayout>(R.id.keyboard_grid)
        val symbolGrid = keyboard.findViewById<GridLayout>(R.id.symbol_keyboard_grid)
        layout(symbolGrid, KEYBOARD_WIDTH)
        assertEquals(SymbolPanel.LANDSCAPE_COLUMN_COUNT, symbolGrid.columnCount)
        assertEquals(SymbolPanel.LANDSCAPE_ROW_COUNT, symbolGrid.rowCount)
        assertEquals(mainGrid.measuredHeight, symbolGrid.measuredHeight)

        SymbolPanel.keys.forEach { key ->
            val button = symbolGrid.findViewById<Button>(key.viewId)
            assertEquals(
                key.landscapeColumn to key.landscapeRow,
                cellOf(button, symbolGrid),
            )
        }
        val close = symbolGrid.findViewById<Button>(R.id.symbol_key_close)
        assertEquals(
            SymbolPanel.LANDSCAPE_CLOSE_COLUMN to SymbolPanel.LANDSCAPE_CLOSE_ROW,
            cellOf(close, symbolGrid),
        )
    }

    @Test
    fun landscape_switchingToSymbolPanelDoesNotChangeKeyboardHeight() {
        val flipper = keyboard.findViewById<ViewFlipper>(R.id.keyboard_flipper)
        val mainHeight = keyboard.measuredHeight
        flipper.displayedChild = 1
        layout(keyboard, KEYBOARD_WIDTH)
        assertEquals(mainHeight, keyboard.measuredHeight)
    }

    @Test
    fun landscape_reservesCandidateBarHeightWhenEmpty() {
        val candidate = keyboard.findViewById<View>(R.id.candidate_scroll)
        val panel = keyboard.findViewById<View>(R.id.keyboard_panel)
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.candidate_bar_height),
            candidate.measuredHeight,
        )
        assertEquals(candidate.bottom, panel.top)
        assertTrue(candidate.measuredHeight > 0)
    }

    private fun assertCells(grid: GridLayout, expected: Map<Int, Pair<Int, Int>>) {
        expected.forEach { (viewId, cell) ->
            assertEquals(cell, cellOf(grid.findViewById(viewId), grid))
        }
    }
}

private const val KEYBOARD_WIDTH = 1080

private fun layout(view: View, width: Int) {
    val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
    val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    view.measure(widthSpec, heightSpec)
    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
}

private fun cellOf(button: View, grid: GridLayout): Pair<Int, Int> {
    val columnWidth = grid.width / grid.columnCount
    val rowHeight = grid.height / grid.rowCount
    return button.left / columnWidth to button.top / rowHeight
}

private fun expectedKeyboardHeight(context: Context, rowCount: Int): Int {
    val key = context.resources.getDimensionPixelSize(R.dimen.keyboard_key_height)
    val margin = context.resources.getDimensionPixelSize(R.dimen.keyboard_key_margin)
    val outer = context.resources.getDimensionPixelSize(R.dimen.keyboard_outer_padding)
    val candidate = context.resources.getDimensionPixelSize(R.dimen.candidate_bar_height)
    return candidate + outer * 2 + rowCount * (key + margin * 2)
}

private fun GridLayout.children(): List<View> = List(childCount) { getChildAt(it) }
