"""Tactical engine: multi-criteria move scoring (rule-based, no LLM).

Reads the current GameState (hand, elixir, units, towers, clock, opponent
cycle knowledge) and returns ranked tactical moves with the exact card,
a concrete placement point, the reasoning, risk level and confidence.
"""
from __future__ import annotations

import json
import os
from dataclasses import dataclass, field

from ..arena import (Arena, Unit, TowerState, RIVER_Y,
                     TOWER_LEFT_OWN, TOWER_RIGHT_OWN)
from .placement import PlacementEngine

_CARDS_PATH = os.path.join(os.path.dirname(__file__), "..", "data", "cards.json")


class CardDB:
    def __init__(self, path: str | None = None):
        path = path or _CARDS_PATH
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        self.cards: dict[str, dict] = {c["name"]: c for c in data["cards"]}

    def get(self, name: str) -> dict | None:
        card = self.cards.get(name)
        if card is None and name.endswith("-evolution"):
            card = self.cards.get(name[: -len("-evolution")])
            if card is not None:
                card = dict(card, evolution=True)
        if card is None and name.endswith("-hero"):
            card = self.cards.get(name[: -len("-hero")])
        return card

    def cost(self, name: str) -> int | None:
        card = self.get(name)
        return card["elixir"] if card else None


@dataclass
class GameState:
    hand: list[str | None]                       # 4 card class names (may be None)
    elixir: float                                # player elixir 0..10
    units: list[Unit] = field(default_factory=list)
    own_towers: list[TowerState] = field(default_factory=list)
    enemy_towers: list[TowerState] = field(default_factory=list)
    clock_s: float = 0.0
    phase: str = "single"                        # single | double | triple
    opponent_next_cards: list[dict] = field(default_factory=list)
    opponent_known_count: int = 0
    vision_ok: bool = True                       # any units detected at all?


# counter fit: which roles answer which threat composition
THREAT_ROLE_MATRIX = {
    "swarm": {"splash", "spell_damage", "reset"},
    "tank": set(),            # high dps handled numerically below
    "air": {"air_defense"},
    "win_condition": {"building", "mini_tank", "building_targeting"},
    "mobility": {"building", "mini_tank"},
}

DEFEND_THRESHOLD = 0.45
ATTACK_MIN_ELIXIR = 7.0
SAVE_MAX_ELIXIR = 3.0


@dataclass
class Move:
    action: str                 # DEFEND | ATTACK | SUPPORT | SAVE | WAIT
    card: str | None
    placement: tuple[float, float] | None
    score: float
    confidence: float
    risk: str
    reason: str
    reasons: list[str]

    def as_dict(self) -> dict:
        return {
            "action": self.action,
            "recommended_card": self.card,
            "placement": ({"x": round(self.placement[0], 4),
                           "y": round(self.placement[1], 4)} if self.placement else None),
            "reason": self.reason,
            "confidence": round(self.confidence, 3),
            "risk": self.risk,
            "reasons": self.reasons,
        }


