package com.example.twotouchkeyboard.dictionary

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mozcengine.ConversionCandidate
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dictionaryStore by preferencesDataStore(name = "user_dictionary")

data class DictionaryEntry(val id: String, val reading: String, val value: String)

data class DictionarySnapshot(
    val entries: List<DictionaryEntry> = emptyList(),
) {
    fun candidatesFor(reading: String): List<ConversionCandidate> = entries
        .filter { it.reading == reading }
        .map { ConversionCandidate(it.value, it.reading) }
}

/** Separate from learned history: clearing learning never removes explicit registrations. */
class UserDictionaryRepository(context: Context) {
    private val store = context.applicationContext.dictionaryStore
    val snapshot = store.data.map { preferences ->
        DictionarySnapshot(
            decode(preferences[ENTRIES] ?: "[]"),
        )
    }

    suspend fun save(id: String?, reading: String, value: String) {
        require(validationError(reading, value) == null)
        val normalizedReading = reading.trim()
        val normalizedValue = value.trim()
        store.edit { preferences ->
            val entries = decode(preferences[ENTRIES] ?: "[]")
            require(entries.none {
                it.id != id && it.reading == normalizedReading && it.value == normalizedValue
            }) { "同じ読みと内容が登録されています。" }
            val entry = DictionaryEntry(id ?: UUID.randomUUID().toString(), normalizedReading, normalizedValue)
            val updated = if (id == null) entries + entry else {
                require(entries.any { it.id == id }) { "登録が見つかりません。" }
                entries.map { if (it.id == id) entry else it }
            }
            preferences[ENTRIES] = encode(updated)
        }
    }

    suspend fun delete(id: String) {
        store.edit { preferences ->
            preferences[ENTRIES] = encode(decode(preferences[ENTRIES] ?: "[]").filterNot { it.id == id })
        }
    }

    companion object {
        private val ENTRIES = stringPreferencesKey("entries_v1")

        fun validationError(reading: String, value: String): String? = when {
            !reading.trim().matches(Regex("[ぁ-ゖー]{1,64}")) ->
                "読みはひらがな・長音（ー）で1〜64文字入力してください。"
            value.trim().isEmpty() || value.trim().length > 1000 ->
                "単語・定型文は1〜1000文字入力してください。"
            else -> null
        }

        internal fun encode(entries: List<DictionaryEntry>): String = JSONArray().apply {
            entries.forEach { entry ->
                put(JSONObject().put("id", entry.id).put("reading", entry.reading).put("value", entry.value))
            }
        }.toString()

        internal fun decode(text: String): List<DictionaryEntry> {
            val array = JSONArray(text)
            return List(array.length()) { index ->
                val entry = array.getJSONObject(index)
                DictionaryEntry(entry.getString("id"), entry.getString("reading"), entry.getString("value"))
            }
        }
    }
}
