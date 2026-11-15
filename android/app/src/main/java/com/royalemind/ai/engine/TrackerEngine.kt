package com.royalemind.ai.engine

import kotlin.math.abs
import kotlin.math.max

/**
 * ByteTrack-lite port (mirror of ai/tracking/tracker.py):
 * two-stage IoU association, class/team locked, constant-velocity motion model.
 * Boxes are normalized [0..1] (x0, y0, x1, y1).
 */
data class Detection(
    val bbox: FloatArray,          // x0, y0, x1, y1
    val clsName: String,
    val conf: Float,
    val team: String = "enemy"
)

data class Track(
    val id: Int,
    var clsName: String,
    val team: String,
    var bbox: FloatArray,
    var conf: Float,
    var velocity: Pair<Float, Float> = 0f to 0f,
    var hits: Int = 1,
    var timeSinceUpdate: Int = 0,
    var confirmed: Boolean = false
) {
    val center: Pair<Float, Float>
        get() = ((bbox[0] + bbox[2]) / 2f) to ((bbox[1] + bbox[3]) / 2f)
}

class ByteTrackerLite(
    private val highConf: Float = 0.5f,
    private val lowConf: Float = 0.15f,
    private val newTrackConf: Float = 0.55f,
    private val confirmHits: Int = 2,
    private val maxAge: Int = 12,
    private val iouThr: Float = 0.3f
) {
    val tracks = mutableListOf<Track>()
    private var nextId = 1

    private fun iou(a: FloatArray, b: FloatArray): Float {
        val ix0 = max(a[0], b[0]); val iy0 = max(a[1], b[1])
        val ix1 = minOf(a[2], b[2]); val iy1 = minOf(a[3], b[3])
        val iw = max(0f, ix1 - ix0); val ih = max(0f, iy1 - iy0)
        val inter = iw * ih
        if (inter <= 0f) return 0f
        val areaA = (a[2] - a[0]) * (a[3] - a[1])
        val areaB = (b[2] - b[0]) * (b[3] - b[1])
        return inter / (areaA + areaB - inter)
    }

    fun update(detections: List<Detection>): Pair<List<Track>, List<Int>> {
        val born = mutableListOf<Int>()
        val dead = mutableListOf<Int>()
        predict()
        val matched = HashSet<Track>()
        val usedDets = HashSet<Detection>()

        fun matchStage(dets: List<Detection>, thr: Float) {
            val pairs = mutableListOf<Triple<Float, Track, Detection>>()
            for (t in tracks) {
                if (matched.contains(t)) continue
                for (d in dets) {
                    if (d in usedDets) continue
                    if (t.clsName != d.clsName || t.team != d.team) continue
                    val ov = iou(t.bbox, d.bbox)
                    if (ov >= thr) pairs += Triple(ov, t, d)
                }
            }
            pairs.sortByDescending { it.first }
            for ((_, t, d) in pairs) {
                if (matched.contains(t) || d in usedDets) continue
                matched += t; usedDets += d
                t.bbox = d.bbox; t.clsName = d.clsName; t.conf = d.conf
                t.hits += 1; t.timeSinceUpdate = 0
                if (!t.confirmed && t.hits >= confirmHits) { t.confirmed = true; born += t.id }
            }
        }

        matchStage(detections.filter { it.conf >= highConf }, iouThr)
        // stage 2: looser gate rescues occluded tracks (ByteTrack spirit)
        matchStage(detections.filter { it.conf >= lowConf && it.conf < highConf }, max(0.15f, iouThr * 0.6f))

        // age + remove
        val survivors = mutableListOf<Track>()
        for (t in tracks) {
            if (!matched.contains(t)) {
                t.timeSinceUpdate += 1
                t.conf *= 0.7f
            }
            if (t.timeSinceUpdate > maxAge) dead += t.id else survivors += t
        }
        tracks.clear(); tracks += survivors

        for (d in detections) {
            if (d in usedDets || d.conf < newTrackConf) continue
            tracks += Track(nextId++, d.clsName, d.team, d.bbox, d.conf)
        }
        estimateVelocity()
        return tracks.filter { it.confirmed } to dead
    }

    private fun predict() {
        for (t in tracks) if (t.timeSinceUpdate > 0) {
            val (dx, dy) = t.velocity
            t.bbox = floatArrayOf(t.bbox[0] + dx, t.bbox[1] + dy, t.bbox[2] + dx, t.bbox[3] + dy)
        }
    }

    private val lastCenters = HashMap<Int, Pair<Float, Float>>()
    private fun estimateVelocity() {
        for (t in tracks) {
            val c = t.center
            val last = lastCenters[t.id]
            if (last != null && t.timeSinceUpdate == 0) {
                val dx = 0.6f * t.velocity.first + 0.4f * (c.first - last.first)
                val dy = 0.6f * t.velocity.second + 0.4f * (c.second - last.second)
                t.velocity = dx to dy
            }
            lastCenters[t.id] = c
        }
    }

    fun reset() { tracks.clear(); lastCenters.clear() }
}

/** Board position = feet of the bbox; team from river side. Velocity -> tiles/sec. */
fun tracksToUnits(tracks: List<Track>, fps: Double): List<BoardUnit> {
    val out = mutableListOf<BoardUnit>()
    for (t in tracks) {
        val pos = ArenaGeometry.Point(
            ((t.bbox[0] + t.bbox[2]) / 2.0).toDouble(), t.bbox[3].toDouble())
        val team = if (pos.y < ArenaGeometry.RIVER_Y) "enemy" else "own"
        val vx = t.velocity.first * ArenaGeometry.TILES_X * fps
        val vy = t.velocity.second * ArenaGeometry.TILES_Y * fps
        out += BoardUnit(t.id, t.clsName, team, pos,
            ArenaGeometry.Point(vx.toDouble(), vy.toDouble()), t.conf.toDouble())
    }
    return out
}

data class BoardUnit(
    val trackId: Int,
    val card: String,
    val team: String,
    val pos: ArenaGeometry.Point,
    val velocity: ArenaGeometry.Point,
    val confidence: Double
) {
    fun speedTilesPerSec() = velocity.distTiles(ArenaGeometry.Point(0.0, 0.0))
    val descending: Boolean get() = velocity.y > 0
}
