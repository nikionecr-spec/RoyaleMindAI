package com.royalemind.ai.engine

import com.royalemind.ai.data.CardDb
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * Tactical engine port (mirror of ai/tactical/tactical_engine.py):
 * multi-criteria rule-based scoring - NO LLM in the decision loop.
 */
class TacticalEngine(private val db: CardDb) {
    private val placement = PlacementEngine()

    data class Threat(val unit: BoardUnit, val lane: String, val etaTower: Double,
                      val threat: Double, val roles: Set<String>)

    fun threats(units: List<BoardUnit>, cfg: ArenaGeometry.Config): List<Threat> {
        val ownTower = if (cfg.ownLeftAlive)
            ArenaGeometry.Point(ArenaGeometry.TOWER_X_LEFT, ArenaGeometry.TOWER_Y_OWN)
        else ArenaGeometry.Point(ArenaGeometry.TOWER_X_RIGHT, ArenaGeometry.TOWER_Y_OWN)
        val out = mutableListOf<Threat>()
        for (u in units.filter { it.team == "enemy" }) {
            val card = db.get(u.card) ?: JSONObject()
            val lane = if (u.pos.x < 0.5) "left" else "right"
            val eta = ArenaGeometry.travelTime(card.optString("speed", "medium"),
                max(0.5, u.pos.distTiles(ownTower)))
            val count = card.optInt("count", 1)
            val dps = card.optDouble("dps", 100.0) * count
            val hp = card.optDouble("hp", 500.0) * count
            val wc = "win_condition" in roles(card) || card.optString("target") == "buildings_only"
            val threat = (dps + hp / 20.0) / max(2.0, eta) *
                (if (wc) 1.6 else 1.0) * u.confidence
            val roles = roles(card).toMutableSet()
            if (card.optBoolean("flying")) roles += "air"
            if (count >= 3) roles += "swarm"
            if (hp >= 2000) roles += "tank"
            if (wc) roles += "win_condition"
            out += Threat(u, lane, eta, threat, roles)
        }
        return out.sortedByDescending { it.threat }
    }

