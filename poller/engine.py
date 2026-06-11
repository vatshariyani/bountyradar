"""The poll cycle: fetch every source -> dedupe -> find NEW -> persist -> notify.

Designed to be run once per cron tick (GitHub Actions) or in a loop (VM).
"""
from __future__ import annotations

import logging

import requests

import config
from models import Program
from sources import ALL_SOURCE_CLASSES
from store import Store, get_store

log = logging.getLogger("bountyradar.engine")


def _build_sources():
    session = requests.Session()
    enabled = config.ENABLED_SOURCES  # None == all
    out = []
    for cls in ALL_SOURCE_CLASSES:
        if enabled is not None and cls.name not in enabled:
            continue
        out.append(cls(session=session))
    return out


def collect_programs() -> dict[str, Program]:
    """Fetch all sources, returning {doc_id: Program}. Later wins on dup ids,
    which is fine — same program from two sources is the same target."""
    programs: dict[str, Program] = {}
    for src in _build_sources():
        for prog in src.safe_fetch():
            programs[prog.doc_id] = prog
    return programs


def run_once(store: Store | None = None) -> dict:
    store = store or get_store()
    current = collect_programs()
    log.info("collected %d unique programs", len(current))

    known = store.known_ids()
    new_ids = [doc_id for doc_id in current if doc_id not in known]
    new_programs = [current[i] for i in new_ids]

    seeding = config.SEED_MODE or store.is_empty
    if seeding:
        # Establish baseline WITHOUT notifying (avoids first-run alert storm).
        store.save(list(current.values()))
        log.info("SEED: baselined %d programs, no notifications sent", len(current))
        return {"collected": len(current), "new": 0, "seeded": True}

    if new_programs:
        store.save(new_programs)
        store.notify(new_programs)
        for p in new_programs:
            log.info("NEW  %-14s %s  %s", p.platform, p.name, p.url)
    else:
        log.info("no new programs this tick")

    return {"collected": len(current), "new": len(new_programs), "seeded": False}
