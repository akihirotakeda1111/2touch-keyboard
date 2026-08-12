package com.example.twotouchkeyboard

import android.text.InputType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/**
 * お試し入力画面で利用する入力欄シナリオ定義。
 */
object InputTryScenarios {

    data class Scenario(
        val title: String,
        val description: String,
        val hint: String,
        val inputType: Int,
        val keyboardOptions: KeyboardOptions,
        val visualTransformation: VisualTransformation = VisualTransformation.None,
    )

    val all: List<Scenario> = listOf(
        Scenario(
            title = "通常テキスト",
            description = "ひらがな入力・変換・モード切替が有効な一般的な入力欄です。",
            hint = "例: こんにちは",
            inputType = InputType.TYPE_CLASS_TEXT,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                autoCorrectEnabled = true,
            ),
        ),
        Scenario(
            title = "パスワード",
            description = "英字パススルー入力です。変換は無効ですが、モード切替は可能です。",
            hint = "パスワード",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        ),
        Scenario(
            title = "数値",
            description = "初期モードは数字です。変換とモード切替が有効です。",
            hint = "例: 12345",
            inputType = InputType.TYPE_CLASS_NUMBER,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        ),
        Scenario(
            title = "電話番号",
            description = "初期モードは数字です。変換とモード切替が有効です。",
            hint = "例: 09012345678",
            inputType = InputType.TYPE_CLASS_PHONE,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        ),
        Scenario(
            title = "メールアドレス",
            description = "初期モードは英字です。変換とモード切替が有効です。",
            hint = "例: user@example.com",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        ),
        Scenario(
            title = "URL",
            description = "初期モードは英字です。変換とモード切替が有効です。",
            hint = "例: https://example.com",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        ),
    )
}
