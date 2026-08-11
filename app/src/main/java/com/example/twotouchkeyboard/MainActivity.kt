package com.example.twotouchkeyboard

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * ランチャー Activity。
 * IME アプリには通常 Activity がないため Android Studio の Run が失敗する。
 * この Activity でインストール後の有効化手順を案内する。
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_open_ime_settings).setOnClickListener {
            ImeSetupNavigation.openInputMethodSettings(this)
        }

        findViewById<Button>(R.id.btn_open_ime_picker).setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }
}
