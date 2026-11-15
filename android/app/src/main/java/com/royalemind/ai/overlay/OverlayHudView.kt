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
 * Pure-display HUD (touch-transparent):
 *  - green arrow at the recommended placement point
 *  - red circles on high-threat enemy units
 *  - lane path lines, elixir bar, suggestion text with confidence
 */
class OverlayHudView(context: Context) : View(context) {
    private val green = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 220, 120); style = Paint.Style.FILL
    }
    private val greenStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 220, 120); style = Paint.Style.STROKE; strokeWidth = 6f
    }
    private val red = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(230, 70, 70); style = Paint.Style.STROKE; strokeWidth = 5f
    }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 34f; isShadowLayer = true
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }
    private val barBack = Paint().apply { color = Color.argb(160, 0, 0, 0) }
    private val barFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(126, 62, 195) }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 190, 60); style = Paint.Style.STROKE; strokeWidth = 5f
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val pred = GameStateRepository.lastPrediction ?: return
        val w = width.toFloat(); val h = height.toFloat()

        // threat circles + lanes
        val ths = pred.optJSONArray("threats")
        if (ths != null) {
            for (i in 0 until ths.length()) {
                val t = ths.optJSONObject(i) ?: continue
                val unit = GameStateRepository.lastUnits.firstOrNull {
                    it.card == t.optString("card") && it.team == "enemy"
                } ?: continue
                val (sx, sy) = ArenaGeometry.normalizedToScreen(unit.pos, width, height)
                canvas.drawCircle(sx.toFloat(), sy.toFloat(), 64f, red)
                path.reset()
                path.moveTo(sx.toFloat(), sy.toFloat())
                val towerY = (ArenaGeometry.TOWER_Y_OWN * h).toFloat()
                path.quadTo(sx, (sy + towerY) / 2, sx, towerY)
                canvas.drawPath(path, pathPaint)
            }
        }

        // placement marker
        val placement = pred.optJSONObject("placement")
        if (placement != null) {
            val p = ArenaGeometry.Point(placement.optDouble("x", 0.5),
                placement.optDouble("y", 0.5))
            val (sx, sy) = ArenaGeometry.normalizedToScreen(p, width, height)
            canvas.drawCircle(sx.toFloat(), sy.toFloat(), 46f, greenStroke)
            arrow(canvas, sx.toFloat(), sy.toFloat() - 80f)
            val card = pred.optString("recommended_card")
            if (card.isNotEmpty()) canvas.drawText(card, sx.toFloat() + 60f,
                sy.toFloat() - 70f, white)
        }

        // suggestion text
        val action = pred.optString("action")
        val conf = pred.optDouble("confidence", 0.0)
        canvas.drawText("$action  •  ${(conf * 100).toInt()}%", 40f, h - 150f, white)
        val reason = pred.optString("reason")
        if (reason.isNotEmpty()) canvas.drawText(reason, 40f, h - 108f, white)

        // elixir bar (player + estimated opponent)
        val ex = pred.optJSONObject("elixir")
        if (ex != null) {
            val player = ex.optDouble("player", 0.0).toFloat()
            val opp = ex.optDouble("opponent_estimate", 0.0).toFloat()
            val oppConf = ex.optDouble("opponent_confidence", 0.0).toFloat()
            drawBar(canvas, w - 60f, 40f, 260f, 26f, player / 10f, barFill)
            drawBar(canvas, w - 60f, 78f, 260f, 18f, opp / 10f,
                Paint().apply { color = Color.argb(220, 60, 130, 220) })
            canvas.drawText("opp ~${(opp * 10).toInt()}/10 (${(oppConf * 100).toInt()}%)",
                w - 460f, 96f, white)
        }
    }

    private fun drawBar(canvas: Canvas, right: Float, top: Float, len: Float,
                        height: Float, fraction: Float, paint: Paint) {
        canvas.drawRoundRect(right - len, top, right, top + height, 8f, 8f, barBack)
        canvas.drawRoundRect(right - len * fraction, top, right, top + height, 8f, 8f, paint)
    }

    private fun arrow(canvas: Canvas, x: Float, y: Float) {
        val p = Path()
        p.moveTo(x, y - 40f)
        p.lineTo(x + 34f, y + 20f)
        p.lineTo(x - 34f, y + 20f)
        p.close()
        canvas.drawPath(p, green)
    }
}
