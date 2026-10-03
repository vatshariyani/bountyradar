"""Independent (self-hosted) bounty programs from the disclose.io database.

diodb lists ~2,400 disclosure policies. We keep only the ones that PAY and are
not hosted on a platform we already track — i.e. companies running their own
bounty program, which get far less researcher attention.
"""
from __future__ import annotations

import re
from typing import Iterable

from models import Program
from sources.base import Source

URL = "https://raw.githubusercontent.com/disclose/diodb/master/program-list.json"
PLATFORM_HOSTS = (
    "hackerone.com", "bugcrowd.com", "intigriti.com", "yeswehack.com",
    "immunefi.com", "hackenproof.com", "federacy.com", "openbugbounty.org",
    "synack.com", "cobalt.io", "standoff365.com",
)


def _host(url: str) -> str:
    return re.sub(r"^https?://(www\.)?", "", url or "").split("/")[0].lower()


class Independent(Source):
    name = "independent"

    def fetch(self) -> Iterable[Program]:
        rows = self._get(URL).json()
        seen: set[str] = set()
        for r in rows:
            url = (r.get("policy_url") or "").strip()
            if not url or str(r.get("offers_bounty", "")).lower() not in ("yes", "partial"):
                continue
            if str(r.get("policy_url_status", "alive")).lower() == "dead":
                continue
            host = _host(url)
            if any(host == h or host.endswith("." + h) for h in PLATFORM_HOSTS):
                continue
            key = re.sub(r"^https?://(www\.)?", "", url).rstrip("/").lower()
            if key in seen:
                continue
            seen.add(key)
            yield Program(
                platform=self.name,
                handle=key,
                name=(r.get("program_name") or host).strip(),
                url=url,
                bounty=True,
                tags=["self-hosted"],
                launched_at=str(r.get("launch_date") or ""),
                source_meta={"safe_harbor": r.get("safe_harbor"),
                             "contact": r.get("contact_email")},
            )
