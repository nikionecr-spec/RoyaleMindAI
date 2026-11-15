package com.royalemind.ai.data

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.royalemind.ai.engine.BoardUnit
import com.royalemind.ai.engine.Predictor
import org.json.JSONObject

/**
 * Shared, observable state between the capture pipeline, the overlay and the UI.
 */
object GameStateRepository {
    var lastPrediction by mutableStateOf<JSONObject?>(null)
        private set
    var lastUnits by mutableStateOf<List<BoardUnit>>(emptyList())
        private set
    var fps by mutableStateOf(0f)
        private set
    var inferenceMs by mutableStateOf(0f)
        private set
    var frameCount by mutableStateOf(0L)
        private set
    var modelLoaded by mutableStateOf(false)
        private set
    var modelMessage by mutableStateOf("not initialized")
        private set
    var assistantRunning by mutableStateOf(false)
        private set

    var predictor: Predictor? = null

    fun publish(prediction: JSONObject?, units: List<BoardUnit>,
                fps: Float, ms: Float, frames: Long) {
        lastPrediction = prediction
        lastUnits = units
        this.fps = fps
        inferenceMs = ms
        frameCount = frames
    }

    fun setModelStatus(loaded: Boolean, message: String) {
        modelLoaded = loaded
        modelMessage = message
    }

    fun setRunning(running: Boolean) { assistantRunning = running }

    /** Extract the 4 hand card names from a hand-strip bitmap (via crop classifier)
     *  is a future step; for now the hand can be set manually in the UI. */
    var manualHand by mutableStateOf(listOf<String?, String?, String?, String?>(null, null, null, null))
}
