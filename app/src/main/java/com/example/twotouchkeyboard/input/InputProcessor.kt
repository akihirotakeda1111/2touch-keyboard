package com.example.twotouchkeyboard.input

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.twotouchkeyboard.CursorDirection
import com.example.twotouchkeyboard.InputMode
import com.example.twotouchkeyboard.KeyboardKey

interface InputProcessor {
    fun onKeyPressed(key: KeyboardKey)
    fun getKeyLabel(key: KeyboardKey): String
    fun resetPartialInput()
    fun resetInputSession()
    fun confirmPendingInput()
    fun onDelete(ic: InputConnection)
    fun onEnter(ic: InputConnection, editorInfo: EditorInfo?)
    fun onSpace(ic: InputConnection)
    fun onCursorMove(ic: InputConnection, direction: Int)

    /** 2タッチ待ち・トグル中など、数字キーが文字入力に使われる状態か */
    fun isMidCharacterInput(): Boolean = false
}

interface InputProcessorHost {
    fun appendConfirmedCharacter(character: String)
    fun setComposingPreview(text: String)
    fun onProcessorStateChanged()
    fun getConfirmedBuffer(): String
    fun getComposingPreview(): String
    fun deleteLastConfirmedCharacter()
    fun commitComposingText(ic: InputConnection)
    fun clearComposingState()
    fun getInputMode(): InputMode
    fun requestConversion()
    fun commitDirectText(text: String)
}
