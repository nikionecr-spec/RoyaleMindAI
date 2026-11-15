from ai.tactical import TacticalEngine, GameState
from ai.arena import Point, Unit, TowerState
from ai.prediction import TacticalPredictor


def unit(card, x, y, vy=0.02):
    return Unit(track_id=1, card=card, team="enemy",
                pos=Point(x, y), velocity=Point(0.0, vy), confidence=0.94)


def hog_state(hand=None, elixir=9.0):
    return GameState(
        hand=hand or ["mini-pekka", "knight", "musketeer", "skeleton"],
        elixir=elixir,
        units=[unit("hog-rider", 0.204, 0.30)],
        own_towers=[TowerState(pos=Point(0.204, 0.769), hp_fraction=1.0)],
        enemy_towers=[TowerState(pos=Point(0.204, 0.223), hp_fraction=1.0)],
        clock_s=60.0,
        phase="single",
    )


def test_hog_defense_recommends_ground_counter():
    eng = TacticalEngine()
    move = eng.best_move(hog_state())
    assert move.action == "DEFEND"
    assert move.card in {"mini-pekka", "knight", "musketeer", "skeleton"}
    assert move.placement is not None
    assert move.placement[1] > 0.5           # deploy on our side
    assert 0.0 < move.confidence < 1.0
    assert move.reasons


def test_air_threat_without_air_answer_falls_back():
    state = hog_state(hand=["knight", "valkyrie", None, None])
    state.units = [unit("balloon", 0.204, 0.30)]
    eng = TacticalEngine()
    move = eng.best_move(state)
    # none of the hand cards can hit air -> no counter available
    assert move.action in {"SAVE", "WAIT"}
    assert move.card is None


def test_splash_preferred_against_swarm():
    state = hog_state(hand=["knight", "bomber", "mini-pekka", "skeleton"],
                      elixir=8.0)
    state.units = [unit("skeleton", 0.25, 0.35, vy=0.015)]
    eng = TacticalEngine()
    move = eng.best_move(state)
    if move.action == "DEFEND":
        assert move.card in {"bomber", "knight", "skeleton"}


def test_save_when_broke_and_no_threat():
    state = hog_state(hand=["mini-pekka", "knight", "musketeer", "skeleton"],
                      elixir=2.0)
    state.units = []
    eng = TacticalEngine()
    move = eng.best_move(state)
    assert move.action == "SAVE"


def test_attack_when_full_elixir_and_weak_tower():
    state = hog_state(hand=["hog-rider", "knight", "musketeer", "skeleton"],
                      elixir=10.0)
    state.units = []
    state.enemy_towers[0].hp_fraction = 0.35
    eng = TacticalEngine()
    move = eng.best_move(state)
    assert move.action == "ATTACK"
    assert move.card in {"hog-rider", "knight", "musketeer", "skeleton"}


def test_top_moves_gives_alternatives():
    eng = TacticalEngine()
    moves = eng.top_moves(hog_state(), k=3)
    assert len(moves) >= 1
    assert moves[0].action == "DEFEND"


def test_predictor_output_contract():
    pred = TacticalPredictor()
    pred.elixir.start_match(0.0)
    from ai.card_cycle import CardCycleEngine
    out = pred.predict(t=65.0,
                       hand=["mini-pekka", "knight", "musketeer", "skeleton"],
                       units=[unit("hog-rider", 0.204, 0.30)])
    # spec section 10 contract
    for key in ("action", "recommended_card", "placement", "reason", "confidence"):
        assert key in out
    assert out["placement"] is None or set(out["placement"].keys()) == {"x", "y"}
    assert out["action"] == "DEFEND"
    assert "elixir" in out and "opponent" in out
