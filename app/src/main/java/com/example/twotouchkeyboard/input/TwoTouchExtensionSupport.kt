package com.example.twotouchkeyboard.input

import com.example.twotouchkeyboard.ExtensionSlots
import com.example.twotouchkeyboard.KeyboardKey
import com.example.twotouchkeyboard.KeyboardMappings

internal object TwoTouchExtensionSupport {

    fun resolvePrimaryCharacter(
        row: Int,
        secondKey: Int,
        primaryRows: Map<Int, String>,
        maxSecondKey: Int = 5,
    ): String? {
        if (secondKey !in 1..maxSecondKey) return null
        val chars = primaryRows[row] ?: return null
        val index = secondKey - 1
        if (index >= chars.length) return null
        return chars[index].toString()
    }

    fun resolveExtensionCharacter(
        row: Int,
        secondKey: Int,
        extensionRows: Map<Int, ExtensionSlots>,
    ): String? {
        if (!KeyboardMappings.isExtensionSecondKey(secondKey)) return null
        return extensionRows[row]?.get(secondKey)?.toString()
    }

    fun waitingLabel(
        row: Int,
        key: KeyboardKey,
        primaryRows: Map<Int, String>,
        extensionRows: Map<Int, ExtensionSlots>,
        maxPrimarySecondKey: Int = 5,
        preferExtensionOnConflict: Boolean = false,
    ): String? {
        val secondKey = KeyboardMappings.secondKeyNumber(key) ?: return null
        if (KeyboardMappings.shouldShowNumericSecondKeyLabel(row, secondKey)) {
            return secondKey.toString()
        }
        return resolveSecondTouchCharacter(
            row = row,
            secondKey = secondKey,
            primaryRows = primaryRows,
            extensionRows = extensionRows,
            maxPrimarySecondKey = maxPrimarySecondKey,
            preferExtensionOnConflict = preferExtensionOnConflict,
        )
    }

    fun appendFromSecondKey(
        row: Int,
        key: KeyboardKey,
        primaryRows: Map<Int, String>,
        extensionRows: Map<Int, ExtensionSlots>,
        append: (String) -> Unit,
        maxPrimarySecondKey: Int = 5,
        preferExtensionOnConflict: Boolean = false,
    ): Boolean {
        val secondKey = KeyboardMappings.secondKeyNumber(key) ?: return false
        val character = resolveSecondTouchCharacter(
            row = row,
            secondKey = secondKey,
            primaryRows = primaryRows,
            extensionRows = extensionRows,
            maxPrimarySecondKey = maxPrimarySecondKey,
            preferExtensionOnConflict = preferExtensionOnConflict,
        ) ?: return false
        append(character)
        return true
    }

    private fun resolveSecondTouchCharacter(
        row: Int,
        secondKey: Int,
        primaryRows: Map<Int, String>,
        extensionRows: Map<Int, ExtensionSlots>,
        maxPrimarySecondKey: Int,
        preferExtensionOnConflict: Boolean,
    ): String? {
        val extension = resolveExtensionCharacter(row, secondKey, extensionRows)
        val primary = resolvePrimaryCharacter(row, secondKey, primaryRows, maxPrimarySecondKey)
        if (extension != null && primary != null && preferExtensionOnConflict) {
            return extension
        }
        return primary ?: extension
    }
}
