package com.royalemind.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royalemind.ai.R
import com.royalemind.ai.data.CardDb
import com.royalemind.ai.data.GameStateRepository
import com.royalemind.ai.engine.ArenaGeometry
import org.json.JSONObject

private val purple = Color(0xFF6C4CE0)

/** 8 sections per the project spec, bottom navigation, fa/en aware. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoyaleMindApp(
    onStartAssistant: () -> Unit,
    onStopAssistant: () -> Unit,
    onEnableOverlay: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_dashboard),
        stringResource(R.string.tab_live),
        stringResource(R.string.tab_cards),
        stringResource(R.string.tab_history),
        stringResource(R.string.tab_tactics),
        stringResource(R.string.tab_model),
        stringResource(R.string.tab_perf),
        stringResource(R.string.tab_settings)
    )
    Scaffold(
        topBar = { TopAppBar(title = { Text("RoyaleMind AI", color = Color.White) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = purple)) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        label = { Text(t, fontSize = 9.sp) },
                        icon = { Text(take(i)) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> DashboardScreen()
                1 -> LiveScreen(onStartAssistant, onStopAssistant, onEnableOverlay)
                2 -> CardDatabaseScreen()
                3 -> HistoryScreen()
                4 -> TacticsScreen()
                5 -> ModelStatusScreen()
                6 -> PerformanceScreen()
                7 -> SettingsScreen()
            }
        }
    }
}

private fun take(i: Int) = listOf("🏠", "▶", "🃏", "🕘", "🎯", "🧠", "⚡", "⚙")[i]

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label); Text(value, color = purple)
    }
}

@Composable
fun DashboardScreen() {
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Card { Column(Modifier.padding(16.dp)) {
            StatLine(stringResource(R.string.assistant),
                if (GameStateRepository.assistantRunning) stringResource(R.string.on)
                else stringResource(R.string.off))
            StatLine(stringResource(R.string.model),
                if (GameStateRepository.modelLoaded) stringResource(R.string.loaded)
                else stringResource(R.string.not_loaded))
            StatLine("FPS", "%.0f".format(GameStateRepository.fps))
            StatLine(stringResource(R.string.inference),
                "%.1f ms".format(GameStateRepository.inferenceMs))
        } }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.dashboard_hint),
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun LiveScreen(start: () -> Unit, stop: () -> Unit, overlay: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = start, enabled = !GameStateRepository.assistantRunning) {
                Text(stringResource(R.string.start_assistant))
            }
            Button(onClick = stop, enabled = GameStateRepository.assistantRunning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.stop_assistant))
            }
        }
        TextButton(onClick = overlay) { Text(stringResource(R.string.overlay_permission)) }

        Spacer(Modifier.height(16.dp))
        val pred = GameStateRepository.lastPrediction
        Text(stringResource(R.string.manual_hand), style = MaterialTheme.typography.titleMedium)
        var hand by remember { mutableStateOf(listOf("", "", "", "")) }
        hand.forEachIndexed { i, v ->
            OutlinedTextField(value = v, onValueChange = { nv ->
                hand = hand.toMutableList().also { it[i] = nv }
                val db = CardDb.get(androidx.compose.ui.platform.LocalContext.current)
                GameStateRepository.manualHand = hand.map { h -> h.trim().takeIf { it.isNotEmpty() } }
                if (db != null && nv.isNotBlank() && db.get(nv.trim()) == null) { /* unknown name */ }
            },
                label = { Text("card ${i + 1}") }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.current_suggestion), style = MaterialTheme.typography.titleMedium)
        Text(pred?.optString("action") ?: stringResource(R.string.none),
            style = MaterialTheme.typography.headlineMedium, color = purple)
        Text(pred?.optString("recommended_card") ?: "")
        val ex = pred?.optJSONObject("elixir")
        if (ex != null) {
            StatLine(stringResource(R.string.elixir_player), "${ex.optDouble("player", 0.0)}")
            StatLine(stringResource(R.string.elixir_opponent),
                "~${ex.optDouble("opponent_estimate", 0.0)} " +
                        "(${ex.optDouble("opponent_confidence", 0.0) * 100}%)")
        }
    }
}

@Composable
fun CardDatabaseScreen() {
    val db = CardDb.get(androidx.compose.ui.platform.LocalContext.current)
    LazyColumn(Modifier.padding(16.dp)) {
        item { Text(stringResource(R.string.cards_count, db.size()),
            style = MaterialTheme.typography.titleMedium) }
        items(db.cards.keys.sorted()) { name ->
            val c = db.cards[name]!!
            val roles = c.optJSONArray("roles")?.joinToString(", ") {
                (it as? String) ?: "" } ?: ""
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(name, color = purple, fontSize = 15.sp)
                    Text("elixir ${c.optInt("elixir")}  •  kind ${c.optString("kind")}  •  " +
                            "target ${c.optString("target")}${if (c.optBoolean("flying")) "  •  flying" else ""}",
                        fontSize = 11.sp)
                    Text(roles, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
fun HistoryScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val history = remember { MatchHistoryStore.loadAll(ctx) }
    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge)
            Button(onClick = {
                GameStateRepository.lastPrediction?.let {
                    MatchHistoryStore.save(ctx, it.toString())
                }
            }) { Text(stringResource(R.string.save_now)) }
        }
        if (history.isEmpty()) {
            Text(stringResource(R.string.history_empty), Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn {
            items(history.asReversed()) { h ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(h.time, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        Text(h.summary.take(180), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TacticsScreen() {
    val pred = GameStateRepository.lastPrediction
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.tactics_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (pred == null) {
            Text(stringResource(R.string.none))
        } else {
            Text("action: ${pred.optString("action")}", color = purple)
            Text("card: ${pred.optString("recommended_card")}")
            Text("confidence: ${pred.optDouble("confidence", 0.0)}")
            Text("risk: ${pred.optString("risk")}")
            val reasons = pred.optJSONArray("reasons")
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.reasons), style = MaterialTheme.typography.titleMedium)
            if (reasons != null) for (i in 0 until reasons.length())
                Text("• ${reasons.optString(i)}", fontSize = 12.sp)
            val opp = pred.optJSONObject("opponent")
            if (opp != null) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.opponent_coverage, opp.optDouble("coverage", 0.0) * 100))
            }
        }
    }
}

@Composable
fun ModelStatusScreen() {
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.tab_model), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        StatLine(stringResource(R.string.model),
            if (GameStateRepository.modelLoaded) stringResource(R.string.loaded)
            else stringResource(R.string.not_loaded))
        Text(GameStateRepository.modelMessage, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        val db = CardDb.get(androidx.compose.ui.platform.LocalContext.current)
        Text(stringResource(R.string.classes_count, db.classes.size))
        Text(stringResource(R.string.cards_count, db.size()))
        Text(stringResource(R.string.model_source_note),
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PerformanceScreen() {
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.tab_perf), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        StatLine("FPS (processing)", "%.0f".format(GameStateRepository.fps))
        StatLine(stringResource(R.string.inference), "%.1f ms".format(GameStateRepository.inferenceMs))
        StatLine(stringResource(R.string.frames), "${GameStateRepository.frameCount}")
        Text(stringResource(R.string.perf_target),
            style = MaterialTheme.typography.bodySmall)
    }
}
