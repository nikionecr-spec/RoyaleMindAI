"""Tactical predictor: orchestrates all engines into the final JSON output.

This is the single entry point used by both the Python side (tests, offline
analysis) and mirrored by the Android app. LLMs are NOT in this loop - the
decision is purely rule-based and explainable.
"""
from __future__ import annotations

from ..arena import Arena, Unit, TowerState
from ..elixir import ElixirEngine
from ..card_cycle import CardCycleEngine
from ..tactical import TacticalEngine, GameState


class TacticalPredictor:
    def __init__(self, elixir: ElixirEngine | None = None,
                 cycle: CardCycleEngine | None = None,
                 tactical: TacticalEngine | None = None,
                 arena: Arena | None = None):
        self.arena = arena or Arena()
        self.elixir = elixir or ElixirEngine()
        self.cycle = cycle or CardCycleEngine()
        self.tactical = tactical or TacticalEngine(arena=self.arena)

    def predict(self, t: float, hand: list[str | None], units: list[Unit],
                own_towers: list[TowerState] | None = None,
                enemy_towers: list[TowerState] | None = None) -> dict:
        self.elixir.update(t)
        snap = self.elixir.snapshot(t)
        cycle_snap = self.cycle.snapshot()

        state = GameState(
            hand=hand,
            elixir=snap["player"],
            units=units,
            own_towers=own_towers or [],
            enemy_towers=enemy_towers or [],
            clock_s=self.elixir.elapsed(t),
            phase=snap["phase"],
            opponent_next_cards=cycle_snap["opponent"]["next"],
            opponent_known_count=int(cycle_snap["opponent"]["coverage"] * 8),
            vision_ok=bool(units),
        )
        moves = self.tactical.top_moves(state, k=3)
        best = moves[0]
        alt = [m.as_dict() for m in moves[1:]]

        return {
            "action": best.action,
            "recommended_card": best.card,
            "placement": best.as_dict()["placement"],
            "reason": best.reason,
            "confidence": best.confidence,
            "risk": best.risk,
            "reasons": best.reasons,
            "alternatives": alt,
            "elixir": snap,
            "opponent": cycle_snap["opponent"],
            "threats": [
                {"card": th["unit"].card, "lane": th["lane"],
                 "eta_to_tower_s": round(th["eta_tower"], 1),
                 "threat": round(th["threat"], 2)}
                for th in self.tactical.threats(state)[:5]
            ],
        }


__all__ = ["TacticalPredictor"]
