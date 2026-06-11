"""Primary aggregator: arkadiyt/bounty-targets-data.

One GitHub repo publishes clean JSON for the big platforms, refreshed every
~30 min, no auth, no rate limits. We parse each file to its real schema (verified
against the live data) and emit normalized Programs.

Covers in Phase 1: HackerOne, Bugcrowd, Intigriti, YesWeHack, Federacy.
This single source replaces per-platform scraping for public programs.
"""
from __future__ import annotations

from typing import Iterable

from models import Program
from sources.base import Source

BASE = "https://raw.githubusercontent.com/arkadiyt/bounty-targets-data/main/data/"
FILES = {
    "hackerone": "hackerone_data.json",
    "bugcrowd": "bugcrowd_data.json",
    "intigriti": "intigriti_data.json",
    "yeswehack": "yeswehack_data.json",
    "federacy": "federacy_data.json",
}


class BountyTargets(Source):
    name = "bounty_targets"

    def fetch(self) -> Iterable[Program]:
        for platform, filename in FILES.items():
            try:
                rows = self._get(BASE + filename).json()
            except Exception as exc:  # one file down shouldn't kill the rest
                self._log_skip(platform, exc)
                continue
            parser = PARSERS.get(platform)
            if not parser:
                continue
            for row in rows:
                prog = parser(platform, row)
                if prog is not None:
                    yield prog

    @staticmethod
    def _log_skip(platform, exc):
        import logging
        logging.getLogger("bountyradar.source").warning(
            "bounty_targets:%s skipped: %s", platform, exc
        )


# --------------------------------------------------------------------------- #
# Per-platform parsers — built to the schemas verified from live data.          #
# --------------------------------------------------------------------------- #
def _scope(targets: dict, *fields: str) -> list[str]:
    out = []
    for item in (targets or {}).get("in_scope", []) or []:
        for f in fields:
            val = item.get(f)
            if val:
                out.append(str(val))
                break
    return out


def _hackerone(platform: str, row: dict) -> Program | None:
    if row.get("submission_state") not in (None, "open"):
        return None
    handle = row.get("handle")
    if not handle:
        return None
    return Program(
        platform=platform,
        handle=handle,
        name=row.get("name") or handle,
        url=row.get("url") or f"https://hackerone.com/{handle}",
        bounty=bool(row.get("offers_bounties")),
        scope=_scope(row.get("targets"), "asset_identifier"),
        tags=["bounty"] if row.get("offers_bounties") else ["vdp"],
        source_meta={"managed": row.get("managed_program"),
                     "website": row.get("website")},
    )


def _bugcrowd(platform: str, row: dict) -> Program | None:
    url = row.get("url") or ""
    handle = url.rstrip("/").rsplit("/", 1)[-1] or row.get("name", "")
    max_payout = row.get("max_payout") or 0
    return Program(
        platform=platform,
        handle=handle,
        name=(row.get("name") or handle).strip(),
        url=url,
        bounty=max_payout > 0,
        reward_range=f"up to ${max_payout:,}" if max_payout else "",
        scope=_scope(row.get("targets"), "target", "uri", "name"),
        tags=["bounty"] if max_payout else ["vdp"],
        source_meta={"safe_harbor": row.get("safe_harbor"),
                     "managed": row.get("managed_by_bugcrowd")},
    )


def _intigriti(platform: str, row: dict) -> Program | None:
    if row.get("status") not in (None, "open"):
        return None
    if row.get("confidentiality_level") not in (None, "public"):
        return None
    handle = row.get("handle") or row.get("id")
    if not handle:
        return None
    return Program(
        platform=platform,
        handle=str(handle),
        name=row.get("name") or str(handle),
        url=row.get("url") or "",
        bounty=_money_val(row.get("max_bounty")) > 0,
        reward_range=_money_range(row.get("min_bounty"), row.get("max_bounty")),
        scope=_scope(row.get("targets"), "endpoint", "uri"),
        tags=["bounty"] if _money_val(row.get("max_bounty")) > 0 else ["vdp"],
        source_meta={"company": row.get("company_handle")},
    )


def _yeswehack(platform: str, row: dict) -> Program | None:
    if not row.get("public", True) or row.get("disabled"):
        return None
    handle = row.get("id")
    if not handle:
        return None
    lo, hi = row.get("min_bounty") or 0, row.get("max_bounty") or 0
    return Program(
        platform=platform,
        handle=str(handle),
        name=row.get("name") or str(handle),
        url=f"https://yeswehack.com/programs/{handle}",
        bounty=hi > 0,
        reward_range=f"${lo:,}–${hi:,}" if (lo or hi) else "",
        scope=_scope(row.get("targets"), "target"),
        tags=["bounty"] if hi > 0 else ["vdp"],
    )


def _federacy(platform: str, row: dict) -> Program | None:
    handle = row.get("handle") or row.get("name")
    if not handle:
        return None
    return Program(
        platform=platform,
        handle=str(handle),
        name=row.get("name") or str(handle),
        url=row.get("url") or "",
        scope=_scope(row.get("targets"), "target", "asset_identifier"),
    )


def _money_val(m) -> float:
    if isinstance(m, dict):
        return float(m.get("value") or 0)
    return float(m or 0)


def _money_range(lo, hi) -> str:
    def sym(m):
        cur = m.get("currency", "") if isinstance(m, dict) else ""
        return {"EUR": "€", "USD": "$", "GBP": "£"}.get(cur, "")
    lov, hiv = _money_val(lo), _money_val(hi)
    if not (lov or hiv):
        return ""
    s = sym(hi) or sym(lo) or "$"
    return f"{s}{lov:,.0f}–{s}{hiv:,.0f}"


PARSERS = {
    "hackerone": _hackerone,
    "bugcrowd": _bugcrowd,
    "intigriti": _intigriti,
    "yeswehack": _yeswehack,
    "federacy": _federacy,
}
