"""Source registry. Add a new platform by importing it and listing it here."""
from __future__ import annotations

from sources.base import Source
from sources.bounty_targets import BountyTargets
from sources.openbugbounty import OpenBugBounty
from sources.hackerone import HackerOne
from sources.immunefi import Immunefi
from sources.sherlock import Sherlock
from sources.cantina import Cantina

# Order is cosmetic. Each is gated by config.ENABLED_SOURCES at runtime.
ALL_SOURCE_CLASSES = [
    # Primary (web2): one maintained repo covering HackerOne, Bugcrowd, Intigriti,
    # YesWeHack, Federacy as public JSON (refreshed ~30 min).
    BountyTargets,
    # Phase 2 (web3): highest-payout smart-contract platforms + audit contests.
    Immunefi,
    Sherlock,
    Cantina,
    # Beginner-friendly coordinated disclosure (best-effort feed).
    OpenBugBounty,
    # Phase 3: your private/invited HackerOne programs (needs API token; returns
    # nothing without one, so it's safe to leave enabled).
    HackerOne,
    # Note: Code4rena intentionally omitted — the platform is winding down.
]

__all__ = ["Source", "ALL_SOURCE_CLASSES"]
