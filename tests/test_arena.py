import math

from ai.arena import (
    Arena, ArenaConfig, Point, RIVER_Y, TOWER_LEFT_ENEMY, TOWER_RIGHT_ENEMY,
    TOWER_LEFT_OWN, TOWER_RIGHT_OWN, KING_OWN, BRIDGE_LEFT, BRIDGE_RIGHT,
    normalized_to_screen, screen_to_normalized, arena_rect_for_aspect,
    SPEED_TILES_PER_MIN,
)


def test_landmarks_match_dataset_spec():
    # queen towers from SPEC.md: (116,200) (461,200) (116,689) (461,689) on 568x896
    assert math.isclose(TOWER_LEFT_ENEMY.x, 116 / 568, abs_tol=1e-6)
    assert math.isclose(TOWER_RIGHT_ENEMY.y, 200 / 896, abs_tol=1e-6)
    assert math.isclose(TOWER_LEFT_OWN.y, 689 / 896, abs_tol=1e-6)
    assert math.isclose(KING_OWN.y, 790 / 896, abs_tol=1e-6)
    assert 0.45 < RIVER_Y < 0.55


def test_bridges_align_with_tower_columns():
    assert BRIDGE_LEFT.x == TOWER_LEFT_ENEMY.x
    assert BRIDGE_RIGHT.x == TOWER_RIGHT_ENEMY.x


def test_can_deploy_own_half_ok():
    arena = Arena()
    assert arena.can_deploy(Point(0.3, 0.7))
    assert arena.can_deploy(Point(0.7, 0.7))


def test_river_blocked_for_ground_allowed_for_air_and_spells():
    arena = Arena()
    river_mid = Point(0.5, RIVER_Y)
    assert not arena.can_deploy(river_mid)                      # ground
    assert arena.can_deploy(river_mid, flying=True)             # air
    assert arena.can_deploy(river_mid, spell=True)              # spell


def test_enemy_half_blocked_until_tower_destroyed():
    arena = Arena(ArenaConfig(enemy_left_alive=False))
    assert arena.can_deploy(Point(0.25, 0.40))      # left lane, enemy side now allowed
    assert not arena.can_deploy(Point(0.75, 0.40))  # right tower still alive


def test_tower_footprint_blocked():
    arena = Arena()
    assert not arena.can_deploy(Point(TOWER_LEFT_OWN.x, TOWER_LEFT_OWN.y))
    assert arena.can_deploy(Point(TOWER_LEFT_OWN.x, TOWER_LEFT_OWN.y + 0.08))


def test_lane_path_and_nearest_lane():
    arena = Arena()
    path = arena.lane_path("left", enemy_side=True)
    assert len(path) == 4
    assert path[0].y < RIVER_Y < path[-1].y
    assert arena.nearest_lane(Point(0.1, 0.6)) == "left"
    assert arena.nearest_lane(Point(0.9, 0.6)) == "right"


def test_travel_time_hog_rider_very_fast():
    arena = Arena()
    # 120 tiles/min = 2 tiles/s -> 6 tiles in 3 s
    assert math.isclose(arena.travel_time("very_fast", 6.0), 3.0, abs_tol=1e-6)


def test_screen_mapping_roundtrip():
    rect = arena_rect_for_aspect(1080, 2400)
    p = Point(0.3, 0.6)
    sx, sy = normalized_to_screen(p, 1080, 2400, rect)
    q = screen_to_normalized(sx, sy, 1080, 2400, rect)
    assert abs(q.x - p.x) < 0.01 and abs(q.y - p.y) < 0.01
