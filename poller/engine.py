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

    if config.SEED_MODE or store.is_empty:
        # Establish baseline WITHOUT notifying (avoids first-run alert storm).
        progs = list(current.values())
        store.save_new(progs)
        store.mark_seen(current.keys())
        store.flush()
        log.info("SEED: baselined %d programs, no notifications sent", len(current))
        return {"collected": len(current), "new": 0, "updated": 0, "pruned": 0, "seeded": True}

    known = store.known_hashes()  # {doc_id: content_hash or None} — from the index (1 read)

    # Outage guard: a big drop almost always means a source failed, not that
    # hundreds of programs closed at once. Skip ALL writes + prune so a transient
    # failure can never wipe the database.
    if known and len(current) < config.PRUNE_MIN_RATIO * len(known):
        log.warning("only %d programs vs %d known (<%.0f%%) — suspected source "
                    "outage; skipping writes/prune this tick",
                    len(current), len(known), config.PRUNE_MIN_RATIO * 100)
        return {"collected": len(current), "known": len(known), "aborted": "source-outage"}

    new_programs, updated_programs, backfill = [], [], []
    for doc_id, p in current.items():
        if doc_id not in known:
            new_programs.append(p)
        else:
            old = known[doc_id]
            if old is None:
                backfill.append(p)            # legacy index entry without a hash
            elif old != p.content_hash:
                updated_programs.append(p)    # genuine change -> notify

    if new_programs:
        store.save_new(new_programs)
        for p in new_programs:
            log.info("NEW     %-14s %s", p.platform, p.name)
    if updated_programs:
        store.save_updated(updated_programs)
        for p in updated_programs:
            log.info("UPDATED %-14s %s", p.platform, p.name)
    if backfill:
        store.set_hashes(backfill)            # silent: index-only, no doc write
        log.info("stamped hashes on %d legacy entries", len(backfill))

    # Everything present this tick is "seen today" -> protected from pruning.
    store.mark_seen(current.keys())

    pruned: list[str] = []
    if config.PRUNE:
        pruned = store.prune_stale(config.STALE_DAYS)
        if pruned:
            log.info("pruned %d stale programs (gone > %d days)", len(pruned), config.STALE_DAYS)

    store.flush()  # persist the index in ONE write

    store.notify_new(new_programs)
    store.notify_updated(updated_programs)

    if not new_programs and not updated_programs and not pruned:
        log.info("no changes this tick")

    return {
        "collected": len(current),
        "new": len(new_programs),
        "updated": len(updated_programs),
        "pruned": len(pruned),
        "seeded": False,
    }