class TacticalEngine:
    WEIGHTS = {
        "counter_fit": 0.35,
        "intercept": 0.25,
        "tower_support": 0.15,
        "elixir_efficiency": 0.15,
        "survival": 0.10,
    }

    def __init__(self, card_db: CardDB | None = None, arena: Arena | None = None):
        self.db = card_db or CardDB()
        self.arena = arena or Arena()
        self.placement = PlacementEngine(self.arena)

    # ---------------------------------------------------------- threats
    def threats(self, state: GameState) -> list[dict]:
        """Score every enemy unit: dps * presence / time-to-tower."""
        out = []
        for u in state.units:
            if u.team != "enemy":
                continue
            card = self.db.get(u.card) or {}
            lane = "left" if u.pos.x < 0.5 else "right"
            target = TOWER_LEFT_OWN if lane == "left" else TOWER_RIGHT_OWN
            speed = card.get("speed", "medium")
            eta = self.arena.travel_time(speed, max(0.5, u.pos.dist_tiles(target)))
            dps = card.get("dps", 100) * card.get("count", 1)
            hp = card.get("hp", 500) * card.get("count", 1)
            wc = "win_condition" in card.get("roles", []) or card.get("target") == "buildings_only"
            threat = (dps + hp / 20.0) / max(2.0, eta) * (1.6 if wc else 1.0) * u.confidence
            roles = set(card.get("roles", []))
            if card.get("flying"):
                roles.add("air")     # the threat itself flies -> needs air-targeting
            if card.get("count", 1) >= 3 or "swarm" in roles:
                roles.add("swarm")
            if hp >= 2000:
                roles.add("tank")
            if wc:
                roles.add("win_condition")
            if "mobility" in roles or "hog" in u.card:
                roles.add("mobility")
            out.append({"unit": u, "lane": lane, "eta_tower": eta,
                        "threat": threat, "roles": roles})
        out.sort(key=lambda x: -x["threat"])
        return out

    # ---------------------------------------------------------- decisions
    def best_move(self, state: GameState) -> Move:
        threats = self.threats(state)
        urgent = [t for t in threats if t["threat"] >= DEFEND_THRESHOLD]
        mode = self._choose_mode(state, urgent)
        if mode == "DEFEND":
            return self._best_defense(state, urgent[0] if urgent else threats[0])
        if mode == "ATTACK":
            return self._best_attack(state)
        if mode == "SAVE":
            return Move("SAVE", None, None, 0.5, 0.7, "low",
                        "Build elixir; no affordable answer now", [])
        return Move("WAIT", None, None, 0.4, 0.6, "low",
                    "No urgent threat; keep options open", [])

    def _choose_mode(self, state: GameState, urgent: list[dict]) -> str:
        if urgent:
            return "DEFEND"
        if state.elixir < SAVE_MAX_ELIXIR and state.phase == "single":
            return "SAVE"
        if state.elixir >= ATTACK_MIN_ELIXIR and self._enemy_tower_weakest(state) < 0.6:
            return "ATTACK"
        if state.elixir >= 9.0:
            return "ATTACK"
        return "WAIT"

    # ---------------------------------------------------------- defense
    def _best_defense(self, state: GameState, threat: dict) -> Move:
        ranked: list[tuple[float, str, list[str], str, float]] = []
        for name in state.hand:
            if not name:
                continue
            card = self.db.get(name)
            if card is None or card["elixir"] > state.elixir:
                continue
            fit, why = self._counter_fit(card, threat)
            if fit <= 0:
                continue
            spots = self.placement.rank(card, [threat["unit"]], top_k=1)
            if not spots:
                continue
            spot = spots[0]
            intercept = 1.0 if "before" in " ".join(spot.reasons) else 0.5
            support = 0.8 if any("tower" in r for r in spot.reasons) else 0.4
            eff = self._elixir_efficiency(card, threat)
            survival = 0.8 if spot.risk == "low" else 0.4
            score = (self.WEIGHTS["counter_fit"] * fit
                     + self.WEIGHTS["intercept"] * intercept
                     + self.WEIGHTS["tower_support"] * support
                     + self.WEIGHTS["elixir_efficiency"] * eff
                     + self.WEIGHTS["survival"] * survival)
            ranked.append((score, name, why + spot.reasons, spot.risk, fit))

        if not ranked:
            return Move("SAVE", None, None, 0.4, 0.5, "medium",
                        "No counter available at current elixir", [])
        ranked.sort(key=lambda x: -x[0])
        score, card_name, reasons, risk, _ = ranked[0]
        spot = self.placement.rank(self.db.get(card_name), [threat["unit"]], top_k=1)[0]
        conf = self._confidence(state, score)
        return Move("DEFEND", card_name, (spot.pos.x, spot.pos.y), score, conf, risk,
                    f"Defensive answer to {threat['unit'].card}", reasons)

    def _counter_fit(self, card: dict, threat: dict) -> tuple[float, list[str]]:
        roles = set(card.get("roles", []))
        threat_roles: set = threat["roles"]
        fit = 0.0
        reasons = []
        dps = card.get("dps", 0) * card.get("count", 1)
        hp = card.get("hp", 0) * card.get("count", 1)

        if "air" in threat_roles and card.get("target") not in ("both", "air"):
            return 0.0, ["cannot hit air"]
        if "swarm" in threat_roles:
            if roles & THREAT_ROLE_MATRIX["swarm"]:
                fit += 0.5
                reasons.append(f"{card['name']} clears swarms")
            elif card.get("count", 1) == 1 and card.get("range_t", 1) <= 1.5:
                fit -= 0.2
        if "tank" in threat_roles:
            if dps >= 300:
                fit += 0.5
                reasons.append(f"high dps ({dps}) vs tank")
        if "win_condition" in threat_roles or "mobility" in threat_roles:
            if roles & THREAT_ROLE_MATRIX["win_condition"]:
                fit += 0.4
                reasons.append("pulls/building defense vs win condition")
            elif dps >= 250:
                fit += 0.3
                reasons.append("hard hitter vs win condition")
        if not reasons and dps >= 150:
            fit = 0.35
            reasons.append("generic solid defender")
        if "air" in threat_roles and card.get("target") in ("both", "air"):
            fit += 0.3
            reasons.append("hits air")
        return max(0.0, min(1.0, fit)), reasons

    def _elixir_efficiency(self, card: dict, threat: dict) -> float:
        threat_cost = self.db.get(threat["unit"].card)
        cost = threat_cost["elixir"] if threat_cost else 4
        ratio = (cost + 1) / max(1, card["elixir"])
        return max(0.0, min(1.0, 0.5 * ratio))

    # ---------------------------------------------------------- attack
    def _best_attack(self, state: GameState) -> Move:
        weakest = self._enemy_tower_weakest(state)
        lane = "left" if self._weakest_tower_x(state) < 0.5 else "right"
        ranked = []
        for name in state.hand:
            if not name:
                continue
            card = self.db.get(name)
            if card is None or card["elixir"] > state.elixir:
                continue
            roles = set(card.get("roles", []))
            if "win_condition" in roles or "tank" in roles or "mobility" in roles:
                base = 0.6
            elif "support" in roles or roles & {"air_defense", "splash"}:
                base = 0.45
            else:
                base = 0.3
            hp_weakened = 0.15 * (1.0 - weakest)
            score = base + hp_weakened
            y = RIVER_Y - 0.10 if lane == "left" else RIVER_Y - 0.10
            x = 0.204 if lane == "left" else 0.811
            ranked.append((score, name, [f"attack {lane} lane (tower at {weakest:.0%})"],
                           "medium" if weakest > 0.4 else "low", score))
        if not ranked:
            return Move("WAIT", None, None, 0.3, 0.5, "low", "No affordable attacker", [])
        ranked.sort(key=lambda x: -x[0])
        score, card_name, reasons, risk, _ = ranked[0]
        return Move("ATTACK", card_name,
                    (0.204 if lane == "left" else 0.811, RIVER_Y - 0.10),
                    score, self._confidence(state, score), risk,
                    f"Push {lane} lane at the weaker tower", reasons)

    # ---------------------------------------------------------- helpers
    def _enemy_tower_weakest(self, state: GameState) -> float:
        if not state.enemy_towers:
            return 1.0
        return min(t.hp_fraction for t in state.enemy_towers if t.alive) or 1.0

    def _weakest_tower_x(self, state: GameState) -> float:
        alive = [t for t in state.enemy_towers if t.alive and not t.is_king]
        if not alive:
            return 0.5
        return min(alive, key=lambda t: t.hp_fraction).pos.x

    def _confidence(self, state: GameState, score: float) -> float:
        """Confidence blends the move score with input completeness."""
        inputs = 1.0
        if not state.vision_ok or not state.units:
            inputs *= 0.7
        if any(h is None for h in state.hand):
            inputs *= 0.85
        if state.opponent_known_count < 8:
            inputs *= 0.9
        return max(0.1, min(0.97, score * inputs))

    def top_moves(self, state: GameState, k: int = 3) -> list[Move]:
        """Best move plus alternatives (2nd/3rd ranked cards for the same action)."""
        best = self.best_move(state)
        alt: list[Move] = []
        threats = self.threats(state)
        urgent = [t for t in threats if t["threat"] >= DEFEND_THRESHOLD]
        if best.action == "DEFEND" and urgent:
            scored = []
            for name in state.hand:
                if not name:
                    continue
                card = self.db.get(name)
                if card is None or card["elixir"] > state.elixir:
                    continue
                fit, why = self._counter_fit(card, urgent[0])
                if fit > 0:
                    scored.append((fit, name, why))
            scored.sort(key=lambda x: -x[0])
            for fit, name, why in scored[1:k]:
                alt.append(Move("DEFEND", name, None, fit, self._confidence(state, fit),
                                "medium", f"Alternative defense with {name}", why))
        return [best] + alt
