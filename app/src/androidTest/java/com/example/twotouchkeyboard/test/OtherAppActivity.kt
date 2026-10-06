package com.example.twotouchkeyboard.test

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** 設定キーの回帰で、キーボードアプリとは別の前面アプリとして使う。 */
class OtherAppActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val input = EditText(this).apply {
            hint = "入力"
            contentDescription = "別アプリ入力"
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@OtherAppActivity).apply { text = "別アプリ" })
                addView(input)
            },
        )
        input.requestFocus()
        input.post {
            val imm = getSystemService(InputMethodManager::class.java)
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
