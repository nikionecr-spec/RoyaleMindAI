"""ByteTrack-lite: multi-object tracking tuned for dense Clash Royale boards.

Implements the core ByteTrack idea (two-stage association: high-confidence
detections first, low-confidence second, so occluded units keep their IDs)
without heavy dependencies: constant-velocity motion model + greedy IoU
matching. All boxes are normalized [0..1] (x0, y0, x1, y1).
"""
from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field
import itertools


def iou(a: tuple[float, float, float, float], b: tuple[float, float, float, float]) -> float:
    ix0, iy0 = max(a[0], b[0]), max(a[1], b[1])
    ix1, iy1 = min(a[2], b[2]), min(a[3], b[3])
    iw, ih = max(0.0, ix1 - ix0), max(0.0, iy1 - iy0)
    inter = iw * ih
    if inter <= 0:
        return 0.0
    area_a = (a[2] - a[0]) * (a[3] - a[1])
    area_b = (b[2] - b[0]) * (b[3] - b[1])
    return inter / (area_a + area_b - inter)


@dataclass
class Detection:
    bbox: tuple[float, float, float, float]
    cls_name: str
    conf: float
    team: str = "enemy"          # assigned by arena position upstream


@dataclass
class Track:
    id: int
    cls_name: str
    team: str
    bbox: tuple[float, float, float, float]
    conf: float
    velocity: tuple[float, float] = (0.0, 0.0)   # normalized units / frame
    hits: int = 1
    time_since_update: int = 0
    confirmed: bool = False
    history: deque = field(default_factory=lambda: deque(maxlen=30))
    _last_center: tuple[float, float] | None = field(default=None, repr=False)

    @property
    def center(self) -> tuple[float, float]:
        x0, y0, x1, y1 = self.bbox
        return ((x0 + x1) / 2, (y0 + y1) / 2)


class ByteTrackerLite:
    """Greedy-IoU two-stage tracker.

    Parameters
    ----------
    high_conf : detections at/above this enter association stage 1
    low_conf  : detections at/above this (but below ``high_conf``) enter stage 2
    new_track_conf : minimum confidence to *spawn* a track
    confirm_hits   : consecutive hits before a track is reported as confirmed
    max_age        : frames a track survives without a matching detection
    iou_thr        : matching threshold
    """

    def __init__(self, high_conf: float = 0.5, low_conf: float = 0.15,
                 new_track_conf: float = 0.55, confirm_hits: int = 2,
                 max_age: int = 12, iou_thr: float = 0.3):
        self.high_conf = high_conf
        self.low_conf = low_conf
        self.new_track_conf = new_track_conf
        self.confirm_hits = confirm_hits
        self.max_age = max_age
        self.iou_thr = iou_thr
        self.tracks: list[Track] = []
        self._next_id = itertools.count(1)
        self._matched_this_frame: set[int] = set()

    # ------------------------------------------------------------------
    def update(self, detections: list[Detection]) -> tuple[list[Track], list[int], list[int]]:
        """Advance one frame.

        Returns ``(active_tracks, born_ids, dead_ids)`` where active tracks are
        confirmed tracks still alive after this frame.
        """
        born, dead = [], []
        self._matched_this_frame = set()
        self._predict()

        highs = [d for d in detections if d.conf >= self.high_conf]
        lows = [d for d in detections if self.low_conf <= d.conf < self.high_conf]

        # stage 1: high-confidence detections
        pairs = self._match(highs, self.tracks)
        # stage 2: low-confidence detections rescue the still-unmatched tracks;
        # the IoU gate is looser here (occluded boxes drift) - ByteTrack spirit
        unmatched = [t for t in self.tracks if id(t) not in self._matched_this_frame]
        pairs += self._match(lows, unmatched, iou_thr=max(0.15, self.iou_thr * 0.6))

        matched_dets: set[int] = set()
        for t, d in pairs:
            matched_dets.add(id(d))
            t.bbox = d.bbox
            t.cls_name = d.cls_name
            t.conf = d.conf
            t.hits += 1
            t.time_since_update = 0
            t.history.append((t.center, t.cls_name))
            if not t.confirmed and t.hits >= self.confirm_hits:
                t.confirmed = True
                born.append(t.id)

        # age unmatched tracks
        for t in self.tracks:
            if id(t) not in self._matched_this_frame:
                t.time_since_update += 1
                t.conf *= 0.7

        survivors: list[Track] = []
        for t in self.tracks:
            if t.time_since_update > self.max_age:
                dead.append(t.id)
            else:
                survivors.append(t)
        self.tracks = survivors

        # spawn new tracks from unmatched, confident-enough detections
        for d in detections:
            if id(d) in matched_dets or d.conf < self.new_track_conf:
                continue
            t = Track(id=next(self._next_id), cls_name=d.cls_name, team=d.team,
                      bbox=d.bbox, conf=d.conf)
            t.history.append((t.center, t.cls_name))
            self.tracks.append(t)

        self._estimate_velocity()
        active = [t for t in self.tracks if t.confirmed]
        return active, born, dead

    # ------------------------------------------------------------------
    def _match(self, dets: list[Detection], tracks: list[Track],
               iou_thr: float | None = None) -> list[tuple[Track, Detection]]:
        thr = self.iou_thr if iou_thr is None else iou_thr
        pairs: list[tuple[float, Track, int]] = []
        for t in tracks:
            for di, d in enumerate(dets):
                if t.cls_name != d.cls_name or t.team != d.team:
                    continue    # never switch class or side mid-track
                ov = iou(t.bbox, d.bbox)
                if ov >= thr:
                    pairs.append((ov, t, di))
        pairs.sort(key=lambda x: -x[0])
        used_d: set[int] = set()
        out: list[tuple[Track, Detection]] = []
        for _ov, t, di in pairs:
            if di in used_d or id(t) in self._matched_this_frame:
                continue
            used_d.add(di)
            self._matched_this_frame.add(id(t))
            out.append((t, dets[di]))
        return out

    # ------------------------------------------------------------------
    def _predict(self) -> None:
        for t in self.tracks:
            if t.time_since_update > 0:
                x0, y0, x1, y1 = t.bbox
                dx, dy = t.velocity
                t.bbox = (x0 + dx, y0 + dy, x1 + dx, y1 + dy)

    def _estimate_velocity(self) -> None:
        for t in self.tracks:
            c = t.center
            if t._last_center is not None and t.time_since_update == 0:
                dx, dy = c[0] - t._last_center[0], c[1] - t._last_center[1]
                t.velocity = (0.6 * t.velocity[0] + 0.4 * dx, 0.6 * t.velocity[1] + 0.4 * dy)
            t._last_center = c

    # ------------------------------------------------------------------
    def reset(self) -> None:
        self.tracks.clear()
        self._next_id = itertools.count(1)


def tracks_to_units(tracks: list[Track], fps: float = 15.0) -> list:
    """Convert tracker output to arena Units.

    Board position = feet of the bbox (center-x, bottom-y) - that is the tile a
    unit occupies in Clash Royale. Team is inferred from the side of the river.
    Velocity is converted to tiles/second.
    """
    from ..arena import Unit, Point, RIVER_Y, TILES_X, TILES_Y

    units = []
    for t in tracks:
        x0, y0, x1, y1 = t.bbox
        pos = Point((x0 + x1) / 2, y1)
        team = "enemy" if pos.y < RIVER_Y else "own"
        v = Point(t.velocity[0] * TILES_X * fps, t.velocity[1] * TILES_Y * fps)
        units.append(Unit(track_id=t.id, card=t.cls_name, team=team,
                          pos=pos, velocity=v, confidence=t.conf))
    return units
