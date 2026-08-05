package com.example.twotouchkeyboard.input

import com.example.twotouchkeyboard.KeyboardKey

interface InputProcessor {
    fun onKeyPressed(key: KeyboardKey)
    fun getKeyLabel(key: KeyboardKey): String
    fun resetPartialInput()
    fun resetInputSession()
    fun confirmPendingInput()
}

interface InputProcessorHost {
    fun appendConfirmedCharacter(character: String)
    fun setComposingPreview(text: String)
    fun onProcessorStateChanged()
    fun getConfirmedBuffer(): String
}
