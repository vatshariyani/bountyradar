"""Immunefi — the biggest Web3 / smart-contract bug bounty platform (payouts to $1M+).

Official public JSON: https://immunefi.com/public-api/bounties.json
No auth. ~270 programs. We skip invite-only ones (you can't act on those).
"""
from __future__ import annotations

from typing import Iterable

from models import Program
from sources.base import Source

API = "https://immunefi.com/public-api/bounties.json"
# Immunefi serves the JSON fine but prefers a browser-like UA.
BROWSER_UA = "Mozilla/5.0 (compatible; BountyRadar/0.1)"


class Immunefi(Source):
    name = "immunefi"

    def fetch(self) -> Iterable[Program]:
        rows = self._get(API, headers={"User-Agent": BROWSER_UA}).json()
        for r in rows:
            if r.get("inviteOnly"):
                continue
            slug = r.get("slug")
            if not slug:
                continue
            max_bounty = r.get("maxBounty") or 0
            assets = [a.get("url") for a in (r.get("assets") or []) if a.get("url")]
            yield Program(
                platform=self.name,
                handle=str(slug),
                name=r.get("project") or str(slug),
                url=f"https://immunefi.com/bug-bounty/{slug}/",
                bounty=max_bounty > 0,
                reward_range=f"up to ${max_bounty:,}" if max_bounty else "",
                scope=assets[:25],
                tags=(r.get("ecosystem") or []) + ["web3"],
                launched_at=r.get("launchDate", ""),
                source_meta={
                    "updated": r.get("updatedDate"),
                    "product_type": r.get("productType"),
                    "scope_count": len(assets),
                },
            )
