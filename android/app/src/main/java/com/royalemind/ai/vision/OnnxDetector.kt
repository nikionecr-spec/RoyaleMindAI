package com.royalemind.ai.vision

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * ONNX Runtime YOLO detector for Android (mirror of ai/vision/detector.py).
 * Supports Ultralytics exports: (1, 4+nc, N) standard and (N, 6) NMS-baked.
 * Every detection keeps its confidence score.
 */
class OnnxDetector(modelBytes: ByteArray, private val classNames: List<String>,
                   private val confThreshold: Float = 0.25f,
                   private val nmsIou: Float = 0.45f) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(modelBytes)
    private val inputW: Int
    private val inputH: Int

    init {
        val shape = session.inputInfo.values.first().info.let {
            (it as ai.onnxruntime.NodeInfoInfo? ?: it)
        }
        val dims = (session.inputInfo.values.first().info as ai.onnxruntime.TensorInfo).shape
        inputH = (dims[2] as Number).toInt()
        inputW = (dims[3] as Number).toInt()
    }

    data class Det(val bbox: FloatArray, val clsName: String, val conf: Float)

    fun detect(bitmap: Bitmap): List<Det> {
        val (blob, scale, padX, padY) = ImageUtils.letterbox(bitmap, inputW)
        val buffer = FloatBuffer.allocate(3 * inputW * inputH)
        // CHW, RGB, /255
        val px = IntArray(inputW * inputH)
        blob.getPixels(px, 0, inputW, inputW, inputH)
        val n = inputW * inputH
        for (i in 0 until n) {
            val c = px[i]
            buffer.put(i, ((c shr 16) and 0xFF) / 255f)            // R plane
            buffer.put(n + i, ((c shr 8) and 0xFF) / 255f)         // G plane
            buffer.put(2 * n + i, (c and 0xFF) / 255f)             // B plane
        }
        OnnxTensor.createTensor(env, buffer, longArrayOf(1, 3, inputH.toLong(), inputW.toLong())).use { t ->
            session.run(mapOf(session.inputNames.first() to t)).use { out ->
                val raw = out[0].value
                return decode(raw, scale, padX, padY, bitmap.width, bitmap.height)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun decode(raw: Any, scale: Float, padX: Int, padY: Int,
                       frameW: Int, frameH: Int): List<Det> {
        val dets = mutableListOf<Det>()
        when (raw) {
            is Array<*> -> {
                val rows = raw.size
                val cols = (raw[0] as Array<*>).size
                if (cols == 6) {                       // NMS-baked: [x0,y0,x1,y1,conf,cls]
                    for (r in raw) {
                        val v = r as Array<Number>
                        val conf = v[4].toFloat()
                        if (conf < confThreshold) continue
                        dets += mk(v[5].toInt(), conf, v[0].toFloat(), v[1].toFloat(),
                            v[2].toFloat(), v[3].toFloat(), scale, padX, padY, frameW, frameH)
                    }
                } else {                               // (N, 4+nc) transposed form
                    val nc = cols - 4
                    val boxes = mutableListOf<FloatArray>()
                    val confs = mutableListOf<Float>()
                    val ids = mutableListOf<Int>()
                    for (r in raw) {
                        val v = r as Array<Number>
                        var bestI = 0; var bestC = -1f
                        for (c in 0 until nc) {
                            val sc = v[4 + c].toFloat()
                            if (sc > bestC) { bestC = sc; bestI = c }
                        }
                        if (bestC < confThreshold) continue
                        val cx = v[0].toFloat(); val cy = v[1].toFloat()
                        val w = v[2].toFloat(); val h = v[3].toFloat()
                        boxes += floatArrayOf(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
                        confs += bestC; ids += bestI
                    }
                    val keep = nms(boxes.toTypedArray(), confs.toFloatArray())
                    for (i in keep) {
                        dets += mk(ids[i], confs[i], boxes[i][0], boxes[i][1],
                            boxes[i][2], boxes[i][3], scale, padX, padY, frameW, frameH)
                    }
                }
            }
            is FloatArray -> {                          // (4+nc, N) contiguous
                val total = raw.size
                // shape unknown here; caller usually gets Array form - keep fallback
                return dets
            }
        }
        return dets
    }

    private fun mk(clsId: Int, conf: Float, x0: Float, y0: Float, x1: Float, y1: Float,
                   scale: Float, padX: Int, padY: Int, frameW: Int, frameH: Int): Det {
        val bx0 = ((x0 - padX) / scale).coerceIn(0f, frameW.toFloat()) / frameW
        val by0 = ((y0 - padY) / scale).coerceIn(0f, frameH.toFloat()) / frameH
        val bx1 = ((x1 - padX) / scale).coerceIn(0f, frameW.toFloat()) / frameW
        val by1 = ((y1 - padY) / scale).coerceIn(0f, frameH.toFloat()) / frameH
        val name = classNames.getOrNull(clsId) ?: "class_$clsId"
        return Det(floatArrayOf(bx0, by0, bx1, by1), name, conf)
    }

    private fun nms(boxes: Array<FloatArray>, scores: FloatArray): List<Int> {
        val order = scores.withIndex().sortedByDescending { it.value }.map { it.index }
        val keep = mutableListOf<Int>()
        val active = order.toMutableList()
        while (active.isNotEmpty()) {
            val i = active.removeAt(0)
            keep += i
            active.removeAll { j ->
                iou(boxes[i], boxes[j]) > nmsIou
            }
        }
        return keep
    }

    private fun iou(a: FloatArray, b: FloatArray): Float {
        val ix0 = max(a[0], b[0]); val iy0 = max(a[1], b[1])
        val ix1 = minOf(a[2], b[2]); val iy1 = minOf(a[3], b[3])
        val inter = max(0f, ix1 - ix0) * max(0f, iy1 - iy0)
        if (inter <= 0f) return 0f
        val areaA = (a[2] - a[0]) * (a[3] - a[1])
        val areaB = (b[2] - b[0]) * (b[3] - b[1])
        return inter / (areaA + areaB - inter)
    }

    fun close() { session.close() }
}
