package com.example.twotouchkeyboard

import android.view.inputmethod.EditorInfo

/**
 * 回転で入力ビューが作り直されても、同じ入力先の未確定状態を破棄しない。
 *
 * 2タッチの1打目待ち、トグル中の文字、変換中の文字列と選択はサービス側に残す。
 * 別の入力先へ移ったときだけ、通常の確定と破棄に戻す。
 */
internal class OrientationInputHandoff {
    data class EditorKey(
        val packageName: String?,
        val fieldId: Int,
        val inputType: Int,
        val imeOptions: Int,
    )

    private var pendingEditor: EditorKey? = null

    val isPending: Boolean
        get() = pendingEditor != null

    fun onOrientationChanged(editor: EditorKey) {
        pendingEditor = editor
    }

    fun shouldKeepInput(editor: EditorKey): Boolean = pendingEditor == editor

    fun clear() {
        pendingEditor = null
    }
}

internal fun EditorInfo?.toEditorKey(): OrientationInputHandoff.EditorKey {
    return OrientationInputHandoff.EditorKey(
        packageName = this?.packageName,
        fieldId = this?.fieldId ?: 0,
        inputType = this?.inputType ?: 0,
        imeOptions = this?.imeOptions ?: 0,
    )
}
