package com.example.twotouchkeyboard

/**
 * 入力欄の種別に応じた IME 動作プロファイル。
 */
data class InputFieldProfile(
    val preferredMode: InputMode,
    val conversionEnabled: Boolean,
    val passthroughEnabled: Boolean,
    val modeSwitchEnabled: Boolean,
) {
    companion object {
        val DEFAULT = InputFieldProfile(
            preferredMode = InputMode.HIRAGANA,
            conversionEnabled = true,
            passthroughEnabled = false,
            modeSwitchEnabled = true,
        )
    }
}
