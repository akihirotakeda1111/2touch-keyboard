package com.example.twotouchkeyboard

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import com.example.mozcengine.ConversionEngine
import com.example.mozcengine.AlphabetPredictionSupport
import com.example.mozcengine.ConversionMode
import com.example.mozcengine.JapaneseCandidatePrior
import com.example.twotouchkeyboard.candidate.CandidatePipeline
import com.example.twotouchkeyboard.candidate.CandidateRequestKind
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
    private lateinit var candidateScroll: HorizontalScrollView
    private lateinit var candidateContainer: LinearLayout
    private lateinit var settingsRepository: SettingsRepository

    private val keyButtons: MutableMap<KeyboardKey, Button> = mutableMapOf()
    private val displayedKeyLabels: MutableMap<KeyboardKey, String> = mutableMapOf()
    private lateinit var keyboardRootView: View
    private var labelUpdatePosted = false
    private var pendingForceAllLabels = false
    private lateinit var keyboardFlipper: ViewFlipper
    /** 記号パネルを開いた時点の入力モードで固定した、表示と入力で共通の文字。 */
    private var symbolCharacters: Map<Int, String> = SymbolPanel.charactersFor(InputMode.HIRAGANA)
    private lateinit var conversionEngine: ConversionEngine
    private lateinit var candidateLearningCoordinator: CandidateLearningCoordinator
    private lateinit var candidatePipeline: CandidatePipeline

    private val conversionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var conversionJob: Job? = null
    private var toggleAutoCommitJob: Job? = null
    private var deleteRepeatJob: Job? = null
    private var settingsCollectJob: Job? = null

    private val conversionSession = ConversionSession()
    private val nextInputSuggestionSession = NextInputSuggestionSession()
    private var pendingNextInputSuggestion = false
    private var pendingConversionActivation = false
    private var lastComposingTextForConversion = ""
    private var suppressConversionReset = false
    private var symbolKeyboardVisible = false
    private var appliedOrientation = Configuration.ORIENTATION_UNDEFINED
    private var recreatingInputView = false
    private val orientationHandoff = OrientationInputHandoff()
    private var orientationFieldBefore = ComposingSpanRestore.FieldObservation.Unavailable
    private var inputSessionId = 0L
    private var inputStartPairOpen = false
    private var suppressEditorComposingSync = false

    private var currentHiraganaMethod = CharacterInputMethod.TWOTOUCH
    private var currentAlphabetMethod = CharacterInputMethod.TOGGLE
    private var toggleAutoCommitTimeoutMs = SettingsRepository.DEFAULT_TOGGLE_AUTO_COMMIT_TIMEOUT_MS

    override fun onCreate() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onCreate()
        if (appliedOrientation == Configuration.ORIENTATION_UNDEFINED) {
            appliedOrientation = resources.configuration.orientation
        }
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        settingsRepository = SettingsRepository(applicationContext)
        conversionEngine = ConversionEngineProvider.create(applicationContext)
        candidateLearningCoordinator = CandidateLearningCoordinator(
            conversionEngine = conversionEngine,
            englishUsageStore = EnglishCandidateUsageStore(applicationContext),
        )
        candidatePipeline = CandidatePipeline(
            getUsageCount = candidateLearningCoordinator::getUsageCount,
            getJapanesePriority = { reading, value ->
                if (conversionEngine.isMozc) JapaneseCandidatePrior.current().priorityOf(reading, value) else 0
            },
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
        applyTransparentImeWindow()
        applyEdgeAvoidance(
            root = keyboardView,
            panel = keyboardView.findViewById(R.id.keyboard_panel),
        )
        keyboardView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        candidateScroll = keyboardView.findViewById(R.id.candidate_scroll)
        candidateContainer = keyboardView.findViewById(R.id.candidate_container)
        keyboardFlipper = keyboardView.findViewById(R.id.keyboard_flipper)

        val createdCoordinator = !::coordinator.isInitialized
        if (createdCoordinator) {
            coordinator = createCoordinator()
            coordinator.setHiraganaInputMethod(currentHiraganaMethod)
            coordinator.setAlphabetInputMethod(currentAlphabetMethod)
        }

        keyButtons.clear()
        displayedKeyLabels.clear()
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
        if (symbolKeyboardVisible) {
            showSymbolKeyboard()
        } else {
            showMainKeyboard()
        }
        if (!createdCoordinator) {
            refreshConversionUi()
        }
        return keyboardView
    }

    private fun createCoordinator(): KeyboardInputCoordinator {
        return KeyboardInputCoordinator(
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
    }

    private fun applyEdgeAvoidance(root: View, panel: View) {
        val baseRootLeft = root.paddingLeft
        val baseRootRight = root.paddingRight
        val basePanelBottom = panel.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val safe = KeyboardEdgeAvoidance.paddingFor(windowInsets)
            root.updatePadding(
                left = baseRootLeft + safe.left,
                right = baseRootRight + safe.right,
            )
            panel.updatePadding(bottom = basePanelBottom + safe.bottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun applyTransparentImeWindow() {
        val phoneWindow = window?.window ?: return
        phoneWindow.setBackgroundDrawableResource(android.R.color.transparent)
        applyDisplayCutoutMode(phoneWindow)
    }

    private fun applyDisplayCutoutMode(phoneWindow: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val attributes = phoneWindow.attributes
        if (attributes.layoutInDisplayCutoutMode == mode) return
        attributes.layoutInDisplayCutoutMode = mode
        phoneWindow.attributes = attributes
    }

    private fun bindSymbolKeyboard(root: View) {
        SymbolPanel.keys.forEach { key ->
            root.findViewById<Button>(key.viewId).apply {
                setOnClickListener { insertSymbol(symbolCharacters.getValue(key.viewId)) }
                setOnTouchListener(bindKeyTouchListener {
                    insertSymbol(symbolCharacters.getValue(key.viewId))
                })
            }
        }
        root.findViewById<Button>(R.id.symbol_key_close).apply {
            setOnClickListener { showMainKeyboard() }
            setOnTouchListener(bindKeyTouchListener { showMainKeyboard() })
        }
        applySymbolCharacters(root, currentSymbolMode())
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
        dismissNextInputSuggestionForKey(KeyboardKey.Delete)
        if (canHandleConversionKey(KeyboardKey.Delete) && handleConversionKey(KeyboardKey.Delete)) {
            return
        }
        handleDeleteKey()
    }

    private fun dispatchKey(key: KeyboardKey) {
        coordinator.bindInputConnection(currentInputConnection)

        dismissNextInputSuggestionForKey(key)

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

    private fun dismissesNextInputSuggestion(key: KeyboardKey): Boolean {
        return when (key) {
            is KeyboardKey.Digit,
            KeyboardKey.Zero,
            KeyboardKey.Star,
            KeyboardKey.TextModifier,
            KeyboardKey.Space,
            KeyboardKey.Hash,
            KeyboardKey.Delete,
            KeyboardKey.Enter,
            KeyboardKey.CursorLeft,
            KeyboardKey.CursorRight,
            -> true
            else -> false
        }
    }

    private fun dismissNextInputSuggestionForKey(key: KeyboardKey) {
        if (!dismissesNextInputSuggestion(key)) return
        if (!pendingNextInputSuggestion && !nextInputSuggestionSession.isActive) return

        invalidatePendingNextInputSuggestion()
        clearNextInputSuggestion()
    }

    private fun invalidatePendingNextInputSuggestion() {
        pendingNextInputRequestToken = null
        pendingNextInputSuggestion = false
    }

    private fun canHandleConversionKey(key: KeyboardKey): Boolean {
        if (nextInputSuggestionSession.isActive) return false
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

        val hadComposing = coordinator.getComposingText().isNotEmpty()
        suppressConversionReset = true
        coordinator.onEnter()
        suppressConversionReset = false

        if (hadComposing && shouldOfferNextInputSuggestion()) {
            requestNextInputSuggestion(selectedCandidate = null)
        } else if (hadComposing) {
            resetConversionState()
        }
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
        if (nextInputSuggestionSession.isActive) {
            updateCandidateUi(
                candidates = nextInputSuggestionSession.getCandidates(),
                onCandidateSelected = ::applyNextInputSuggestion,
                highlightSelection = false,
            )
        } else {
            updateCandidateUi(conversionSession.getCandidates())
        }
        scheduleKeyLabelUpdate()
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
            if (composingText.isEmpty()) {
                if (!pendingNextInputSuggestion && !nextInputSuggestionSession.isActive) {
                    resetConversionState()
                }
            } else {
                requestConversion(composingText)
            }
        }
        scheduleKeyLabelUpdate()
    }

    private fun updateComposingText(text: String) {
        if (suppressEditorComposingSync) return
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
        if (composing.isNotEmpty()) {
            invalidatePendingNextInputSuggestion()
        }
        if (composing.isEmpty()) {
            if (!pendingNextInputSuggestion && !nextInputSuggestionSession.isActive) {
                resetConversionState()
            }
            return
        }

        if (activateOnResult) {
            pendingConversionActivation = true
        }

        val target = resolveConversionTarget(composing)
        val requestMode = coordinator.getInputMode()
        val requestComposing = composing
        val lookupTarget = if (requestMode == InputMode.ALPHABET) {
            AlphabetPredictionSupport.lookupInput(target)
        } else {
            target
        }

        conversionJob = conversionScope.launch {
            val rawCandidates = withContext(Dispatchers.Default) {
                conversionEngine.convert(lookupTarget, requestMode.toConversionMode())
            }
            if (requestComposing != coordinator.getComposingText()) return@launch
            if (requestMode != coordinator.getInputMode()) return@launch

            val rankedCandidates = candidatePipeline.prepare(
                mode = requestMode,
                input = target,
                candidates = rawCandidates,
            ).map { it.value }
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

    private fun updateCandidateUi(
        candidates: List<String>,
        onCandidateSelected: (String) -> Unit = ::applyPartialConversionFromUi,
        highlightSelection: Boolean = true,
    ) {
        candidateContainer.removeAllViews()
        if (candidates.isEmpty()) {
            return
        }

        val inflater = LayoutInflater.from(this)
        val selectedIndex = if (highlightSelection) conversionSession.getSelectedIndex() else -1

        candidates.forEachIndexed { index, candidate ->
            val itemView = inflater.inflate(R.layout.suggest_item, candidateContainer, false)
            val textView = itemView.findViewById<TextView>(R.id.candidate_text)
            textView.text = candidate

            if (highlightSelection && conversionSession.isActive && index == selectedIndex) {
                itemView.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.candidate_selected_background),
                )
                textView.setTextColor(
                    ContextCompat.getColor(this, R.color.candidate_selected_text),
                )
            } else {
                itemView.setBackgroundResource(R.drawable.candidate_chip_background)
                textView.setTextColor(
                    ContextCompat.getColor(this, R.color.candidate_text),
                )
            }

            textView.setOnClickListener {
                onCandidateSelected(candidate)
            }
            candidateContainer.addView(itemView)
        }

        if (selectedIndex >= 0) {
            scrollToSelectedCandidate(selectedIndex)
        }
    }

    private fun scrollToSelectedCandidate(selectedIndex: Int) {
        candidateScroll.post {
            val child = candidateContainer.getChildAt(selectedIndex) ?: return@post
            val scrollX = child.left - (candidateScroll.width - child.width) / 2
            candidateScroll.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }

    private fun clearConversionSessionOnly() {
        conversionSession.clear()
        pendingConversionActivation = false
    }

    private fun clearNextInputSuggestion() {
        nextInputSuggestionSession.clear()
        if (conversionSession.getCandidates().isEmpty()) {
            candidateContainer.removeAllViews()
        } else {
            refreshConversionUi()
        }
        scheduleKeyLabelUpdate()
    }

    private fun resetConversionState() {
        conversionJob?.cancel()
        invalidatePendingNextInputSuggestion()
        if (::conversionEngine.isInitialized) {
            conversionEngine.resetSession()
        }
        conversionSession.clear()
        nextInputSuggestionSession.clear()
        pendingConversionActivation = false
        lastComposingTextForConversion = ""
        candidateContainer.removeAllViews()
        if (keyButtons.isNotEmpty()) {
            scheduleKeyLabelUpdate()
        }
    }

    private fun shouldOfferNextInputSuggestion(): Boolean {
        return coordinator.isConversionEnabled() &&
            coordinator.getInputMode() == InputMode.HIRAGANA &&
            !coordinator.isMidCharacterInput()
    }

    private fun requestNextInputSuggestion(selectedCandidate: String?) {
        if (!shouldOfferNextInputSuggestion()) {
            resetConversionState()
            return
        }

        conversionJob?.cancel()
        clearConversionSessionOnly()
        candidateContainer.removeAllViews()
        pendingNextInputSuggestion = true
        val requestToken = Any()
        pendingNextInputRequestToken = requestToken

        conversionJob = conversionScope.launch {
            try {
                val suggestions = withContext(Dispatchers.Default) {
                    conversionEngine.suggestNext(
                        mode = ConversionMode.HIRAGANA,
                        selectedCandidate = selectedCandidate,
                    )
                }
                if (pendingNextInputRequestToken != requestToken) return@launch

                if (coordinator.getComposingText().isNotEmpty()) {
                    return@launch
                }

                if (suggestions.isEmpty()) {
                    clearNextInputSuggestion()
                    return@launch
                }

                val displayed = candidatePipeline.prepare(
                    mode = InputMode.HIRAGANA,
                    input = "",
                    candidates = suggestions,
                    kind = CandidateRequestKind.NEXT_INPUT,
                )
                nextInputSuggestionSession.setCandidates(displayed.map { it.value })
                refreshConversionUi()
            } finally {
                if (pendingNextInputRequestToken == requestToken) {
                    pendingNextInputSuggestion = false
                }
            }
        }
    }

    private var pendingNextInputRequestToken: Any? = null

    private fun applyNextInputSuggestion(candidate: String) {
        val inputConnection = currentInputConnection ?: return
        inputConnection.commitText(candidate, 1)
        clearNextInputSuggestion()
        conversionEngine.resetSession()
    }

    private fun applyPartialConversionFromUi(candidate: String) {
        val composing = coordinator.getComposingText()
        if (!conversionSession.isActive && conversionSession.getCandidates().isNotEmpty()) {
            conversionSession.activate(composing.length)
        }
        applyPartialConversion(candidate)
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
            clearConversionSessionOnly()
            requestNextInputSuggestion(selectedCandidate = candidate)
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
        scheduleKeyLabelUpdate(forceAll = forceAllLabels)
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
        return coordinator.getComposingPreview().isNotEmpty()
    }

    private fun finalizeInputState(connection: InputConnection? = currentInputConnection) {
        if (!::coordinator.isInitialized) return
        coordinator.bindInputConnection(connection)
        if (connection != null) {
            coordinator.commitComposingText(connection)
        }
        coordinator.clearComposingState()
        resetConversionState()
        showMainKeyboard()
    }

    private fun beginOrientationHandoff() {
        if (!::coordinator.isInitialized) return
        orientationHandoff.onOrientationChanged(inputSessionId)
        orientationFieldBefore = ComposingSpanRestore.observe(currentInputConnection)
    }

    private fun clearOrientationRestore() {
        orientationHandoff.clear()
        orientationFieldBefore = ComposingSpanRestore.FieldObservation.Unavailable
    }

    private fun beginInputStart(restarting: Boolean) {
        if (inputStartPairOpen) return
        inputStartPairOpen = true
        if (!restarting) {
            inputSessionId += 1
            clearOrientationRestore()
        }
    }

    private fun completeInputStartPair() {
        inputStartPairOpen = false
    }

    private fun withoutEditorComposingSync(block: () -> Unit) {
        val previous = suppressEditorComposingSync
        suppressEditorComposingSync = true
        if (::coordinator.isInitialized) {
            coordinator.setEditorSyncSuppressed(true)
        }
        try {
            block()
        } finally {
            if (::coordinator.isInitialized) {
                coordinator.setEditorSyncSuppressed(previous)
            }
            suppressEditorComposingSync = previous
        }
    }

    private fun rebindPreservedInput(info: EditorInfo?) {
        coordinator.updateEditorInfoPreservingInput(info)
        val connection = currentInputConnection
        coordinator.bindInputConnection(connection)
        val before = orientationFieldBefore
        orientationFieldBefore = ComposingSpanRestore.FieldObservation.Unavailable
        val composing = coordinator.getComposingText()
        val action = if (connection == null) {
            if (composing.isEmpty()) {
                ComposingSpanRestore.Action.None
            } else {
                ComposingSpanRestore.Action.AbandonInternal
            }
        } else {
            ComposingSpanRestore.apply(connection, composing, before)
        }
        if (action == ComposingSpanRestore.Action.AbandonInternal) {
            withoutEditorComposingSync {
                coordinator.discardUnverifiedComposing()
            }
            resetConversionState()
            // 同じセッションの既存文字は残し、未確定の区間だけ終える。
            connection?.finishComposingText()
        }
        if (symbolKeyboardVisible) {
            showSymbolKeyboard()
        } else {
            showMainKeyboard()
        }
        refreshConversionUi()
        onKeyboardStateChanged(forceAllLabels = true)
    }

    private fun discardInputForUnverifiedTarget(info: EditorInfo?, restarting: Boolean) {
        conversionJob?.cancel()
        resetKeyboardViewState()
        withoutEditorComposingSync {
            coordinator.discardUnverifiedComposing()
            coordinator.applyEditorInfo(info)
        }
        coordinator.bindInputConnection(currentInputConnection)
        resetConversionState()
        if (restarting) {
            // 同じ入力欄の再開では、既存文字を残して未確定範囲だけ終える。
            currentInputConnection?.finishComposingText()
        }
    }

    private fun releasePressedKeys() {
        keyButtons.values.forEach { button -> button.isPressed = false }
        if (!::keyboardRootView.isInitialized) return
        SymbolPanel.keys.forEach { key ->
            keyboardRootView.findViewById<Button>(key.viewId)?.isPressed = false
        }
        keyboardRootView.findViewById<Button>(R.id.symbol_key_close)?.isPressed = false
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
        symbolKeyboardVisible = false
        if (::keyboardFlipper.isInitialized) {
            keyboardFlipper.displayedChild = INDEX_MAIN_KEYBOARD
        }
    }

    private fun showSymbolKeyboard() {
        symbolKeyboardVisible = true
        if (!::keyboardFlipper.isInitialized || !::keyboardRootView.isInitialized) return
        applySymbolCharacters(keyboardRootView, currentSymbolMode())
        keyboardFlipper.displayedChild = INDEX_SYMBOL_KEYBOARD
    }

    private fun currentSymbolMode(): InputMode {
        return if (::coordinator.isInitialized) coordinator.getInputMode() else InputMode.HIRAGANA
    }

    private fun applySymbolCharacters(root: View, mode: InputMode) {
        val characters = SymbolPanel.charactersFor(mode)
        symbolCharacters = characters
        characters.forEach { (viewId, symbol) ->
            root.findViewById<Button>(viewId).text = symbol
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
        // 横表示でも抽出（全画面）モードへ切り替えず、入力先の画面を残す。
        super.onEvaluateFullscreenMode()
        return false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        stopDeleteRepeat()
        releasePressedKeys()
        val orientationChanged = appliedOrientation != Configuration.ORIENTATION_UNDEFINED &&
            newConfig.orientation != appliedOrientation
        if (orientationChanged) {
            beginOrientationHandoff()
        }
        recreatingInputView = true
        try {
            super.onConfigurationChanged(newConfig)
        } finally {
            recreatingInputView = false
            appliedOrientation = newConfig.orientation
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        inputStartPairOpen = false
        beginInputStart(restarting)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        beginInputStart(restarting)
        try {
            if (!::coordinator.isInitialized) return
            if (orientationHandoff.consumeIfSameSession(inputSessionId, restarting)) {
                rebindPreservedInput(info)
                return
            }
            clearOrientationRestore()
            discardInputForUnverifiedTarget(info, restarting)
        } finally {
            completeInputStartPair()
        }
    }

    override fun onFinishInput() {
        inputStartPairOpen = false
        if (!recreatingInputView) {
            clearOrientationRestore()
        }
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopDeleteRepeat()
        releasePressedKeys()
        if (recreatingInputView || (!finishingInput && orientationHandoff.isPending)) {
            super.onFinishInputView(finishingInput)
            return
        }
        if (finishingInput && orientationHandoff.isPending) {
            clearOrientationRestore()
            if (::coordinator.isInitialized) {
                withoutEditorComposingSync {
                    coordinator.discardUnverifiedComposing()
                }
                resetConversionState()
            }
            super.onFinishInputView(finishingInput)
            return
        }
        if (::coordinator.isInitialized) {
            finalizeInputState()
            coordinator.resetInputSession()
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        stopDeleteRepeat()
        if (orientationHandoff.isPending && ::coordinator.isInitialized) {
            clearOrientationRestore()
            withoutEditorComposingSync {
                coordinator.discardUnverifiedComposing()
            }
            resetConversionState()
        }
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
    }
}
