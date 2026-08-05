package com.example.twotouchkeyboard

object CursorDirection {
    const val LEFT = -1
    const val RIGHT = 1
}

sealed class KeyboardKey {
    data class Digit(val number: Int) : KeyboardKey()
    data object Star : KeyboardKey()
    data object Zero : KeyboardKey()
    data object Hash : KeyboardKey()
    data object Delete : KeyboardKey()
    data object Enter : KeyboardKey()
    data object Space : KeyboardKey()
    data object CursorLeft : KeyboardKey()
    data object CursorRight : KeyboardKey()
}
