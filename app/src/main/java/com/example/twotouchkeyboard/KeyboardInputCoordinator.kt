package com.example.twotouchkeyboard

import android.view.inputmethod.InputConnection
import com.example.twotouchkeyboard.input.AlphabetToggleProcessor
import com.example.twotouchkeyboard.input.AlphabetTwoTouchProcessor
import com.example.twotouchkeyboard.input.HiraganaToggleProcessor
import com.example.twotouchkeyboard.input.HiraganaTwoTouchProcessor
import com.example.twotouchkeyboard.input.InputProcessor
import com.example.twotouchkeyboard.input.InputProcessorHost
import com.example.twotouchkeyboard.input.AlphabetCaseModifierSupport
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
        fun scheduleToggleAutoCommit(onTimeout: () -> Unit)
        fun cancelToggleAutoCommit()
        fun requestHideSoftInput()
    }

    private val confirmedBuffer = StringBuilder()
    private var currentPreview: String = ""
    private var inputConnection: InputConnection? = null
    private var currentEditorInfo: EditorInfo? = null
    private var fieldProfile: InputFieldProfile = InputFieldProfile.DEFAULT

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

    fun applyEditorInfo(info: EditorInfo?) {
        currentEditorInfo = info
        fieldProfile = InputFieldProfileResolver.resolve(info)
        applyFieldProfile()
    }

    fun getFieldProfile(): InputFieldProfile = fieldProfile

    fun isConversionEnabled(): Boolean = fieldProfile.conversionEnabled

    fun isModeSwitchEnabled(): Boolean = fieldProfile.modeSwitchEnabled

    fun isPassthroughEnabled(): Boolean = fieldProfile.passthroughEnabled

    private fun applyFieldProfile() {
        activeProcessor().resetPartialInput()
        if (currentInputMode != fieldProfile.preferredMode) {
            currentInputMode = fieldProfile.preferredMode
            listener.onInputModeChanged(currentInputMode)
        }
        listener.onStateChanged()
    }

    fun onKeyPressed(key: KeyboardKey) {
        if (key == KeyboardKey.Star && !fieldProfile.modeSwitchEnabled) {
            inputConnection?.commitText("*", 1)
            return
        }
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
        if (!fieldProfile.modeSwitchEnabled) return
        inputConnection?.let { commitComposingText(it) }
        clearComposingState()
        cycleInputMode()
    }

    fun getKeyLabel(key: KeyboardKey): String {
        if (key == KeyboardKey.Star && !fieldProfile.modeSwitchEnabled) {
            return "*"
        }
        return activeProcessor().getKeyLabel(key)
    }

    fun getComposingText(): String {
        if (fieldProfile.passthroughEnabled) return ""
        return currentPreview
    }

    fun isMidCharacterInput(): Boolean = activeProcessor().isMidCharacterInput()

    fun getCurrentEditorInfo(): EditorInfo? = currentEditorInfo

    fun setComposingFromConversion(text: String) {
        confirmedBuffer.clear()
        confirmedBuffer.append(text)
        currentPreview = text
        activeProcessor().resetPartialInput()
        listener.onComposingTextUpdated(text)
    }

    fun clearComposingText() {
        if (confirmedBuffer.isEmpty() && currentPreview.isEmpty()) return
        confirmedBuffer.clear()
        currentPreview = ""
        if (fieldProfile.passthroughEnabled) {
            inputConnection?.finishComposingText()
        }
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

    fun applyTextModifier() {
        when (currentInputMode) {
            InputMode.HIRAGANA -> applyKanaCycleModifier()
            InputMode.ALPHABET -> {
                (activeProcessor() as? AlphabetCaseModifierSupport)?.toggleLastCharacterCase()
            }
            InputMode.NUMBER -> Unit
        }
    }

    fun getTextModifierLabel(): String {
        return when (currentInputMode) {
            InputMode.HIRAGANA -> "小゛゜"
            InputMode.ALPHABET -> "A/a"
            InputMode.NUMBER -> "─"
        }
    }

    private fun applyKanaCycleModifier() {
        if (currentInputMode != InputMode.HIRAGANA || fieldProfile.passthroughEnabled) return

        activeProcessor().confirmPendingInput()
        activeProcessor().resetPartialInput()
        if (confirmedBuffer.isEmpty()) return

        val lastIndex = confirmedBuffer.lastIndex
        val transformed = KanaModifier.cycle(confirmedBuffer[lastIndex]) ?: return

        confirmedBuffer[lastIndex] = transformed
        currentPreview = confirmedBuffer.toString()
        listener.onComposingTextUpdated(currentPreview)
    }

    fun confirmAllPendingInput() {
        activeProcessor().confirmPendingInput()
    }

    fun cycleInputMode(): InputMode {
        if (!fieldProfile.modeSwitchEnabled) return currentInputMode
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
        if (fieldProfile.passthroughEnabled) {
            inputConnection?.commitText(character, 1)
            currentPreview = ""
            listener.onComposingTextUpdated("")
            return
        }
        confirmedBuffer.append(character)
        currentPreview = confirmedBuffer.toString()
        listener.onComposingTextUpdated(currentPreview)
    }

    override fun setComposingPreview(text: String) {
        if (fieldProfile.passthroughEnabled) {
            currentPreview = text
            val ic = inputConnection
            if (ic != null) {
                if (text.isEmpty()) {
                    ic.finishComposingText()
                } else {
                    ic.setComposingText(text, 1)
                }
            }
            listener.onComposingTextUpdated("")
            return
        }
        currentPreview = text
        listener.onComposingTextUpdated(text)
    }

    override fun onProcessorStateChanged() {
        listener.onStateChanged()
    }

    override fun getConfirmedBuffer(): String {
        if (fieldProfile.passthroughEnabled) return ""
        return confirmedBuffer.toString()
    }

    override fun getComposingPreview(): String = currentPreview

    override fun replaceLastConfirmedCharacter(newChar: Char) {
        if (fieldProfile.passthroughEnabled) return
        if (confirmedBuffer.isEmpty()) return
        confirmedBuffer[confirmedBuffer.lastIndex] = newChar
        currentPreview = confirmedBuffer.toString()
        listener.onComposingTextUpdated(currentPreview)
    }

    override fun deleteLastConfirmedCharacter() {
        if (fieldProfile.passthroughEnabled) {
            inputConnection?.deleteSurroundingText(1, 0)
            currentPreview = ""
            listener.onComposingTextUpdated("")
            return
        }
        if (confirmedBuffer.isEmpty()) return
        confirmedBuffer.deleteCharAt(confirmedBuffer.length - 1)
        currentPreview = confirmedBuffer.toString()
        listener.onComposingTextUpdated(currentPreview)
    }

    override fun commitComposingText(ic: InputConnection) {
        confirmAllPendingInput()
        if (fieldProfile.passthroughEnabled) {
            ic.finishComposingText()
            return
        }
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

    override fun scheduleToggleAutoCommit(onTimeout: () -> Unit) {
        listener.scheduleToggleAutoCommit(onTimeout)
    }

    override fun cancelToggleAutoCommit() {
        listener.cancelToggleAutoCommit()
    }

    override fun requestHideSoftInput() {
        listener.requestHideSoftInput()
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
