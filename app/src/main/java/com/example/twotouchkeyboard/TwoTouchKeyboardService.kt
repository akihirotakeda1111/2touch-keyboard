package com.example.twotouchkeyboard

import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import com.example.mozcengine.ConversionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TwoTouchKeyboardService : InputMethodService(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var coordinator: KeyboardInputCoordinator
    private lateinit var candidateScroll: HorizontalScrollView
    private lateinit var candidateContainer: LinearLayout
    private lateinit var settingsRepository: SettingsRepository

    private val keyButtons: MutableMap<KeyboardKey, Button> = mutableMapOf()
    private lateinit var conversionEngine: ConversionEngine

    private val conversionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var conversionJob: Job? = null
    private var settingsCollectJob: Job? = null

    private val conversionSession = ConversionSession()
    private var pendingConversionActivation = false
    private var lastComposingTextForConversion = ""

    private var currentHiraganaMethod = CharacterInputMethod.TWOTOUCH
    private var currentAlphabetMethod = CharacterInputMethod.TOGGLE

    override fun onCreate() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        settingsRepository = SettingsRepository(applicationContext)
        conversionEngine = ConversionEngineProvider.create(applicationContext)
        settingsCollectJob = lifecycleScope.launch {
            combine(
                settingsRepository.hiraganaInputMode,
                settingsRepository.alphabetInputMode,
            ) { hiragana, alphabet ->
                hiragana to alphabet
            }.collect { (hiragana, alphabet) ->
                currentHiraganaMethod = hiragana
                currentAlphabetMethod = alphabet
                if (::coordinator.isInitialized) {
                    coordinator.setHiraganaInputMethod(hiragana)
                    coordinator.setAlphabetInputMethod(alphabet)
                    updateKeyLabels()
                }
            }
        }
    }

    override fun onCreateInputView(): View {
        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)
        keyboardView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        candidateScroll = keyboardView.findViewById(R.id.candidate_scroll)
        candidateContainer = keyboardView.findViewById(R.id.candidate_container)

        coordinator = KeyboardInputCoordinator(
            listener = object : KeyboardInputCoordinator.Listener {
                override fun onStateChanged() {
                    updateKeyLabels()
                }

                override fun onComposingTextUpdated(composingText: String) {
                    onComposingTextChanged(composingText)
                }

                override fun onInputModeChanged(mode: InputMode) {
                    resetConversionState()
                    updateKeyLabels()
                }
            },
        )

        coordinator.setHiraganaInputMethod(currentHiraganaMethod)
        coordinator.setAlphabetInputMethod(currentAlphabetMethod)

        bindKey(keyboardView, R.id.key_1, KeyboardKey.Digit(1))
        bindKey(keyboardView, R.id.key_2, KeyboardKey.Digit(2))
        bindKey(keyboardView, R.id.key_3, KeyboardKey.Digit(3))
        bindKey(keyboardView, R.id.key_4, KeyboardKey.Digit(4))
        bindKey(keyboardView, R.id.key_5, KeyboardKey.Digit(5))
        bindKey(keyboardView, R.id.key_6, KeyboardKey.Digit(6))
        bindKey(keyboardView, R.id.key_7, KeyboardKey.Digit(7))
        bindKey(keyboardView, R.id.key_8, KeyboardKey.Digit(8))
        bindKey(keyboardView, R.id.key_9, KeyboardKey.Digit(9))
        bindKey(keyboardView, R.id.key_star, KeyboardKey.Star)
        bindKey(keyboardView, R.id.key_0, KeyboardKey.Zero)
        bindKey(keyboardView, R.id.key_hash, KeyboardKey.Hash)
        bindKey(keyboardView, R.id.key_delete, KeyboardKey.Delete)
        bindKey(keyboardView, R.id.key_enter, KeyboardKey.Enter)
        bindKey(keyboardView, R.id.key_space, KeyboardKey.Space)
        bindKey(keyboardView, R.id.key_cursor_left, KeyboardKey.CursorLeft)
        bindKey(keyboardView, R.id.key_cursor_right, KeyboardKey.CursorRight)

        updateKeyLabels()
        return keyboardView
    }

    private fun bindKey(root: View, viewId: Int, key: KeyboardKey) {
        val button = root.findViewById<Button>(viewId)
        keyButtons[key] = button
        button.setOnClickListener {
            dispatchKey(key)
        }
    }

    private fun dispatchKey(key: KeyboardKey) {
        coordinator.bindInputConnection(currentInputConnection)

        if (canHandleConversionKey(key) && handleConversionKey(key)) {
            return
        }

        when (key) {
            KeyboardKey.Star -> coordinator.handleModeSwitchKey()
            KeyboardKey.Delete -> handleDeleteKey()
            KeyboardKey.Enter -> handleEnterKey()
            KeyboardKey.Space -> handleSpaceKey()
            KeyboardKey.CursorLeft -> handleCursorKey(CursorDirection.LEFT)
            KeyboardKey.CursorRight -> handleCursorKey(CursorDirection.RIGHT)
            else -> coordinator.onKeyPressed(key)
        }
    }

    private fun canHandleConversionKey(key: KeyboardKey): Boolean {
        if (coordinator.getInputMode() != InputMode.HIRAGANA) return false
        if (coordinator.getComposingText().isEmpty()) return false
        if (coordinator.isMidCharacterInput()) return false

        if (conversionSession.isActive) {
            return key is KeyboardKey.Digit ||
                key == KeyboardKey.Space ||
                key == KeyboardKey.Enter ||
                key == KeyboardKey.Delete ||
                key == KeyboardKey.CursorLeft ||
                key == KeyboardKey.CursorRight
        }

        return key == KeyboardKey.Space
    }

    private fun handleConversionKey(key: KeyboardKey): Boolean {
        return when (key) {
            KeyboardKey.Space -> {
                handleSpaceKey()
                true
            }
            is KeyboardKey.Digit -> {
                if (!conversionSession.isActive) return false
                val candidate = conversionSession.candidateForDigit(key.number) ?: return false
                commitCandidate(candidate)
                true
            }
            KeyboardKey.Enter -> {
                handleEnterKey()
                true
            }
            KeyboardKey.CursorLeft -> {
                conversionSession.moveSelection(-1)
                refreshConversionUi()
                true
            }
            KeyboardKey.CursorRight -> {
                conversionSession.moveSelection(1)
                refreshConversionUi()
                true
            }
            KeyboardKey.Delete -> {
                handleDeleteKey()
                true
            }
            else -> false
        }
    }

    private fun handleSpaceKey() {
        if (coordinator.getInputMode() == InputMode.HIRAGANA &&
            coordinator.getComposingText().isNotEmpty() &&
            !coordinator.isMidCharacterInput()
        ) {
            if (conversionSession.isActive) {
                conversionSession.getSelectedCandidate()?.let { commitCandidate(it) }
                return
            }
            pendingConversionActivation = true
            if (conversionSession.getCandidates().isNotEmpty()) {
                conversionSession.activate()
                pendingConversionActivation = false
                refreshConversionUi()
            } else {
                requestConversion(coordinator.getComposingText(), activateOnResult = true)
            }
            return
        }
        coordinator.onSpace()
    }

    private fun handleEnterKey() {
        if (conversionSession.isActive) {
            conversionSession.getSelectedCandidate()?.let { commitCandidate(it) }
            return
        }
        coordinator.onEnter()
    }

    private fun handleDeleteKey() {
        if (conversionSession.isActive) {
            conversionSession.deactivate()
            pendingConversionActivation = false
            refreshConversionUi()
            return
        }
        coordinator.onDelete()
    }

    private fun handleCursorKey(direction: Int) {
        if (conversionSession.isActive) {
            conversionSession.moveSelection(direction)
            refreshConversionUi()
            return
        }
        coordinator.onCursorMove(direction)
    }

    private fun refreshConversionUi() {
        updateCandidateUi(conversionSession.getCandidates())
        updateKeyLabels()
    }

    private fun onComposingTextChanged(composingText: String) {
        if (composingText != lastComposingTextForConversion) {
            conversionSession.deactivate()
            pendingConversionActivation = false
            lastComposingTextForConversion = composingText
        }
        updateComposingText(composingText)
        requestConversion(composingText)
    }

    private fun updateComposingText(text: String) {
        val inputConnection = currentInputConnection ?: return
        if (text.isEmpty()) {
            inputConnection.finishComposingText()
        } else {
            inputConnection.setComposingText(text, 1)
        }
    }

    private fun requestConversion(
        input: String,
        activateOnResult: Boolean = false,
    ) {
        conversionJob?.cancel()
        if (input.isEmpty()) {
            resetConversionState()
            return
        }

        if (activateOnResult) {
            pendingConversionActivation = true
        }

        conversionJob = conversionScope.launch {
            val candidates = withContext(Dispatchers.Default) {
                conversionEngine.convert(input, coordinator.getInputMode().toConversionMode())
            }
            if (input != coordinator.getComposingText()) return@launch

            conversionSession.setCandidates(candidates)
            if (pendingConversionActivation && candidates.isNotEmpty()) {
                conversionSession.activate()
                pendingConversionActivation = false
            }
            refreshConversionUi()
        }
    }

    private fun updateCandidateUi(candidates: List<String>) {
        candidateContainer.removeAllViews()
        if (candidates.isEmpty()) {
            candidateScroll.visibility = View.GONE
            return
        }

        val inflater = LayoutInflater.from(this)
        val selectedIndex = conversionSession.getSelectedIndex()
        val showShortcuts = conversionSession.isActive

        candidates.forEachIndexed { index, candidate ->
            val itemView = inflater.inflate(R.layout.suggest_item, candidateContainer, false)
            val textView = itemView.findViewById<TextView>(R.id.candidate_text)
            textView.text = formatCandidateLabel(candidate, index, showShortcuts)

            if (showShortcuts && index == selectedIndex) {
                itemView.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.candidate_selected_background),
                )
                textView.setTextColor(
                    ContextCompat.getColor(this, R.color.candidate_selected_text),
                )
            } else {
                itemView.setBackgroundResource(android.R.drawable.list_selector_background)
                textView.setTextColor(
                    ContextCompat.getColor(this, R.color.candidate_text),
                )
            }

            textView.setOnClickListener {
                commitCandidate(candidate)
            }
            candidateContainer.addView(itemView)
        }

        candidateScroll.visibility = View.VISIBLE
        scrollToSelectedCandidate(selectedIndex)
    }

    private fun formatCandidateLabel(candidate: String, index: Int, showShortcuts: Boolean): String {
        if (!showShortcuts || index >= MAX_CANDIDATE_SHORTCUTS) return candidate
        return "${index + 1}. $candidate"
    }

    private fun scrollToSelectedCandidate(selectedIndex: Int) {
        candidateScroll.post {
            val child = candidateContainer.getChildAt(selectedIndex) ?: return@post
            val scrollX = child.left - (candidateScroll.width - child.width) / 2
            candidateScroll.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }

    private fun resetConversionState() {
        conversionJob?.cancel()
        conversionSession.clear()
        pendingConversionActivation = false
        lastComposingTextForConversion = ""
        candidateContainer.removeAllViews()
        candidateScroll.visibility = View.GONE
    }

    private fun commitCandidate(candidate: String) {
        val inputConnection = currentInputConnection ?: return
        inputConnection.commitText(candidate, 1)
        coordinator.resetInputSession()
        resetConversionState()
    }

    private fun updateKeyLabels() {
        keyButtons.forEach { (key, button) ->
            button.text = getKeyLabel(key)
        }
    }

    private fun getKeyLabel(key: KeyboardKey): String {
        if (conversionSession.isActive && key is KeyboardKey.Digit && key.number in 1..9) {
            val candidate = conversionSession.candidateForDigit(key.number)
            if (candidate != null) {
                return "${key.number}\n${abbreviateCandidate(candidate)}"
            }
        }
        return coordinator.getKeyLabel(key)
    }

    private fun abbreviateCandidate(candidate: String): String {
        return if (candidate.length <= 4) candidate else candidate.take(3) + "…"
    }

    private fun finalizeInputState() {
        coordinator.bindInputConnection(currentInputConnection)
        if (conversionSession.isActive) {
            conversionSession.getSelectedCandidate()?.let { candidate ->
                currentInputConnection?.commitText(candidate, 1)
            }
        } else {
            currentInputConnection?.let { ic ->
                coordinator.commitComposingText(ic)
            }
        }
        coordinator.clearComposingState()
        resetConversionState()
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        super.onShowInputRequested(flags, configChange)
        return true
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        super.onEvaluateFullscreenMode()
        return false
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::coordinator.isInitialized) {
            conversionJob?.cancel()
            coordinator.bindEditorInfo(info)
            coordinator.bindInputConnection(currentInputConnection)
            coordinator.resetInputSession()
            resetConversionState()
            currentInputConnection?.finishComposingText()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (::coordinator.isInitialized) {
            finalizeInputState()
            coordinator.resetInputSession()
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        settingsCollectJob?.cancel()
        conversionScope.cancel()
        if (::conversionEngine.isInitialized) {
            conversionEngine.close()
        }
        super.onDestroy()
    }

    companion object {
        private const val MAX_CANDIDATE_SHORTCUTS = 9
    }
}
