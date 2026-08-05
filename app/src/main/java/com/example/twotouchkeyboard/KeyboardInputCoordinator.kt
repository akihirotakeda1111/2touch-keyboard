package com.example.twotouchkeyboard

import com.example.twotouchkeyboard.input.AlphabetToggleProcessor
import com.example.twotouchkeyboard.input.AlphabetTwoTouchProcessor
import com.example.twotouchkeyboard.input.HiraganaToggleProcessor
import com.example.twotouchkeyboard.input.HiraganaTwoTouchProcessor
import com.example.twotouchkeyboard.input.InputProcessor
import com.example.twotouchkeyboard.input.InputProcessorHost

/**
 * 文字種モードの巡回と InputProcessor の差し替えを統括する。
 */
class KeyboardInputCoordinator(
    private val listener: Listener,
) : InputProcessorHost {

    interface Listener {
        fun onStateChanged()
        fun onComposingTextUpdated(composingText: String)
        fun onInputModeChanged(mode: InputMode)
    }

    private val confirmedBuffer = StringBuilder()
    private var currentPreview: String = ""

    var inputMode: InputMode = InputMode.HIRAGANA
        private set

    private var hiraganaProcessor: InputProcessor =
        HiraganaTwoTouchProcessor(this)
    private var alphabetProcessor: InputProcessor =
        AlphabetToggleProcessor(this)

    private var hiraganaInputMethod: CharacterInputMethod = CharacterInputMethod.TWOTOUCH
    private var alphabetInputMethod: CharacterInputMethod = CharacterInputMethod.TOGGLE

    fun onKeyPressed(key: KeyboardKey) {
        if (inputMode == InputMode.NUMBER) return
        activeProcessor()?.onKeyPressed(key)
    }

    fun getKeyLabel(key: KeyboardKey): String {
        return when (inputMode) {
            InputMode.HIRAGANA -> hiraganaProcessor.getKeyLabel(key)
            InputMode.ALPHABET -> alphabetProcessor.getKeyLabel(key)
            InputMode.NUMBER -> getNumberKeyLabel(key)
        }
    }

    fun getComposingText(): String = currentPreview

    fun clearComposingText() {
        if (confirmedBuffer.isEmpty() && currentPreview.isEmpty()) return
        confirmedBuffer.clear()
        currentPreview = ""
        listener.onComposingTextUpdated("")
    }

    fun resetPartialInput() {
        activeProcessor()?.resetPartialInput()
    }

    fun resetInputSession() {
        hiraganaProcessor.resetInputSession()
        alphabetProcessor.resetInputSession()
        clearComposingText()
        listener.onStateChanged()
    }

    fun confirmAllPendingInput() {
        activeProcessor()?.confirmPendingInput()
    }

    /** HIRAGANA → ALPHABET → NUMBER → HIRAGANA */
    fun cycleInputMode(): InputMode {
        activeProcessor()?.resetPartialInput()
        inputMode = when (inputMode) {
            InputMode.HIRAGANA -> InputMode.ALPHABET
            InputMode.ALPHABET -> InputMode.NUMBER
            InputMode.NUMBER -> InputMode.HIRAGANA
        }
        listener.onInputModeChanged(inputMode)
        listener.onStateChanged()
        return inputMode
    }

    fun setHiraganaInputMethod(method: CharacterInputMethod) {
        if (method == hiraganaInputMethod) return
        hiraganaInputMethod = method
        if (inputMode == InputMode.HIRAGANA) {
            hiraganaProcessor.confirmPendingInput()
        }
        hiraganaProcessor.resetInputSession()
        hiraganaProcessor = createHiraganaProcessor(method)
        listener.onStateChanged()
    }

    fun setAlphabetInputMethod(method: CharacterInputMethod) {
        if (method == alphabetInputMethod) return
        alphabetInputMethod = method
        if (inputMode == InputMode.ALPHABET) {
            alphabetProcessor.confirmPendingInput()
        }
        alphabetProcessor.resetInputSession()
        alphabetProcessor = createAlphabetProcessor(method)
        listener.onStateChanged()
    }

    override fun appendConfirmedCharacter(character: String) {
        confirmedBuffer.append(character)
        currentPreview = confirmedBuffer.toString()
        listener.onComposingTextUpdated(currentPreview)
    }

    override fun setComposingPreview(text: String) {
        currentPreview = text
        listener.onComposingTextUpdated(text)
    }

    override fun onProcessorStateChanged() {
        listener.onStateChanged()
    }

    override fun getConfirmedBuffer(): String = confirmedBuffer.toString()

    private fun activeProcessor(): InputProcessor? = when (inputMode) {
        InputMode.HIRAGANA -> hiraganaProcessor
        InputMode.ALPHABET -> alphabetProcessor
        InputMode.NUMBER -> null
    }

    private fun createHiraganaProcessor(method: CharacterInputMethod): InputProcessor {
        return when (method) {
            CharacterInputMethod.TWOTOUCH -> HiraganaTwoTouchProcessor(this)
            CharacterInputMethod.TOGGLE -> HiraganaToggleProcessor(this)
        }
    }

    private fun createAlphabetProcessor(method: CharacterInputMethod): InputProcessor {
        return when (method) {
            CharacterInputMethod.TWOTOUCH -> AlphabetTwoTouchProcessor(this)
            CharacterInputMethod.TOGGLE -> AlphabetToggleProcessor(this)
        }
    }

    private fun getNumberKeyLabel(key: KeyboardKey): String {
        return when (key) {
            is KeyboardKey.Digit -> key.number.toString()
            KeyboardKey.Star -> "123"
            KeyboardKey.Zero -> "0"
            KeyboardKey.Hash -> "#"
        }
    }
}
