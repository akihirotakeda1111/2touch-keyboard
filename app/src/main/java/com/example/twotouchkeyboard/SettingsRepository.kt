package com.example.twotouchkeyboard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

    companion object {
        val PREF_HIRAGANA_INPUT_MODE = stringPreferencesKey("pref_hiragana_input_mode")
        val PREF_ALPHABET_INPUT_MODE = stringPreferencesKey("pref_alphabet_input_mode")

        private fun String.toCharacterInputMethod(): CharacterInputMethod {
            return runCatching { CharacterInputMethod.valueOf(this) }
                .getOrDefault(CharacterInputMethod.TWOTOUCH)
        }
    }
}
