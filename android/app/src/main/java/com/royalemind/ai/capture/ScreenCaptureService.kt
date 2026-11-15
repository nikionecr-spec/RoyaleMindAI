package com.royalemind.ai.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import com.royalemind.ai.R
import com.royalemind.ai.data.GameStateRepository
import com.royalemind.ai.engine.ArenaGeometry
import com.royalemind.ai.engine.BoardUnit
import com.royalemind.ai.engine.ByteTrackerLite
import com.royalemind.ai.engine.Predictor
import com.royalemind.ai.engine.tracksToUnits
import com.royalemind.ai.vision.ImageUtils
import com.royalemind.ai.vision.OnnxDetector
import org.json.JSONObject

/**
 * MediaProjection screen capture + the full analysis loop:
 *   frame -> ONNX detect -> ByteTrack -> engines -> prediction -> repository.
 * Capture throttling + frame-skip keeps CPU low (no repeated processing).
 * This service NEVER sends any input to the game (analysis only).
 */
class ScreenCaptureService : Service() {
    companion object {
        const val TAG = "RoyaleMind"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val TARGET_FPS = 15.0        // 15-20 FPS processing budget
        private const val CHANNEL_ID = "royalemind_capture"
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var detector: OnnxDetector? = null
    private val tracker = ByteTrackerLite()
    private var predictor: Predictor? = null
    private lateinit var worker: HandlerThread
    private lateinit var workerHandler: Handler

    private var lastProcessAt = 0L
    private var frameN = 0L
    private var lastFpsAt = 0L
    private var fpsCount = 0
    private var screenW = 0
    private var screenH = 0
    private var captureBitmap: android.graphics.Bitmap? = null
    private var matchStartMs = 0L

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { stopSelf() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: return START_NOT_STICKY
        @Suppress("DEPRECATION") val data = intent.getParcelableExtra<android.content.Intent>(EXTRA_DATA)
            ?: return START_NOT_STICKY

        startAsForeground()
        initModel()
        startCapture(resultCode, data)
        GameStateRepository.setRunning(true)
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID,
            getString(R.string.notif_channel), android.app.NotificationManager.IMPORTANCE_LOW))
        val notification: Notification = android.app.Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_capture_title))
            .setContentText(getString(R.string.notif_capture_text))
            .setSmallIcon(R.drawable.ic_app)
            .build()
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }

    private fun initModel() {
        try {
            val classes = assets.open("classes.json").bufferedReader().use { it.readText() }
            val names = org.json.JSONArray(classes).let { arr ->
                List(arr.length()) { arr.getString(it) }
            }
            val bytes = assets.open("model/detector_int8.onnx").use { it.readBytes() }
            detector = OnnxDetector(bytes, names)
            GameStateRepository.setModelStatus(true, "detector_int8.onnx (${names.size} classes)")
        } catch (e: Exception) {
            Log.w(TAG, "model asset missing: ${e.message}")
            GameStateRepository.setModelStatus(false,
                "model asset not found - put detector_int8.onnx in app/src/main/assets/model/ (see docs)")
        }
        predictor = GameStateRepository.predictor
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val dm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val bounds = dm.currentWindowMetrics.bounds
            screenW = bounds.width(); screenH = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val display = (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay
            val size = android.util.DisplayMetrics()
            display.getRealMetrics(size)
            screenW = size.widthPixels; screenH = size.heightPixels
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, data)
        projection?.registerCallback(projectionCallback, Handler(mainLooper))

        predictor?.elixir?.startMatch(0.0)
        matchStartMs = System.currentTimeMillis()

        imageReader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.nanoTime()
            val minInterval = (1_000_000_000L / TARGET_FPS).toLong()
            if (now - lastProcessAt < minInterval) {
                reader.acquireLatestImage()?.close()   // drop frame (backpressure)
                return@setOnImageAvailableListener
            }
            lastProcessAt = now
            workerHandler.post { process(reader.acquireLatestImage()) }
        }, workerHandler)

        virtualDisplay = projection?.createVirtualDisplay(
            "RoyaleMindCapture", screenW, screenH, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, workerHandler)
    }

    private fun process(image: Image?) {
        if (image == null) return
        try {
            frameN++
            val t0 = System.nanoTime()
            val bmp = ImageUtils.rgbaToBitmap(image.planes[0].buffer, image.width,
                image.height, image.planes[0].rowStride, captureBitmap)
            captureBitmap = bmp

            val dets = detector?.detect(bmp) ?: emptyList()
            // units live in the arena viewport: filter out hand UI and top bars
            val arenaDets = dets.filter { it.bbox[1] > 0.06f && it.bbox[3] < 0.90f }
            val teamDets = arenaDets.map { d ->
                val team = if ((d.bbox[1] + d.bbox[3]) / 2f < ArenaGeometry.RIVER_Y.toFloat())
                    "enemy" else "own"
                com.royalemind.ai.engine.Detection(d.bbox, d.clsName, d.conf, team)
            }
            val (active, born) = tracker.update(teamDets)
            val units = tracksToUnits(active, fps = 15.0)

            // opponent plays = freshly confirmed enemy tracks
            val db = com.royalemind.ai.data.CardDb.get(this)
            val p = predictor
            if (p != null) {
                for (id in born) {
                    val t = active.firstOrNull { it.id == id } ?: continue
                    if (t.team == "enemy") {
                        val cost = db.cost(t.clsName)
                        if (cost != null) p.elixir.opponentPlayed(cost, elapsedMatchSec())
                        p.cycle.opponentPlayed(t.clsName, elapsedMatchSec())
                    }
                }
                val cfg = ArenaGeometry.Config()
                val prediction: JSONObject? =
                    if (GameStateRepository.modelLoaded) p.predict(
                        elapsedMatchSec(), GameStateRepository.manualHand, units, cfg)
                    else null
                val dtMs = (System.nanoTime() - t0) / 1e6f
                fpsCount++
                if (System.nanoTime() - lastFpsAt > 1_000_000_000L) {
                    GameStateRepository.publish(prediction, units, fpsCount.toFloat(),
                        dtMs, frameN)
                    fpsCount = 0; lastFpsAt = System.nanoTime()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "process failed", e)
        } finally {
            image?.close()
        }
    }

    private fun elapsedMatchSec(): Double =
        (System.currentTimeMillis() - matchStartMs) / 1000.0

    override fun onDestroy() {
        GameStateRepository.setRunning(false)
        try {
            virtualDisplay?.release()
            imageReader?.close()
            projection?.stop()
            detector?.close()
            tracker.reset()
        } catch (_: Exception) {}
        worker.quitSafely()
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        worker = HandlerThread("royalemind-worker").also { it.start() }
        workerHandler = Handler(worker.looper)
    }
}
