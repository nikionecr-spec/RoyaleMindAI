from ai.elixir import ElixirEngine, ElixirConfig


def test_regen_single_phase():
    e = ElixirEngine()
    e.start_match(0.0)
    e.update(2.8)
    assert abs(e.player_value() - 6.0) < 0.05
    e.update(5.6)
    assert abs(e.player_value() - 7.0) < 0.05


def test_cap_at_ten():
    e = ElixirEngine()
    e.start_match(0.0)
    e.update(60)
    assert e.player_value() == 10.0


def test_double_elixir_rate():
    e = ElixirEngine()
    e.start_match(0.0)
    e.update(60)              # cap at 10
    e.player_played(10, 119.0)   # drain to 0 just before double phase
    e.update(121.8)           # 2.8 s inside double phase (elapsed >= 120) -> +2
    assert abs(e.player_value() - 2.0) < 0.1


def test_phase_boundaries():
    e = ElixirEngine()
    e.start_match(0.0)
    assert e.phase(119.9) == "single"
    assert e.phase(120.1) == "double"
    assert e.phase(180.1) == "triple"
    assert abs(e.rate(130) - 2 / 2.8) < 1e-6


def test_player_play_deducts_and_blocks_overdraft():
    e = ElixirEngine()
    e.start_match(0.0)
    ok = e.player_played(4, 0.1)
    assert ok and abs(e.player_value() - 1.0) < 0.05
    ok = e.player_played(5, 0.2)          # not enough -> leak
    assert not ok and e.player_value() == 0.0
    assert any(ev.kind == "leak" for ev in e.history)


def test_opponent_estimate_follows_mirror_regen():
    e = ElixirEngine()
    e.start_match(0.0)
    e.update(10)
    val, conf = e.opponent_estimate()
    assert val >= 8.0 and conf >= 0.9


def test_opponent_play_subtracts_and_flags_missed_plays():
    e = ElixirEngine()
    e.start_match(0.0)
    e.update(5)                 # 5/2.8 + 5 start = 6.79
    e.opponent_played(4, 5)
    val, conf = e.opponent_estimate()
    assert 2.5 < val < 4.2
    e.opponent_played(8, 5.5)   # impossible -> missed play flag
    assert any(ev.kind == "missed_play" for ev in e.history)
    _, conf2 = e.opponent_estimate()
    assert conf2 < conf


def test_confidence_decays_without_observations():
    e = ElixirEngine()
    e.start_match(0.0)
    e.update(5)
    _, c0 = e.opponent_estimate()
    e.update(40)                # no plays observed
    _, c1 = e.opponent_estimate()
    assert c1 < c0


def test_snapshot_shape():
    e = ElixirEngine()
    e.start_match(0.0)
    e.update(3)
    snap = e.snapshot(3.0)
    for key in ("player", "opponent_estimate", "opponent_confidence",
                "phase", "rate_per_second", "elapsed_s"):
        assert key in snap
