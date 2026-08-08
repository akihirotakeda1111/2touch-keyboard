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
import androidx.core.view.isVisible
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TwoTouchKeyboardService : InputMethodService(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var coordinator: KeyboardInputCoordinator
    private lateinit var conversionHint: TextView
    private lateinit var candidateScroll: HorizontalScrollView
    private lateinit var candidateContainer: LinearLayout
    private lateinit var settingsRepository: SettingsRepository

    private val keyButtons: MutableMap<KeyboardKey, Button> = mutableMapOf()
    private lateinit var conversionEngine: ConversionEngine

    private val conversionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var conversionJob: Job? = null
    private var toggleAutoCommitJob: Job? = null
    private var settingsCollectJob: Job? = null

    private val conversionSession = ConversionSession()
    private var pendingConversionActivation = false
    private var lastComposingTextForConversion = ""
    private var suppressConversionReset = false

    private var currentHiraganaMethod = CharacterInputMethod.TWOTOUCH
    private var currentAlphabetMethod = CharacterInputMethod.TOGGLE
    private var toggleAutoCommitTimeoutMs = SettingsRepository.DEFAULT_TOGGLE_AUTO_COMMIT_TIMEOUT_MS

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
                settingsRepository.toggleAutoCommitTimeoutMs,
            ) { hiragana, alphabet, timeoutMs ->
                Triple(hiragana, alphabet, timeoutMs)
            }.collect { (hiragana, alphabet, timeoutMs) ->
                currentHiraganaMethod = hiragana
                currentAlphabetMethod = alphabet
                toggleAutoCommitTimeoutMs = timeoutMs
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

        conversionHint = keyboardView.findViewById(R.id.conversion_hint)
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

                override fun scheduleToggleAutoCommit(onTimeout: () -> Unit) {
                    toggleAutoCommitJob?.cancel()
                    if (toggleAutoCommitTimeoutMs <= 0) return
                    toggleAutoCommitJob = conversionScope.launch {
                        delay(toggleAutoCommitTimeoutMs.toLong())
                        onTimeout()
                    }
                }

                override fun cancelToggleAutoCommit() {
                    toggleAutoCommitJob?.cancel()
                    toggleAutoCommitJob = null
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

        return key == KeyboardKey.Space ||
            key == KeyboardKey.CursorLeft ||
            key == KeyboardKey.CursorRight
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
                applyPartialConversion(candidate)
                true
            }
            KeyboardKey.Enter -> {
                handleEnterKey()
                true
            }
            KeyboardKey.CursorLeft -> {
                adjustConversionBoundary(CursorDirection.LEFT)
                true
            }
            KeyboardKey.CursorRight -> {
                adjustConversionBoundary(CursorDirection.RIGHT)
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
                conversionSession.getSelectedCandidate()?.let { applyPartialConversion(it) }
                return
            }
            enterConversionMode()
            return
        }
        coordinator.onSpace()
    }

    private fun handleEnterKey() {
        if (conversionSession.isActive) {
            conversionSession.deactivate()
            refreshConversionUi()
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
        if (coordinator.getInputMode() == InputMode.HIRAGANA &&
            coordinator.getComposingText().isNotEmpty() &&
            !coordinator.isMidCharacterInput()
        ) {
            adjustConversionBoundary(direction)
            return
        }
        coordinator.onCursorMove(direction)
    }

    private fun enterConversionMode() {
        val composing = coordinator.getComposingText()
        pendingConversionActivation = true
        conversionSession.resetConversionEnd(composing.length)
        if (conversionSession.getCandidates().isNotEmpty()) {
            conversionSession.activate(composing.length)
            pendingConversionActivation = false
            refreshConversionUi()
        } else {
            requestConversion(composing, activateOnResult = true)
        }
    }

    private fun adjustConversionBoundary(direction: Int) {
        val composing = coordinator.getComposingText()
        if (composing.isEmpty()) return

        if (!conversionSession.isActive) {
            conversionSession.resetConversionEnd(composing.length)
            conversionSession.activate(composing.length)
        }

        conversionSession.moveConversionEnd(direction, composing.length)
        requestConversion(composing)
        refreshConversionUi()
    }

    private fun refreshConversionUi() {
        updateConversionHint()
        updateCandidateUi(conversionSession.getCandidates())
        updateKeyLabels()
    }

    private fun updateConversionHint() {
        val composing = coordinator.getComposingText()
        if (!conversionSession.isActive || composing.isEmpty()) {
            conversionHint.isVisible = false
            return
        }

        val target = conversionSession.getConversionTarget(composing)
        val suffix = conversionSession.getRemainingSuffix(composing)
        conversionHint.text = if (suffix.isEmpty()) {
            getString(R.string.conversion_hint_full, target)
        } else {
            getString(R.string.conversion_hint_partial, target, suffix)
        }
        conversionHint.isVisible = true
    }

    private fun onComposingTextChanged(composingText: String) {
        if (!suppressConversionReset && composingText != lastComposingTextForConversion) {
            conversionSession.deactivate()
            pendingConversionActivation = false
            conversionSession.resetConversionEnd(composingText.length)
        }
        lastComposingTextForConversion = composingText
        if (!suppressConversionReset) {
            updateComposingText(composingText)
            requestConversion(composingText)
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

    private fun requestConversion(
        composing: String,
        activateOnResult: Boolean = false,
    ) {
        conversionJob?.cancel()
        if (composing.isEmpty()) {
            resetConversionState()
            return
        }

        if (activateOnResult) {
            pendingConversionActivation = true
        }

        val target = resolveConversionTarget(composing)
        val requestComposing = composing

        conversionJob = conversionScope.launch {
            val candidates = withContext(Dispatchers.Default) {
                conversionEngine.convert(target, coordinator.getInputMode().toConversionMode())
            }
            if (requestComposing != coordinator.getComposingText()) return@launch

            conversionSession.setCandidates(candidates)
            if (pendingConversionActivation && candidates.isNotEmpty()) {
                conversionSession.activate(requestComposing.length)
                pendingConversionActivation = false
            }
            refreshConversionUi()
        }
    }

    private fun resolveConversionTarget(composing: String): String {
        if (conversionSession.isActive || pendingConversionActivation) {
            return conversionSession.getConversionTarget(composing)
        }
        return composing
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
                val composing = coordinator.getComposingText()
                if (!conversionSession.isActive && conversionSession.getCandidates().isNotEmpty()) {
                    conversionSession.activate(composing.length)
                }
                applyPartialConversion(candidate)
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
        conversionHint.isVisible = false
        candidateContainer.removeAllViews()
        candidateScroll.visibility = View.GONE
    }

    private fun applyPartialConversion(candidate: String) {
        val composing = coordinator.getComposingText()
        if (composing.isEmpty()) return

        val inputConnection = currentInputConnection ?: return
        val suffix = conversionSession.getRemainingSuffix(composing)

        inputConnection.beginBatchEdit()
        inputConnection.commitText(candidate, 1)
        if (suffix.isNotEmpty()) {
            inputConnection.setComposingText(suffix, 1)
        } else {
            inputConnection.finishComposingText()
        }
        inputConnection.endBatchEdit()

        suppressConversionReset = true
        if (suffix.isNotEmpty()) {
            coordinator.setComposingFromConversion(suffix)
        } else {
            coordinator.clearComposingState()
        }

        conversionSession.deactivate()
        lastComposingTextForConversion = suffix

        if (suffix.isNotEmpty()) {
            conversionSession.resetConversionEnd(suffix.length)
            requestConversion(suffix, activateOnResult = true)
        } else {
            resetConversionState()
        }
        suppressConversionReset = false
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
        currentInputConnection?.let { ic ->
            coordinator.commitComposingText(ic)
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
        toggleAutoCommitJob?.cancel()
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
