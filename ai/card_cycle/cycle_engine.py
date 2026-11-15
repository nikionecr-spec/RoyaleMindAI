"""Card cycle engine: 8-card deck tracking for the opponent and the player.

Model (same mental model as ERA, implemented independently):
  hand   = slots 0..3 (playable now)
  queue  = slots 4..7 (cycling order; 4 is the next card)
  When a card is played it moves to the back of the queue; every card shifts
  forward one slot.

Champions do not cycle through the queue - once played they leave the hand and
return after a cooldown (approximate, configurable). Evolutions are the *same*
deck slot as their base card: "knight-evolution" and "knight" both occupy the
"knight" slot, but the evolution flag is recorded.
"""
from __future__ import annotations

from collections import OrderedDict
from dataclasses import dataclass, field


def base_card(name: str) -> tuple[str, bool]:
    """Split a dataset class name into (base card, evolution flag)."""
    if name.endswith("-evolution"):
        return name[: -len("-evolution")], True
    return name, False


@dataclass
class CycleState:
    hand: list[str | None] = field(default_factory=lambda: [None] * 4)
    queue: list[str | None] = field(default_factory=lambda: [None] * 4)
    known: set = field(default_factory=set)
    evolution_flags: dict = field(default_factory=dict)
    champions_in_cooldown: list[tuple[str, float]] = field(default_factory=list)
    plays: list[tuple[str, float]] = field(default_factory=list)


class CardCycleEngine:
    HAND_SLOTS = 4
    QUEUE_SLOTS = 4

    def __init__(self, champion_return_sec: float = 10.0):
        self.champion_return_sec = champion_return_sec
        self.opponent = CycleState()
        self.player = CycleState()
        self._champion_names: set[str] = set()

    # ---------------------------------------------------------------- setup
    def set_champions(self, names: list[str]) -> None:
        self._champion_names = set(names)

    def reset(self) -> None:
        self.opponent = CycleState()
        self.player = CycleState()

    # ------------------------------------------------------------- opponent
    def opponent_played(self, card_name: str, t: float) -> None:
        """Register a card the enemy just played (from vision).

        If we know the card's slot, rotate the cycle. If it is new, fill the
        first unknown queue slot (discovery phase)."""
        st = self.opponent
        base, evo = base_card(card_name)
        st.plays.append((base, t))
        if evo:
            st.evolution_flags[base] = True

        if base in self._champion_names:
            st.hand = [c if c != base else None for c in st.hand]
            st.champions_in_cooldown.append((base, t))
            st.known.add(base)
            return

        idx = self._find(st.hand, base)
        if idx is not None:
            # shift queue forward into the freed hand slot
            st.hand[idx] = st.queue[0]
            st.queue = st.queue[1:] + [base]
        else:
            qidx = self._find(st.queue, base)
            if qidx is not None:
                # known card played from the queue (rare - e.g. a mis-read);
                # rotate within the queue
                st.queue[qidx] = None
                st.queue = [c for c in st.queue] + [base]
            elif any(c is None for c in st.queue):
                # discovery phase 1: first 4 plays populate the queue
                for i, c in enumerate(st.queue):
                    if c is None:
                        st.queue[i] = base
                        break
            elif any(c is None for c in st.hand):
                # discovery phase 2 (5th+ play): queue is full - the played card
                # frees a hand slot which is refilled by queue[0]
                hidx = next(i for i, c in enumerate(st.hand) if c is None)
                st.hand[hidx] = st.queue[0]
                st.queue = st.queue[1:] + [base]
            else:
                # everything known but the card fits nowhere (missed info):
                # push to the back as a best-effort guess
                st.queue = st.queue[1:] + [base]
            st.known.add(base)
        self._fill_champion_return(t)

    def _fill_champion_return(self, t: float) -> None:
        st = self.opponent
        remaining = []
        for name, t0 in st.champions_in_cooldown:
            if t - t0 >= self.champion_return_sec:
                if name not in st.hand:
                    for i, c in enumerate(st.hand):
                        if c is None:
                            st.hand[i] = name
                            break
            else:
                remaining.append((name, t0))
        st.champions_in_cooldown = remaining

    # --------------------------------------------------------------- player
    def player_hand_observed(self, cards: list[str | None]) -> None:
        """Direct observation of the 4 hand cards (bottom of the screen)."""
        st = self.player
        for i, name in enumerate(cards[:4]):
            if name is None:
                continue
            base, evo = base_card(name)
            st.hand[i] = base
            if evo:
                st.evolution_flags[base] = True
            st.known.add(base)
            # remove from queue if it cycled into the hand
            q = self._find(st.queue, base)
            if q is not None:
                st.queue[q] = None
        # keep hand/queue consistent: no card in two places
        for i, c in enumerate(st.hand):
            if c is not None and c in st.queue:
                st.queue[st.queue.index(c)] = None

    def player_played(self, hand_index: int, t: float) -> None:
        st = self.player
        card = st.hand[hand_index] if 0 <= hand_index < 4 else None
        if card is None:
            return
        if card in self._champion_names:
            st.hand[hand_index] = None
            st.champions_in_cooldown.append((card, t))
            return
        st.hand[hand_index] = st.queue[0]
        st.queue = st.queue[1:] + [card]

    # ------------------------------------------------------------ prediction
    def opponent_next_cards(self, top_k: int = 4) -> list[dict]:
        """Ranked prediction of the opponent's upcoming plays.

        Cards sitting closer to the front of the queue are exponentially more
        likely; unknown slots spread the remaining probability mass evenly.
        """
        st = self.opponent
        weights = [0.55, 0.25, 0.13, 0.07]     # queue positions 4..7
        scores: "OrderedDict[str, float]" = OrderedDict()
        for w, c in zip(weights, st.queue):
            if c is None:
                continue
            scores[c] = scores.get(c, 0.0) + w
        unknown_mass = sum(w for w, c in zip(weights, st.queue) if c is None)
        # probabilities are honest: unknown queue slots soak up their mass,
        # they are NOT redistributed onto known cards
        total = sum(scores.values()) + unknown_mass
        out = []
        for card, s in sorted(scores.items(), key=lambda kv: -kv[1])[:top_k]:
            out.append({
                "card": card,
                "probability": round(s / total, 3),
                "evolution": st.evolution_flags.get(card, False),
            })
        return out

    def opponent_deck_coverage(self) -> float:
        return round(len(self.opponent.known) / 8.0, 3)

    def opponent_known_cards(self) -> list[str]:
        return sorted(self.opponent.known)

    # ---------------------------------------------------------------- utils
    @staticmethod
    def _find(slots: list[str | None], card: str) -> int | None:
        for i, c in enumerate(slots):
            if c == card:
                return i
        return None

    def snapshot(self) -> dict:
        st = self.opponent
        return {
            "opponent": {
                "hand": list(st.hand),
                "queue": list(st.queue),
                "known": self.opponent_known_cards(),
                "coverage": self.opponent_deck_coverage(),
                "next": self.opponent_next_cards(),
                "champions_on_cooldown": [n for n, _ in st.champions_in_cooldown],
                "evolutions_seen": sorted(st.evolution_flags.keys()),
            },
            "player": {
                "hand": list(self.player.hand),
                "queue": list(self.player.queue),
            },
        }
