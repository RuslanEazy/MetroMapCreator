package com.example.metromap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun MapCanvas(
    vm: MapViewModel,
    onEditStation: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer(cacheSize = 128)
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset(80f, 160f)) }
    val scheme = MaterialTheme.colorScheme
    val palette = MapPalette(scheme.surface, scheme.onSurface, scheme.onSurface.copy(alpha = 0.18f))
    val editCallback by rememberUpdatedState(onEditStation)

    fun toWorld(p: Offset): Offset = (p - offset) / zoom

    fun handleTap(w: Offset, radius: Float) {
        val hit = hitStation(vm.map, w, radius)
        when (vm.tool) {
            Tool.ADD -> {
                if (hit != null) {
                    // тап по существующей станции: начать/прервать цепочку отсюда
                    vm.selectedStation = if (vm.selectedStation == hit.id) null else hit.id
                } else {
                    val from = if (vm.autoLink) vm.selectedStation else null
                    val newId = vm.addStation(w.x, w.y, from)
                    vm.selectedStation = if (vm.autoLink) newId else null
                }
            }
            Tool.CONNECT -> {
                if (hit == null) {
                    vm.selectedStation = null
                } else {
                    val sel = vm.selectedStation
                    if (sel != null && sel != hit.id) vm.connect(sel, hit.id)
                    vm.selectedStation = hit.id
                }
            }
            Tool.MOVE -> if (hit != null) editCallback(hit.id)
            Tool.ERASE -> {
                if (hit != null) {
                    vm.deleteStation(hit.id)
                } else {
                    hitLink(vm.map, w, radius)?.let { vm.deleteLink(it) }
                }
            }
        }
    }

    Box(modifier.fillMaxSize().background(palette.background)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val hitRadius = { max(24.dp.toPx() / zoom, STATION_R) }
                        var dragId: Int? = null
                        if (vm.tool == Tool.MOVE) {
                            dragId = hitStation(vm.map, toWorld(down.position), hitRadius())?.id
                        }
                        var moved = false
                        var checkpointed = false

                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size >= 2) {
                                // два пальца: масштаб + панорама
                                val z = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid()
                                if (centroid.isSpecified) {
                                    val newZoom = (zoom * z).coerceIn(0.25f, 4f)
                                    offset = centroid - (centroid - offset) * (newZoom / zoom) + pan
                                    zoom = newZoom
                                }
                                moved = true
                                dragId = null
                                event.changes.forEach { it.consume() }
                            } else {
                                val change = event.changes.first()
                                if (!moved && (change.position - down.position).getDistance() > slop) {
                                    moved = true
                                }
                                if (moved && change.pressed) {
                                    val id = dragId
                                    if (id != null) {
                                        if (!checkpointed) {
                                            vm.checkpoint()
                                            checkpointed = true
                                        }
                                        val w = toWorld(change.position)
                                        vm.moveStation(id, w.x, w.y)
                                    } else {
                                        offset += change.positionChange()
                                    }
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (checkpointed) vm.endDrag()
                        if (!moved) handleTap(toWorld(down.position), hitRadius())
                    }
                }
        ) {
            withTransform({
                translate(offset.x, offset.y)
                scale(zoom, zoom, pivot = Offset.Zero)
            }) {
                if (vm.snap) drawGrid(palette.grid, zoom, offset)
                drawMetroMap(vm.map, tm, palette, vm.selectedStation)
            }
        }

        if (vm.map.stations.isEmpty()) {
            Text(
                text = "Выберите линию, оставьте инструмент «Станция»\nи касайтесь экрана — станции будут\nсоединяться цепочкой",
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
