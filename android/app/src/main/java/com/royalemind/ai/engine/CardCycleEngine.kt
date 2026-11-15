package com.royalemind.ai.engine

/**
 * Card cycle engine port (mirror of ai/card_cycle/cycle_engine.py).
 * 8-card deck: hand(4) + queue(4); played card -> back of queue; champions
 * return after a cooldown; evolutions share the base card slot.
 */
class CardCycleEngine(private val championReturnSec: Double = 10.0) {
    private val championNames = HashSet<String>()
    var opponentHand = arrayOfNulls<String>(4); private set
    var opponentQueue = arrayOfNulls<String>(4); private set
    val opponentKnown = LinkedHashSet<String>()
    val evolutionFlags = HashSet<String>()
    private val championsCooldown = mutableListOf<Pair<String, Double>>()

    fun setChampions(names: List<String>) { championNames += names }

    fun reset() {
        opponentHand = arrayOfNulls(4); opponentQueue = arrayOfNulls(4)
        opponentKnown.clear(); evolutionFlags.clear(); championsCooldown.clear()
    }

    fun baseCard(name: String): Pair<String, Boolean> =
        if (name.endsWith("-evolution")) name.removeSuffix("-evolution") to true
        else name to false

    fun opponentPlayed(cardName: String, t: Double) {
        val (base, evo) = baseCard(cardName)
        if (evo) evolutionFlags += base
        if (base in championNames) {
            for (i in 0 until 4) if (opponentHand[i] == base) opponentHand[i] = null
            championsCooldown += base to t
            opponentKnown += base
            fillChampionReturn(t)
            return
        }
        val handIdx = opponentHand.indexOfFirst { it == base }
        if (handIdx >= 0) {
            opponentHand[handIdx] = opponentQueue[0]
            for (i in 0 until 3) opponentQueue[i] = opponentQueue[i + 1]
            opponentQueue[3] = base
        } else if (opponentQueue.indexOfFirst { it == base } >= 0) {
            val q = opponentQueue.indexOfFirst { it == base }
            for (i in q until 3) opponentQueue[i] = opponentQueue[i + 1]
            opponentQueue[3] = base
        } else if (opponentQueue.any { it == null }) {
            val q = opponentQueue.indexOfFirst { it == null }
            opponentQueue[q] = base
        } else if (opponentHand.any { it == null }) {
            val h = opponentHand.indexOfFirst { it == null }
            opponentHand[h] = opponentQueue[0]
            for (i in 0 until 3) opponentQueue[i] = opponentQueue[i + 1]
            opponentQueue[3] = base
        } else {
            for (i in 0 until 3) opponentQueue[i] = opponentQueue[i + 1]
            opponentQueue[3] = base
        }
        opponentKnown += base
        fillChampionReturn(t)
    }

    private fun fillChampionReturn(t: Double) {
        val remaining = mutableListOf<Pair<String, Double>>()
        for ((name, t0) in championsCooldown) {
            if (t - t0 >= championReturnSec) {
                val h = opponentHand.indexOfFirst { it == null }
                if (h >= 0 && name !in opponentHand) opponentHand[h] = name
            } else remaining += name to t0
        }
        championsCooldown.clear(); championsCooldown += remaining
    }

    /** Ranked next-card predictions; unknown queue slots keep their probability mass. */
    fun nextCards(topK: Int = 4): List<Map<String, Any>> {
        val weights = listOf(0.55, 0.25, 0.13, 0.07)
        val scores = LinkedHashMap<String, Double>()
        var unknownMass = 0.0
        for ((w, c) in weights.zip(opponentQueue)) {
            if (c == null) { unknownMass += w; continue }
            scores[c] = (scores[c] ?: 0.0) + w
        }
        val total = scores.values.sum() + unknownMass
        return scores.entries.sortedByDescending { it.value }.take(topK).map {
            mapOf("card" to it.key,
                  "probability" to (kotlin.math.round(it.value / total * 1000) / 1000),
                  "evolution" to (it.key in evolutionFlags))
        }
    }

    fun coverage() = opponentKnown.size / 8.0
}
