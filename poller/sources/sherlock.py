"""Sherlock — Web3 audit *contests* (time-boxed). A newly announced contest is a
fresh, unaudited target = exactly the low-hanging-fruit signal we want.

API: https://mainnet-contest.sherlock.xyz/contests  (paginated: page, items, has_next)
We only keep non-private contests that haven't ended yet, scanning the first few
(newest) pages — that's where any just-announced contest appears.
"""
from __future__ import annotations

import time
from datetime import datetime, timezone
from typing import Iterable

from models import Program
from sources.base import Source

API = "https://mainnet-contest.sherlock.xyz/contests"
BROWSER_UA = "Mozilla/5.0 (compatible; BountyRadar/0.1)"
MAX_PAGES = 4  # newest-first; new contests surface on the first page(s)


class Sherlock(Source):
    name = "sherlock"

    def fetch(self) -> Iterable[Program]:
        now = time.time()
        page = 1
        while page <= MAX_PAGES:
            data = self._get(API, params={"page": page},
                             headers={"User-Agent": BROWSER_UA}).json()
            for it in data.get("items", []):
                if it.get("private"):
                    continue
                ends = it.get("ends_at")
                if ends and ends < now:   # already finished — not actionable
                    continue
                cid = it.get("id")
                if cid is None:
                    continue
                pool = it.get("prize_pool") or 0
                yield Program(
                    platform=self.name,
                    handle=str(cid),
                    name=it.get("title") or f"Sherlock contest {cid}",
                    url=f"https://audits.sherlock.xyz/contests/{cid}",
                    bounty=pool > 0,
                    reward_range=f"${pool:,} pool" if pool else "",
                    tags=["web3", "audit-contest"],
                    launched_at=_iso(it.get("starts_at")),
                    source_meta={
                        "status": it.get("status"),
                        "ends_at": _iso(ends),
                        "type": it.get("type_label"),
                        "summary": (it.get("short_description") or "")[:200],
                    },
                )
            if not data.get("has_next"):
                break
            page += 1


def _iso(unix) -> str:
    if not unix:
        return ""
    return datetime.fromtimestamp(unix, tz=timezone.utc).isoformat()
