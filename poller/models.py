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

    @property
    def content_hash(self) -> str:
        """Fingerprint of the parts that matter for "did this program change":
        name, link, reward, paid-status, scope and tags. A change here = an
        update worth notifying about (e.g. new scope = new attack surface)."""
        parts = [
            self.name, self.url, str(self.bounty), self.reward_range,
            "|".join(sorted(self.scope)), "|".join(sorted(self.tags)),
        ]
        return hashlib.sha1("::".join(parts).encode("utf-8")).hexdigest()[:16]

    def to_firestore(self) -> dict[str, Any]:
        """Full document — used for NEW programs and seeding."""
        d = asdict(self)
        d["doc_id"] = self.doc_id
        d["content_hash"] = self.content_hash
        now = datetime.now(timezone.utc).isoformat()
        if not d["first_seen"]:
            d["first_seen"] = now
        d["updated_at"] = now
        return d

    def to_firestore_update(self) -> dict[str, Any]:
        """Partial update for an EXISTING program — never touches first_seen so
        the program keeps its original discovery time, but bumps updated_at."""
        return {
            "name": self.name, "url": self.url, "bounty": self.bounty,
            "reward_range": self.reward_range, "scope": self.scope,
            "tags": self.tags, "source_meta": self.source_meta,
            "content_hash": self.content_hash,
            "updated_at": datetime.now(timezone.utc).isoformat(),
        }

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

    def update_title(self) -> str:
        return f"🔄 Updated: {self.name}"

    def update_body(self) -> str:
        bits = [self.platform.title(), "scope/reward changed"]
        if self.scope:
            bits.append(f"{len(self.scope)} scope item(s) now")
        return " · ".join(bits)
