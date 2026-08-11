package com.example.twotouchkeyboard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

internal object ImeSetupNavigation {

    fun openInputMethodSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
