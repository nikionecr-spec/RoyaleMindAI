package com.royalemind.ai.engine

import com.royalemind.ai.data.CardDb
import org.json.JSONObject

/**
 * Predictor facade (mirror of ai/prediction/predictor.py): merges elixir,
 * cycle and tactical engines into the final JSON contract.
 */
class Predictor(private val db: CardDb) {
    val elixir = ElixirEngine()
    val cycle = CardCycleEngine()

    fun predict(t: Double, hand: List<String?>, units: List<BoardUnit>,
                cfg: ArenaGeometry.Config): JSONObject {
        elixir.update(t)
        val snap = elixir.snapshot(t)
        val tactical = TacticalEngine(db)
        val move = tactical.bestMove(hand, snap["player"] as Double, units, cfg,
            snap["phase"] as String, cycle.opponentKnown.size)
        val threats = tactical.threats(units, cfg).take(5)
        return JSONObject().apply {
            put("action", move.action)
            put("recommended_card", move.card ?: JSONObject.NULL)
            put("placement", move.placement?.let {
                JSONObject().put("x", it.x).put("y", it.y) } ?: JSONObject.NULL)
            put("reason", move.reason)
            put("confidence", move.confidence)
            put("risk", move.risk)
            put("reasons", org.json.JSONArray().apply { move.reasons.forEach { put(it) } })
            put("elixir", JSONObject(snap))
            put("opponent", JSONObject().apply {
                put("hand", org.json.JSONArray().apply { cycle.opponentHand.forEach { put(it ?: JSONObject.NULL) } })
                put("coverage", (kotlin.math.round(cycle.coverage() * 1000) / 1000))
                put("next", org.json.JSONArray().apply { cycle.nextCards().forEach { put(JSONObject(it)) } })
            })
            put("threats", org.json.JSONArray().apply {
                threats.forEach {
                    put(JSONObject().put("card", it.unit.card).put("lane", it.lane)
                        .put("eta_to_tower_s", (kotlin.math.round(it.etaTower * 10) / 10))
                        .put("threat", (kotlin.math.round(it.threat * 100) / 100)))
                }
            })
        }
    }
}
