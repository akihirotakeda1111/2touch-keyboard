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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TwoTouchKeyboardService : InputMethodService() {

    private lateinit var stateMachine: TwoTouchStateMachine
    private lateinit var candidateScroll: HorizontalScrollView
    private lateinit var candidateContainer: LinearLayout

    private val keyButtons: MutableMap<TwoTouchStateMachine.Key, Button> = mutableMapOf()
    private val conversionEngine = DummyConversionEngine()

    private val conversionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var conversionJob: Job? = null

    override fun onCreateInputView(): View {
        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)
        keyboardView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        candidateScroll = keyboardView.findViewById(R.id.candidate_scroll)
        candidateContainer = keyboardView.findViewById(R.id.candidate_container)

        stateMachine = TwoTouchStateMachine(
            listener = object : TwoTouchStateMachine.Listener {
                override fun onStateChanged(
                    state: TwoTouchStateMachine.State,
                    rowKey: Int?,
                ) {
                    updateKeyLabels()
                }

                override fun onComposingTextUpdated(composingText: String) {
                    updateComposingText(composingText)
                    requestConversion(composingText)
                }

                override fun onInputModeChanged(mode: InputMode) {
                    updateKeyLabels()
                }
            },
        )

        bindKey(keyboardView, R.id.key_1, TwoTouchStateMachine.Key.Digit(1))
        bindKey(keyboardView, R.id.key_2, TwoTouchStateMachine.Key.Digit(2))
        bindKey(keyboardView, R.id.key_3, TwoTouchStateMachine.Key.Digit(3))
        bindKey(keyboardView, R.id.key_4, TwoTouchStateMachine.Key.Digit(4))
        bindKey(keyboardView, R.id.key_5, TwoTouchStateMachine.Key.Digit(5))
        bindKey(keyboardView, R.id.key_6, TwoTouchStateMachine.Key.Digit(6))
        bindKey(keyboardView, R.id.key_7, TwoTouchStateMachine.Key.Digit(7))
        bindKey(keyboardView, R.id.key_8, TwoTouchStateMachine.Key.Digit(8))
        bindKey(keyboardView, R.id.key_9, TwoTouchStateMachine.Key.Digit(9))
        bindKey(keyboardView, R.id.key_star, TwoTouchStateMachine.Key.Star)
        bindKey(keyboardView, R.id.key_0, TwoTouchStateMachine.Key.Zero)
        bindKey(keyboardView, R.id.key_hash, TwoTouchStateMachine.Key.Hash)

        updateKeyLabels()
        return keyboardView
    }

    private fun bindKey(root: View, viewId: Int, key: TwoTouchStateMachine.Key) {
        val button = root.findViewById<Button>(viewId)
        keyButtons[key] = button
        button.setOnClickListener {
            if (key == TwoTouchStateMachine.Key.Star) {
                switchInputMode()
            } else {
                handleKeyPress(key)
            }
        }
    }

    private fun handleKeyPress(key: TwoTouchStateMachine.Key) {
        if (stateMachine.inputMode == InputMode.NUMBER) {
            handleNumberModeKey(key)
            return
        }
        stateMachine.onKeyPressed(key)
    }

    /** NUMBER モード: 1 タッチで即 commitText */
    private fun handleNumberModeKey(key: TwoTouchStateMachine.Key) {
        val text = when (key) {
            is TwoTouchStateMachine.Key.Digit -> key.number.toString()
            TwoTouchStateMachine.Key.Zero -> "0"
            TwoTouchStateMachine.Key.Hash -> "#"
            TwoTouchStateMachine.Key.Star -> return
        }
        currentInputConnection?.commitText(text, 1)
    }

    /**
     * モード切替: WAITING_VOWEL を破棄し、未確定文字列があれば強制確定してから巡回する。
     */
    private fun switchInputMode() {
        forceCommitComposingText()
        stateMachine.cycleInputMode()
        updateKeyLabels()
    }

    private fun forceCommitComposingText() {
        val composingText = stateMachine.getComposingText()
        if (composingText.isEmpty()) return
        currentInputConnection?.commitText(composingText, 1)
        stateMachine.clearComposingText()
        clearCandidateUi()
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
                conversionEngine.convert(input, stateMachine.inputMode)
            }
            if (input != stateMachine.getComposingText()) return@launch
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
        stateMachine.clearComposingText()
        clearCandidateUi()
    }

    /** 入力モード・ステートマシン状態に応じて全ボタンのラベルを再描画する */
    private fun updateKeyLabels() {
        keyButtons.forEach { (key, button) ->
            button.text = stateMachine.getKeyLabel(key)
        }
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
        if (::stateMachine.isInitialized) {
            conversionJob?.cancel()
            stateMachine.resetInputSession()
            clearCandidateUi()
            currentInputConnection?.finishComposingText()
        }
    }

    override fun onDestroy() {
        conversionScope.cancel()
        super.onDestroy()
    }
}
