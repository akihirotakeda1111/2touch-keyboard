package com.example.twotouchkeyboard

sealed class KeyboardKey {
    data class Digit(val number: Int) : KeyboardKey()
    data object Star : KeyboardKey()
    data object Zero : KeyboardKey()
    data object Hash : KeyboardKey()
}
