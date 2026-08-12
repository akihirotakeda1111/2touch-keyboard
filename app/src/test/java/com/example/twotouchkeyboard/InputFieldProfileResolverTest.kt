package com.example.twotouchkeyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InputFieldProfileResolverTest {

    @Test
    fun resolve_returnsDefault_forPlainText() {
        val profile = InputFieldProfileResolver.resolve(editorInfo(InputType.TYPE_CLASS_TEXT))

        assertEquals(InputMode.HIRAGANA, profile.preferredMode)
        assertTrue(profile.conversionEnabled)
        assertFalse(profile.passthroughEnabled)
        assertTrue(profile.modeSwitchEnabled)
    }

    @Test
    fun resolve_returnsPasswordProfile_forPassword() {
        val profile = InputFieldProfileResolver.resolve(
            editorInfo(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            ),
        )

        assertEquals(InputMode.ALPHABET, profile.preferredMode)
        assertFalse(profile.conversionEnabled)
        assertTrue(profile.passthroughEnabled)
        assertTrue(profile.modeSwitchEnabled)
    }

    @Test
    fun resolve_returnsFieldProfile_forPhone() {
        val profile = InputFieldProfileResolver.resolve(
            editorInfo(InputType.TYPE_CLASS_PHONE),
        )

        assertEquals(InputMode.NUMBER, profile.preferredMode)
        assertTrue(profile.conversionEnabled)
        assertFalse(profile.passthroughEnabled)
        assertTrue(profile.modeSwitchEnabled)
    }

    @Test
    fun resolve_returnsFieldProfile_forEmail() {
        val profile = InputFieldProfileResolver.resolve(
            editorInfo(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            ),
        )

        assertEquals(InputMode.ALPHABET, profile.preferredMode)
        assertTrue(profile.conversionEnabled)
        assertFalse(profile.passthroughEnabled)
        assertTrue(profile.modeSwitchEnabled)
    }

    @Test
    fun resolve_returnsFieldProfile_forNumberClass() {
        val profile = InputFieldProfileResolver.resolve(
            editorInfo(InputType.TYPE_CLASS_NUMBER),
        )

        assertEquals(InputMode.NUMBER, profile.preferredMode)
        assertTrue(profile.conversionEnabled)
        assertFalse(profile.passthroughEnabled)
        assertTrue(profile.modeSwitchEnabled)
    }

    private fun editorInfo(inputType: Int): EditorInfo {
        return EditorInfo().apply { this.inputType = inputType }
    }
}
