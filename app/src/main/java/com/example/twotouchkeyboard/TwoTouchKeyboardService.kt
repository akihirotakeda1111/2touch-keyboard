package com.example.twotouchkeyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button

class TwoTouchKeyboardService : InputMethodService() {

    private lateinit var stateMachine: TwoTouchStateMachine
    private val keyButtons: MutableMap<TwoTouchStateMachine.Key, Button> = mutableMapOf()

    override fun onCreateInputView(): View {
        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)
        keyboardView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        stateMachine = TwoTouchStateMachine(
            listener = object : TwoTouchStateMachine.Listener {
                override fun onStateChanged(
                    state: TwoTouchStateMachine.State,
                    rowKey: Int?,
                ) {
                    updateKeyLabels()
                }

                override fun onCharacterConfirmed(character: String) {
                    currentInputConnection?.commitText(character, 1)
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
            stateMachine.onKeyPressed(key)
        }
    }

    /** ステートマシンの状態に応じて全ボタンのテキストラベルを更新する */
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
            stateMachine.reset()
        }
    }
}
