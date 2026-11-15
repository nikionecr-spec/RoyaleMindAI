package com.royalemind.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Card statistics + class names loaded from assets (same JSON as the Python side).
 * Provenance note travels with the file: approximate tournament-standard values,
 * used for RELATIVE scoring; re-audit against the official API.
 */
class CardDb private constructor(context: Context) {
    val cards = HashMap<String, JSONObject>()   // public: UI reads entries directly
    private val champions = HashSet<String>()
    val classes: List<String>

    init {
        val classesJson = context.assets.open("classes.json").bufferedReader().use { it.readText() }
        classes = JSONArray(classesJson).let { arr -> List(arr.length()) { arr.getString(it) } }
        val cardsJson = context.assets.open("cards.json").bufferedReader().use { it.readText() }
        val root = JSONObject(cardsJson)
        val arr = root.getJSONArray("cards")
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            cards[c.getString("name")] = c
            if (c.optBoolean("champion")) champions += c.getString("name")
        }
    }

    fun get(name: String): JSONObject? {
        cards[name]?.let { return it }
        if (name.endsWith("-evolution")) return cards[name.removeSuffix("-evolution")]
        if (name.endsWith("-hero")) return cards[name.removeSuffix("-hero")]
        return null
    }

    fun cost(name: String): Int? = get(name)?.optInt("elixir")
    fun isChampion(name: String) = name in champions
    fun size() = cards.size

    companion object {
        @Volatile private var instance: CardDb? = null
        fun get(context: Context): CardDb =
            instance ?: synchronized(this) {
                instance ?: CardDb(context.applicationContext).also { instance = it }
            }
    }
}
