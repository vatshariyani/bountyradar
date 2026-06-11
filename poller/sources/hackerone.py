"""HackerOne.

Two ways in:
  1. Hacker API (used here): GET https://api.hackerone.com/v1/hackers/programs
     HTTP Basic auth with (api_username, api_token) from your H1 account.
     Reliable JSON. Lists programs you can see/are eligible for.
  2. Public directory (Phase 2): GraphQL scrape of hackerone.com/directory —
     broader, but brittle and unauthenticated-rate-limited.

This module uses #1 when H1_API_USERNAME + H1_API_TOKEN are set, else yields
nothing (so it is safe to leave enabled even before you add a token).
"""
from __future__ import annotations

import os
from typing import Iterable

from models import Program
from sources.base import Source

API = "https://api.hackerone.com/v1/hackers/programs"
WEB = "https://hackerone.com/"


class HackerOne(Source):
    name = "hackerone"

    def fetch(self) -> Iterable[Program]:
        user = os.environ.get("H1_API_USERNAME")
        token = os.environ.get("H1_API_TOKEN")
        if not (user and token):
            # No credentials: stay quiet. Public-directory scrape lands in Phase 2.
            return

        url = API
        page = 0
        while url and page < 25:
            resp = self.session.get(
                url, auth=(user, token),
                headers={"Accept": "application/json"}, timeout=25,
            )
            resp.raise_for_status()
            payload = resp.json()
            for item in payload.get("data", []):
                attrs = item.get("attributes", {})
                handle = attrs.get("handle") or item.get("id")
                if not handle:
                    continue
                yield Program(
                    platform=self.name,
                    handle=str(handle),
                    name=attrs.get("name") or str(handle),
                    url=f"{WEB}{handle}",
                    bounty=bool(attrs.get("offers_bounties")),
                    tags=_state_tags(attrs),
                    launched_at=attrs.get("started_accepting_at", ""),
                    source_meta={
                        "submission_state": attrs.get("submission_state"),
                        "currency": attrs.get("currency"),
                    },
                )
            url = (payload.get("links") or {}).get("next")
            page += 1


def _state_tags(attrs: dict) -> list[str]:
    tags = []
    if attrs.get("offers_bounties"):
        tags.append("bounty")
    state = attrs.get("submission_state")
    if state:
        tags.append(state)
    return tags
