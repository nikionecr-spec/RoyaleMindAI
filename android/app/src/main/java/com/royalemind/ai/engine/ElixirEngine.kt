package com.royalemind.ai.engine

/**
 * Elixir engine port (mirror of ai/elixir/elixir_engine.py).
 * 1 elixir / 2.8 s; x2 after 120 s; x3 overtime. Opponent bar is an ESTIMATE
 * with an explicit confidence score - never shown as fact.
 */
class ElixirEngine(
    private val cap: Double = 10.0,
    private val start: Double = 5.0,
    private val periodS: Double = 2.8,
    private val doubleFromS: Double = 120.0,
    private val tripleFromS: Double = 180.0
) {
    private var matchStart: Double? = null
    private var lastUpdate: Double? = null
    var player = start; private set
    var opponent = start; private set
    var opponentConfidence = 1.0; private set

    fun startMatch(t: Double) {
        matchStart = t; lastUpdate = t
        player = start; opponent = start; opponentConfidence = 1.0
    }

    fun elapsed(t: Double) = matchStart?.let { maxOf(0.0, t - it) } ?: 0.0

    fun phase(t: Double): String {
        val e = elapsed(t)
        return when {
            e >= tripleFromS -> "triple"
            e >= doubleFromS -> "double"
            else -> "single"
        }
    }

    fun rate(t: Double) = when (phase(t)) {
        "triple" -> 3.0 / periodS
        "double" -> 2.0 / periodS
        else -> 1.0 / periodS
    }

    fun update(t: Double) {
        val last = lastUpdate
        if (last == null) { lastUpdate = t; return }
        val dt = maxOf(0.0, t - last)
        val gain = rate(t) * dt
        player = minOf(cap, player + gain)
        opponent = minOf(cap, opponent + gain)
        lastUpdate = t
        opponentConfidence = maxOf(0.35, opponentConfidence - 0.01 * dt)
    }

    fun playerPlayed(cost: Int, t: Double): Boolean {
        update(t)
        return if (cost <= player) { player -= cost; true }
        else { player = maxOf(0.0, player - cost); false }
    }

    fun opponentPlayed(cost: Int, t: Double) {
        update(t)
        if (cost > opponent) {
            opponent = 0.0
            opponentConfidence = maxOf(0.35, opponentConfidence - 0.2)
        } else {
            opponent -= cost
            opponentConfidence = minOf(1.0, opponentConfidence + 0.25)
        }
    }

    fun snapshot(t: Double): Map<String, Any> = mapOf(
        "player" to kotlin.math.round(player * 10) / 10,
        "opponent_estimate" to kotlin.math.round(opponent * 10) / 10,
        "opponent_confidence" to (kotlin.math.round(opponentConfidence * 100) / 100),
        "phase" to phase(t),
        "elapsed_s" to kotlin.math.round(elapsed(t) * 10) / 10
    )
}
