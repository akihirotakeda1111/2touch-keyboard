package com.example.twotouchkeyboard

import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat

/**
 * ナビゲーションバーと画面の切り欠きにキーや候補が重ならない余白。
 *
 * 横表示では左右へ来るバーや切り欠きも避ける。上端のステータスバーは対象にしない。
 */
internal object KeyboardEdgeAvoidance {
    fun paddingFor(insets: WindowInsetsCompat): Insets {
        val safe = insets.getInsets(
            WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        return Insets.of(safe.left, 0, safe.right, safe.bottom)
    }
}
