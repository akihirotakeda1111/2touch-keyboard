package com.example.twotouchkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SymbolPanelTest {

    @Test
    fun landscape_expandsReadingOrderIntoEightByThree() {
        assertEquals(8, SymbolPanel.LANDSCAPE_COLUMN_COUNT)
        assertEquals(3, SymbolPanel.LANDSCAPE_ROW_COUNT)
        assertEquals(7, SymbolPanel.LANDSCAPE_CLOSE_COLUMN)
        assertEquals(2, SymbolPanel.LANDSCAPE_CLOSE_ROW)
        assertEquals(
            listOf(
                listOf("、", "。", "，", "．", "！", "？", "：", "；"),
                listOf("（", "）", "｛", "｝", "－", "＿", "＋", "＝"),
                listOf("＠", "＃", "＆", "＄", "＊", "／", "・"),
            ),
            SymbolPanel.landscapeRowsFor(InputMode.HIRAGANA),
        )
    }

    @Test
    fun layout_placesTwentyThreeSymbolsInFourBySixLeavingCloseCellEmpty() {
        assertEquals(4, SymbolPanel.COLUMN_COUNT)
        assertEquals(6, SymbolPanel.ROW_COUNT)
        assertEquals(23, SymbolPanel.keys.size)
        assertEquals(3, SymbolPanel.CLOSE_COLUMN)
        assertEquals(5, SymbolPanel.CLOSE_ROW)
    }

    @Test
    fun hiraganaMode_usesFullWidthSymbols() {
        assertEquals(
            listOf(
                listOf("、", "。", "，", "．"),
                listOf("！", "？", "：", "；"),
                listOf("（", "）", "｛", "｝"),
                listOf("－", "＿", "＋", "＝"),
                listOf("＠", "＃", "＆", "＄"),
                listOf("＊", "／", "・"),
            ),
            SymbolPanel.rowsFor(InputMode.HIRAGANA),
        )
    }

    @Test
    fun alphabetAndNumberModes_useHalfWidthSymbols() {
        val halfWidth = listOf(
            listOf("､", "｡", ",", "."),
            listOf("!", "?", ":", ";"),
            listOf("(", ")", "{", "}"),
            listOf("-", "_", "+", "="),
            listOf("@", "#", "&", "$"),
            listOf("*", "/", "･"),
        )

        assertEquals(halfWidth, SymbolPanel.rowsFor(InputMode.ALPHABET))
        assertEquals(halfWidth, SymbolPanel.rowsFor(InputMode.NUMBER))
    }

    @Test
    fun charactersFor_keepsTheModeSnapshotIndependentOfLaterModes() {
        val hiragana = SymbolPanel.charactersFor(InputMode.HIRAGANA)
        val alphabet = SymbolPanel.charactersFor(InputMode.ALPHABET)

        assertEquals("、", hiragana.getValue(R.id.symbol_key_ideographic_comma))
        assertEquals("､", alphabet.getValue(R.id.symbol_key_ideographic_comma))
        assertEquals("、", hiragana.getValue(R.id.symbol_key_ideographic_comma))
        assertEquals("，", hiragana.getValue(R.id.symbol_key_comma))
        assertEquals(",", alphabet.getValue(R.id.symbol_key_comma))
    }

    @Test
    fun punctuation_distinguishesIdeographicMarksFromAddedCommaAndPeriod() {
        val ideographicComma = key(R.id.symbol_key_ideographic_comma)
        val ideographicPeriod = key(R.id.symbol_key_ideographic_period)
        val comma = key(R.id.symbol_key_comma)
        val period = key(R.id.symbol_key_period)
        val middleDot = key(R.id.symbol_key_middle_dot)

        assertEquals(0x3001, ideographicComma.fullWidth.codePointAt(0))
        assertEquals(0xFF64, ideographicComma.halfWidth.codePointAt(0))
        assertEquals(0x3002, ideographicPeriod.fullWidth.codePointAt(0))
        assertEquals(0xFF61, ideographicPeriod.halfWidth.codePointAt(0))
        assertEquals(0xFF0C, comma.fullWidth.codePointAt(0))
        assertEquals(0x002C, comma.halfWidth.codePointAt(0))
        assertEquals(0xFF0E, period.fullWidth.codePointAt(0))
        assertEquals(0x002E, period.halfWidth.codePointAt(0))
        assertEquals(0x30FB, middleDot.fullWidth.codePointAt(0))
        assertEquals(0xFF65, middleDot.halfWidth.codePointAt(0))
        assertNotEquals(ideographicComma.fullWidth, comma.fullWidth)
        assertNotEquals(ideographicPeriod.fullWidth, period.fullWidth)
    }

    @Test
    fun everyKey_usesADifferentCharacterForFullWidthAndHalfWidth() {
        val codePoints = SymbolPanel.keys.flatMap { key ->
            listOf(
                key.fullWidth.codePointAt(0),
                key.halfWidth.codePointAt(0),
            )
        }

        assertEquals(codePoints.toSet().size, codePoints.size)
        SymbolPanel.keys.forEach { key ->
            assertEquals(key.fullWidth, key.characterFor(InputMode.HIRAGANA))
            assertEquals(key.halfWidth, key.characterFor(InputMode.ALPHABET))
            assertEquals(key.halfWidth, key.characterFor(InputMode.NUMBER))
        }
    }

    private fun key(viewId: Int): SymbolPanel.Key {
        return SymbolPanel.keys.first { it.viewId == viewId }
    }
}
