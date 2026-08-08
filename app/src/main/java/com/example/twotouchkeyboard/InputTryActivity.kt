package com.example.twotouchkeyboard

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class InputTryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                InputTryScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputTryScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("お試し入力") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("戻る")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "各入力欄をタップして 2Touch Keyboard の動作を確認できます。",
                style = MaterialTheme.typography.bodyMedium,
            )

            InputTryScenarios.all.forEachIndexed { index, scenario ->
                if (index > 0) {
                    HorizontalDivider()
                }
                InputTryField(scenario = scenario)
            }
        }
    }
}

@Composable
private fun InputTryField(scenario: InputTryScenarios.Scenario) {
    var text by remember(scenario.title) { mutableStateOf("") }
    val profile = remember(scenario.inputType) {
        InputFieldProfileResolver.resolve(
            EditorInfo().apply { inputType = scenario.inputType },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = scenario.title,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = scenario.description,
            style = MaterialTheme.typography.bodyMedium,
        )
        ProfileSummary(profile = profile)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(scenario.hint) },
            keyboardOptions = scenario.keyboardOptions,
            visualTransformation = scenario.visualTransformation,
            singleLine = true,
        )
    }
}

@Composable
private fun ProfileSummary(profile: InputFieldProfile) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "IME 動作: 初期モード ${profile.preferredMode.label()} / " +
                "変換 ${if (profile.conversionEnabled) "有効" else "無効"} / " +
                "モード切替 ${if (profile.modeSwitchEnabled) "可" else "不可"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun InputMode.label(): String = when (this) {
    InputMode.HIRAGANA -> "ひらがな"
    InputMode.ALPHABET -> "英字"
    InputMode.NUMBER -> "数字"
}
