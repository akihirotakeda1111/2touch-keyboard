package com.example.twotouchkeyboard

import com.example.twotouchkeyboard.input.TwoTouchExtensionSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardMappingsTest {

    @Test
    fun hiraganaExtensionRows_matchSpecifiedAssignments() {
        assertEquals("Ａ", charAt(KeyboardMappings.hiraganaExtensionRows, row = 1, secondKey = 6))
        assertEquals("Ｅ", charAt(KeyboardMappings.hiraganaExtensionRows, row = 1, secondKey = 0))
        assertEquals("Ｚ", charAt(KeyboardMappings.hiraganaExtensionRows, row = 6, secondKey = 6))
        assertEquals("１", charAt(KeyboardMappings.hiraganaExtensionRows, row = 9, secondKey = 6))
        assertEquals("５", charAt(KeyboardMappings.hiraganaExtensionRows, row = 9, secondKey = 0))
        assertEquals("６", charAt(KeyboardMappings.hiraganaExtensionRows, row = 0, secondKey = 6))
        assertEquals("０", charAt(KeyboardMappings.hiraganaExtensionRows, row = 0, secondKey = 0))
        assertEquals("！", charAt(KeyboardMappings.hiraganaExtensionRows, row = 6, secondKey = 7))
        assertEquals("／", charAt(KeyboardMappings.hiraganaExtensionRows, row = 6, secondKey = 0))
        assertEquals("￥", charAt(KeyboardMappings.hiraganaExtensionRows, row = 7, secondKey = 6))
        assertEquals("・", charAt(KeyboardMappings.hiraganaExtensionRows, row = 7, secondKey = 0))
        assertEquals("（", charAt(KeyboardMappings.hiraganaExtensionRows, row = 8, secondKey = 6))
        assertEquals("＄", charAt(KeyboardMappings.hiraganaExtensionRows, row = 8, secondKey = 0))
    }

    @Test
    fun alphabetExtensionRows_useHalfwidthForNonLetterSlots() {
        assertEquals("6", extensionChar(row = 0, secondKey = 6))
        assertEquals("0", extensionChar(row = 0, secondKey = 0))
        assertNull(extensionChar(row = 2, secondKey = 6))
        assertEquals("!", extensionChar(row = 6, secondKey = 7))
        assertEquals("/", extensionChar(row = 6, secondKey = 0))
        assertEquals("¥", extensionChar(row = 7, secondKey = 6))
        assertEquals("&", extensionChar(row = 7, secondKey = 7))
        assertEquals("(", extensionChar(row = 8, secondKey = 6))
        assertEquals(")", extensionChar(row = 8, secondKey = 7))
        assertEquals("1", extensionChar(row = 9, secondKey = 6))
        assertEquals("5", extensionChar(row = 9, secondKey = 0))
    }

    @Test
    fun appendFromSecondKey_supportsAlphabetRowSevenKeySix() {
        var appended: String? = null
        val appendedResult = TwoTouchExtensionSupport.appendFromSecondKey(
            row = 7,
            key = KeyboardKey.Digit(6),
            primaryRows = KeyboardMappings.alphabetRows,
            extensionRows = KeyboardMappings.alphabetExtensionRows,
        ) { appended = it }

        assertEquals(true, appendedResult)
        assertEquals("¥", appended)
    }

    @Test
    fun appendFromSecondKey_supportsZeroAsSecondTouch() {
        var appended: String? = null
        val appendedResult = TwoTouchExtensionSupport.appendFromSecondKey(
            row = 1,
            key = KeyboardKey.Zero,
            primaryRows = KeyboardMappings.hiraganaRows,
            extensionRows = KeyboardMappings.hiraganaExtensionRows,
        ) { appended = it }

        assertEquals(true, appendedResult)
        assertEquals("Ｅ", appended)
    }

    private fun charAt(
        rows: Map<Int, ExtensionSlots>,
        row: Int,
        secondKey: Int,
    ): String? {
        return rows[row]?.get(secondKey)?.toString()
    }

    private fun extensionChar(row: Int, secondKey: Int): String? {
        return charAt(KeyboardMappings.alphabetExtensionRows, row, secondKey)
    }
}
