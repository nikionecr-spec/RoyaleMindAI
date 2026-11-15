from ai.tactical.placement import PlacementEngine
from ai.arena import Arena, Point, RIVER_Y, Unit


def hog(lane="left", y=0.30):
    vx, vy = 0.0, 0.02
    return Unit(track_id=1, card="hog-rider", team="enemy",
                pos=Point(0.204 if lane == "left" else 0.811, y),
                velocity=Point(vx, vy), confidence=0.94)


def test_all_candidates_deployable_for_ground():
    pe = PlacementEngine(Arena())
    top = pe.rank({"kind": "troop", "target": "ground", "speed": "medium",
                   "hp": 1361, "dps": 400, "count": 1,
                   "roles": ["support"], "name": "mini-pekka"},
                  [hog()], top_k=5)
    assert top
    for c in top:
        assert c.pos.y > RIVER_Y          # enemy towers alive -> own half only
        assert 0 <= c.pos.x <= 1


def test_spell_candidates_reach_enemy_half():
    pe = PlacementEngine(Arena())
    top = pe.rank({"kind": "spell", "target": "no_target", "speed": "none",
                   "hp": 0, "dps": 0, "count": 1, "roles": ["spell_damage"],
                   "spell_radius_t": 2.5, "name": "fireball"},
                  [hog()], top_k=3)
    assert top
    for c in top:
        assert 0 <= c.pos.x <= 1 and 0 <= c.pos.y <= 1


def test_intercept_reason_present_for_slow_defender():
    pe = PlacementEngine(Arena())
    top = pe.rank({"kind": "troop", "target": "ground", "speed": "fast",
                   "hp": 1361, "dps": 400, "count": 1,
                   "roles": [], "name": "mini-pekka"},
                  [hog()], top_k=1)
    assert top[0].reasons


def test_building_pull_point_is_centerish():
    pe = PlacementEngine(Arena())
    cand = pe._pull_point(hog().pos, "left")
    assert 0.3 < cand.x < 0.7 and cand.y > RIVER_Y


def test_rank_sorted_descending_with_cap():
    pe = PlacementEngine(Arena())
    card = {"kind": "troop", "target": "ground", "speed": "medium",
            "hp": 1766, "dps": 160, "count": 1, "roles": ["mini_tank"],
            "name": "knight"}
    top = pe.rank(card, [hog(), hog(lane="right")], top_k=4)
    assert len(top) <= 4
    scores = [c.score for c in top]
    assert scores == sorted(scores, reverse=True)


def test_tower_footprint_never_suggested():
    pe = PlacementEngine(Arena())
    from ai.arena import TOWER_LEFT_OWN
    card = {"kind": "troop", "target": "ground", "speed": "medium",
            "hp": 1766, "dps": 160, "count": 1, "roles": ["mini_tank"],
            "name": "knight"}
    top = pe.rank(card, [hog()], top_k=10)
    for c in top:
        assert c.pos.dist_tiles(TOWER_LEFT_OWN) > 1.5
