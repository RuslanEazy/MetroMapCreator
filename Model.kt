package com.example.metromap

import org.json.JSONArray
import org.json.JSONObject

enum class Tool(val label: String) {
    ADD("Станция"),
    CONNECT("Связь"),
    MOVE("Двигать"),
    ERASE("Ластик"),
}

data class Station(val id: Int, val name: String, val x: Float, val y: Float)

data class MetroLine(val id: Int, val name: String, val color: Int)

/** Перегон: участок линии [lineId] между станциями [a] и [b]. */
data class Link(val lineId: Int, val a: Int, val b: Int)

val LINE_COLORS: List<Int> = listOf(
    0xFFE53935, // красный
    0xFF1E88E5, // синий
    0xFF43A047, // зелёный
    0xFFFB8C00, // оранжевый
    0xFF8E24AA, // фиолетовый
    0xFF00ACC1, // бирюзовый
    0xFFFDD835, // жёлтый
    0xFF6D4C41, // коричневый
    0xFFEC407A, // розовый
    0xFF7CB342, // салатовый
    0xFF546E7A, // серо-синий
    0xFF212121, // чёрный
).map { it.toInt() }

data class MetroMap(
    val name: String = "Новая схема",
    val stations: List<Station> = emptyList(),
    val lines: List<MetroLine> = listOf(MetroLine(1, "Линия 1", LINE_COLORS[0])),
    val links: List<Link> = emptyList(),
    val nextId: Int = 2,
)

// ---------- JSON ----------

fun MetroMap.toJson(): String {
    val root = JSONObject()
    root.put("version", 1)
    root.put("name", name)
    root.put("nextId", nextId)
    val linesJson = JSONArray()
    lines.forEach { linesJson.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) }
    root.put("lines", linesJson)
    val stationsJson = JSONArray()
    stations.forEach {
        stationsJson.put(
            JSONObject().put("id", it.id).put("name", it.name)
                .put("x", it.x.toDouble()).put("y", it.y.toDouble())
        )
    }
    root.put("stations", stationsJson)
    val linksJson = JSONArray()
    links.forEach { linksJson.put(JSONObject().put("line", it.lineId).put("a", it.a).put("b", it.b)) }
    root.put("links", linksJson)
    return root.toString(2)
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

fun metroMapFromJson(text: String): MetroMap {
    val o = JSONObject(text)
    val lines = o.getJSONArray("lines").objects()
        .map { MetroLine(it.getInt("id"), it.getString("name"), it.getInt("color")) }
        .ifEmpty { listOf(MetroLine(1, "Линия 1", LINE_COLORS[0])) }
    val stations = o.getJSONArray("stations").objects()
        .map { Station(it.getInt("id"), it.getString("name"), it.getDouble("x").toFloat(), it.getDouble("y").toFloat()) }
    val stationIds = stations.map { it.id }.toSet()
    val lineIds = lines.map { it.id }.toSet()
    val links = o.getJSONArray("links").objects()
        .map { Link(it.getInt("line"), it.getInt("a"), it.getInt("b")) }
        .filter { it.a in stationIds && it.b in stationIds && it.lineId in lineIds }
    val maxId = (stations.map { it.id } + lines.map { it.id }).maxOrNull() ?: 0
    return MetroMap(
        name = o.optString("name", "Схема"),
        stations = stations,
        lines = lines,
        links = links,
        nextId = maxOf(o.optInt("nextId", 1), maxId + 1),
    )
}
