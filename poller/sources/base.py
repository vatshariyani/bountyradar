"""Base class every source implements.

A source knows how to fetch the *current* list of programs from one platform (or
one aggregator). It must NOT do any diffing or notifying — the engine handles
that. Keep each source small and resilient: a failure in one source must never
take down the whole poll.
"""
from __future__ import annotations

import logging
from typing import Iterable

import requests

from models import Program

log = logging.getLogger("bountyradar.source")

# A polite, identifiable UA. Some platforms block obviously-bot/default agents.
USER_AGENT = "BountyRadar/0.1 (personal new-program notifier)"

DEFAULT_TIMEOUT = 25


class Source:
    #: short stable id, also used as the `platform` on emitted Programs
    name: str = "base"

    #: set False to skip without deleting the module (see config.ENABLED_SOURCES)
    enabled: bool = True

    def __init__(self, session: requests.Session | None = None):
        self.session = session or requests.Session()
        self.session.headers.setdefault("User-Agent", USER_AGENT)

    def fetch(self) -> Iterable[Program]:  # pragma: no cover - interface
        """Return the current programs visible on this platform. Override me."""
        raise NotImplementedError

    # -- helpers -----------------------------------------------------------
    def _get(self, url: str, **kw) -> requests.Response:
        kw.setdefault("timeout", DEFAULT_TIMEOUT)
        resp = self.session.get(url, **kw)
        resp.raise_for_status()
        return resp

    def safe_fetch(self) -> list[Program]:
        """Wrapper used by the engine: never raises, logs and returns []."""
        try:
            items = list(self.fetch())
            log.info("source %s: %d programs", self.name, len(items))
            return items
        except Exception as exc:  # noqa: BLE001 - isolation is intentional
            log.warning("source %s failed: %s", self.name, exc)
            return []
