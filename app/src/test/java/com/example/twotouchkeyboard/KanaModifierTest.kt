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

    @Test
    fun cycle_smallKana_cyclesAAndSmallA() {
        assertEquals('ぁ', KanaModifier.cycle('あ'))
        assertEquals('あ', KanaModifier.cycle('ぁ'))
    }

    @Test
    fun cycle_dakuten_cyclesKaAndGa() {
        assertEquals('が', KanaModifier.cycle('か'))
        assertEquals('か', KanaModifier.cycle('が'))
    }

    @Test
    fun cycle_haRow_cyclesHaBaAndPa() {
        assertEquals('ば', KanaModifier.cycle('は'))
        assertEquals('ぱ', KanaModifier.cycle('ば'))
        assertEquals('は', KanaModifier.cycle('ぱ'))
    }

    @Test
    fun cycle_tsu_cyclesSmallThenDakuten() {
        assertEquals('っ', KanaModifier.cycle('つ'))
        assertEquals('づ', KanaModifier.cycle('っ'))
        assertEquals('つ', KanaModifier.cycle('づ'))
    }

    @Test
    fun cycle_returnsNull_forUnsupportedCharacter() {
        assertNull(KanaModifier.cycle('ん'))
    }
}
