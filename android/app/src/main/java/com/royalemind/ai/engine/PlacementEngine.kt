package com.royalemind.ai.engine

import com.royalemind.ai.data.CardDb
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * Geometric placement engine port (mirror of ai/tactical/placement.py):
 * generates and ranks deployable candidate points with human-readable reasons.
 */
class PlacementEngine(private val arenaCfg: ArenaGeometry.Config = ArenaGeometry.Config()) {

    data class Candidate(
        val pos: ArenaGeometry.Point,
        var score: Double = 0.0,
        var risk: String = "low",
        var reasons: MutableList<String> = mutableListOf()
    )

    private val gridCandidates: List<Candidate> by lazy {
        val out = mutableListOf<Candidate>()
        val step = 1.0 / ArenaGeometry.TILES_X
        var x = 0.0
        while (x <= 1.0) {
            var y = ArenaGeometry.RIVER_Y + 0.02
            while (y <= 0.98) {
                val p = ArenaGeometry.Point(x, y)
                if (ArenaGeometry.canDeploy(p, arenaCfg)) out += Candidate(pos = p)
                y += 1.0 / ArenaGeometry.TILES_Y
            }
            x += step
        }
        out
    }

    fun rank(card: JSONObject, enemyUnits: List<BoardUnit>, topK: Int = 5): List<Candidate> {
        val spell = card.optString("kind") == "spell"
        val flying = card.optString("target") in setOf("both", "air")
        val out = mutableListOf<Candidate>()
        if (spell) {
            // spells may be cast anywhere: use the grid over the enemy half too
            val step = 1.0 / ArenaGeometry.TILES_X
            var x = 0.0
            while (x <= 1.0) {
                var y = 0.10
                while (y <= ArenaGeometry.RIVER_Y) {
                    out += Candidate(ArenaGeometry.Point(x, y))
                    y += 1.0 / ArenaGeometry.TILES_Y
                }
                x += step
            }
        } else {
            out += gridCandidates.map { Candidate(it.pos) }
        }
        // semantic anchors
        for (e in enemyUnits) {
            out += Candidate(interceptPoint(e), reasons = mutableListOf("intercept ${e.card}"))
        }
        for (bx in listOf(ArenaGeometry.TOWER_X_LEFT, ArenaGeometry.TOWER_X_RIGHT)) {
            out += Candidate(ArenaGeometry.Point(bx, ArenaGeometry.RIVER_Y + 0.08),
                reasons = mutableListOf("bridge defense"))
        }
        for (tw in listOf(
            ArenaGeometry.Point(ArenaGeometry.TOWER_X_LEFT, ArenaGeometry.TOWER_Y_OWN),
            ArenaGeometry.Point(ArenaGeometry.TOWER_X_RIGHT, ArenaGeometry.TOWER_Y_OWN))) {
            out += Candidate(ArenaGeometry.Point(tw.x, tw.y - 0.08),
                reasons = mutableListOf("in front of tower"))
        }
        val scored = out.mapNotNull { c ->
            val s = score(card, c, enemyUnits, spell, flying) ?: return@mapNotNull null
            c.score = s; c
        }
        return scored.sortedByDescending { it.score }.take(topK)
    }

    private fun score(card: JSONObject, cand: Candidate, enemies: List<BoardUnit>,
                      spell: Boolean, flying: Boolean): Double? {
        val p = cand.pos
        if (!ArenaGeometry.canDeploy(p, arenaCfg, flying, spell)) return null
        var s = 0.30
        val reasons = cand.reasons
        cand.risk = "low"

        if (enemies.isNotEmpty()) {
            val primary = primaryThreat(enemies)
            val dTiles = p.distTiles(primary.pos)
            val speed = card.optString("speed", "medium")
            val eta = ArenaGeometry.travelTime(speed, dTiles)
            val threatEta = ArenaGeometry.travelTime(
                if (primary.card.contains("hog")) "very_fast" else "medium",
                max(0.5, primary.pos.distTiles(
                    if (primary.pos.x < 0.5)
                        ArenaGeometry.Point(ArenaGeometry.TOWER_X_LEFT, ArenaGeometry.TOWER_Y_OWN)
                    else ArenaGeometry.Point(ArenaGeometry.TOWER_X_RIGHT, ArenaGeometry.TOWER_Y_OWN))))
            if (!spell) {
                if (eta <= threatEta) { s += 0.30; reasons += "arrives in time" }
                else if (eta <= threatEta * 1.4) { s += 0.15; reasons += "tight timing" }
                else { s -= 0.15; reasons += "too slow" }
                val ideal = if (hasRole(card, "swarm")) 1.5 else 2.5
                s += 0.20 * max(0.0, 1.0 - abs(dTiles - ideal) / 6.0)
            } else {
                val radius = card.optDouble("spell_radius_t", 2.5)
                s += 0.40 * max(0.0, 1.0 - abs(dTiles - radius) / (radius + 3.0))
            }
        }
        if (!spell && ArenaGeometry.inOwnTowerSupport(p, arenaCfg)) { s += 0.15; reasons += "tower support" }
        if (card.optString("kind") == "troop" && ArenaGeometry.inEnemyTowerRange(p, arenaCfg)) {
            s -= 0.25
            cand.risk = if (s < 0.3) "high" else "medium"
            reasons += "inside enemy tower range"
        }
        if (card.optString("kind") == "building") {
            s += 0.25 * max(0.0, 1.0 - abs(p.x - 0.5) / 0.35)
            reasons += "center pull"
        }
        cand.reasons = reasons
        return min(1.0, max(0.0, s))
    }

    private fun primaryThreat(enemies: List<BoardUnit>): BoardUnit =
        enemies.sortedWith(compareBy(
            { u: BoardUnit -> !isWinCondition(u) },
            { u: BoardUnit -> !u.descending },
            { u: BoardUnit -> -kotlin.math.abs(u.velocity.y) }
        )).first()

    private fun isWinCondition(u: BoardUnit) =
        listOf("hog", "balloon", "ram", "giant", "drill", "barrel").any { u.card.contains(it) }

    private fun hasRole(card: JSONObject, role: String): Boolean {
        val arr = card.optJSONArray("roles") ?: return false
        for (i in 0 until arr.length()) if (arr.optString(i) == role) return true
        return false
    }

    private fun interceptPoint(e: BoardUnit): ArenaGeometry.Point {
        val norm = max(1e-6, hypot(e.velocity.x, e.velocity.y))
        val x = (e.pos.x + e.velocity.x / norm / ArenaGeometry.TILES_X).coerceIn(0.02, 0.98)
        val y = (e.pos.y + e.velocity.y / norm / ArenaGeometry.TILES_Y)
            .coerceIn(ArenaGeometry.RIVER_Y + 0.04, 0.98)
        return ArenaGeometry.Point(x, y)
    }

    private fun hypot(a: Double, b: Double) = kotlin.math.hypot(a, b)
    private fun abs(a: Double) = kotlin.math.abs(a)
}
