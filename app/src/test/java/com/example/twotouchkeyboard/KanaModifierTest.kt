package com.example.twotouchkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KanaModifierTest {

    @Test
    fun applyDakuten_transformsKaToGa() {
        assertEquals('が', KanaModifier.applyDakuten('か'))
    }

    @Test
    fun applyHandakuten_transformsHaToPa() {
        assertEquals('ぱ', KanaModifier.applyHandakuten('は'))
    }

    @Test
    fun applySmallKana_transformsYaToSmallYa() {
        assertEquals('ゃ', KanaModifier.applySmallKana('や'))
    }

    @Test
    fun applyDakuten_returnsNull_forUnsupportedCharacter() {
        assertNull(KanaModifier.applyDakuten('あ'))
    }
}
