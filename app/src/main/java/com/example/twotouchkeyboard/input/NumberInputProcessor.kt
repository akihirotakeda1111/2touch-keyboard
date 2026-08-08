package com.example.twotouchkeyboard.input

import com.example.twotouchkeyboard.KeyboardKey

class NumberInputProcessor(
    host: InputProcessorHost,
) : BaseInputProcessor(host) {

    override fun onKeyPressed(key: KeyboardKey) {
        val text = when (key) {
            is KeyboardKey.Digit -> key.number.toString()
            KeyboardKey.Zero -> "0"
            KeyboardKey.Hash -> "#"
            else -> return
        }
        host.commitDirectText(text)
    }

    override fun getKeyLabel(key: KeyboardKey): String {
        return when (key) {
            is KeyboardKey.Digit -> key.number.toString()
            KeyboardKey.Star -> "切替\n123"
            KeyboardKey.Zero -> "0"
            KeyboardKey.Hash -> "#"
            else -> super.getKeyLabel(key)
        }
    }
}
