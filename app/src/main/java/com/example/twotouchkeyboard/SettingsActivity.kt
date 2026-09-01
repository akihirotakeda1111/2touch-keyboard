package com.example.twotouchkeyboard

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.lifecycleScope
import com.example.twotouchkeyboard.candidate.CandidateUsageHistoryManager
import com.example.twotouchkeyboard.update.InstalledAppVersion
import com.example.twotouchkeyboard.update.InstalledAppVersionProvider
import com.example.twotouchkeyboard.update.UpdateCheckResult
import com.example.twotouchkeyboard.update.UpdateDownloadFailure
import com.example.twotouchkeyboard.update.UpdateDownloadResult
import com.example.twotouchkeyboard.update.UpdateDownloader
import com.example.twotouchkeyboard.update.UpdateInstallFailure
import com.example.twotouchkeyboard.update.UpdateInstallResult
import com.example.twotouchkeyboard.update.UpdateInstaller
import com.example.twotouchkeyboard.update.UpdateMetadata
import com.example.twotouchkeyboard.update.UpdateRepository
import java.io.File
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val updateRepository by lazy { UpdateRepository() }
    private val updateDownloader by lazy { UpdateDownloader(applicationContext) }
    private val updateInstaller by lazy { UpdateInstaller(this) }
    private val installedAppVersionProvider by lazy {
        InstalledAppVersionProvider(applicationContext)
    }

    private var updateUiState by mutableStateOf<UpdateUiState>(UpdateUiState.Hidden)
    private var updateCheckStarted = false
    private var updatePromptDismissed = false
    private var pendingPermissionUpdate: PendingUpdate? = null

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val pendingUpdate = pendingPermissionUpdate ?: return@registerForActivityResult
        if (updateInstaller.canRequestPackageInstalls()) {
            continueInstallation(pendingUpdate, verifyAgain = true)
        } else {
            updateUiState = UpdateUiState.PermissionRequired(pendingUpdate)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updatePromptDismissed = savedInstanceState?.getBoolean(KEY_UPDATE_PROMPT_DISMISSED)
            ?: false
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                SettingsScreen(
                    repository = settingsRepository,
                    onOpenInputTry = {
                        startActivity(Intent(this, InputTryActivity::class.java))
                    },
                    onOpenImeSettings = {
                        ImeSetupNavigation.openInputMethodSettings(this)
                    },
                    onOpenImePicker = {
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showInputMethodPicker()
                    },
                    onClearCandidateUsageHistory = {
                        CandidateUsageHistoryManager.clearAll(applicationContext)
                    },
                )
                UpdateDialogs(
                    state = updateUiState,
                    onLater = ::dismissUpdatePrompt,
                    onUpdate = ::downloadUpdate,
                    onOpenInstallPermission = ::openInstallPermissionSettings,
                )
            }
        }

        checkForUpdates()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UPDATE_PROMPT_DISMISSED, updatePromptDismissed)
        super.onSaveInstanceState(outState)
    }

    private fun checkForUpdates() {
        if (updateCheckStarted || updatePromptDismissed) return
        updateCheckStarted = true
        lifecycleScope.launch {
            updateDownloader.cleanup()
            val installedVersion = runCatching { installedAppVersionProvider.get() }
                .getOrNull() ?: return@launch
            when (val result = updateRepository.checkForUpdate(installedVersion.versionCode)) {
                is UpdateCheckResult.Available -> {
                    updateUiState = UpdateUiState.Available(
                        metadata = result.metadata,
                        installedVersion = installedVersion,
                    )
                }

                UpdateCheckResult.UpToDate,
                is UpdateCheckResult.Failed,
                -> Unit
            }
        }
    }

    private fun downloadUpdate(metadata: UpdateMetadata, installedVersion: InstalledAppVersion) {
        updateUiState = UpdateUiState.Progress("APKをダウンロードしています…")
        lifecycleScope.launch {
            when (val result = updateDownloader.download(metadata)) {
                is UpdateDownloadResult.Downloaded -> {
                    val pendingUpdate = PendingUpdate(
                        metadata = metadata,
                        installedVersion = installedVersion,
                        apkFile = result.apkFile,
                    )
                    if (updateInstaller.canRequestPackageInstalls()) {
                        continueInstallation(pendingUpdate, verifyAgain = false)
                    } else {
                        pendingPermissionUpdate = pendingUpdate
                        updateUiState = UpdateUiState.PermissionRequired(pendingUpdate)
                    }
                }

                is UpdateDownloadResult.Failed -> {
                    updateUiState = UpdateUiState.Error(
                        metadata = metadata,
                        installedVersion = installedVersion,
                        message = result.reason.userMessage(),
                    )
                }
            }
        }
    }

    private fun openInstallPermissionSettings(pendingUpdate: PendingUpdate) {
        pendingPermissionUpdate = pendingUpdate
        if (updateInstaller.canRequestPackageInstalls()) {
            continueInstallation(pendingUpdate, verifyAgain = true)
            return
        }
        installPermissionLauncher.launch(updateInstaller.createInstallPermissionIntent())
    }

    private fun continueInstallation(pendingUpdate: PendingUpdate, verifyAgain: Boolean) {
        pendingPermissionUpdate = pendingUpdate
        updateUiState = UpdateUiState.Progress(
            if (verifyAgain) "APKを再検証しています…" else "インストーラーを準備しています…",
        )
        lifecycleScope.launch {
            if (verifyAgain &&
                !updateDownloader.verify(pendingUpdate.apkFile, pendingUpdate.metadata.sha256)
            ) {
                updateDownloader.delete(pendingUpdate.apkFile)
                pendingPermissionUpdate = null
                updateUiState = UpdateUiState.Error(
                    metadata = pendingUpdate.metadata,
                    installedVersion = pendingUpdate.installedVersion,
                    message = "APKのSHA-256が一致しません。ファイルを破棄しました。",
                )
                return@launch
            }

            when (val result = updateInstaller.install(
                pendingUpdate.apkFile,
                pendingUpdate.metadata,
            )) {
                UpdateInstallResult.Started -> {
                    pendingPermissionUpdate = null
                    updatePromptDismissed = true
                    updateUiState = UpdateUiState.Hidden
                }

                is UpdateInstallResult.Failed -> {
                    updateDownloader.delete(pendingUpdate.apkFile)
                    pendingPermissionUpdate = null
                    updateUiState = UpdateUiState.Error(
                        metadata = pendingUpdate.metadata,
                        installedVersion = pendingUpdate.installedVersion,
                        message = result.reason.userMessage(),
                    )
                }
            }
        }
    }

    private fun dismissUpdatePrompt() {
        val pendingApk = (updateUiState as? UpdateUiState.PermissionRequired)
            ?.pendingUpdate
            ?.apkFile
        pendingPermissionUpdate = null
        updatePromptDismissed = true
        updateUiState = UpdateUiState.Hidden
        if (pendingApk != null) {
            lifecycleScope.launch { updateDownloader.delete(pendingApk) }
        }
    }

    private companion object {
        const val KEY_UPDATE_PROMPT_DISMISSED = "update_prompt_dismissed"
    }
}

