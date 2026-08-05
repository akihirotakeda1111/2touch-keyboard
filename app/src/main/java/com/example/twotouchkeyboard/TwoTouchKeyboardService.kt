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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
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
    private val conversionEngine = DummyConversionEngine()

    private val conversionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var conversionJob: Job? = null
    private var settingsCollectJob: Job? = null

    private var currentHiraganaMethod = CharacterInputMethod.TWOTOUCH
    private var currentAlphabetMethod = CharacterInputMethod.TOGGLE

    override fun onCreate() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        settingsRepository = SettingsRepository(applicationContext)
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
                    updateComposingText(composingText)
                    requestConversion(composingText)
                }

                override fun onInputModeChanged(mode: InputMode) {
                    clearCandidateUi()
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
        when (key) {
            KeyboardKey.Star -> coordinator.handleModeSwitchKey()
            KeyboardKey.Delete -> coordinator.onDelete()
            KeyboardKey.Enter -> coordinator.onEnter()
            KeyboardKey.Space -> coordinator.onSpace()
            KeyboardKey.CursorLeft -> coordinator.onCursorMove(CursorDirection.LEFT)
            KeyboardKey.CursorRight -> coordinator.onCursorMove(CursorDirection.RIGHT)
            else -> coordinator.onKeyPressed(key)
        }
    }

    private fun updateComposingText(text: String) {
        val inputConnection = currentInputConnection ?: return
        if (text.isEmpty()) {
            inputConnection.finishComposingText()
        } else {
            inputConnection.setComposingText(text, 1)
        }
    }

    private fun requestConversion(input: String) {
        conversionJob?.cancel()
        if (input.isEmpty()) {
            clearCandidateUi()
            return
        }

        conversionJob = conversionScope.launch {
            val candidates = withContext(Dispatchers.Default) {
                conversionEngine.convert(input, coordinator.getInputMode())
            }
            if (input != coordinator.getComposingText()) return@launch
            updateCandidateUi(candidates)
        }
    }

    private fun updateCandidateUi(candidates: List<String>) {
        candidateContainer.removeAllViews()
        if (candidates.isEmpty()) {
            clearCandidateUi()
            return
        }

        val inflater = LayoutInflater.from(this)
        candidates.forEach { candidate ->
            val itemView = inflater.inflate(R.layout.suggest_item, candidateContainer, false)
            val textView = itemView.findViewById<TextView>(R.id.candidate_text)
            textView.text = candidate
            textView.setOnClickListener {
                commitCandidate(candidate)
            }
            candidateContainer.addView(itemView)
        }
        candidateScroll.visibility = View.VISIBLE
        candidateScroll.scrollTo(0, 0)
    }

    private fun clearCandidateUi() {
        conversionJob?.cancel()
        candidateContainer.removeAllViews()
        candidateScroll.visibility = View.GONE
    }

    private fun commitCandidate(candidate: String) {
        val inputConnection = currentInputConnection ?: return
        inputConnection.commitText(candidate, 1)
        coordinator.resetInputSession()
        clearCandidateUi()
    }

    private fun updateKeyLabels() {
        keyButtons.forEach { (key, button) ->
            button.text = coordinator.getKeyLabel(key)
        }
    }

    private fun finalizeInputState() {
        coordinator.bindInputConnection(currentInputConnection)
        currentInputConnection?.let { ic ->
            coordinator.commitComposingText(ic)
        }
        coordinator.clearComposingState()
        clearCandidateUi()
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
            clearCandidateUi()
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
        super.onDestroy()
    }
}
