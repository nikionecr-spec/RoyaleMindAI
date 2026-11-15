"""Elixir engine: player elixir accounting + opponent elixir estimation.

Constants follow the game's public mechanics: 1 elixir per 2.8 s in single
elixir, x2 after 120 s (double), x3 in overtime. These are configurable so
special modes can be supported.

The opponent bar is never directly readable from pixels during a match, so the
opponent value is an *estimate*: we start from the same regen curve and
subtract every observed opponent play. All estimates carry a confidence score
and are clamped; if the model would exceed the cap we log a missed-play event
instead of silently inventing elixir.
"""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class ElixirConfig:
    cap: float = 10.0
    start: float = 5.0
    period_s: float = 2.8          # seconds per 1 elixir
    double_from_s: float = 120.0   # x2 regen after this match time
    triple_from_s: float = 180.0   # x3 regen (overtime)


@dataclass
class ElixirEvent:
    t: float
    kind: str          # "play" | "cap" | "missed_play" | "leak"
    value: float
    detail: str = ""


class ElixirEngine:
    def __init__(self, cfg: ElixirConfig | None = None):
        self.cfg = cfg or ElixirConfig()
        self._match_start: float | None = None
        self.player: float = self.cfg.start
        self.opponent: float = self.cfg.start
        self.opponent_confidence: float = 1.0
        self.history: list[ElixirEvent] = []
        self._last_update: float | None = None

    # ------------------------------------------------------------------ clock
    def start_match(self, t: float) -> None:
        self._match_start = t
        self._last_update = t
        self.player = self.cfg.start
        self.opponent = self.cfg.start
        self.opponent_confidence = 1.0
        self.history.clear()

    def elapsed(self, t: float) -> float:
        if self._match_start is None:
            return 0.0
        return max(0.0, t - self._match_start)

    def phase(self, t: float) -> str:
        e = self.elapsed(t)
        if e >= self.cfg.triple_from_s:
            return "triple"
        if e >= self.cfg.double_from_s:
            return "double"
        return "single"

    def rate(self, t: float) -> float:
        p = self.phase(t)
        return {"single": 1.0, "double": 2.0, "triple": 3.0}[p] / self.cfg.period_s

    # ------------------------------------------------------------------ regen
    def update(self, t: float) -> None:
        """Advance both bars to time ``t`` (seconds, same clock as start_match)."""
        if self._last_update is None:
            self._last_update = t
            return
        if t <= self._last_update:
            # out-of-order / replayed timestamp: never rewind the clock
            return
        dt = t - self._last_update
        gain = self.rate(t) * dt
        for side, val in (("player", self.player), ("opponent", self.opponent)):
            new = min(self.cfg.cap, val + gain)
            if side == "player" and val < self.cfg.cap <= new:
                self.history.append(ElixirEvent(t, "cap", self.cfg.cap, side))
            if side == "player":
                self.player = new
            else:
                self.opponent = new
        self._last_update = t
        # confidence decays since our last direct observation of the enemy
        self.opponent_confidence = max(0.35, self.opponent_confidence - 0.01 * dt)

    # ------------------------------------------------------------------ plays
    def player_played(self, cost: int, t: float) -> bool:
        self.update(t)
        if cost <= self.player + 1e-6:
            self.player -= cost
            self.history.append(ElixirEvent(t, "play", cost, "player"))
            return True
        self.history.append(ElixirEvent(t, "leak", cost, "player"))
        self.player = max(0.0, self.player - cost)
        return False

    def opponent_played(self, cost: int, t: float) -> None:
        self.update(t)
        if cost > self.opponent + 1e-6:
            # we must have missed earlier plays - do not go negative, flag it
            self.history.append(ElixirEvent(t, "missed_play", cost,
                                            f"had {self.opponent:.1f}, cost {cost}"))
            self.opponent = 0.0
            self.opponent_confidence = max(0.35, self.opponent_confidence - 0.2)
        else:
            self.opponent -= cost
            self.opponent_confidence = min(1.0, self.opponent_confidence + 0.25)
        self.history.append(ElixirEvent(t, "play", cost, "opponent"))

    # ------------------------------------------------------------------ read
    def player_value(self) -> float:
        return round(self.player, 1)

    def opponent_estimate(self) -> tuple[float, float]:
        """Return (estimated elixir, confidence 0..1)."""
        return round(self.opponent, 1), round(self.opponent_confidence, 2)

    def snapshot(self, t: float) -> dict:
        opp, conf = self.opponent_estimate()
        return {
            "player": self.player_value(),
            "opponent_estimate": opp,
            "opponent_confidence": conf,
            "phase": self.phase(t),
            "rate_per_second": round(self.rate(t), 3),
            "elapsed_s": round(self.elapsed(t), 1),
        }
