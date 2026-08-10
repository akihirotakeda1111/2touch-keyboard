package com.example.twotouchkeyboard.input

import com.example.twotouchkeyboard.KeyboardKey
import com.example.twotouchkeyboard.KeyboardMappings

internal object TwoTouchExtensionSupport {

    fun resolvePrimaryCharacter(
        row: Int,
        secondKey: Int,
        primaryRows: Map<Int, String>,
    ): String? {
        if (secondKey !in 1..5) return null
        val chars = primaryRows[row] ?: return null
        val index = secondKey - 1
        if (index >= chars.length) return null
        return chars[index].toString()
    }

    fun resolveExtensionCharacter(
        row: Int,
        secondKey: Int,
        extensionRows: Map<Int, KeyboardMappings.ExtensionSlots>,
    ): String? {
        if (!KeyboardMappings.isExtensionSecondKey(secondKey)) return null
        return extensionRows[row]?.get(secondKey)?.toString()
    }

    fun waitingLabel(
        row: Int,
        key: KeyboardKey,
        primaryRows: Map<Int, String>,
        extensionRows: Map<Int, KeyboardMappings.ExtensionSlots>,
    ): String? {
        val secondKey = KeyboardMappings.secondKeyNumber(key) ?: return null
        return resolvePrimaryCharacter(row, secondKey, primaryRows)
            ?: resolveExtensionCharacter(row, secondKey, extensionRows)
    }

    fun appendFromSecondKey(
        row: Int,
        key: KeyboardKey,
        primaryRows: Map<Int, String>,
        extensionRows: Map<Int, KeyboardMappings.ExtensionSlots>,
        append: (String) -> Unit,
    ): Boolean {
        val secondKey = KeyboardMappings.secondKeyNumber(key) ?: return false
        val character = resolvePrimaryCharacter(row, secondKey, primaryRows)
            ?: resolveExtensionCharacter(row, secondKey, extensionRows)
            ?: return false
        append(character)
        return true
    }
}
