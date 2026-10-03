"""HackenProof — crypto/Web3-focused bug bounty platform.

There is no public JSON API; the programs page is a Nuxt app that embeds its
data in a `__NUXT_DATA__` script (devalue format: a flat array where objects and
lists hold *indices* into that same array). We decode that payload.
"""
from __future__ import annotations

import json
import re
from typing import Any, Iterable

from models import Program
from sources.base import Source

PAGE = "https://hackenproof.com/programs"
BROWSER_UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              "(KHTML, like Gecko) Chrome/126 Safari/537.36")
MAX_PAGES = 25
_WRAPPERS = {"Reactive", "ShallowReactive", "Ref", "ShallowRef"}
_PAYLOAD = re.compile(r'id="__NUXT_DATA__"[^>]*>(.*?)</script>', re.S)


def _resolve(arr: list, i: Any, depth: int = 0) -> Any:
    if not isinstance(i, int) or i < 0 or i >= len(arr) or depth > 8:
        return None
    v = arr[i]
    if isinstance(v, dict):
        return {k: _resolve(arr, x, depth + 1) for k, x in v.items()}
    if isinstance(v, list):
        if v and isinstance(v[0], str):          # special form, e.g. ["Reactive", 12]
            return _resolve(arr, v[1], depth + 1) if v[0] in _WRAPPERS and len(v) > 1 else None
        return [_resolve(arr, x, depth + 1) for x in v]
    return v


class HackenProof(Source):
    name = "hackenproof"

    def _page(self, page: int) -> list[dict]:
        html = self._get(PAGE, params={"page": page},
                         headers={"User-Agent": BROWSER_UA, "Accept": "text/html"}).text
        m = _PAYLOAD.search(html)
        if not m:
            return []
        arr = json.loads(m.group(1))
        for node in arr:
            if isinstance(node, dict) and "programs-api-bounty" in node:
                data = _resolve(arr, node["programs-api-bounty"]) or {}
                return [p for p in (data.get("programs") or []) if isinstance(p, dict)]
        return []

    def fetch(self) -> Iterable[Program]:
        seen: set[str] = set()
        for page in range(1, MAX_PAGES + 1):
            rows = self._page(page)
            fresh = [r for r in rows if r.get("slug") and r["slug"] not in seen]
            if not fresh:            # empty page, or the site ignored ?page= and repeated page 1
                break
            for r in fresh:
                slug = str(r["slug"])
                seen.add(slug)
                reward = str(r.get("reward") or "").strip()
                tags = ["web3"] + (["audit-contest"] if r.get("isAudit") else [])
                yield Program(
                    platform=self.name,
                    handle=slug,
                    name=str(r.get("name") or slug),
                    url=f"https://hackenproof.com/programs/{slug}",
                    bounty=bool(reward),
                    reward_range=f"up to {reward}" if reward else "",
                    tags=tags,
                    launched_at=str(r.get("startDate") or ""),
                    source_meta={"summary": str(r.get("description") or "")[:200]},
                )
