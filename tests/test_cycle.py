from ai.card_cycle import CardCycleEngine


def setup_full_deck():
    eng = CardCycleEngine()
    # discover all 8 cards via plays (queue fills first, then hand rotates)
    cards = ["hog-rider", "musketeer", "knight", "skeleton", "fireball",
             "zap", "valkyrie", "cannon"]
    t = 0.0
    for c in cards:
        eng.opponent_played(c, t)
        t += 1.0
    return eng, cards


def test_discovery_phase_fills_queue_then_hand():
    eng = CardCycleEngine()
    eng.opponent_played("hog-rider", 0)
    eng.opponent_played("musketeer", 1)
    eng.opponent_played("knight", 2)
    eng.opponent_played("skeleton", 3)
    assert eng.opponent.queue.count(None) == 0
    assert eng.opponent.hand.count(None) == 4


def test_full_rotation_knows_all_eight():
    eng, cards = setup_full_deck()
    assert eng.opponent_deck_coverage() == 1.0
    known = set(eng.opponent_known_cards())
    assert known == set(cards)


def test_played_card_moves_to_back_of_queue():
    eng, _ = setup_full_deck()
    # knight was played at t=2; after full discovery play again hog-rider
    eng.opponent_played("hog-rider", 10)
    # hog must now be in the queue again (rotated), not in hand
    assert "hog-rider" in eng.opponent.queue
    assert "hog-rider" not in eng.opponent.hand


def test_evolution_flag_recorded():
    eng = CardCycleEngine()
    eng.opponent_played("knight-evolution", 0)
    base = "knight"
    assert base in eng.opponent.known
    assert eng.opponent.evolution_flags.get(base) is True


def test_champion_leaves_hand_and_returns_after_cooldown():
    eng = CardCycleEngine()
    eng.set_champions(["golden-knight"])
    eng.opponent.hand = ["golden-knight", "knight", "hog-rider", None]
    eng.opponent_played("golden-knight", 0)
    assert "golden-knight" not in eng.opponent.hand
    eng._fill_champion_return(5)
    assert "golden-knight" not in eng.opponent.hand      # still cooling down
    eng._fill_champion_return(11)
    assert "golden-knight" in eng.opponent.hand          # back after ~10 s


def test_player_hand_observed_and_play_rotation():
    eng = CardCycleEngine()
    eng.player_hand_observed(["hog-rider", "musketeer", "knight", "skeleton"])
    assert eng.player.hand == ["hog-rider", "musketeer", "knight", "skeleton"]
    eng.player_played(0, 1.0)
    assert eng.player.hand[0] is None                    # unknown next card


def test_next_predictions_order_matches_queue():
    eng, cards = setup_full_deck()
    preds = eng.opponent_next_cards(top_k=4)
    assert preds, "predictions must not be empty"
    probs = [p["probability"] for p in preds]
    assert probs == sorted(probs, reverse=True)
    assert all(0 < p["probability"] <= 0.55 for p in preds)
    # the card played 5th (first after discovery) sits at queue front -> next
    assert preds[0]["card"] == cards[4]


def test_snapshot_shape():
    eng, _ = setup_full_deck()
    snap = eng.snapshot()
    for key in ("hand", "queue", "known", "coverage", "next"):
        assert key in snap["opponent"]
