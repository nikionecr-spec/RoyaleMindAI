"""Geometric placement engine.

Generates and ranks concrete deployment points (normalized coordinates) for a
given card against the current board state. Every candidate point carries the
reasons it scored well so the UI can explain the suggestion.

All coordinates are normalized; tile math uses the 18x32 board grid
(see ai/arena.py for the landmark provenance).
"""
from __future__ import annotations

from dataclasses import dataclass, field

from ..arena import (
    Arena, Point, RIVER_Y, BRIDGE_LEFT, BRIDGE_RIGHT,
    TOWER_LEFT_OWN, TOWER_RIGHT_OWN, TOWER_LEFT_ENEMY, TOWER_RIGHT_ENEMY,
    TOWER_RANGE_TILES, Unit, TILES_X, TILES_Y,
)


@dataclass
class Candidate:
    pos: Point
    score: float = 0.0
    reasons: list[str] = field(default_factory=list)
    risk: str = "low"

    def as_dict(self) -> dict:
        return {
            "placement": self.pos.as_dict(),
            "score": round(self.score, 3),
            "risk": self.risk,
            "reasons": self.reasons,
        }


class PlacementEngine:
    """Produces ranked placement points for one card."""

    GRID_STEP_TILES = 1.0

    def __init__(self, arena: Arena | None = None):
        self.arena = arena or Arena()

    # ------------------------------------------------------------- generator
    def candidates(self, card: dict, enemy_units: list[Unit]) -> list[Candidate]:
        out: list[Candidate] = []
        spell = card.get("kind") == "spell"
        flying = card.get("target") in ("both", "air")

        # 1) regular grid over the deployable area
        step = self.GRID_STEP_TILES / TILES_X
        x = 0.0
        while x <= 1.0:
            y = RIVER_Y + 0.02
            while y <= 0.98:
                p = Point(round(x, 4), round(y, 4))
                if self.arena.can_deploy(p, flying=flying, spell=spell):
                    out.append(Candidate(pos=p))
                y += step / TILES_Y * TILES_X   # keep square step in tiles
            x += step
        # spells may target anywhere; add a light grid over the enemy half too
        if spell:
            x = 0.0
            while x <= 1.0:
                y = 0.10
                while y <= RIVER_Y:
                    out.append(Candidate(pos=Point(round(x, 4), round(y, 4))))
                    y += step / TILES_Y * TILES_X
                x += step

        # 2) semantic anchors
        for e in enemy_units:
            lane = "left" if e.pos.x < 0.5 else "right"
            out.append(Candidate(pos=self._intercept_point(e), reasons=[f"intercept {e.card}"]))
            out.append(Candidate(pos=self._pull_point(e.pos, lane), reasons=[f"pull {e.card} away"]))
            out.append(Candidate(pos=self._kite_point(e.pos, lane), reasons=[f"kite {e.card}"]))
        out.append(Candidate(pos=Point(0.5, RIVER_Y + 0.10), reasons=["center pull"]))
        for bridge in (BRIDGE_LEFT, BRIDGE_RIGHT):
            out.append(Candidate(pos=Point(bridge.x, RIVER_Y + 0.08), reasons=["bridge defense"]))
        for tw in (TOWER_LEFT_OWN, TOWER_RIGHT_OWN):
            out.append(Candidate(pos=Point(tw.x, tw.y - 0.08), reasons=["in front of tower"]))
        return out

    # ------------------------------------------------------------- scoring
    def rank(self, card: dict, enemy_units: list[Unit],
             top_k: int = 5) -> list[Candidate]:
        scored: list[Candidate] = []
        for cand in self.candidates(card, enemy_units):
            s = self._score(card, cand, enemy_units)
            if s is None:
                continue
            cand.score = s
            scored.append(cand)
        scored.sort(key=lambda c: -c.score)
        return scored[:top_k]

    def _score(self, card: dict, cand: Candidate, enemy_units: list[Unit]) -> float | None:
        p = cand.pos
        roles = set(card.get("roles", []))
        kind = card.get("kind", "troop")
        speed = card.get("speed", "medium")

        # validity (grid candidates already validated; semantic ones must pass)
        if not self.arena.can_deploy(p, flying=card.get("target") in ("both", "air"),
                                     spell=kind == "spell"):
            return None

        score = 0.30                                   # baseline: any legal spot
        reasons = list(cand.reasons)
        risk = "low"

        if enemy_units:
            primary = self._primary_threat(enemy_units)
            d_tiles = p.dist_tiles(primary.pos)
            eta = self.arena.travel_time(speed, d_tiles)
            threat_eta = self.arena.travel_time(
                "very_fast" if "hog-rider" in primary.card else "medium",
                max(0.0, primary.pos.dist_tiles(TOWER_LEFT_OWN if primary.pos.x < 0.5
                                                else TOWER_RIGHT_OWN)))
            if kind != "spell":
                if eta <= threat_eta:
                    score += 0.30
                    reasons.append(f"arrives {eta:.1f}s before threat {threat_eta:.1f}s")
                elif eta <= threat_eta * 1.4:
                    score += 0.15
                    reasons.append("arrives in time (tight)")
                else:
                    score -= 0.15
                    reasons.append("too slow to intercept")
                # defensive troops want to be near (not on top of) the threat
                ideal = 2.5 if "swarm" not in roles else 1.5
                score += 0.20 * max(0.0, 1.0 - abs(d_tiles - ideal) / 6.0)
                reasons.append(f"distance {d_tiles:.1f} tiles (ideal ~{ideal})")
            else:
                radius = card.get("spell_radius_t", 2.5)
                score += 0.40 * max(0.0, 1.0 - abs(d_tiles - radius) / (radius + 3.0))
                reasons.append(f"spell center {d_tiles:.1f} tiles from threat")

        # own-tower support = safer defense
        if kind != "spell" and self.arena.in_own_tower_support(p):
            score += 0.15
            reasons.append("covered by own tower")
        # standing inside enemy tower fire is risky for troops
        if kind == "troop" and self.arena.in_enemy_tower_range(p):
            score -= 0.25
            risk = "high" if score < 0.3 else "medium"
            reasons.append("inside enemy tower range")

        # buildings: pulling between the towers is the classic defense
        if kind == "building":
            center_dist = abs(p.x - 0.5)
            score += 0.25 * max(0.0, 1.0 - center_dist / 0.35)
            reasons.append("pull position toward center")

        # counter-push potential: keep some space toward the enemy half
        if "win_condition" in roles or "mobility" in roles:
            score += 0.10 * (p.y - RIVER_Y) / (1.0 - RIVER_Y)
            reasons.append("keeps counter-push lane open")

        cand.reasons = reasons
        cand.risk = risk
        return max(0.0, min(1.0, score))

    # ------------------------------------------------------------- anchors
    @staticmethod
    def _primary_threat(enemy_units: list[Unit]) -> Unit:
        """Highest-priority enemy: win conditions and building-targeting units
        first, then fastest movers toward our side."""
        def key(u: Unit):
            wc = "hog" in u.card or "balloon" in u.card or "ram" in u.card or "giant" in u.card
            descending = u.velocity[1] > 0 if isinstance(u.velocity, tuple) else u.velocity.y > 0
            return (not wc, not descending, -abs(u.velocity[1] if isinstance(u.velocity, tuple) else u.velocity.y))
        return sorted(enemy_units, key=key)[0]

    def _intercept_point(self, e: Unit) -> Point:
        """Point on the unit's projected path, one tile ahead, on our side."""
        vx = e.velocity[0] if isinstance(e.velocity, tuple) else e.velocity.x
        vy = e.velocity[1] if isinstance(e.velocity, tuple) else e.velocity.y
        norm = (vx ** 2 + vy ** 2) ** 0.5 or 1e-6
        ahead = 1.0 / TILES_X
        x = min(0.98, max(0.02, e.pos.x + vx / norm * ahead))
        y = min(0.98, max(0.02, e.pos.y + vy / norm * ahead))
        # clamp to our side for troops
        y = max(y, RIVER_Y + 0.04)
        return Point(round(x, 4), round(y, 4))

    def _pull_point(self, threat: Point, lane: str) -> Point:
        """Building pull point: center-ish, between river and our towers."""
        x = 0.5 if threat.x < 0.5 else 0.5
        x = 0.38 if lane == "left" else 0.62
        return Point(x, 0.60)

    def _kite_point(self, threat: Point, lane: str) -> Point:
        x = 0.30 if lane == "left" else 0.70
        return Point(x, 0.68)
