package com.example.twotouchkeyboard.dictionary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun UserDictionarySection() {
    val context = LocalContext.current
    val repository = remember { UserDictionaryRepository(context) }
    val snapshot by repository.snapshot.collectAsStateWithLifecycle(initialValue = DictionarySnapshot())
    val scope = rememberCoroutineScope()
    var showList by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var entryId by rememberSaveable { mutableStateOf<String?>(null) }
    var reading by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun perform(action: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = if (failure is IllegalArgumentException) failure.message else "保存できませんでした。再度お試しください。"
            } finally {
                busy = false
            }
        }
    }

    Text("単語・定型文", style = MaterialTheme.typography.titleMedium)
    Text("登録した読みと一致すると、変換候補に表示します。学習履歴を消去しても登録は残ります。")
    Button(onClick = { showList = true }, modifier = Modifier.fillMaxWidth()) {
        Text("ユーザー辞書（${snapshot.entries.size}件）")
    }
    if (!showList) error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    if (showList && !editing && deleteId == null && detailId == null) {
        val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
        Dialog(
            onDismissRequest = { if (!busy) showList = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f).heightIn(max = maxHeight),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ユーザー辞書", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Text("${snapshot.entries.size}件", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(16.dp))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
                        Text("読み", modifier = Modifier.weight(0.3f), style = MaterialTheme.typography.labelMedium)
                        Text("単語・定型文", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(48.dp))
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    if (snapshot.entries.isEmpty()) {
                        Text("よく使う単語や定型文を登録できます。", modifier = Modifier.padding(vertical = 24.dp))
                    }
                    LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false)) {
                        items(snapshot.entries, key = { it.id }) { entry ->
                            DictionaryListRow(
                                entry = entry,
                                onDetails = { detailId = entry.id },
                                onEdit = {
                                    entryId = entry.id; reading = entry.reading; value = entry.value
                                    error = null; editing = true
                                },
                                onDelete = { error = null; deleteId = entry.id },
                            )
                            HorizontalDivider()
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showList = false }) { Text("閉じる") }
                        TextButton(onClick = {
                            entryId = null; reading = ""; value = ""; error = null; editing = true
                        }) { Text("追加") }
                    }
                }
            }
        }
    }
    detailId?.let { id ->
        val entry = snapshot.entries.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { detailId = null },
            title = { Text("登録内容") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("読み", style = MaterialTheme.typography.labelLarge)
                    Text(entry?.reading.orEmpty())
                    Text("単語・定型文", style = MaterialTheme.typography.labelLarge)
                    Text(entry?.value ?: "登録が見つかりません。")
                }
            },
            confirmButton = { TextButton(onClick = { detailId = null }) { Text("一覧に戻る") } },
        )
    }
    if (editing) {
        val validation = UserDictionaryRepository.validationError(reading, value)
        AlertDialog(
            onDismissRequest = { if (!busy) editing = false },
            title = { Text(if (entryId == null) "単語・定型文を追加" else "登録内容を編集") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = reading, onValueChange = { reading = it }, enabled = !busy,
                        label = { Text("読み（ひらがな）") }, singleLine = true,
                    )
                    OutlinedTextField(
                        value = value, onValueChange = { value = it }, enabled = !busy,
                        label = { Text("単語・定型文") }, minLines = 2, maxLines = 5,
                    )
                    Text(validation ?: "読みが完全一致したとき、優先して表示します。")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && validation == null, onClick = {
                    perform { repository.save(entryId, reading, value); editing = false }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { editing = false; error = null }) { Text("キャンセル") }
            },
        )
    }
    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!busy) deleteId = null },
            title = { Text("登録を削除しますか？") },
            text = { Text(error ?: snapshot.entries.firstOrNull { it.id == id }?.value.orEmpty()) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    perform { repository.delete(id); deleteId = null }
                }) { Text("削除") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleteId = null }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun DictionaryListRow(
    entry: DictionaryEntry,
    onDetails: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "登録内容を表示", onClick = onDetails)
            .heightIn(min = 56.dp).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.reading, modifier = Modifier.weight(0.3f).padding(end = 8.dp),
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            entry.value, modifier = Modifier.weight(0.7f).padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Box(modifier = Modifier.width(48.dp)) {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.semantics { contentDescription = "${entry.reading}の操作" },
            ) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text("編集") }, onClick = { menuExpanded = false; onEdit() })
                DropdownMenuItem(text = { Text("削除") }, onClick = { menuExpanded = false; onDelete() })
            }
        }
    }
}
