#!/usr/bin/env python3
"""Parity / demo check: runs the EXACT scenario from the project spec (section
10) through all engines and prints the final JSON contract.

Scenario: enemy plays Hog Rider -> detect -> estimate path/ETA -> check hand,
elixir and opponent cycle -> evaluate defensive positions -> best placement.

This output is the reference for the Kotlin port (Predictor.kt must match the
same keys and behave the same way on identical input).
"""
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from ai.arena import Point, Unit                    # noqa: E402
from ai.prediction import TacticalPredictor         # noqa: E402


def main() -> int:
    pred = TacticalPredictor()
    pred.elixir.start_match(0.0)
    # all events fed chronologically (the engines refuse time travel):
    # opponent plays + player cycles, then the hog scenario at t=65
    events = [("opp", "giant", 5, 10.0), ("player", None, 3, 20.0),
              ("opp", "musketeer", 4, 18.0), ("player", None, 4, 40.0),
              ("opp", "archers", 3, 26.0), ("opp", "fireball", 4, 34.0),
              ("opp", "knight", 3, 42.0), ("opp", "hog-rider", 4, 63.0)]
    for kind, name_or_cost, cost, t in sorted(events, key=lambda e: e[-1]):
        if kind == "opp":
            pred.cycle.opponent_played(name_or_cost, t)
            pred.elixir.opponent_played(cost, t)
        else:
            pred.elixir.player_played(cost, t)

    hog = Unit(track_id=101, card="hog-rider", team="enemy",
               pos=Point(0.204, 0.31), velocity=Point(0.0, 0.02), confidence=0.94)

    out = pred.predict(
        t=65.0,
        hand=["mini-pekka", "knight", "musketeer", "skeleton"],
        units=[hog],
    )
    text = json.dumps(out, indent=2, ensure_ascii=False)
    print(text)
    dst = os.path.join(os.path.dirname(__file__), "..", "documentation", "example_prediction.json")
    with open(dst, "w", encoding="utf-8") as f:
        f.write(text)
    print("\nsaved ->", os.path.relpath(dst))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
