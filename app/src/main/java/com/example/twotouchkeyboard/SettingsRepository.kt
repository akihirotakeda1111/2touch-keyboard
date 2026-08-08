package com.example.twotouchkeyboard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "keyboard_settings",
)

class SettingsRepository(
    private val context: Context,
) {
    val hiraganaInputMode: Flow<CharacterInputMethod> =
        context.settingsDataStore.data.map { preferences ->
            preferences[PREF_HIRAGANA_INPUT_MODE]?.toCharacterInputMethod()
                ?: CharacterInputMethod.TWOTOUCH
        }

    val alphabetInputMode: Flow<CharacterInputMethod> =
        context.settingsDataStore.data.map { preferences ->
            preferences[PREF_ALPHABET_INPUT_MODE]?.toCharacterInputMethod()
                ?: CharacterInputMethod.TOGGLE
        }

    val toggleAutoCommitTimeoutMs: Flow<Int> =
        context.settingsDataStore.data.map { preferences ->
            preferences[PREF_TOGGLE_AUTO_COMMIT_TIMEOUT_MS]
                ?: DEFAULT_TOGGLE_AUTO_COMMIT_TIMEOUT_MS
        }

    suspend fun setHiraganaInputMode(method: CharacterInputMethod) {
        context.settingsDataStore.edit { preferences ->
            preferences[PREF_HIRAGANA_INPUT_MODE] = method.name
        }
    }

    suspend fun setAlphabetInputMode(method: CharacterInputMethod) {
        context.settingsDataStore.edit { preferences ->
            preferences[PREF_ALPHABET_INPUT_MODE] = method.name
        }
    }

    suspend fun setToggleAutoCommitTimeoutMs(timeoutMs: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[PREF_TOGGLE_AUTO_COMMIT_TIMEOUT_MS] = timeoutMs.coerceAtLeast(0)
        }
    }

    companion object {
        val PREF_HIRAGANA_INPUT_MODE = stringPreferencesKey("pref_hiragana_input_mode")
        val PREF_ALPHABET_INPUT_MODE = stringPreferencesKey("pref_alphabet_input_mode")
        val PREF_TOGGLE_AUTO_COMMIT_TIMEOUT_MS =
            intPreferencesKey("pref_toggle_auto_commit_timeout_ms")

        const val DEFAULT_TOGGLE_AUTO_COMMIT_TIMEOUT_MS = 300

        private fun String.toCharacterInputMethod(): CharacterInputMethod {
            return runCatching { CharacterInputMethod.valueOf(this) }
                .getOrDefault(CharacterInputMethod.TWOTOUCH)
        }
    }
}
