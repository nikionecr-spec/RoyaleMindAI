"""Arena geometry for Clash Royale (normalized coordinates).

Coordinate system
-----------------
All positions are normalized: x in [0, 1] left->right, y in [0, 1] top->bottom.
y < RIVER_Y  = enemy (top) half,  y > RIVER_Y = player (bottom) half.

Landmarks are taken from the dataset SPEC (SudhishNeel/clash-royale-detection,
SPEC.md): canonical frame is 568x896 px with an 18x32 tile grid and
    queen towers at (116,200) (461,200) (116,689) (461,689)
    king   towers at (284,108) (284,790)
Bridges are assumed aligned with the queen-tower columns (standard CR layout);
this is a documented assumption that can be re-calibrated per device.
"""
from __future__ import annotations

from dataclasses import dataclass, field
import math

CANON_W = 568.0
CANON_H = 896.0
TILES_X = 18.0
TILES_Y = 32.0


def _nx(px: float) -> float:
    return px / CANON_W


def _ny(py: float) -> float:
    return py / CANON_H


@dataclass(frozen=True)
class Point:
    x: float
    y: float

    def dist(self, other: "Point") -> float:
        return math.hypot(self.x - other.x, self.y - other.y)

    def dist_tiles(self, other: "Point") -> float:
        """Euclidean distance measured in (square) board tiles."""
        return math.hypot((self.x - other.x) * TILES_X, (self.y - other.y) * TILES_Y)

    def as_dict(self) -> dict:
        return {"x": round(self.x, 4), "y": round(self.y, 4)}


# --- landmarks (from dataset SPEC.md) ---------------------------------------
TOWER_LEFT_ENEMY = Point(_nx(116), _ny(200))
TOWER_RIGHT_ENEMY = Point(_nx(461), _ny(200))
TOWER_LEFT_OWN = Point(_nx(116), _ny(689))
TOWER_RIGHT_OWN = Point(_nx(461), _ny(689))
KING_ENEMY = Point(_nx(284), _ny(108))
KING_OWN = Point(_nx(284), _ny(790))

RIVER_Y = (TOWER_LEFT_ENEMY.y + TOWER_LEFT_OWN.y) / 2.0          # ~0.496
RIVER_HALF_TILES = 1.0                                           # river band +/- tiles
BRIDGE_LEFT = Point(TOWER_LEFT_ENEMY.x, RIVER_Y)
BRIDGE_RIGHT = Point(TOWER_RIGHT_ENEMY.x, RIVER_Y)

TOWER_RADIUS_TILES = 1.5          # physical footprint (no-deploy zone)
TOWER_RANGE_TILES = 7.5           # queen/king attack range for support scoring
KING_RADIUS_TILES = 1.8

# Movement speeds in tiles/minute (community-standard CR values)
SPEED_TILES_PER_MIN = {"slow": 45, "medium": 60, "fast": 90, "very_fast": 120}


@dataclass
class ArenaConfig:
    """Which towers are still alive - changes the deployable region."""
    enemy_left_alive: bool = True
    enemy_right_alive: bool = True
    own_left_alive: bool = True
    own_right_alive: bool = True

    # deploy band on the enemy side once their towers fall (y from tower row)
    enemy_side_min_y: float = TOWER_LEFT_ENEMY.y
    own_side_max_y: float = TOWER_LEFT_OWN.y


