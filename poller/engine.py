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

    seeding = config.SEED_MODE or store.is_empty
    if seeding:
        # Establish baseline WITHOUT notifying (avoids first-run alert storm).
        store.save_new(list(current.values()))
        log.info("SEED: baselined %d programs, no notifications sent", len(current))
        return {"collected": len(current), "new": 0, "updated": 0, "seeded": True}

    known = store.known_hashes()  # {doc_id: content_hash or None}
    new_programs, updated_programs, backfill = [], [], []
    for doc_id, p in current.items():
        if doc_id not in known:
            new_programs.append(p)
        else:
            old_hash = known[doc_id]
            if old_hash is None:
                backfill.append(p)          # legacy doc without a hash yet
            elif old_hash != p.content_hash:
                updated_programs.append(p)   # genuine change -> notify

    if backfill:
        # Silently stamp hashes on pre-hash documents so they don't all look
        # "updated" on the first run after this feature ships.
        store.backfill_hashes(backfill)
        log.info("backfilled content_hash on %d legacy programs", len(backfill))

    if new_programs:
        store.save_new(new_programs)
        store.notify_new(new_programs)
        for p in new_programs:
            log.info("NEW     %-14s %s", p.platform, p.name)

    if updated_programs:
        store.save_updated(updated_programs)
        store.notify_updated(updated_programs)
        for p in updated_programs:
            log.info("UPDATED %-14s %s", p.platform, p.name)

    if not new_programs and not updated_programs:
        log.info("no new or updated programs this tick")

    return {
        "collected": len(current),
        "new": len(new_programs),
        "updated": len(updated_programs),
        "backfilled": len(backfill),
        "seeded": False,
    }
