"""Standoff 365 Bug Bounty (Positive Technologies) — public program list API.

GET https://api.standoff365.com/api/bug-bounty/program?page=N
-> {"items": [...], "page": N, "total": <pages>, "totalEntries": <programs>}
"""
from __future__ import annotations

from typing import Iterable

from models import Program
from sources.base import Source

API = "https://api.standoff365.com/api/bug-bounty/program"
WEB = "https://bugbounty.standoff365.com/programs/"
MAX_PAGES = 40


class Standoff365(Source):
    name = "standoff365"

    def fetch(self) -> Iterable[Program]:
        page, pages = 1, 1
        while page <= pages and page <= MAX_PAGES:
            data = self._get(API, params={"page": page},
                             headers={"Accept": "application/json"}, timeout=45).json()
            for it in data.get("items", []):
                slug = it.get("slug")
                if not slug or it.get("visibility") != "public":
                    continue
                if it.get("finished") or it.get("archivedAt") or it.get("status") != "published":
                    continue
                yield Program(
                    platform=self.name,
                    handle=str(slug),
                    name=it.get("name") or str(slug),
                    url=f"{WEB}{slug}",
                    bounty=True,
                    tags=["bounty"],
                    launched_at=it.get("publishedAt") or "",
                    source_meta={"vendor": it.get("vendorName"),
                                 "summary": (it.get("shortDescription") or "")[:200]},
                )
            pages = int(data.get("total") or pages)
            page += 1
