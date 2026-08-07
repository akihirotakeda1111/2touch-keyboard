package com.example.twotouchkeyboard

/**
 * 変換候補の表示・選択状態を管理する。
 *
 * - 入力中: 候補はプレビュー表示のみ（数字キーは文字入力に使う）
 * - 変換モード: Space で確定後、数字キー・←→・Enter で候補操作
 */
class ConversionSession {

    private var candidates: List<String> = emptyList()
    private var selectedIndex: Int = 0
    private var active: Boolean = false

    val isActive: Boolean
        get() = active && candidates.isNotEmpty()

    fun getCandidates(): List<String> = candidates

    fun getSelectedIndex(): Int = selectedIndex

    fun getSelectedCandidate(): String? = candidates.getOrNull(selectedIndex)

    fun setCandidates(newCandidates: List<String>) {
        candidates = newCandidates
        if (selectedIndex >= candidates.size) {
            selectedIndex = 0
        }
        if (candidates.isEmpty()) {
            active = false
        }
    }

    fun activate() {
        if (candidates.isNotEmpty()) {
            active = true
            selectedIndex = 0
        }
    }

    fun deactivate() {
        active = false
    }

    fun clear() {
        candidates = emptyList()
        selectedIndex = 0
        active = false
    }

    fun candidateForDigit(digit: Int): String? {
        if (digit !in 1..9) return null
        return candidates.getOrNull(digit - 1)
    }

    fun moveSelection(delta: Int) {
        if (candidates.isEmpty()) return
        selectedIndex = (selectedIndex + delta).coerceIn(0, candidates.lastIndex)
    }
}
