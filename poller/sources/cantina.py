"""Cantina — Web3 audit competitions + bounties. Clean public JSON list.

API: https://cantina.xyz/api/v0/competitions
Each item already carries name, url, company, timeframe, totalRewardPot. We keep
competitions that haven't ended yet.
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Iterable

from models import Program
from sources.base import Source

API = "https://cantina.xyz/api/v0/competitions"
BROWSER_UA = "Mozilla/5.0 (compatible; BountyRadar/0.1)"


class Cantina(Source):
    name = "cantina"

    def fetch(self) -> Iterable[Program]:
        now = datetime.now(timezone.utc)
        rows = self._get(API, headers={"User-Agent": BROWSER_UA}).json()
        for r in rows:
            end = _parse(((r.get("timeframe") or {}).get("end")))
            if end and end < now:        # finished — skip
                continue
            cid = r.get("id")
            if not cid:
                continue
            try:
                pot = float(r.get("totalRewardPot") or 0)
            except (TypeError, ValueError):
                pot = 0.0
            cur = r.get("currencyCode") or "USD"
            company = (r.get("company") or {}).get("name", "")
            yield Program(
                platform=self.name,
                handle=str(cid),
                name=r.get("name") or str(cid),
                url=r.get("url") or f"https://cantina.xyz/competitions/{cid}",
                bounty=pot > 0,
                reward_range=f"{pot:,.0f} {cur}" if pot else "",
                tags=["web3", "audit-contest"],
                launched_at=str((r.get("timeframe") or {}).get("start") or ""),
                source_meta={
                    "status": r.get("status"),
                    "company": company,
                    "kyc": r.get("kycRequired"),
                },
            )


def _parse(value) -> datetime | None:
    if not value:
        return None
    try:
        return datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError:
        return None
