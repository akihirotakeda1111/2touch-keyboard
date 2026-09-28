package com.example.twotouchkeyboard

/**
 * 回転で入力ビューが作り直されても、同じ入力セッションの未確定状態を破棄しない。
 *
 * [restarting] が true の再開だけを、同じ入力欄での再開として扱う。
 * パッケージ名や入力欄 ID では欄を区別しない。画面の再生成で新しいセッションが
 * 始まる場合は、ここだけでは復元しない。
 */
internal class OrientationInputHandoff {
    private var pendingSessionId: Long? = null

    val isPending: Boolean
        get() = pendingSessionId != null

    fun onOrientationChanged(sessionId: Long) {
        pendingSessionId = sessionId
    }

    /**
     * 復元待ちを、同じセッションの再開として確認できたときだけ消費する。
     * 新しいセッションでは消費せず、呼び出し側が待ちを解除する。
     */
    fun consumeIfSameSession(sessionId: Long, restarting: Boolean): Boolean {
        val pending = pendingSessionId ?: return false
        if (!restarting || pending != sessionId) return false
        pendingSessionId = null
        return true
    }

    fun clear() {
        pendingSessionId = null
    }
}
