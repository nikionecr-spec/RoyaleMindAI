package com.royalemind.ai.ui

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Simple JSON-file match history (one line per saved snapshot). */
object MatchHistoryStore {
    data class Entry(val time: String, val summary: String)

    private fun file(ctx: Context) = File(ctx.filesDir, "match_history.jsonl")

    fun save(ctx: Context, predictionJson: String) {
        val p = JSONObject(predictionJson)
        val line = JSONObject()
            .put("time", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.US).format(java.util.Date()))
            .put("action", p.optString("action"))
            .put("card", p.optString("recommended_card"))
            .put("confidence", p.optDouble("confidence", 0.0))
            .put("elixir", p.optJSONObject("elixir")?.optDouble("player", 0.0))
            .put("summary", p.toString().take(400))
        file(ctx).appendText(line.toString() + "\n")
    }

    fun loadAll(ctx: Context): List<Entry> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return f.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
            runCatching {
                val o = JSONObject(line)
                Entry(o.getString("time"),
                    "action=${o.optString("action")} card=${o.optString("card")} " +
                            "conf=${o.optDouble("confidence", 0.0)}")
            }.getOrNull()
        }
    }
}
