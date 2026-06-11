"""Normalized representation of a bug bounty program, shared across all sources."""
from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass, field, asdict
from datetime import datetime, timezone
from typing import Any


def _slug(value: str) -> str:
    value = (value or "").strip().lower()
    value = re.sub(r"[^a-z0-9]+", "-", value)
    return value.strip("-")


@dataclass
class Program:
    """One bug bounty program / scope, normalized from any platform.

    `doc_id` is a stable hash of (platform, handle) so the same program produces
    the same id on every poll — that is how we detect what is genuinely NEW.
    """

    platform: str               # "hackerone", "yeswehack", "bugcrowd", ...
    handle: str                 # platform-unique slug/id for the program
    name: str                   # human-readable program name
    url: str                    # link the hunter opens
    bounty: bool = False        # offers monetary reward (vs VDP/points only)
    reward_range: str = ""      # e.g. "$100–$10,000" if known
    scope: list[str] = field(default_factory=list)   # in-scope asset identifiers
    tags: list[str] = field(default_factory=list)     # e.g. ["web", "api", "web3"]
    launched_at: str = ""       # ISO date the PROGRAM launched, if the source tells us
    source_meta: dict[str, Any] = field(default_factory=dict)  # raw extras

    # Filled in by the engine, not the source:
    first_seen: str = ""        # ISO timestamp WE first saw it

    @property
    def doc_id(self) -> str:
        raw = f"{_slug(self.platform)}::{_slug(self.handle)}"
        return hashlib.sha1(raw.encode("utf-8")).hexdigest()[:20]

    def to_firestore(self) -> dict[str, Any]:
        d = asdict(self)
        d["doc_id"] = self.doc_id
        if not d["first_seen"]:
            d["first_seen"] = datetime.now(timezone.utc).isoformat()
        return d

    def notification_title(self) -> str:
        reward = " 💰" if self.bounty else ""
        return f"🚨 New program: {self.name}{reward}"

    def notification_body(self) -> str:
        bits = [self.platform.title()]
        if self.reward_range:
            bits.append(self.reward_range)
        if self.scope:
            bits.append(f"{len(self.scope)} scope item(s)")
        return " · ".join(bits)