    private fun roles(card: JSONObject): Set<String> {
        val arr = card.optJSONArray("roles") ?: return emptySet()
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    data class Move(
        val action: String, val card: String?, val placement: ArenaGeometry.Point?,
        val score: Double, val confidence: Double, val risk: String,
        val reason: String, val reasons: List<String>
    ) {
        fun asJson(): JSONObject = JSONObject().apply {
            put("action", action); put("recommended_card", card ?: JSONObject.NULL)
            put("placement", placement?.let { JSONObject().put("x", it.x).put("y", it.y) }
                ?: JSONObject.NULL)
            put("reason", reason)
            put("confidence", (kotlin.math.round(confidence * 1000) / 1000))
            put("risk", risk); put("reasons", JSONArrayish(reasons))
        }
    }

    private fun JSONArrayish(items: List<String>): org.json.JSONArray =
        org.json.JSONArray().apply { items.forEach { put(it) } }

    fun bestMove(hand: List<String?>, elixir: Double, units: List<BoardUnit>,
                 cfg: ArenaGeometry.Config, phase: String,
                 opponentKnown: Int): Move {
        val th = threats(units, cfg)
        val urgent = th.filter { it.threat >= DEFEND_THRESHOLD }
        val mode = when {
            urgent.isNotEmpty() -> "DEFEND"
            elixir < SAVE_MAX && phase == "single" -> "SAVE"
            elixir >= ATTACK_MIN && elixir >= 9.0 -> "ATTACK"
            else -> "WAIT"
        }
        return when (mode) {
            "DEFEND" -> bestDefense(hand, elixir, urgent, cfg, opponentKnown, units)
            "ATTACK" -> bestAttack(hand, elixir, cfg, opponentKnown, units)
            "SAVE" -> Move("SAVE", null, null, 0.5, 0.7, "low",
                "Build elixir; no affordable answer now", emptyList())
            else -> Move("WAIT", null, null, 0.4, 0.6, "low",
                "No urgent threat; keep options open", emptyList())
        }
    }

    private fun bestDefense(hand: List<String?>, elixir: Double, urgent: List<Threat>,
                            cfg: ArenaGeometry.Config, opponentKnown: Int,
                            units: List<BoardUnit>): Move {
        if (urgent.isEmpty()) return Move("SAVE", null, null, 0.4, 0.5, "medium",
            "No threat details - save elixir", emptyList())
        val threat = urgent.first()
        val ranked = mutableListOf<Triple<Double, String, List<String>>>()
        for (name in hand) {
            if (name == null) continue
            val card = db.get(name) ?: continue
            val cost = card.optInt("elixir")
            if (cost > elixir) continue
            val fit = counterFit(card, threat) ?: continue
            val spot = placement.rank(card, listOf(threat.unit), 1).firstOrNull() ?: continue
            val intercept = if (spot.reasons.any { it.contains("arrives") }) 1.0 else 0.5
            val support = if (spot.reasons.any { it.contains("tower") }) 0.8 else 0.4
            val threatCost = db.get(threat.unit.card)?.optInt("elixir") ?: 4
            val eff = min(1.0, 0.5 * (threatCost + 1) / max(1, cost))
            val score = 0.35 * fit + 0.25 * intercept + 0.15 * support + 0.15 * eff +
                    0.10 * (if (spot.risk == "low") 0.8 else 0.4)
            ranked += Triple(score, name, spot.reasons)
        }
        if (ranked.isEmpty()) return Move("SAVE", null, null, 0.4, 0.5, "medium",
            "No counter available at current elixir", emptyList())
        val best = ranked.maxBy { it.first }
        val spot = placement.rank(db.get(best.second)!!, listOf(threat.unit), 1).first()
        val conf = confidence(score = best.first, units.isEmpty(), hand.any { it == null }, opponentKnown)
        return Move("DEFEND", best.second, spot.pos, best.first, conf, spot.risk,
            "Defensive answer to ${threat.unit.card}", best.third)
    }

    /** Returns null when the card cannot address the threat at all. */
    private fun counterFit(card: JSONObject, threat: Threat): Double? {
        val roles = roles(card)
        val fit = { v: Double -> min(1.0, max(0.0, v)) }
        if ("air" in threat.roles && card.optString("target") !in setOf("both", "air")) return null
        var s = 0.0
        if ("swarm" in threat.roles) {
            if (roles.intersect(setOf("splash", "spell_damage", "reset")).isNotEmpty()) s += 0.5
        }
        if ("tank" in threat.roles) {
            val dps = card.optDouble("dps", 0.0) * card.optInt("count", 1)
            if (dps >= 300) s += 0.5
        }
        if ("win_condition" in threat.roles || "mobility" in threat.roles) {
            if (roles.intersect(setOf("building", "mini_tank", "building_targeting")).isNotEmpty()) s += 0.4
            else if (card.optDouble("dps", 0.0) >= 250) s += 0.3
        }
        if (s == 0.0 && card.optDouble("dps", 0.0) >= 150) s = 0.35
        return if (s <= 0.0) null else fit(s)
    }

    private fun bestAttack(hand: List<String?>, elixir: Double,
                           cfg: ArenaGeometry.Config, opponentKnown: Int,
                           units: List<BoardUnit>): Move {
        val ranked = mutableListOf<Pair<Double, String>>()
        for (name in hand) {
            if (name == null) continue
            val card = db.get(name) ?: continue
            if (card.optInt("elixir") > elixir) continue
            val r = roles(card)
            val base = when {
                r.intersect(setOf("win_condition", "tank", "mobility")).isNotEmpty() -> 0.6
                r.intersect(setOf("support", "air_defense", "splash")).isNotEmpty() -> 0.45
                else -> 0.3
            }
            ranked += base to name
        }
        if (ranked.isEmpty()) return Move("WAIT", null, null, 0.3, 0.5, "low",
            "No affordable attacker", emptyList())
        val best = ranked.maxBy { it.first }
        val lane = "left"
        val x = if (lane == "left") ArenaGeometry.TOWER_X_LEFT else ArenaGeometry.TOWER_X_RIGHT
        return Move("ATTACK", best.second, ArenaGeometry.Point(x, ArenaGeometry.RIVER_Y - 0.10),
            best.first, confidence(best.first, units.isEmpty(), hand.any { it == null }, opponentKnown),
            "medium", "Push $lane lane at full elixir", listOf("counter-push window"))
    }

    private fun confidence(score: Double, noVision: Boolean, unknownHand: Boolean,
                           opponentKnown: Int): Double {
        var inputs = 1.0
        if (noVision) inputs *= 0.7
        if (unknownHand) inputs *= 0.85
        if (opponentKnown < 8) inputs *= 0.9
        return max(0.1, min(0.97, score * inputs))
    }

    companion object {
        const val DEFEND_THRESHOLD = 0.45
        const val ATTACK_MIN = 7.0
        const val SAVE_MAX = 3.0
    }
}
