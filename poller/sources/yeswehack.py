"""YesWeHack — has a clean public JSON API listing public programs.

Endpoint:  GET https://api.yeswehack.com/programs?page=N
Returns paginated JSON: {"items": [...], "pagination": {"nb_pages": N}}
No auth needed for PUBLIC programs. This is one of our most reliable sources.
"""
from __future__ import annotations

from typing import Iterable

from models import Program
from sources.base import Source

API = "https://api.yeswehack.com/programs"
WEB = "https://yeswehack.com/programs/"
MAX_PAGES = 25  # safety cap


class YesWeHack(Source):
    name = "yeswehack"

    def fetch(self) -> Iterable[Program]:
        page = 1
        nb_pages = 1
        while page <= nb_pages and page <= MAX_PAGES:
            data = self._get(API, params={"page": page}).json()
            for item in data.get("items", []):
                slug = item.get("slug") or item.get("id")
                if not slug:
                    continue
                yield Program(
                    platform=self.name,
                    handle=str(slug),
                    name=item.get("title") or str(slug),
                    url=f"{WEB}{slug}",
                    bounty=bool(item.get("bounty", False)),
                    reward_range=_reward(item),
                    tags=_tags(item),
                    launched_at=item.get("public_date") or item.get("startedAt") or "",
                    source_meta={
                        "public": item.get("public"),
                        "vdp": item.get("vdp"),
                        "reports_count": item.get("reports_count"),
                    },
                )
            nb_pages = (data.get("pagination") or {}).get("nb_pages", nb_pages)
            page += 1


def _reward(item: dict) -> str:
    lo = item.get("min_bounty") or item.get("bounty_reward_min")
    hi = item.get("max_bounty") or item.get("bounty_reward_max")
    if lo or hi:
        return f"${lo or 0:,}–${hi or 0:,}"
    return ""


def _tags(item: dict) -> list[str]:
    tags = []
    if item.get("vdp"):
        tags.append("vdp")
    if item.get("bounty"):
        tags.append("bounty")
    return tags