class Arena:
    """Deployable-region logic, lanes and geometric helpers."""

    def __init__(self, config: ArenaConfig | None = None):
        self.cfg = config or ArenaConfig()

    # ---- deployable region -------------------------------------------------
    def can_deploy(self, p: Point, flying: bool = False, spell: bool = False) -> bool:
        if not (0.02 <= p.x <= 0.98 and 0.02 <= p.y <= 0.98):
            return False
        # tower footprints are always blocked
        for t, r in self._towers():
            if p.dist_tiles(t) < r:
                return False
        # spells may be cast anywhere on the board
        if spell:
            return True
        # river band is forbidden for ground troops EXCEPT on the two bridges
        in_band = abs(p.y - RIVER_Y) * TILES_Y < RIVER_HALF_TILES
        on_bridge = (min(abs(p.x - BRIDGE_LEFT.x), abs(p.x - BRIDGE_RIGHT.x))
                     * TILES_X < 1.2)
        if not flying and in_band and not on_bridge:
            return False
        # side of the board (flying units may sit right on the river band)
        own_boundary = RIVER_Y - (RIVER_HALF_TILES / TILES_Y if flying else 0.0)
        if p.y >= own_boundary:
            return True                      # own half - always allowed
        # enemy half: allowed only beyond a destroyed enemy tower
        if p.x < 0.5:
            return (not self.cfg.enemy_left_alive) and p.y >= self.cfg.enemy_side_min_y - 0.02
        return (not self.cfg.enemy_right_alive) and p.y >= self.cfg.enemy_side_min_y - 0.02

    def _towers(self) -> list[tuple[Point, float]]:
        towers = []
        if self.cfg.enemy_left_alive:
            towers.append((TOWER_LEFT_ENEMY, TOWER_RADIUS_TILES))
        if self.cfg.enemy_right_alive:
            towers.append((TOWER_RIGHT_ENEMY, TOWER_RADIUS_TILES))
        if self.cfg.own_left_alive:
            towers.append((TOWER_LEFT_OWN, TOWER_RADIUS_TILES))
        if self.cfg.own_right_alive:
            towers.append((TOWER_RIGHT_OWN, TOWER_RADIUS_TILES))
        towers.append((KING_ENEMY, KING_RADIUS_TILES))
        towers.append((KING_OWN, KING_RADIUS_TILES))
        return towers

    # ---- lanes -------------------------------------------------------------
    def lane_path(self, side: str, enemy_side: bool = True) -> list[Point]:
        """Polyline a ground unit roughly follows down a lane."""
        if enemy_side:  # enemy unit walking toward us
            src = TOWER_LEFT_ENEMY if side == "left" else TOWER_RIGHT_ENEMY
            dst = TOWER_LEFT_OWN if side == "left" else TOWER_RIGHT_OWN
        else:
            src = TOWER_LEFT_OWN if side == "left" else TOWER_RIGHT_OWN
            dst = TOWER_LEFT_ENEMY if side == "left" else TOWER_RIGHT_ENEMY
        bridge = BRIDGE_LEFT if side == "left" else BRIDGE_RIGHT
        return [src, Point(bridge.x, bridge.y - 0.03), Point(bridge.x, bridge.y + 0.03), dst]

    def nearest_lane(self, p: Point) -> str:
        return "left" if p.x < 0.5 else "right"

    # ---- support / danger --------------------------------------------------
    def in_own_tower_support(self, p: Point) -> bool:
        for t in (TOWER_LEFT_OWN, TOWER_RIGHT_OWN, KING_OWN):
            alive = {TOWER_LEFT_OWN: self.cfg.own_left_alive,
                     TOWER_RIGHT_OWN: self.cfg.own_right_alive}.get(t, True)
            if alive and p.dist_tiles(t) <= TOWER_RANGE_TILES:
                return True
        return False

    def in_enemy_tower_range(self, p: Point) -> bool:
        for t, alive in ((TOWER_LEFT_ENEMY, self.cfg.enemy_left_alive),
                         (TOWER_RIGHT_ENEMY, self.cfg.enemy_right_alive),
                         (KING_ENEMY, True)):
            if alive and p.dist_tiles(t) <= TOWER_RANGE_TILES:
                return True
        return False

    # ---- travel time -------------------------------------------------------
    def travel_time(self, speed_class: str, dist_tiles: float) -> float:
        tpm = SPEED_TILES_PER_MIN.get(speed_class, 60)
        return dist_tiles / (tpm / 60.0)


@dataclass
class Unit:
    """A unit seen on the board (from the tracker)."""
    track_id: int
    card: str                      # dataset class name, e.g. "hog-rider"
    team: str                      # "own" | "enemy"
    pos: Point
    velocity: Point = field(default_factory=lambda: Point(0.0, 0.0))
    hp_fraction: float = 1.0
    confidence: float = 1.0

    def speed_tiles_per_sec(self) -> float:
        """Norm of velocity, in tiles/second (velocity is normalized-units/frame
        scaled by the caller)."""
        return self.velocity.dist(Point(0, 0)) * TILES_X


@dataclass
class TowerState:
    pos: Point
    hp_fraction: float = 1.0
    alive: bool = True
    is_king: bool = False


def normalized_to_screen(p: Point, screen_w: int, screen_h: int,
                         arena_rect: tuple[float, float, float, float]) -> tuple[int, int]:
    """Map a normalized arena point to device-screen pixels.

    ``arena_rect`` = (x0, y0, x1, y1) fraction of the screen occupied by the
    arena viewport (letterbox-safe).
    """
    x0, y0, x1, y1 = arena_rect
    sx = int(round((x0 + p.x * (x1 - x0)) * screen_w))
    sy = int(round((y0 + p.y * (y1 - y0)) * screen_h))
    return min(max(sx, 0), screen_w - 1), min(max(sy, 0), screen_h - 1)


def screen_to_normalized(px: int, py: int, screen_w: int, screen_h: int,
                         arena_rect: tuple[float, float, float, float]) -> Point:
    x0, y0, x1, y1 = arena_rect
    return Point((px / screen_w - x0) / (x1 - x0), (py / screen_h - y0) / (y1 - y0))


def arena_rect_for_aspect(screen_w: int, screen_h: int) -> tuple[float, float, float, float]:
    """Compute the arena viewport rect of a phone screen for the canonical
    568x896 arena (aspect 0.634). The game letterboxes; we fit height-first
    (portrait) then center horizontally."""
    canon_aspect = CANON_W / CANON_H
    screen_aspect = screen_w / screen_h
    if screen_aspect < canon_aspect:      # screen narrower than arena: pillarbox
        h = 1.0
        w = screen_aspect / canon_aspect
        return ((1 - w) / 2, 0.0, (1 + w) / 2, 1.0)
    w = 1.0
    h = canon_aspect / screen_aspect
    return (0.0, (1 - h) / 2, 1.0, (1 + h) / 2)
