package com.example.twotouchkeyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                SettingsScreen(
                    repository = settingsRepository,
                    onOpenImeSettings = {
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    onOpenImePicker = {
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showInputMethodPicker()
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    repository: SettingsRepository,
    onOpenImeSettings: () -> Unit,
    onOpenImePicker: () -> Unit,
) {
    val hiraganaMode by repository.hiraganaInputMode.collectAsStateWithLifecycle(
        initialValue = CharacterInputMethod.TWOTOUCH,
    )
    val alphabetMode by repository.alphabetInputMode.collectAsStateWithLifecycle(
        initialValue = CharacterInputMethod.TOGGLE,
    )
    val toggleAutoCommitTimeoutMs by repository.toggleAutoCommitTimeoutMs.collectAsStateWithLifecycle(
        initialValue = SettingsRepository.DEFAULT_TOGGLE_AUTO_COMMIT_TIMEOUT_MS,
    )
    val scope = rememberCoroutineScope()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "キーボード設定",
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(24.dp))

            InputMethodSection(
                title = "ひらがな入力方式",
                selected = hiraganaMode,
                onSelected = { method ->
                    scope.launch { repository.setHiraganaInputMode(method) }
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            InputMethodSection(
                title = "英字入力方式",
                selected = alphabetMode,
                onSelected = { method ->
                    scope.launch { repository.setAlphabetInputMode(method) }
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            ToggleAutoCommitTimeoutSection(
                timeoutMs = toggleAutoCommitTimeoutMs,
                onTimeoutChange = { timeoutMs ->
                    scope.launch { repository.setToggleAutoCommitTimeoutMs(timeoutMs) }
                },
            )

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "キーボードの有効化",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "2Touch Keyboard を使うには、システム設定でキーボードを有効化してください。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onOpenImeSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("キーボード設定を開く")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onOpenImePicker,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("キーボードを切り替える")
            }
        }
    }
}

@Composable
private fun InputMethodSection(
    title: String,
    selected: CharacterInputMethod,
    onSelected: (CharacterInputMethod) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        InputMethodOption(
            label = "2タッチ入力",
            value = CharacterInputMethod.TWOTOUCH,
            selected = selected,
            onSelected = onSelected,
        )
        InputMethodOption(
            label = "ケータイ打ち（トグル）",
            value = CharacterInputMethod.TOGGLE,
            selected = selected,
            onSelected = onSelected,
        )
    }
}

@Composable
private fun ToggleAutoCommitTimeoutSection(
    timeoutMs: Int,
    onTimeoutChange: (Int) -> Unit,
) {
    var text by remember(timeoutMs) { mutableStateOf(timeoutMs.toString()) }

    Column {
        Text(
            text = "トグル入力の自動確定",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "ケータイ打ち（トグル）で文字選択後、入力が止まってから自動で確定するまでの時間です。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                val filtered = input.filter { it.isDigit() }
                text = filtered
                if (filtered.isNotEmpty()) {
                    onTimeoutChange(filtered.toInt())
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("タイムアウト (ms)") },
            supportingText = {
                Text("0で無効。デフォルト: ${SettingsRepository.DEFAULT_TOGGLE_AUTO_COMMIT_TIMEOUT_MS}ms")
            },
            singleLine = true,
        )
    }
}

@Composable
private fun InputMethodOption(
    label: String,
    value: CharacterInputMethod,
    selected: CharacterInputMethod,
    onSelected: (CharacterInputMethod) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected == value,
                onClick = { onSelected(value) },
                role = Role.RadioButton,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(
            selected = selected == value,
            onClick = null,
        )
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
