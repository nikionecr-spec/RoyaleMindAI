package com.royalemind.ai.engine

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Arena geometry - Kotlin port of ai/arena.py.
 * Landmarks from the dataset SPEC (568x896 canonical frame, 18x32 tiles):
 *   queen towers (116,200) (461,200) (116,689) (461,689), kings (284,108) (284,790).
 * All coordinates normalized to [0..1]x[0..1]; y < river = enemy half.
 */
object ArenaGeometry {
    const val TILES_X = 18.0
    const val TILES_Y = 32.0
    const val CANON_W = 568.0
    const val CANON_H = 896.0

    const val TOWER_X_LEFT = 116.0 / CANON_W      // 0.2042
    const val TOWER_X_RIGHT = 461.0 / CANON_W     // 0.8114
    const val TOWER_Y_ENEMY = 200.0 / CANON_H     // 0.2232
    const val TOWER_Y_OWN = 689.0 / CANON_H       // 0.7690
    const val KING_Y_ENEMY = 108.0 / CANON_H
    const val KING_Y_OWN = 790.0 / CANON_H
    const val KING_X = 284.0 / CANON_W            // 0.5

    val RIVER_Y = (TOWER_Y_ENEMY + TOWER_Y_OWN) / 2.0
    const val RIVER_HALF_TILES = 1.0
    const val TOWER_RADIUS_TILES = 1.5
    const val TOWER_RANGE_TILES = 7.5
    const val KING_RADIUS_TILES = 1.8

    val SPEED_TILES_PER_MIN = mapOf(
        "slow" to 45, "medium" to 60, "fast" to 90, "very_fast" to 120, "none" to 0
    )

    data class Point(val x: Double, val y: Double) {
        fun distTiles(o: Point) = hypot((x - o.x) * TILES_X, (y - o.y) * TILES_Y)
        fun asMap() = mapOf("x" to x, "y" to y)
    }

    data class Config(
        val enemyLeftAlive: Boolean = true,
        val enemyRightAlive: Boolean = true,
        val ownLeftAlive: Boolean = true,
        val ownRightAlive: Boolean = true
    )

    private fun towers(cfg: Config): List<Pair<Point, Double>> {
        val t = mutableListOf<Pair<Point, Double>>()
        if (cfg.enemyLeftAlive) t += Point(TOWER_X_LEFT, TOWER_Y_ENEMY) to TOWER_RADIUS_TILES
        if (cfg.enemyRightAlive) t += Point(TOWER_X_RIGHT, TOWER_Y_ENEMY) to TOWER_RADIUS_TILES
        if (cfg.ownLeftAlive) t += Point(TOWER_X_LEFT, TOWER_Y_OWN) to TOWER_RADIUS_TILES
        if (cfg.ownRightAlive) t += Point(TOWER_X_RIGHT, TOWER_Y_OWN) to TOWER_RADIUS_TILES
        t += Point(KING_X, KING_Y_ENEMY) to KING_RADIUS_TILES
        t += Point(KING_X, KING_Y_OWN) to KING_RADIUS_TILES
        return t
    }

    /** Deploy legality: bounds, tower footprints; spells anywhere; ground troops
     *  blocked from the river except on the two bridges; own half always allowed;
     *  enemy half only past a destroyed enemy tower. */
    fun canDeploy(p: Point, cfg: Config, flying: Boolean = false, spell: Boolean = false): Boolean {
        if (p.x < 0.02 || p.x > 0.98 || p.y < 0.02 || p.y > 0.98) return false
        for ((t, r) in towers(cfg)) if (p.distTiles(t) < r) return false
        if (spell) return true
        val inBand = abs(p.y - RIVER_Y) * TILES_Y < RIVER_HALF_TILES
        val onBridge = minOf(abs(p.x - TOWER_X_LEFT), abs(p.x - TOWER_X_RIGHT)) * TILES_X < 1.2
        if (!flying && inBand && !onBridge) return false
        val ownBoundary = RIVER_Y - (RIVER_HALF_TILES / TILES_Y).takeIf { flying } ?: 0.0
        if (p.y >= ownBoundary) return true
        return if (p.x < 0.5) (!cfg.enemyLeftAlive && p.y >= TOWER_Y_ENEMY - 0.02)
        else (!cfg.enemyRightAlive && p.y >= TOWER_Y_ENEMY - 0.02)
    }

    fun inOwnTowerSupport(p: Point, cfg: Config): Boolean {
        val own = listOf(
            Point(TOWER_X_LEFT, TOWER_Y_OWN) to cfg.ownLeftAlive,
            Point(TOWER_X_RIGHT, TOWER_Y_OWN) to cfg.ownRightAlive,
            Point(KING_X, KING_Y_OWN) to true
        )
        return own.any { (t, alive) -> alive && p.distTiles(t) <= TOWER_RANGE_TILES }
    }

    fun inEnemyTowerRange(p: Point, cfg: Config): Boolean {
        val enemy = listOf(
            Point(TOWER_X_LEFT, TOWER_Y_ENEMY) to cfg.enemyLeftAlive,
            Point(TOWER_X_RIGHT, TOWER_Y_ENEMY) to cfg.enemyRightAlive,
            Point(KING_X, KING_Y_ENEMY) to true
        )
        return enemy.any { (t, alive) -> alive && p.distTiles(t) <= TOWER_RANGE_TILES }
    }

    fun travelTime(speedClass: String, distTiles: Double): Double {
        val tpm = SPEED_TILES_PER_MIN[speedClass] ?: 60
        if (tpm == 0) return Double.POSITIVE_INFINITY
        return distTiles / (tpm / 60.0)
    }

    /** Map a normalized arena point to device-screen pixels for the letterboxed viewport. */
    fun normalizedToScreen(p: Point, screenW: Int, screenH: Int): Pair<Int, Int> {
        val canonAspect = CANON_W / CANON_H
        val screenAspect = screenW.toDouble() / screenH
        val (x0, x1, y0, y1) =
            if (screenAspect < canonAspect) {
                val w = screenAspect / canonAspect
                listOf((1 - w) / 2, (1 + w) / 2, 0.0, 1.0)
            } else {
                val h = canonAspect / screenAspect
                listOf(0.0, 1.0, (1 - h) / 2, (1 + h) / 2)
            }
        val sx = ((x0 + p.x * (x1 - x0)) * screenW).toInt().coerceIn(0, screenW - 1)
        val sy = ((y0 + p.y * (y1 - y0)) * screenH).toInt().coerceIn(0, screenH - 1)
        return sx to sy
    }
}
