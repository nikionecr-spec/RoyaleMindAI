package com.royalemind.ai.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.royalemind.ai.data.GameStateRepository
import com.royalemind.ai.engine.ArenaGeometry
import org.json.JSONObject

/**
 * Pure-display HUD (touch-transparent), redesigned for glanceability:
 *  - top-center panel: OPPONENT ELIXIR (big number + bar + confidence)
 *  - bottom-left panel: mini arena map + recommended card/action
 *  - bottom-right: my elixir bar
 *  - on the arena itself: red threat rings with lane paths + green placement marker
 */
class OverlayHudView(context: Context) : View(context) {
    private val panelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(205, 18, 10, 34) }
    private val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(245, 185, 66); textSize = 62f; isFakeBoldText = true
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(187, 170, 221); textSize = 27f
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }
    private val bigCard = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 36f; isFakeBoldText = true
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }
    private val barBack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 40, 24, 60) }
    private val goldBar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 185, 66) }
    private val purpleBar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(126, 62, 195) }
    private val greenFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 220, 120) }
    private val greenStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 220, 120); style = Paint.Style.STROKE; strokeWidth = 6f
    }
    private val red = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 230, 70, 70); style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val redDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(230, 70, 70); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val redDotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 70, 70) }
    private val allyDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(76, 201, 240) }
    private val purpleDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(126, 62, 195); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val arenaBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 20, 12, 40) }
    private val river = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 47, 82, 160) }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 190, 60); style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val pred = GameStateRepository.lastPrediction
        val w = width.toFloat(); val h = height.toFloat()

        if (pred != null) drawArenaMarkers(canvas, pred, w, h)
        drawOpponentElixir(canvas, pred, w)
        drawRecommendation(canvas, pred, h)
        drawMyElixir(canvas, pred, w, h)
    }

    /** red threat rings + lane paths + green placement marker directly on the arena */
    private fun drawArenaMarkers(canvas: Canvas, pred: JSONObject, w: Float, h: Float) {
        val ths = pred.optJSONArray("threats")
        if (ths != null) for (i in 0 until ths.length()) {
            val t = ths.optJSONObject(i) ?: continue
            val unit = GameStateRepository.lastUnits.firstOrNull {
                it.card == t.optString("card") && it.team == "enemy"
            } ?: continue
            val (sx, sy) = ArenaGeometry.normalizedToScreen(unit.pos, width, height)
            canvas.drawCircle(sx.toFloat(), sy.toFloat(), 56f, red)
            path.reset()
            path.moveTo(sx.toFloat(), sy.toFloat())
            val towerY = (ArenaGeometry.TOWER_Y_OWN * h).toFloat()
            path.quadTo(sx.toFloat(), (sy + towerY) / 2f, sx.toFloat(), towerY)
            canvas.drawPath(path, pathPaint)
        }
        val placement = pred.optJSONObject("placement") ?: return
        val p = ArenaGeometry.Point(placement.optDouble("x", 0.5),
            placement.optDouble("y", 0.5))
        val (sx, sy) = ArenaGeometry.normalizedToScreen(p, width, height)
        canvas.drawCircle(sx.toFloat(), sy.toFloat(), 46f, greenStroke)
        arrow(canvas, sx.toFloat(), sy.toFloat() - 80f)
    }

    /** top-center panel: opponent elixir estimate — the headline number */
    private fun drawOpponentElixir(canvas: Canvas, pred: JSONObject?, w: Float) {
        val ex = pred?.optJSONObject("elixir")
        val opp = ex?.optDouble("opponent_estimate", 0.0) ?: 0.0
        val conf = ex?.optDouble("opponent_confidence", 0.0) ?: 0.0
        val pw = 380f
        val l = w / 2f - pw / 2f; val t = 28f; val b = t + 148f
        canvas.drawRoundRect(l, t, l + pw, b, 22f, 22f, panelBg)
        canvas.drawText("اکسیر حریف", l + 18f, t + 40f, label)
        canvas.drawText("~%.1f".format(opp), l + 14f, t + 112f, gold)
        val bl = l + 158f; val bt = t + 62f; val bw = pw - 158f - 18f
        canvas.drawRoundRect(bl, bt, bl + bw, bt + 24f, 10f, 10f, barBack)
        canvas.drawRoundRect(bl, bt, bl + bw * (opp / 10.0).toFloat().coerceIn(0f, 1f),
            bt + 24f, 10f, 10f, goldBar)
        canvas.drawText("اطمینان ${(conf * 100).toInt()}٪", bl, bt + 62f, label)
    }

    /** bottom-left panel: mini arena map + recommended action/card */
    private fun drawRecommendation(canvas: Canvas, pred: JSONObject?, h: Float) {
        val mw = 150f; val mh = mw * 896f / 568f
        val ml = 34f; val mb = h - 34f; val mt = mb - mh - 74f
        canvas.drawRoundRect(ml - 10f, mt - 10f, ml + mw + 10f, mb, 20f, 20f, panelBg)
        drawMiniMap(canvas, ml, mt, mw, mh, pred)
        val action = pred?.optString("action").orEmpty()
        val card = pred?.optString("recommended_card").orEmpty()
        val act = when (action) {
            "ATTACK" -> "حمله"
            "DEFEND" -> "دفاع"
            "SAVE" -> "جمع اکسیر"
            "WAIT" -> "صبر"
            else -> ""
        }
        canvas.drawText(act, ml, mt + mh + 40f, bigCard)
        if (card.isNotEmpty()) canvas.drawText(card, ml, mt + mh + 68f, label)
    }

    /** bottom-right: my elixir */
    private fun drawMyElixir(canvas: Canvas, pred: JSONObject?, w: Float, h: Float) {
        val my = pred?.optJSONObject("elixir")?.optDouble("player", 0.0) ?: 0.0
        val l = w - 266f
        canvas.drawText("اکسیر من", l, h - 98f, label)
        canvas.drawText("%.1f".format(my), w - 66f, h - 98f, bigCard)
        canvas.drawRoundRect(l, h - 84f, w - 26f, h - 56f, 10f, 10f, barBack)
        canvas.drawRoundRect(l, h - 84f, l + 240f * (my / 10.0).toFloat().coerceIn(0f, 1f),
            h - 56f, 10f, 10f, purpleBar)
    }

    private fun drawMiniMap(canvas: Canvas, l: Float, t: Float, mw: Float, mh: Float, pred: JSONObject?) {
        canvas.drawRoundRect(l, t, l + mw, t + mh, 10f, 10f, arenaBg)
        canvas.drawRect(l, t + (ArenaGeometry.RIVER_Y - 0.03).toFloat() * mh,
            l + mw, t + (ArenaGeometry.RIVER_Y + 0.03).toFloat() * mh, river)
        fun px(nx: Double) = l + (nx * mw).toFloat()
        fun py(ny: Double) = t + (ny * mh).toFloat()
        listOf(ArenaGeometry.TOWER_X_LEFT to ArenaGeometry.TOWER_Y_ENEMY,
            ArenaGeometry.TOWER_X_RIGHT to ArenaGeometry.TOWER_Y_ENEMY,
            ArenaGeometry.KING_X to ArenaGeometry.KING_Y_ENEMY).forEach {
            canvas.drawCircle(px(it.first), py(it.second), 7f, redDot)
        }
        listOf(ArenaGeometry.TOWER_X_LEFT to ArenaGeometry.TOWER_Y_OWN,
            ArenaGeometry.TOWER_X_RIGHT to ArenaGeometry.TOWER_Y_OWN,
            ArenaGeometry.KING_X to ArenaGeometry.KING_Y_OWN).forEach {
            canvas.drawCircle(px(it.first), py(it.second), 7f, purpleDot)
        }
        GameStateRepository.lastUnits.forEach { u ->
            canvas.drawCircle(px(u.pos.x), py(u.pos.y), 5f,
                if (u.team == "enemy") redDotFill else allyDot)
        }
        pred?.optJSONObject("placement")?.let { pl ->
            val x = pl.optDouble("x", 0.5); val y = pl.optDouble("y", 0.5)
            canvas.drawCircle(px(x), py(y), 10f, greenFill)
            canvas.drawCircle(px(x), py(y), 14f, greenStroke)
        }
    }

    private fun arrow(canvas: Canvas, x: Float, y: Float) {
        val p = Path()
        p.moveTo(x, y - 40f)
        p.lineTo(x + 34f, y + 20f)
        p.lineTo(x - 34f, y + 20f)
        p.close()
        canvas.drawPath(p, greenFill)
    }
}
