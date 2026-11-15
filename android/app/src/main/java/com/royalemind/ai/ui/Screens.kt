package com.royalemind.ai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royalemind.ai.R
import com.royalemind.ai.data.CardDb
import com.royalemind.ai.data.GameStateRepository
import com.royalemind.ai.engine.ArenaGeometry
import com.royalemind.ai.engine.BoardUnit
import org.json.JSONObject

private val purple = Color(0xFF6C4CE0)
private val pageBg = Color(0xFF120B22)
private val cardBg = Color(0xFF221838)
private val arenaBg = Color(0xFF1D1436)
private val riverBlue = Color(0xFF2F52A0)
private val green = Color(0xFF3DDC84)
private val red = Color(0xFFE84646)
private val allyBlue = Color(0xFF4CC9F0)
private val gold = Color(0xFFF5B942)
private val dimText = Color(0xFFBBAADD)
private val faintText = Color(0xFF82709E)

/** Three simple tabs. The assistant screen is the whole point of the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoyaleMindApp(
    onStartAssistant: () -> Unit,
    onStopAssistant: () -> Unit,
    onEnableOverlay: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_live),
        stringResource(R.string.tab_cards),
        stringResource(R.string.tab_status)
    )
    Scaffold(
        containerColor = pageBg,
        topBar = { TopAppBar(
            title = { Text("RoyaleMind AI", color = Color.White, fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = purple)) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        label = { Text(t, fontSize = 12.sp) },
                        icon = { Text(listOf("🎯", "🃏", "ℹ️")[i], fontSize = 18.sp) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> LiveScreen(onStartAssistant, onStopAssistant, onEnableOverlay)
                1 -> CardDatabaseScreen()
                else -> StatusScreen()
            }
        }
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = cardBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(color = cardBg, shape = RoundedCornerShape(20.dp)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = color, fontSize = 12.sp)
    }
}

/** Persian label for the engine's action codes. */
private fun actionFa(action: String): String = when (action) {
    "ATTACK" -> "حمله کن"
    "DEFEND" -> "دفاع کن"
    "SAVE" -> "اکسیر جمع کن"
    "WAIT" -> "صبر کن"
    else -> if (action.isNotEmpty()) action else "—"
}

/** Persian label for known engine reasons; unknown ones pass through. */
private fun reasonFa(r: String): String = when (r) {
    "arrives in time" -> "به‌موقع می‌رسد"
    "tight timing" -> "زمان تنگ است"
    "too slow" -> "کند است"
    "tower support" -> "پشتیبانی برج"
    "inside enemy tower range" -> "در برد برج حریف"
    "center pull" -> "کشش به مرکز"
    "bridge defense" -> "دفاع از پل"
    "in front of tower" -> "جلوِ برج"
    else -> r
}