private data class PendingUpdate(
    val metadata: UpdateMetadata,
    val installedVersion: InstalledAppVersion,
    val apkFile: File,
)

private sealed interface UpdateUiState {
    data object Hidden : UpdateUiState
    data class Available(
        val metadata: UpdateMetadata,
        val installedVersion: InstalledAppVersion,
    ) : UpdateUiState

    data class Progress(val message: String) : UpdateUiState
    data class PermissionRequired(val pendingUpdate: PendingUpdate) : UpdateUiState
    data class Error(
        val metadata: UpdateMetadata,
        val installedVersion: InstalledAppVersion,
        val message: String,
    ) : UpdateUiState
}

@Composable
private fun UpdateDialogs(
    state: UpdateUiState,
    onLater: () -> Unit,
    onUpdate: (UpdateMetadata, InstalledAppVersion) -> Unit,
    onOpenInstallPermission: (PendingUpdate) -> Unit,
) {
    when (state) {
        UpdateUiState.Hidden -> Unit
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text("新しいバージョンがあります") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("現在: ${state.installedVersion.versionName}")
                    Text("最新: ${state.metadata.versionName}")
                }
            },
            dismissButton = {
                TextButton(onClick = onLater) { Text("あとで") }
            },
            confirmButton = {
                TextButton(
                    onClick = { onUpdate(state.metadata, state.installedVersion) },
                ) {
                    Text("更新する")
                }
            },
        )

        is UpdateUiState.Progress -> AlertDialog(
            onDismissRequest = {},
            title = { Text("アプリを更新") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(state.message)
                }
            },
            confirmButton = {},
        )

        is UpdateUiState.PermissionRequired -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text("インストールの許可が必要です") },
            text = {
                Text(
                    "Androidの設定で「この提供元のアプリを許可」を有効にしてください。" +
                        "設定から戻るとインストールを続行します。",
                )
            },
            dismissButton = {
                TextButton(onClick = onLater) { Text("あとで") }
            },
            confirmButton = {
                TextButton(
                    onClick = { onOpenInstallPermission(state.pendingUpdate) },
                ) {
                    Text("設定を開く")
                }
            },
        )

        is UpdateUiState.Error -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text("更新できませんでした") },
            text = { Text(state.message) },
            dismissButton = {
                TextButton(onClick = onLater) { Text("あとで") }
            },
            confirmButton = {
                TextButton(
                    onClick = { onUpdate(state.metadata, state.installedVersion) },
                ) {
                    Text("再試行")
                }
            },
        )
    }
}

