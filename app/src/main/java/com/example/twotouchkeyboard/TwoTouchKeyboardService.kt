package com.example.twotouchkeyboard

import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import com.example.mozcengine.ConversionEngine
import com.example.mozcengine.AlphabetPredictionSupport
import com.example.twotouchkeyboard.candidate.CandidateLearningCoordinator
import com.example.twotouchkeyboard.candidate.CandidateUsageContext
import com.example.twotouchkeyboard.candidate.EnglishCandidateUsageStore
import com.example.twotouchkeyboard.input.EnterBehaviorResolver
import com.example.twotouchkeyboard.input.EnterKeyLabels
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
    private val displayedKeyLabels: MutableMap<KeyboardKey, String> = mutableMapOf()
    private lateinit var keyboardRootView: View
    private var highlightedWaitingRowKey: KeyboardKey? = null
    private var labelUpdatePosted = false
    private var pendingForceAllLabels = false
    private lateinit var keyboardFlipper: ViewFlipper
    private lateinit var conversionEngine: ConversionEngine
    private lateinit var candidateLearningCoordinator: CandidateLearningCoordinator

    private val conversionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var conversionJob: Job? = null
    private var toggleAutoCommitJob: Job? = null
    private var deleteRepeatJob: Job? = null
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
        candidateLearningCoordinator = CandidateLearningCoordinator(
            conversionEngine = conversionEngine,
            englishUsageStore = EnglishCandidateUsageStore(applicationContext),
        )
        settingsCollectJob = lifecycleScope.launch {
            combine(
                settingsRepository.hiraganaInputMode,
                settingsRepository.alphabetInputMode,
                settingsRepository.toggleAutoCommitTimeoutMs,
                settingsRepository.candidateUsageLearningEnabled,
            ) { hiragana, alphabet, timeoutMs, usageLearningEnabled ->
                SettingsSnapshot(hiragana, alphabet, timeoutMs, usageLearningEnabled)
            }.collect { snapshot ->
                currentHiraganaMethod = snapshot.hiraganaMethod
                currentAlphabetMethod = snapshot.alphabetMethod
                toggleAutoCommitTimeoutMs = snapshot.toggleAutoCommitTimeoutMs
                candidateLearningCoordinator.learningEnabled = snapshot.candidateUsageLearningEnabled
                if (::coordinator.isInitialized) {
                    coordinator.setHiraganaInputMethod(snapshot.hiraganaMethod)
                    coordinator.setAlphabetInputMethod(snapshot.alphabetMethod)
                    onKeyboardStateChanged(forceAllLabels = true)
                }
            }
        }
    }

    private data class SettingsSnapshot(
        val hiraganaMethod: CharacterInputMethod,
        val alphabetMethod: CharacterInputMethod,
        val toggleAutoCommitTimeoutMs: Int,
        val candidateUsageLearningEnabled: Boolean,
    )

    override fun onCreateInputView(): View {
        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)
        keyboardRootView = keyboardView
        applyNavigationBarPadding(keyboardView)
        keyboardView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        conversionHint = keyboardView.findViewById(R.id.conversion_hint)
        candidateScroll = keyboardView.findViewById(R.id.candidate_scroll)
        candidateContainer = keyboardView.findViewById(R.id.candidate_container)
        keyboardFlipper = keyboardView.findViewById(R.id.keyboard_flipper)

        coordinator = KeyboardInputCoordinator(
            listener = object : KeyboardInputCoordinator.Listener {
                override fun onStateChanged() {
                    onKeyboardStateChanged()
                }

                override fun onComposingTextUpdated(composingText: String) {
                    onComposingTextChanged(composingText)
                }

                override fun onInputModeChanged(mode: InputMode) {
                    resetConversionState()
                    onKeyboardStateChanged(forceAllLabels = true)
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

                override fun requestHideSoftInput() {
                    requestHideSelf(0)
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
        bindDeleteKey(keyboardView, R.id.key_delete)
        bindKey(keyboardView, R.id.key_enter, KeyboardKey.Enter)
        bindKey(keyboardView, R.id.key_space, KeyboardKey.Space)
        bindKey(keyboardView, R.id.key_cursor_left, KeyboardKey.CursorLeft)
        bindKey(keyboardView, R.id.key_cursor_right, KeyboardKey.CursorRight)
        bindKey(keyboardView, R.id.key_text_modifier, KeyboardKey.TextModifier)
        bindSymbolKeyboard(keyboardView)

        onKeyboardStateChanged(forceAllLabels = true)
        showMainKeyboard()
        return keyboardView
    }

    private fun applyNavigationBarPadding(view: View) {
        val basePaddingBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, windowInsets ->
            val navigationBarInset =
                windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            target.updatePadding(bottom = basePaddingBottom + navigationBarInset)
            windowInsets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private fun bindSymbolKeyboard(root: View) {
        SYMBOL_KEY_BINDINGS.forEach { (viewId, symbol) ->
            root.findViewById<Button>(viewId).apply {
                setOnClickListener { insertSymbol(symbol) }
                setOnTouchListener(bindKeyTouchListener { insertSymbol(symbol) })
            }
        }
        root.findViewById<Button>(R.id.symbol_key_close).apply {
            setOnClickListener { showMainKeyboard() }
            setOnTouchListener(bindKeyTouchListener { showMainKeyboard() })
        }
    }

    private fun bindKey(root: View, viewId: Int, key: KeyboardKey) {
        val button = root.findViewById<Button>(viewId)
        keyButtons[key] = button
        button.setOnClickListener { dispatchKey(key) }
        button.setOnTouchListener(bindKeyTouchListener { dispatchKey(key) })
    }

    private fun bindKeyTouchListener(onPress: () -> Unit): View.OnTouchListener {
        return View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    view.isPressed = true
                    onPress()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    private fun bindDeleteKey(root: View, viewId: Int) {
        val button = root.findViewById<Button>(viewId)
        keyButtons[KeyboardKey.Delete] = button
        button.setOnClickListener {
            performDeleteKeyAction()
        }
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    view.isPressed = true
                    startDeleteRepeat()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isTouchInsideView(view, event)) {
                        view.isPressed = false
                        stopDeleteRepeat()
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (hasRemainingPointerInsideView(view, event)) {
                        view.isPressed = true
                        true
                    } else {
                        view.isPressed = false
                        stopDeleteRepeat()
                        true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    stopDeleteRepeat()
                    true
                }
                else -> false
            }
        }
    }

    private fun hasRemainingPointerInsideView(view: View, event: MotionEvent): Boolean {
        val liftedPointerIndex = event.actionIndex
        for (pointerIndex in 0 until event.pointerCount) {
            if (pointerIndex == liftedPointerIndex) continue
            if (isTouchInsideView(view, event.getX(pointerIndex), event.getY(pointerIndex))) {
                return true
            }
        }
        return false
    }

    private fun isTouchInsideView(view: View, event: MotionEvent): Boolean {
        return isTouchInsideView(view, event.x, event.y)
    }

    private fun isTouchInsideView(view: View, x: Float, y: Float): Boolean {
        return x in 0f..view.width.toFloat() &&
            y in 0f..view.height.toFloat()
    }

    private fun startDeleteRepeat() {
        stopDeleteRepeat()
        performDeleteKeyAction()
        deleteRepeatJob = conversionScope.launch {
            delay(DELETE_REPEAT_INITIAL_DELAY_MS)
            var intervalMs = DELETE_REPEAT_INTERVAL_MS
            while (true) {
                performDeleteKeyAction()
                delay(intervalMs)
                intervalMs = (intervalMs * DELETE_REPEAT_ACCELERATION_RATIO)
                    .toLong()
                    .coerceAtLeast(DELETE_REPEAT_MIN_INTERVAL_MS)
            }
        }
    }

    private fun stopDeleteRepeat() {
        deleteRepeatJob?.cancel()
        deleteRepeatJob = null
    }

    private fun performDeleteKeyAction() {
        coordinator.bindInputConnection(currentInputConnection)
        if (canHandleConversionKey(KeyboardKey.Delete) && handleConversionKey(KeyboardKey.Delete)) {
            return
        }
        handleDeleteKey()
    }

    private fun dispatchKey(key: KeyboardKey) {
        coordinator.bindInputConnection(currentInputConnection)

        if (key == KeyboardKey.Hash) {
            openSymbolKeyboard()
            return
        }

        if (canHandleConversionKey(key) && handleConversionKey(key)) {
            return
        }

        when (key) {
            KeyboardKey.Star -> {
                if (coordinator.isModeSwitchEnabled()) {
                    coordinator.handleModeSwitchKey()
                } else {
                    coordinator.onKeyPressed(key)
                }
            }
            KeyboardKey.TextModifier -> {
                if (coordinator.getInputMode() == InputMode.NUMBER) {
                    coordinator.onKeyPressed(key)
                } else {
                    coordinator.applyTextModifier()
                    onKeyboardStateChanged()
                }
            }
            KeyboardKey.Delete -> handleDeleteKey()
            KeyboardKey.Enter -> handleEnterKey()
            KeyboardKey.Space -> handleSpaceKey()
            KeyboardKey.CursorLeft -> handleCursorKey(CursorDirection.LEFT)
            KeyboardKey.CursorRight -> handleCursorKey(CursorDirection.RIGHT)
            else -> coordinator.onKeyPressed(key)
        }
    }

    private fun canHandleConversionKey(key: KeyboardKey): Boolean {
        if (!coordinator.isConversionEnabled()) return false
        if (!isPredictionConversionMode()) return false
        if (coordinator.getComposingText().isEmpty()) return false
        if (coordinator.isMidCharacterInput()) return false

        if (conversionSession.isActive) {
            return key == KeyboardKey.Space ||
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
        if (shouldShowConversionKey()) {
            if (!conversionSession.isActive) {
                enterConversionMode()
                return
            }
            conversionSession.selectNextCandidate()
            refreshConversionUi()
            return
        }
        coordinator.onSpace()
    }

    private fun handleEnterKey() {
        if (conversionSession.isActive) {
            conversionSession.getSelectedCandidate()?.let { candidate ->
                applyPartialConversion(candidate)
                return
            }
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
        if (canStartPredictionConversion()) {
            adjustConversionBoundary(direction)
            return
        }
        coordinator.onCursorMove(direction)
    }

    private fun isPredictionConversionMode(): Boolean {
        return when (coordinator.getInputMode()) {
            InputMode.HIRAGANA, InputMode.ALPHABET -> true
            InputMode.NUMBER -> false
        }
    }

    private fun canStartPredictionConversion(): Boolean {
        return coordinator.isConversionEnabled() &&
            isPredictionConversionMode() &&
            coordinator.getComposingText().isNotEmpty() &&
            !coordinator.isMidCharacterInput()
    }

    private fun shouldShowConversionKey(): Boolean {
        return canStartPredictionConversion() && conversionSession.getCandidates().isNotEmpty()
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
        scheduleKeyLabelUpdate()
    }

    private fun updateConversionHint() {
        val composing = coordinator.getComposingText()
        if (!conversionSession.isActive || composing.isEmpty()) {
            conversionHint.isVisible = false
            return
        }

        val target = conversionSession.getConversionTarget(composing)
        val suffix = conversionSession.getRemainingSuffix(composing)
        val hintTemplate = if (coordinator.getInputMode() == InputMode.ALPHABET) {
            if (suffix.isEmpty()) {
                R.string.prediction_hint_full
            } else {
                R.string.prediction_hint_partial
            }
        } else {
            if (suffix.isEmpty()) {
                R.string.conversion_hint_full
            } else {
                R.string.conversion_hint_partial
            }
        }
        conversionHint.text = if (suffix.isEmpty()) {
            getString(hintTemplate, target)
        } else {
            getString(hintTemplate, target, suffix)
        }
        conversionHint.isVisible = true
    }

    private fun onComposingTextChanged(composingText: String) {
        if (!coordinator.isConversionEnabled()) {
            lastComposingTextForConversion = composingText
            scheduleKeyLabelUpdate()
            return
        }
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
        scheduleKeyLabelUpdate()
    }

    private fun updateComposingText(text: String) {
        val inputConnection = currentInputConnection ?: return
        // finishComposingText() commits the current composing span. When deleting the
        // last character we must clear composing without committing it.
        inputConnection.setComposingText(text, 1)
    }

    private fun requestConversion(
        composing: String,
        activateOnResult: Boolean = false,
    ) {
        if (!coordinator.isConversionEnabled()) {
            resetConversionState()
            return
        }
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
        val lookupTarget = if (coordinator.getInputMode() == InputMode.ALPHABET) {
            AlphabetPredictionSupport.lookupInput(target)
        } else {
            target
        }

        conversionJob = conversionScope.launch {
            val rawCandidates = withContext(Dispatchers.Default) {
                conversionEngine.convert(lookupTarget, coordinator.getInputMode().toConversionMode())
            }
            if (requestComposing != coordinator.getComposingText()) return@launch

            val candidates = if (coordinator.getInputMode() == InputMode.ALPHABET) {
                AlphabetPredictionSupport.prepareEnglishCandidates(rawCandidates, target)
            } else {
                rawCandidates
            }
            val rankedCandidates = candidateLearningCoordinator.rank(
                mode = coordinator.getInputMode(),
                contextKey = target,
                candidates = candidates,
            )
            conversionSession.setCandidates(rankedCandidates)
            if (pendingConversionActivation && rankedCandidates.isNotEmpty()) {
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

        candidates.forEachIndexed { index, candidate ->
            val itemView = inflater.inflate(R.layout.suggest_item, candidateContainer, false)
            val textView = itemView.findViewById<TextView>(R.id.candidate_text)
            textView.text = candidate

            if (conversionSession.isActive && index == selectedIndex) {
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

    private fun scrollToSelectedCandidate(selectedIndex: Int) {
        candidateScroll.post {
            val child = candidateContainer.getChildAt(selectedIndex) ?: return@post
            val scrollX = child.left - (candidateScroll.width - child.width) / 2
            candidateScroll.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }

    private fun resetConversionState() {
        conversionJob?.cancel()
        if (::conversionEngine.isInitialized) {
            conversionEngine.resetSession()
        }
        conversionSession.clear()
        pendingConversionActivation = false
        lastComposingTextForConversion = ""
        conversionHint.isVisible = false
        candidateContainer.removeAllViews()
        candidateScroll.visibility = View.GONE
        if (keyButtons.isNotEmpty()) {
            scheduleKeyLabelUpdate()
        }
    }

    private fun applyPartialConversion(candidate: String) {
        val composing = coordinator.getComposingText()
        if (composing.isEmpty()) return

        recordCandidateUsage(candidate, composing)

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

    private fun recordCandidateUsage(candidate: String, composing: String) {
        if (!coordinator.isConversionEnabled()) return

        candidateLearningCoordinator.recordCommit(
            CandidateUsageContext(
                mode = coordinator.getInputMode(),
                contextKey = conversionSession.getConversionTarget(composing),
                candidate = candidate,
            ),
        )
    }

    private fun onKeyboardStateChanged(forceAllLabels: Boolean = false) {
        updateTwoTouchWaitingHighlight()
        scheduleKeyLabelUpdate(forceAll = forceAllLabels)
    }

    private fun updateTwoTouchWaitingHighlight() {
        val waitingRowKey = coordinator.getTwoTouchWaitingRowKey()
        if (waitingRowKey == highlightedWaitingRowKey) return

        highlightedWaitingRowKey?.let { previousKey ->
            keyButtons[previousKey]?.isActivated = false
        }
        highlightedWaitingRowKey = waitingRowKey
        waitingRowKey?.let { key ->
            keyButtons[key]?.isActivated = true
        }
    }

    private fun scheduleKeyLabelUpdate(forceAll: Boolean = false) {
        if (forceAll) {
            pendingForceAllLabels = true
        }
        if (!::keyboardRootView.isInitialized) {
            applyKeyLabelDiff(forceAll = pendingForceAllLabels)
            pendingForceAllLabels = false
            return
        }
        if (labelUpdatePosted) return
        labelUpdatePosted = true
        keyboardRootView.post {
            labelUpdatePosted = false
            val force = pendingForceAllLabels
            pendingForceAllLabels = false
            applyKeyLabelDiff(forceAll = force)
        }
    }

    private fun applyKeyLabelDiff(forceAll: Boolean = false) {
        if (forceAll) {
            displayedKeyLabels.clear()
        }
        keyButtons.forEach { (key, button) ->
            val newLabel = getKeyLabel(key)
            val previousLabel = displayedKeyLabels[key]
            if (forceAll || previousLabel != newLabel) {
                if (button.text.toString() != newLabel) {
                    button.text = newLabel
                }
                displayedKeyLabels[key] = newLabel
            }
        }
    }

    private fun getKeyLabel(key: KeyboardKey): String {
        if (key == KeyboardKey.Hash) {
            return getString(R.string.key_symbols)
        }
        if (key == KeyboardKey.TextModifier) {
            return coordinator.getTextModifierLabel()
        }
        if (key == KeyboardKey.Space && shouldShowConversionKey()) {
            return getString(R.string.key_conversion)
        }
        if (key == KeyboardKey.Enter && shouldShowEnterConfirmLabel()) {
            return getString(R.string.key_confirm)
        }
        if (key == KeyboardKey.Enter) {
            return EnterBehaviorResolver.getEnterKeyLabel(
                info = coordinator.getCurrentEditorInfo(),
                labels = EnterKeyLabels(
                    newline = getString(R.string.key_enter),
                    close = getString(R.string.key_symbol_close),
                    go = getString(R.string.key_go),
                    search = getString(R.string.key_search),
                    next = getString(R.string.key_next),
                    previous = getString(R.string.key_previous),
                ),
            )
        }
        return coordinator.getKeyLabel(key)
    }

    private fun shouldShowEnterConfirmLabel(): Boolean {
        if (conversionSession.isActive) return true
        return coordinator.getComposingText().isNotEmpty()
    }

    private fun finalizeInputState() {
        coordinator.bindInputConnection(currentInputConnection)
        currentInputConnection?.let { ic ->
            coordinator.commitComposingText(ic)
        }
        coordinator.clearComposingState()
        resetConversionState()
        showMainKeyboard()
    }

    private fun openSymbolKeyboard() {
        commitComposingForSymbolTransition()
        showSymbolKeyboard()
    }

    private fun commitComposingForSymbolTransition() {
        conversionJob?.cancel()
        if (conversionSession.isActive) {
            conversionSession.deactivate()
            pendingConversionActivation = false
        }
        resetConversionState()

        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            suppressConversionReset = true
            coordinator.commitComposingText(inputConnection)
            coordinator.clearComposingState()
            suppressConversionReset = false
        } else {
            coordinator.clearComposingState()
        }
        coordinator.resetPartialInput()
    }

    private fun insertSymbol(symbol: String) {
        coordinator.bindInputConnection(currentInputConnection)
        currentInputConnection?.commitText(symbol, 1)
        showMainKeyboard()
    }

    private fun showMainKeyboard() {
        if (::keyboardFlipper.isInitialized) {
            keyboardFlipper.displayedChild = INDEX_MAIN_KEYBOARD
        }
    }

    private fun showSymbolKeyboard() {
        if (::keyboardFlipper.isInitialized) {
            keyboardFlipper.displayedChild = INDEX_SYMBOL_KEYBOARD
        }
    }

    private fun resetKeyboardViewState() {
        showMainKeyboard()
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
            resetKeyboardViewState()
            coordinator.applyEditorInfo(info)
            coordinator.bindInputConnection(currentInputConnection)
            coordinator.resetInputSession()
            resetConversionState()
            currentInputConnection?.finishComposingText()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopDeleteRepeat()
        if (::coordinator.isInitialized) {
            finalizeInputState()
            coordinator.resetInputSession()
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        stopDeleteRepeat()
        settingsCollectJob?.cancel()
        toggleAutoCommitJob?.cancel()
        conversionScope.cancel()
        if (::conversionEngine.isInitialized) {
            conversionEngine.close()
        }
        super.onDestroy()
    }

    companion object {
        private const val INDEX_MAIN_KEYBOARD = 0
        private const val INDEX_SYMBOL_KEYBOARD = 1
        private const val DELETE_REPEAT_INITIAL_DELAY_MS = 400L
        private const val DELETE_REPEAT_INTERVAL_MS = 50L
        private const val DELETE_REPEAT_MIN_INTERVAL_MS = 20L
        private const val DELETE_REPEAT_ACCELERATION_RATIO = 0.85

        private val SYMBOL_KEY_BINDINGS = mapOf(
            R.id.symbol_key_comma to "、",
            R.id.symbol_key_period to "。",
            R.id.symbol_key_exclamation to "！",
            R.id.symbol_key_question to "？",
            R.id.symbol_key_middle_dot to "・",
            R.id.symbol_key_at to "@",
            R.id.symbol_key_hash to "#",
            R.id.symbol_key_ampersand to "&",
            R.id.symbol_key_asterisk to "*",
            R.id.symbol_key_hyphen to "-",
            R.id.symbol_key_underscore to "_",
            R.id.symbol_key_plus to "+",
            R.id.symbol_key_equals to "=",
            R.id.symbol_key_slash to "/",
            R.id.symbol_key_colon to ":",
        )
    }
}