@Composable
fun LiveScreen(start: () -> Unit, stop: () -> Unit, overlay: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {

        // one big obvious button
        val running = GameStateRepository.assistantRunning
        Button(onClick = if (running) stop else start,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (running) red else green)) {
            Text(if (running) stringResource(R.string.stop_assistant)
                 else stringResource(R.string.start_assistant),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        if (!running) {
            TextButton(onClick = overlay) {
                Text(stringResource(R.string.overlay_permission), color = dimText)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(if (GameStateRepository.modelLoaded)
                stringResource(R.string.loaded) else stringResource(R.string.not_loaded),
                if (GameStateRepository.modelLoaded) green else gold)
            StatusChip("FPS %.0f".format(GameStateRepository.fps), dimText)
            StatusChip("%.0f ms".format(GameStateRepository.inferenceMs), dimText)
        }

        Spacer(Modifier.height(14.dp))
        val pred = GameStateRepository.lastPrediction
        val ex = pred?.optJSONObject("elixir")
        val opp = (ex?.optDouble("opponent_estimate", 0.0) ?: 0.0)
        val mine = (ex?.optDouble("player", 0.0) ?: 0.0)
        val oppConf = (ex?.optDouble("opponent_confidence", 0.0) ?: 0.0) * 100.0
        val conf = (pred?.optDouble("confidence", 0.0) ?: 0.0) * 100.0
        val recCard = pred?.optString("recommended_card").orEmpty()
        val action = pred?.optString("action").orEmpty()

        // ---- opponent elixir: the headline number ----
        PanelCard {
            Text(stringResource(R.string.elixir_opponent), fontSize = 14.sp, color = dimText)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (ex == null) "—" else "%.1f".format(opp),
                    fontSize = 48.sp, fontWeight = FontWeight.Black, color = gold)
                Text(" / 10", fontSize = 16.sp, color = faintText,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.confidence_pct, oppConf),
                    fontSize = 12.sp, color = faintText,
                    modifier = Modifier.padding(bottom = 10.dp))
            }
            LinearProgressIndicator(
                progress = (opp / 10.0).toFloat().coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(12.dp),
                color = gold, trackColor = Color(0xFF33224A))
        }

        Spacer(Modifier.height(10.dp))
        // ---- my elixir ----
        PanelCard {
            Text(stringResource(R.string.elixir_player), fontSize = 14.sp, color = dimText)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (ex == null) "—" else "%.1f".format(mine),
                    fontSize = 34.sp, fontWeight = FontWeight.Black, color = purple)
                Text(" / 10", fontSize = 14.sp, color = faintText,
                    modifier = Modifier.padding(start = 4.dp, bottom = 7.dp))
            }
            LinearProgressIndicator(
                progress = (mine / 10.0).toFloat().coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = purple, trackColor = Color(0xFF33224A))
        }

        Spacer(Modifier.height(10.dp))
        // ---- recommendation with the mini arena map ----
        PanelCard {
            Text(stringResource(R.string.current_suggestion), fontSize = 14.sp, color = dimText)
            Spacer(Modifier.height(8.dp))
            Row {
                MiniArenaMap(Modifier.width(112.dp).aspectRatio(568f / 896f),
                    pred, GameStateRepository.lastUnits)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (action.isEmpty()) "—" else actionFa(action),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = if (action == "WAIT" || action == "SAVE") gold else green)
                    if (recCard.isNotEmpty()) {
                        Text(recCard, fontSize = 16.sp, color = Color.White,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                    Text(stringResource(R.string.confidence_pct, conf),
                        fontSize = 12.sp, color = faintText, modifier = Modifier.padding(top = 4.dp))
                    val reasons = pred?.optJSONArray("reasons")
                    if (reasons != null) {
                        for (i in 0 until reasons.length().coerceAtMost(3)) {
                            Text("• " + reasonFa(reasons.optString(i)),
                                fontSize = 12.sp, color = dimText, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // ---- my hand, with autocomplete ----
        val ctx = LocalContext.current
        val db = remember(ctx) { CardDb.get(ctx) }
        val names = remember(db) { db.cards.keys.toList().sorted() }
        Text(stringResource(R.string.manual_hand), fontSize = 14.sp, color = dimText,
            modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(6.dp))
        var hand by remember { mutableStateOf(listOf("", "", "", "")) }
        hand.forEachIndexed { i, v ->
            CardNameField(i, v, names) { nv ->
                hand = hand.toMutableList().also { it[i] = nv }
                GameStateRepository.manualHand = hand.map { h -> h.trim().takeIf { it.isNotEmpty() } }
            }
        }
        Text(stringResource(R.string.hand_hint), fontSize = 11.sp, color = faintText,
            modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardNameField(index: Int, value: String, names: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val q = value.trim()
    val filtered = remember(q) {
        if (q.isEmpty()) emptyList() else names.filter { it.contains(q, ignoreCase = true) }.take(6)
    }
    ExposedDropdownMenuBox(expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it }) {
        OutlinedTextField(value = value, onValueChange = { nv -> onChange(nv); expanded = true },
            label = { Text(stringResource(R.string.card_n, index + 1)) }, singleLine = true,
            modifier = Modifier.menuAnchor().fillMaxWidth().padding(vertical = 3.dp),
            isError = q.isNotEmpty() && names.none { it.equals(q, ignoreCase = true) })
        ExposedDropdownMenu(expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false }) {
            filtered.forEach { n ->
                DropdownMenuItem(text = { Text(n) }, onClick = { onChange(n); expanded = false })
            }
        }
    }
}

/** Scaled arena: towers, live units, and the green placement spot. */
@Composable
private fun MiniArenaMap(modifier: Modifier, pred: JSONObject?, units: List<BoardUnit>) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val iw = w.toInt().coerceAtLeast(1); val ih = h.toInt().coerceAtLeast(1)
        drawRoundRect(color = arenaBg, cornerRadius = CornerRadius(14f, 14f))
        drawRect(riverBlue,
            topLeft = Offset(0f, (ArenaGeometry.RIVER_Y * h - 0.025 * h).toFloat()),
            size = Size(w, (0.05 * h).toFloat()))
        val towerR = 0.05f * w
        val lx = (ArenaGeometry.TOWER_X_LEFT * w).toFloat()
        val rx = (ArenaGeometry.TOWER_X_RIGHT * w).toFloat()
        val kx = (ArenaGeometry.KING_X * w).toFloat()
        val ey = (ArenaGeometry.TOWER_Y_ENEMY * h).toFloat()
        val oy = (ArenaGeometry.TOWER_Y_OWN * h).toFloat()
        val eky = (ArenaGeometry.KING_Y_ENEMY * h).toFloat()
        val oky = (ArenaGeometry.KING_Y_OWN * h).toFloat()
        listOf(Offset(lx, ey), Offset(rx, ey), Offset(kx, eky)).forEach {
            drawCircle(red.copy(alpha = 0.8f), towerR, it, style = Stroke(5f))
        }
        listOf(Offset(lx, oy), Offset(rx, oy), Offset(kx, oky)).forEach {
            drawCircle(purple, towerR, it, style = Stroke(5f))
        }
        units.forEach { u ->
            val (sx, sy) = ArenaGeometry.normalizedToScreen(u.pos, iw, ih)
            drawCircle(if (u.team == "enemy") red else allyBlue, 7f,
                Offset(sx.toFloat(), sy.toFloat()))
        }
        pred?.optJSONObject("placement")?.let { pl ->
            val p = ArenaGeometry.Point(pl.optDouble("x", 0.5), pl.optDouble("y", 0.5))
            val (sx, sy) = ArenaGeometry.normalizedToScreen(p, iw, ih)
            drawCircle(green, 15f, Offset(sx.toFloat(), sy.toFloat()), style = Stroke(6f))
            drawCircle(green, 5f, Offset(sx.toFloat(), sy.toFloat()))
        }
    }
}

@Composable
fun CardDatabaseScreen() {
    val db = CardDb.get(LocalContext.current)
    LazyColumn(Modifier.padding(16.dp)) {
        item { Text(stringResource(R.string.cards_count, db.size()),
            style = MaterialTheme.typography.titleMedium, color = Color.White) }
        items(db.cards.keys.sorted()) { name ->
            val c = db.cards[name]!!
            val rolesArr = c.optJSONArray("roles")
            val roles = if (rolesArr != null)
                (0 until rolesArr.length()).mapNotNull { rolesArr.optString(it).takeIf { r -> r.isNotEmpty() } }
                    .joinToString(", ")
            else ""
            Surface(color = cardBg, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(name, color = purple, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.elixir_cost, c.optInt("elixir")),
                            color = purple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${c.optString("kind")}  •  ${c.optString("target")}" +
                            if (c.optBoolean("flying")) "  •  flying" else "",
                        fontSize = 11.sp, color = dimText)
                    if (roles.isNotEmpty()) Text(roles, fontSize = 10.sp, color = faintText)
                }
            }
        }
    }
}

@Composable
fun StatusScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        PanelCard {
            Text(stringResource(R.string.tab_status), fontSize = 16.sp, color = Color.White,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            StatusLine(stringResource(R.string.model),
                if (GameStateRepository.modelLoaded) stringResource(R.string.loaded)
                else stringResource(R.string.not_loaded))
            Text(GameStateRepository.modelMessage, fontSize = 11.sp, color = faintText)
            val db = CardDb.get(LocalContext.current)
            StatusLine(stringResource(R.string.classes_count, db.classes.size), "")
            StatusLine(stringResource(R.string.cards_count, db.size()), "")
        }
        Spacer(Modifier.height(10.dp))
        PanelCard {
            StatusLine("FPS", "%.0f".format(GameStateRepository.fps))
            StatusLine(stringResource(R.string.inference),
                "%.1f ms".format(GameStateRepository.inferenceMs))
            StatusLine(stringResource(R.string.frames), "${GameStateRepository.frameCount}")
            Text(stringResource(R.string.perf_target), fontSize = 11.sp, color = faintText,
                modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        Surface(color = cardBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(stringResource(R.string.policy_title), fontSize = 13.sp,
                    color = gold, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.policy_text), fontSize = 11.sp,
                    color = dimText, modifier = Modifier.padding(top = 4.dp))
                Text(stringResource(R.string.model_source_note), fontSize = 10.sp,
                    color = faintText, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = dimText)
        if (value.isNotEmpty()) Text(value, fontSize = 13.sp, color = Color.White)
    }
}