private fun UpdateDownloadFailure.userMessage(): String = when (this) {
    UpdateDownloadFailure.NETWORK ->
        "APKのダウンロードに失敗しました。通信状態を確認して再試行してください。"

    UpdateDownloadFailure.HTTP ->
        "APKを取得できませんでした。しばらく待ってから再試行してください。"

    UpdateDownloadFailure.INTEGRITY ->
        "APKのSHA-256が一致しません。ファイルを破棄しました。"

    UpdateDownloadFailure.STORAGE ->
        "APKを端末内へ保存できませんでした。空き容量を確認してください。"
}

private fun UpdateInstallFailure.userMessage(): String = when (this) {
    UpdateInstallFailure.INVALID_APK ->
        "APKのアプリIDまたはversionCodeを確認できないため、インストールを中止しました。"

    UpdateInstallFailure.INSTALLER_UNAVAILABLE ->
        "AndroidのPackage Installerを起動できませんでした。"
}

@Composable
private fun SettingsScreen(
    repository: SettingsRepository,
    onOpenInputTry: () -> Unit,
    onOpenImeSettings: () -> Unit,
    onOpenImePicker: () -> Unit,
    onClearCandidateUsageHistory: () -> Unit,
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
    val candidateUsageLearningEnabled by repository.candidateUsageLearningEnabled.collectAsStateWithLifecycle(
        initialValue = SettingsRepository.DEFAULT_CANDIDATE_USAGE_LEARNING_ENABLED,
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

            Spacer(modifier = Modifier.height(24.dp))

            CandidateUsageLearningSection(
                enabled = candidateUsageLearningEnabled,
                onEnabledChange = { enabled ->
                    scope.launch { repository.setCandidateUsageLearningEnabled(enabled) }
                },
                onClearHistory = onClearCandidateUsageHistory,
            )

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "お試し入力",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "入力形式ごとに 2Touch Keyboard の動作を試せます。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onOpenInputTry,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("お試し入力を開く")
            }

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
private fun CandidateUsageLearningSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
) {
    Column {
        Text(
            text = "変換候補の学習",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "よく使う変換候補を優先表示します。日本語は Mozc の履歴、英字は端末内の使用回数を利用します。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "使用頻度を学習する",
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onClearHistory,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("学習した候補履歴を消去")
        }
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
