package com.royalemind.ai.overlay

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.royalemind.ai.R
import com.royalemind.ai.data.GameStateRepository

/**
 * Floating overlay: a touch-transparent HUD (green placement marker, threat
 * circles, elixir bar, suggestion text) plus a small draggable control bubble.
 * The HUD NEVER touches the game; only the tiny control bubble is interactive.
 */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var hudView: OverlayHudView? = null
    private var controlView: View? = null
    private var ticker: android.os.Handler? = null
    private var minimized = false

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InflateParams")
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addHud()
        addControlBubble()
        startTicking()
    }

    private fun addHud() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT)
        hudView = OverlayHudView(this)
        wm.addView(hudView, params)
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun addControlBubble() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 40; params.y = 200

        val v = View(this).apply {
            background = androidx.core.content.ContextCompat.getDrawable(
                this@OverlayService, R.drawable.ic_control_bubble)
            layoutParams = android.view.ViewGroup.LayoutParams(96, 96)
            elevation = 8f
        }
        var downX = 0f; var downY = 0f; var startX = params.x; var startY = params.y
        var moved = false
        v.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    startX = params.x; startY = params.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX; val dy = ev.rawY - downY
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true
                    params.x = startX + dx.toInt(); params.y = startY + dy.toInt()
                    wm.updateViewLayout(v, params); true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onBubbleTap()
                    true
                }
                else -> false
            }
        }
        controlView = v
        wm.addView(v, params)
    }

    private fun onBubbleTap() {
        minimized = !minimized
        hudView?.visibility = if (minimized) View.GONE else View.VISIBLE
    }

    private fun startTicking() {
        ticker = android.os.Handler(android.os.Looper.getMainLooper())
        ticker?.post(object : Runnable {
            override fun run() {
                hudView?.invalidate()
                ticker?.postDelayed(this, 250)   // 4 Hz HUD refresh is enough
            }
        })
    }

    override fun onDestroy() {
        try { hudView?.let { wm.removeView(it) }; controlView?.let { wm.removeView(it) } }
        catch (_: Exception) {}
        ticker?.callbacks?.clear()
        GameStateRepository.setRunning(false)
        super.onDestroy()
    }
}
