package com.example.metromap

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MetroTheme { MetroApp() }
        }
    }
}

@Composable
fun MetroTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** line == null — создаём новую линию. */
private class LineEdit(val line: MetroLine?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetroApp(vm: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tm = rememberTextMeasurer(cacheSize = 128)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var menuOpen by remember { mutableStateOf(false) }
    var editStationId by remember { mutableStateOf<Int?>(null) }
    var lineEdit by remember { mutableStateOf<LineEdit?>(null) }
    var renameMapDialog by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }

    fun say(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    val saveJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(vm.map.toJson().toByteArray()) }
            }.isSuccess
            say(if (ok) "Схема сохранена" else "Не удалось сохранить файл")
        }
    }

    val openJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val ok = text != null && vm.loadFromJson(text)
            say(if (ok) "Схема открыта" else "Не удалось открыть файл")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.map.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = { vm.undo() }, enabled = vm.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Отменить")
                    }
                    IconButton(onClick = { vm.redo() }, enabled = vm.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Повторить")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Переименовать схему") },
                                onClick = { menuOpen = false; renameMapDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Новая схема") },
                                onClick = { menuOpen = false; confirmNew = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Открыть из файла (JSON)") },
                                onClick = { menuOpen = false; openJson.launch(arrayOf("*/*")) },
                            )
                            DropdownMenuItem(
                                text = { Text("Сохранить в файл (JSON)") },
                                onClick = { menuOpen = false; saveJson.launch("${vm.map.name}.json") },
                            )
                            DropdownMenuItem(
                                text = { Text("Экспорт в PNG (галерея)") },
                                onClick = {
                                    menuOpen = false
                                    val bmp = renderMapBitmap(vm.map, tm, density)
                                    if (bmp == null) {
                                        say("Схема пуста — нечего экспортировать")
                                    } else {
                                        val ok = saveToGallery(context, bmp, vm.map.name)
                                        say(if (ok) "PNG сохранён в Pictures/MetroMaps" else "Не удалось сохранить PNG")
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Привязка к сетке") },
                                trailingIcon = { if (vm.snap) Icon(Icons.Default.Check, null) },
                                onClick = { vm.snap = !vm.snap },
                            )
                            DropdownMenuItem(
                                text = { Text("Автосоединение в «Станция»") },
                                trailingIcon = { if (vm.autoLink) Icon(Icons.Default.Check, null) },
                                onClick = { vm.autoLink = !vm.autoLink; vm.selectedStation = null },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            BottomPanel(vm, onEditLine = { lineEdit = LineEdit(it) })
        },
    ) { inner ->
        Box(Modifier.padding(inner)) {
            MapCanvas(vm, onEditStation = { editStationId = it })
        }
    }

    // ----- диалоги -----

    val station = editStationId?.let { id -> vm.map.stations.find { it.id == id } }
    if (station != null) {
        StationDialog(
            station = station,
            onSave = { vm.renameStation(station.id, it); editStationId = null },
            onDelete = { vm.deleteStation(station.id); editStationId = null },
            onDismiss = { editStationId = null },
        )
    }

    val le = lineEdit
    if (le != null) {
        LineDialog(
            line = le.line,
            initialColor = le.line?.color ?: LINE_COLORS[vm.map.lines.size % LINE_COLORS.size],
            canDelete = vm.map.lines.size > 1,
            onSave = { name, color ->
                if (le.line == null) {
                    vm.addLine(name.ifBlank { "Линия ${vm.map.lines.size + 1}" }, color)
                } else {
                    vm.updateLine(le.line.id, name.ifBlank { le.line.name }, color)
                }
                lineEdit = null
            },
            onDelete = { le.line?.let { vm.deleteLine(it.id) }; lineEdit = null },
            onDismiss = { lineEdit = null },
        )
    }

    if (renameMapDialog) {
        TextDialog(
            title = "Название схемы",
            initial = vm.map.name,
            onConfirm = { if (it.isNotBlank()) vm.renameMap(it.trim()); renameMapDialog = false },
            onDismiss = { renameMapDialog = false },
        )
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("Новая схема?") },
            text = { Text("Текущая схема будет очищена. Это действие можно отменить кнопкой «Отменить».") },
            confirmButton = { TextButton(onClick = { vm.newMap(); confirmNew = false }) { Text("Создать") } },
            dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun BottomPanel(vm: MapViewModel, onEditLine: (MetroLine?) -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.map.lines, key = { it.id }) { line ->
                    val active = line.id == vm.activeLineId
                    FilterChip(
                        selected = active,
                        // повторный тап по активной линии — редактирование
                        onClick = { if (active) onEditLine(line) else vm.selectLine(line.id) },
                        label = { Text(line.name, maxLines = 1) },
                        leadingIcon = {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(line.color)))
                        },
                    )
                }
                item {
                    AssistChip(
                        onClick = { onEditLine(null) },
                        label = { Text("Линия") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tool.entries.forEach { t ->
                    FilterChip(
                        selected = vm.tool == t,
                        onClick = { vm.selectTool(t) },
                        label = { Text(t.label, maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StationDialog(
    station: Station,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(station.id) { mutableStateOf(station.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Станция") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim().ifEmpty { station.name }) }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Удалить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun LineDialog(
    line: MetroLine?,
    initialColor: Int,
    canDelete: Boolean,
    onSave: (String, Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(line?.name ?: "") }
    var color by remember { mutableIntStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (line == null) "Новая линия" else "Линия") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LINE_COLORS.forEach { c -> ColorDot(c, c == color) { color = c } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), color) }) { Text("Готово") } },
        dismissButton = {
            Row {
                if (line != null && canDelete) TextButton(onClick = onDelete) { Text("Удалить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun ColorDot(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(color))
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun TextDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
