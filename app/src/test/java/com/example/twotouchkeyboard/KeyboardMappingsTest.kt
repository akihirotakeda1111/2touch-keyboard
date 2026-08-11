package com.example.twotouchkeyboard

import com.example.twotouchkeyboard.input.TwoTouchExtensionSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardMappingsTest {

    @Test
    fun hiraganaRows_assignRaRiRuReRoToNineAndWaToZero() {
        assertEquals("らりるれろ", KeyboardMappings.hiraganaRows[9])
        assertEquals("わをん", KeyboardMappings.hiraganaRows[0])
        assertEquals("ら", KeyboardMappings.hiraganaRowHeadLabels[9])
        assertEquals("わ", KeyboardMappings.hiraganaRowHeadLabels[0])
    }

    @Test
    fun alphabetRows_useLowercaseOnly() {
        assertEquals("abc", KeyboardMappings.alphabetRows[1])
        assertEquals("pqr", KeyboardMappings.alphabetRows[6])
        assertEquals("yz", KeyboardMappings.alphabetRows[9])
        assertNull(KeyboardMappings.alphabetRows[0])
    }

    @Test
    fun alphabetTwoTouchIdleLabel_showsLowercaseOnly() {
        assertEquals("abc", KeyboardMappings.alphabetTwoTouchIdleLabel(1))
        assertEquals("yz", KeyboardMappings.alphabetTwoTouchIdleLabel(9))
    }

    @Test
    fun waitingLabel_returnsNullForUnavailableSecondTouchKeys() {
        assertNull(
            TwoTouchExtensionSupport.waitingLabel(
                row = 8,
                key = KeyboardKey.Digit(4),
                primaryRows = KeyboardMappings.hiraganaRows,
                extensionRows = KeyboardMappings.hiraganaExtensionRows,
            ),
        )
    }

    @Test
    fun alphabetToggleDigitRow_containsZeroThroughNine() {
        assertEquals("0123456789", KeyboardMappings.alphabetToggleDigitRow)
    }

    @Test
    fun waitingLabel_matchesExtensionInputForNineAndZeroRows() {
        assertEquals(
            "１",
            TwoTouchExtensionSupport.waitingLabel(
                row = 9,
                key = KeyboardKey.Digit(6),
                primaryRows = KeyboardMappings.hiraganaRows,
                extensionRows = KeyboardMappings.hiraganaExtensionRows,
            ),
        )
        assertEquals(
            "０",
            TwoTouchExtensionSupport.waitingLabel(
                row = 0,
                key = KeyboardKey.Zero,
                primaryRows = KeyboardMappings.hiraganaRows,
                extensionRows = KeyboardMappings.hiraganaExtensionRows,
            ),
        )
        assertEquals(
            "1",
            TwoTouchExtensionSupport.waitingLabel(
                row = 9,
                key = KeyboardKey.Digit(6),
                primaryRows = KeyboardMappings.alphabetRows,
                extensionRows = KeyboardMappings.alphabetExtensionRows,
                maxPrimarySecondKey = 6,
                preferExtensionOnConflict = true,
            ),
        )
        assertEquals(
            "0",
            TwoTouchExtensionSupport.waitingLabel(
                row = 0,
                key = KeyboardKey.Zero,
                primaryRows = KeyboardMappings.alphabetRows,
                extensionRows = KeyboardMappings.alphabetExtensionRows,
                maxPrimarySecondKey = 6,
                preferExtensionOnConflict = true,
            ),
        )
    }

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
        assertNull(extensionChar(row = 1, secondKey = 6))
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
    fun appendFromSecondKey_supportsAlphabetRowSixKeyThree() {
        var appended: String? = null
        val appendedResult = TwoTouchExtensionSupport.appendFromSecondKey(
            row = 6,
            key = KeyboardKey.Digit(3),
            primaryRows = KeyboardMappings.alphabetRows,
            extensionRows = KeyboardMappings.alphabetExtensionRows,
            maxPrimarySecondKey = 6,
            preferExtensionOnConflict = true,
            append = { appended = it },
        )

        assertEquals(true, appendedResult)
        assertEquals("r", appended)
    }

    @Test
    fun appendFromSecondKey_supportsAlphabetRowSevenKeySix() {
        var appended: String? = null
        val appendedResult = TwoTouchExtensionSupport.appendFromSecondKey(
            row = 7,
            key = KeyboardKey.Digit(6),
            primaryRows = KeyboardMappings.alphabetRows,
            extensionRows = KeyboardMappings.alphabetExtensionRows,
            maxPrimarySecondKey = 6,
            preferExtensionOnConflict = true,
            append = { appended = it },
        )

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
            append = { appended = it },
        )

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
