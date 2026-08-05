package com.example.twotouchkeyboard

import android.view.inputmethod.InputConnection
import com.example.twotouchkeyboard.input.AlphabetToggleProcessor
import com.example.twotouchkeyboard.input.AlphabetTwoTouchProcessor
import com.example.twotouchkeyboard.input.HiraganaToggleProcessor
import com.example.twotouchkeyboard.input.HiraganaTwoTouchProcessor
import com.example.twotouchkeyboard.input.InputProcessor
import com.example.twotouchkeyboard.input.InputProcessorHost
import com.example.twotouchkeyboard.input.NumberInputProcessor
import android.view.inputmethod.EditorInfo

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
    private var inputConnection: InputConnection? = null
    private var currentEditorInfo: EditorInfo? = null

    private var currentInputMode: InputMode = InputMode.HIRAGANA

    private var hiraganaProcessor: InputProcessor =
        HiraganaTwoTouchProcessor(this)
    private var alphabetProcessor: InputProcessor =
        AlphabetToggleProcessor(this)
    private var numberProcessor: InputProcessor =
        NumberInputProcessor(this)

    private var hiraganaInputMethod: CharacterInputMethod = CharacterInputMethod.TWOTOUCH
    private var alphabetInputMethod: CharacterInputMethod = CharacterInputMethod.TOGGLE

    fun bindInputConnection(ic: InputConnection?) {
        inputConnection = ic
    }

    fun bindEditorInfo(info: EditorInfo?) {
        currentEditorInfo = info
    }

    fun onKeyPressed(key: KeyboardKey) {
        activeProcessor().onKeyPressed(key)
    }

    fun onDelete() {
        val ic = inputConnection ?: return
        activeProcessor().onDelete(ic)
    }

    fun onEnter() {
        val ic = inputConnection ?: return
        activeProcessor().onEnter(ic, currentEditorInfo)
    }

    fun onSpace() {
        val ic = inputConnection ?: return
        activeProcessor().onSpace(ic)
    }

    fun onCursorMove(direction: Int) {
        val ic = inputConnection ?: return
        activeProcessor().onCursorMove(ic, direction)
    }

    fun handleModeSwitchKey() {
        inputConnection?.let { commitComposingText(it) }
        clearComposingState()
        cycleInputMode()
    }

    fun getKeyLabel(key: KeyboardKey): String {
        return activeProcessor().getKeyLabel(key)
    }

    fun getComposingText(): String = currentPreview

    fun clearComposingText() {
        if (confirmedBuffer.isEmpty() && currentPreview.isEmpty()) return
        confirmedBuffer.clear()
        currentPreview = ""
        listener.onComposingTextUpdated("")
    }

    fun resetPartialInput() {
        activeProcessor().resetPartialInput()
    }

    fun resetInputSession() {
        hiraganaProcessor.resetInputSession()
        alphabetProcessor.resetInputSession()
        numberProcessor.resetInputSession()
        clearComposingText()
        listener.onStateChanged()
    }

    fun confirmAllPendingInput() {
        activeProcessor().confirmPendingInput()
    }

    fun cycleInputMode(): InputMode {
        activeProcessor().resetPartialInput()
        currentInputMode = when (currentInputMode) {
            InputMode.HIRAGANA -> InputMode.ALPHABET
            InputMode.ALPHABET -> InputMode.NUMBER
            InputMode.NUMBER -> InputMode.HIRAGANA
        }
        listener.onInputModeChanged(currentInputMode)
        listener.onStateChanged()
        return currentInputMode
    }

    fun setHiraganaInputMethod(method: CharacterInputMethod) {
        if (method == hiraganaInputMethod) return
        hiraganaInputMethod = method
        if (currentInputMode == InputMode.HIRAGANA) {
            hiraganaProcessor.confirmPendingInput()
        }
        hiraganaProcessor.resetInputSession()
        hiraganaProcessor = createHiraganaProcessor(method)
        listener.onStateChanged()
    }

    fun setAlphabetInputMethod(method: CharacterInputMethod) {
        if (method == alphabetInputMethod) return
        alphabetInputMethod = method
        if (currentInputMode == InputMode.ALPHABET) {
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

    override fun getComposingPreview(): String = currentPreview

    override fun deleteLastConfirmedCharacter() {
        if (confirmedBuffer.isEmpty()) return
        confirmedBuffer.deleteCharAt(confirmedBuffer.length - 1)
        currentPreview = confirmedBuffer.toString()
        listener.onComposingTextUpdated(currentPreview)
    }

    override fun commitComposingText(ic: InputConnection) {
        confirmAllPendingInput()
        val text = getComposingPreview()
        if (text.isNotEmpty()) {
            ic.commitText(text, 1)
        }
    }

    override fun clearComposingState() {
        clearComposingText()
        activeProcessor().resetPartialInput()
    }

    override fun getInputMode(): InputMode = currentInputMode

    override fun requestConversion() {
        listener.onComposingTextUpdated(currentPreview)
    }

    override fun commitDirectText(text: String) {
        inputConnection?.commitText(text, 1)
    }

    private fun activeProcessor(): InputProcessor = when (currentInputMode) {
        InputMode.HIRAGANA -> hiraganaProcessor
        InputMode.ALPHABET -> alphabetProcessor
        InputMode.NUMBER -> numberProcessor
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
}
