package com.example.twotouchkeyboard

import android.view.inputmethod.InputConnection

/** 入力先アプリの編集操作を使い、選択範囲やクリップボードを扱う。 */
internal fun performCandidateBarEdit(connection: InputConnection, action: CandidateBarAction): Boolean {
    val contextAction = when (action) {
        CandidateBarAction.PASTE -> android.R.id.paste
        CandidateBarAction.SELECT_ALL -> android.R.id.selectAll
        CandidateBarAction.COPY -> android.R.id.copy
        CandidateBarAction.CUT -> android.R.id.cut
        else -> return false
    }
    return connection.performContextMenuAction(contextAction)
}
