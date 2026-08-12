package com.example.twotouchkeyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * [EditorInfo.inputType] から入力欄プロファイルを決定する。
 */
object InputFieldProfileResolver {

    fun resolve(info: EditorInfo?): InputFieldProfile {
        if (info == null) return InputFieldProfile.DEFAULT

        val inputType = info.inputType
        val typeClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION

        if (isPasswordVariation(variation)) {
            return passwordProfile(preferredMode = InputMode.ALPHABET)
        }

        when (typeClass) {
            InputType.TYPE_CLASS_NUMBER -> return fieldProfile(InputMode.NUMBER)
            InputType.TYPE_CLASS_PHONE -> return fieldProfile(InputMode.NUMBER)
        }

        if (isAlphabetPassthroughVariation(variation)) {
            return fieldProfile(InputMode.ALPHABET)
        }

        return InputFieldProfile.DEFAULT
    }

    private fun isPasswordVariation(variation: Int): Boolean {
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun isAlphabetPassthroughVariation(variation: Int): Boolean {
        return variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI
    }

    private fun passwordProfile(preferredMode: InputMode): InputFieldProfile {
        return InputFieldProfile(
            preferredMode = preferredMode,
            conversionEnabled = false,
            passthroughEnabled = true,
            modeSwitchEnabled = true,
        )
    }

    private fun fieldProfile(preferredMode: InputMode): InputFieldProfile {
        return InputFieldProfile(
            preferredMode = preferredMode,
            conversionEnabled = true,
            passthroughEnabled = false,
            modeSwitchEnabled = true,
        )
    }
}
