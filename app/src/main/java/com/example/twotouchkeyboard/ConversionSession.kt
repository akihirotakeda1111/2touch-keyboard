package com.example.twotouchkeyboard

/**
 * 変換候補の表示・選択状態を管理する。
 *
 * - 入力中: 候補はプレビュー表示のみ
 * - 変換モード: Space で開始、←→ で変換範囲（部分変換）を調整
 * - 確定: Space で変換部分を確定し、残りを引き続き変換対象にする
 */
class ConversionSession {

    private var candidates: List<String> = emptyList()
    private var selectedIndex: Int = 0
    private var active: Boolean = false
    private var conversionEnd: Int = 0

    val isActive: Boolean
        get() = active && candidates.isNotEmpty()

    fun getCandidates(): List<String> = candidates

    fun getSelectedIndex(): Int = selectedIndex

    fun getSelectedCandidate(): String? = candidates.getOrNull(selectedIndex)

    fun getConversionEnd(): Int = conversionEnd

    fun isPartialConversion(composingLength: Int): Boolean {
        return conversionEnd in 1 until composingLength
    }

    fun getConversionTarget(composing: String): String {
        if (composing.isEmpty()) return ""
        val end = conversionEnd.coerceIn(1, composing.length)
        return composing.substring(0, end)
    }

    fun getRemainingSuffix(composing: String): String {
        if (composing.isEmpty()) return ""
        val end = conversionEnd.coerceIn(0, composing.length)
        return composing.substring(end)
    }

    fun setCandidates(newCandidates: List<String>) {
        candidates = newCandidates
        if (selectedIndex >= candidates.size) {
            selectedIndex = 0
        }
        if (candidates.isEmpty()) {
            active = false
        }
    }

    fun activate(composingLength: Int) {
        if (candidates.isNotEmpty()) {
            active = true
            selectedIndex = 0
            conversionEnd = composingLength.coerceAtLeast(1)
        }
    }

    fun deactivate() {
        active = false
    }

    fun clear() {
        candidates = emptyList()
        selectedIndex = 0
        active = false
        conversionEnd = 0
    }

    fun resetConversionEnd(length: Int) {
        conversionEnd = length.coerceAtLeast(0)
    }

    fun moveConversionEnd(delta: Int, composingLength: Int) {
        if (composingLength <= 0) {
            conversionEnd = 0
            return
        }
        conversionEnd = (conversionEnd + delta).coerceIn(1, composingLength)
        selectedIndex = 0
    }
}
