package com.royalemind.ai

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.royalemind.ai.data.GameStateRepository
import com.royalemind.ai.data.CardDb
import com.royalemind.ai.engine.Predictor
import com.royalemind.ai.ui.RoyaleMindApp

class MainActivity : ComponentActivity() {

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                val svc = Intent(this, com.royalemind.ai.capture.ScreenCaptureService::class.java)
                    .putExtra(com.royalemind.ai.capture.ScreenCaptureService.EXTRA_RESULT_CODE,
                        result.resultCode)
                    .putExtra(com.royalemind.ai.capture.ScreenCaptureService.EXTRA_DATA, data)
                ContextCompat.startForegroundService(this, svc)
            }
        }

    private val overlayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // engines ready for the whole app
        GameStateRepository.predictor = Predictor(CardDb.get(this))

        setContent {
            RoyaleMindApp(
                onStartAssistant = { requestCapture() },
                onStopAssistant = { stopAssistant() },
                onEnableOverlay = { requestOverlay() }
            )
        }
    }

    private fun requestCapture() {
        if (!Settings.canDrawOverlays(this)) { requestOverlay(); return }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun requestOverlay() {
        overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:$packageName")))
    }

    private fun stopAssistant() {
        stopService(Intent(this, com.royalemind.ai.capture.ScreenCaptureService::class.java))
        stopService(Intent(this, com.royalemind.ai.overlay.OverlayService::class.java))
        GameStateRepository.setRunning(false)
    }
}
