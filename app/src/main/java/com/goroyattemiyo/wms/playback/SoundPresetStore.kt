package com.goroyattemiyo.wms.playback

import org.json.JSONArray
import org.json.JSONObject

/** Local-only JSON schema; malformed entries are skipped instead of crashing playback. */
internal object SoundPresetStore {
    const val SCHEMA_VERSION = 1

    fun encode(presets: List<SoundPreset>): String {
        val entries = JSONArray()
        presets.filterNot(SoundPreset::builtIn).forEach { preset ->
            val points = JSONArray()
            preset.points.forEach { point ->
                points.put(JSONObject().put("hz", point.frequencyHz).put("gainMb", point.gainMb))
            }
            entries.put(JSONObject().put("id", preset.id).put("name", preset.name).put("points", points))
        }
        return JSONObject().put("version", SCHEMA_VERSION).put("presets", entries).toString()
    }

    fun decode(json: String?): List<SoundPreset> = runCatching {
        if (json.isNullOrBlank()) return emptyList()
        val root = JSONObject(json)
        if (root.optInt("version") != SCHEMA_VERSION) return emptyList()
        val entries = root.optJSONArray("presets") ?: return emptyList()
        buildList {
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val name = SoundPresets.validName(item.optString("name")) ?: continue
                if (!id.startsWith("user:") && id != "manual") continue
                val pointArray = item.optJSONArray("points") ?: continue
                if (pointArray.length() !in 1..64) continue
                val points = buildList {
                    for (index in 0 until pointArray.length()) {
                        val point = pointArray.optJSONObject(index) ?: continue
                        val hz = point.optInt("hz")
                        val mb = point.optInt("gainMb")
                        if (hz in 1..100_000 && mb in -12_000..12_000) add(SoundPoint(hz, mb))
                    }
                }
                if (points.isEmpty() || any { it.id == id || it.name.equals(name, true) }) continue
                add(SoundPreset(id, name, points))
            }
        }
    }.getOrDefault(emptyList())
}
