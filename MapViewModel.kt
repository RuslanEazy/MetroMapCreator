package com.example.metromap

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File
import kotlin.math.roundToInt

class MapViewModel(app: Application) : AndroidViewModel(app) {

    private val file = File(app.filesDir, "autosave.json")

    var map by mutableStateOf(loadSaved())
        private set
    var activeLineId by mutableIntStateOf(map.lines.first().id)
        private set
    var tool by mutableStateOf(Tool.ADD)
        private set
    var selectedStation by mutableStateOf<Int?>(null)
    var snap by mutableStateOf(true)
    var autoLink by mutableStateOf(true)

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val undoStack = ArrayDeque<MetroMap>()
    private val redoStack = ArrayDeque<MetroMap>()

    // ---------- хранение ----------

    private fun loadSaved(): MetroMap = runCatching {
        if (file.exists()) metroMapFromJson(file.readText()) else MetroMap()
    }.getOrDefault(MetroMap())

    private fun persist() {
        runCatching { file.writeText(map.toJson()) }
    }

    // ---------- история ----------

    private fun refreshHistory() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    /** Запомнить текущее состояние для «Отменить» (вызывается перед изменением). */
    fun checkpoint() {
        undoStack.addLast(map)
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        refreshHistory()
    }

    private fun commit(newMap: MetroMap) {
        checkpoint()
        map = newMap
        persist()
    }

    private fun fixSelection() {
        if (map.lines.none { it.id == activeLineId }) activeLineId = map.lines.first().id
        if (selectedStation != null && map.stations.none { it.id == selectedStation }) selectedStation = null
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(map)
        map = prev
        fixSelection()
        persist()
        refreshHistory()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(map)
        map = next
        fixSelection()
        persist()
        refreshHistory()
    }

    // ---------- инструменты ----------

    fun selectTool(t: Tool) {
        tool = t
        selectedStation = null
    }

    fun selectLine(id: Int) {
        activeLineId = id
    }

    private fun snapV(v: Float): Float = if (snap) (v / GRID).roundToInt() * GRID else v

    // ---------- станции ----------

    fun addStation(x: Float, y: Float, linkFrom: Int?): Int {
        val id = map.nextId
        val station = Station(id, "Станция ${map.stations.size + 1}", snapV(x), snapV(y))
        val links = if (linkFrom != null && map.stations.any { it.id == linkFrom }) {
            map.links + Link(activeLineId, linkFrom, id)
        } else {
            map.links
        }
        commit(map.copy(stations = map.stations + station, links = links, nextId = id + 1))
        return id
    }

    fun renameStation(id: Int, name: String) {
        commit(map.copy(stations = map.stations.map { if (it.id == id) it.copy(name = name) else it }))
    }

    /** Перетаскивание: история пишется один раз через [checkpoint] в начале жеста. */
    fun moveStation(id: Int, x: Float, y: Float) {
        map = map.copy(
            stations = map.stations.map { if (it.id == id) it.copy(x = snapV(x), y = snapV(y)) else it }
        )
    }

    fun endDrag() = persist()

    fun deleteStation(id: Int) {
        commit(
            map.copy(
                stations = map.stations.filterNot { it.id == id },
                links = map.links.filterNot { it.a == id || it.b == id },
            )
        )
        if (selectedStation == id) selectedStation = null
    }

    // ---------- перегоны ----------

    fun connect(a: Int, b: Int) {
        if (a == b) return
        val exists = map.links.any {
            it.lineId == activeLineId && ((it.a == a && it.b == b) || (it.a == b && it.b == a))
        }
        if (exists) return
        commit(map.copy(links = map.links + Link(activeLineId, a, b)))
    }

    fun deleteLink(link: Link) {
        commit(map.copy(links = map.links - link))
    }

    // ---------- линии ----------

    fun addLine(name: String, color: Int) {
        val id = map.nextId
        commit(map.copy(lines = map.lines + MetroLine(id, name, color), nextId = id + 1))
        activeLineId = id
    }

    fun updateLine(id: Int, name: String, color: Int) {
        commit(map.copy(lines = map.lines.map { if (it.id == id) it.copy(name = name, color = color) else it }))
    }

    fun deleteLine(id: Int) {
        if (map.lines.size <= 1) return
        commit(
            map.copy(
                lines = map.lines.filterNot { it.id == id },
                links = map.links.filterNot { it.lineId == id },
            )
        )
        fixSelection()
    }

    // ---------- схема целиком ----------

    fun renameMap(name: String) {
        commit(map.copy(name = name))
    }

    fun newMap() {
        commit(MetroMap())
        activeLineId = 1
        selectedStation = null
    }

    fun loadFromJson(text: String): Boolean {
        val parsed = runCatching { metroMapFromJson(text) }.getOrNull() ?: return false
        commit(parsed)
        activeLineId = parsed.lines.first().id
        selectedStation = null
        return true
    }
}
