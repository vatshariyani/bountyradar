"""Open Bug Bounty — beginner-friendly, coordinated-disclosure platform.

It exposes RSS/Atom feeds of recent activity. We use the feed to surface newly
active programs/sites. Feed URL may need confirming against the live site; the
parser is defensive so a layout change just yields fewer items, never a crash.
"""
from __future__ import annotations

from typing import Iterable

import feedparser

from models import Program
from sources.base import Source

# Open Bug Bounty publishes RSS for latest coordinated disclosures / programs.
FEED = "https://www.openbugbounty.org/feed/"


class OpenBugBounty(Source):
    name = "openbugbounty"

    def fetch(self) -> Iterable[Program]:
        # feedparser handles the HTTP GET itself; pass our UA for politeness.
        parsed = feedparser.parse(FEED, agent="BountyRadar/0.1")
        for entry in parsed.entries:
            link = entry.get("link", "")
            title = entry.get("title", "").strip()
            if not link or not title:
                continue
            # handle = the unique report/program URL path
            handle = link.rstrip("/").rsplit("/", 1)[-1] or link
            yield Program(
                platform=self.name,
                handle=handle,
                name=title,
                url=link,
                bounty=False,  # OBB is mostly non-monetary coordinated disclosure
                tags=["coordinated-disclosure", "beginner"],
                launched_at=entry.get("published", ""),
                source_meta={"summary": entry.get("summary", "")[:300]},
            )
