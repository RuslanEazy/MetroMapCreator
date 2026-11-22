package com.example.metromap

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sign

const val GRID = 40f
const val LINE_W = 10f
const val STATION_R = 13f

class MapPalette(val background: Color, val text: Color, val grid: Color)

val LightPalette = MapPalette(Color.White, Color(0xFF1B1B1F), Color(0x22000000))

/** Геометрия перегона: ломаная a -> излом -> b (углы только 0° / 45° / 90°). */
class LinkGeometry(val link: Link, val pts: List<Offset>)

fun computeGeometry(map: MetroMap): List<LinkGeometry> {
    val byId = map.stations.associateBy { it.id }
    val groups = map.links.groupBy { minOf(it.a, it.b) to maxOf(it.a, it.b) }
    val out = ArrayList<LinkGeometry>()
    for ((key, group) in groups) {
        val s = byId[key.first] ?: continue
        val e = byId[key.second] ?: continue
        val dx = e.x - s.x
        val dy = e.y - s.y
        val len = hypot(dx, dy).coerceAtLeast(1f)
        val px = -dy / len
        val py = dx / len
        val a = Offset(s.x, s.y)
        val b = Offset(e.x, e.y)
        val adx = abs(dx)
        val ady = abs(dy)
        val mid = if (adx >= ady) {
            Offset(a.x + sign(dx) * (adx - ady), a.y)
        } else {
            Offset(a.x, a.y + sign(dy) * (ady - adx))
        }
        group.forEachIndexed { i, link ->
            // несколько линий на одном перегоне рисуем параллельно
            val k = (i - (group.size - 1) / 2f) * (LINE_W + 2f)
            val sh = Offset(px * k, py * k)
            out += LinkGeometry(link, listOf(a + sh, mid + sh, b + sh))
        }
    }
    return out
}

fun DrawScope.drawGrid(color: Color, zoom: Float, offset: Offset) {
    if (zoom < 0.6f) return
    val x0 = floor(-offset.x / zoom / GRID).toInt()
    val x1 = ceil((size.width - offset.x) / zoom / GRID).toInt()
    val y0 = floor(-offset.y / zoom / GRID).toInt()
    val y1 = ceil((size.height - offset.y) / zoom / GRID).toInt()
    for (i in x0..x1) {
        for (j in y0..y1) {
            drawCircle(color, 2.5f, Offset(i * GRID, j * GRID))
        }
    }
}

/** Рисует схему в мировых координатах (трансформацию камеры задаёт вызывающий). */
fun DrawScope.drawMetroMap(
    map: MetroMap,
    tm: TextMeasurer,
    palette: MapPalette,
    selected: Int? = null,
) {
    val lineColor = map.lines.associate { it.id to Color(it.color) }
    val style = Stroke(width = LINE_W, cap = StrokeCap.Round, join = StrokeJoin.Round)

    for (g in computeGeometry(map)) {
        val path = Path().apply {
            moveTo(g.pts[0].x, g.pts[0].y)
            lineTo(g.pts[1].x, g.pts[1].y)
            lineTo(g.pts[2].x, g.pts[2].y)
        }
        drawPath(path, lineColor[g.link.lineId] ?: Color.Gray, style = style)
    }

    val labelStyle = TextStyle(color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    for (s in map.stations) {
        val ids = map.links.filter { it.a == s.id || it.b == s.id }.map { it.lineId }.distinct()
        val c = Offset(s.x, s.y)
        if (ids.size > 1) {
            // пересадочный узел: чёрное кольцо
            drawCircle(Color(0xFF222222), STATION_R + 3f, c)
            drawCircle(Color.White, STATION_R - 1.5f, c)
        } else {
            drawCircle(ids.firstOrNull()?.let { lineColor[it] } ?: Color.Gray, STATION_R, c)
            drawCircle(Color.White, STATION_R - 4.5f, c)
        }
        if (s.id == selected) {
            drawCircle(Color(0xFFFFB300), STATION_R + 9f, c, style = Stroke(width = 4f))
        }
        val layout = tm.measure(s.name, labelStyle)
        drawText(layout, topLeft = Offset(s.x + STATION_R + 10f, s.y - layout.size.height / 2f))
    }
}

// ---------- hit-testing ----------

fun hitStation(map: MetroMap, p: Offset, radius: Float): Station? =
    map.stations
        .map { it to hypot(it.x - p.x, it.y - p.y) }
        .filter { it.second <= radius }
        .minByOrNull { it.second }
        ?.first

private fun distToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val l2 = ab.x * ab.x + ab.y * ab.y
    if (l2 == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / l2).coerceIn(0f, 1f)
    return (p - (a + ab * t)).getDistance()
}

fun hitLink(map: MetroMap, p: Offset, radius: Float): Link? =
    computeGeometry(map)
        .map { it.link to minOf(distToSegment(p, it.pts[0], it.pts[1]), distToSegment(p, it.pts[1], it.pts[2])) }
        .filter { it.second <= radius }
        .minByOrNull { it.second }
        ?.first
