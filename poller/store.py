"""Persistence + push. Two backends:

  FirebaseStore — production: Firestore as the program DB, FCM for push.
  LocalStore    — dev/testing: a JSON file on disk, push printed to console.

Quota design (this is the important part):
  The poller must NOT read the whole `programs` collection every tick — doing so
  burned ~182k reads/day against Firebase's free 50k/day limit and took the app
  down. Instead we keep a single `_state/hash_index` document holding a compact
  {doc_id: {h: content_hash, seen: YYYY-MM-DD}} map. Each poll reads that ONE
  document, so reads per poll are O(1) regardless of how many programs exist.

  `seen` is bumped to today for every program present on a poll; anything not
  seen for > STALE_DAYS is pruned (ended contests, closed programs).
"""
from __future__ import annotations

import json
import logging
import os
import re
import sys
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Iterable

import config
from models import Program

log = logging.getLogger("bountyradar.store")


def platform_key(platform: str) -> str:
    """Topic-safe platform id (FCM topics allow [a-zA-Z0-9-_.~%])."""
    return re.sub(r"[^a-zA-Z0-9_.~-]", "_", platform.removeprefix("fb:"))


def _today() -> str:
    return datetime.now(timezone.utc).date().isoformat()


def _age_days(seen: str | None) -> int:
    """Days since `seen` (YYYY-MM-DD / ISO). Unparseable -> 0 (treat as fresh)."""
    if not seen:
        return 0
    try:
        return (datetime.now(timezone.utc).date() - date.fromisoformat(seen[:10])).days
    except ValueError:
        return 0


class Store:
    # index: {doc_id: {"h": content_hash|None, "seen": "YYYY-MM-DD"}}
    index: dict[str, dict]
    # small key/value state persisted alongside the index (news_at / news_hash)
    meta: dict[str, str]

    def write_news(self, items: list[dict]) -> None: raise NotImplementedError

    def known_hashes(self) -> dict[str, str | None]:
        return {k: v.get("h") for k, v in self.index.items()}

    @property
    def is_empty(self) -> bool:
        return len(self.index) == 0

    def _touch(self, p: Program) -> None:
        self.index[p.doc_id] = {"h": p.content_hash, "seen": _today()}

    def mark_seen(self, doc_ids: Iterable[str]) -> None:
        today = _today()
        for d in doc_ids:
            if d in self.index:
                self.index[d]["seen"] = today

    def set_hashes(self, programs: list[Program]) -> None:
        """Index-only hash stamp (no document write, no notification)."""
        for p in programs:
            self._touch(p)

    def stale_ids(self, stale_days: int) -> list[str]:
        return [d for d, e in self.index.items() if _age_days(e.get("seen")) > stale_days]

    # --- backend-specific ---
    def save_new(self, programs: list[Program]) -> None: raise NotImplementedError
    def save_updated(self, programs: list[Program]) -> None: raise NotImplementedError
    def prune_stale(self, stale_days: int) -> list[str]: raise NotImplementedError
    def flush(self) -> None: raise NotImplementedError
    def notify_new(self, programs: list[Program]) -> None: raise NotImplementedError
    def notify_updated(self, programs: list[Program]) -> None: raise NotImplementedError


# --------------------------------------------------------------------------- #
# Local JSON store — no Firebase needed. Great for DRY_RUN and tests.           #
# --------------------------------------------------------------------------- #
class LocalStore(Store):
    def __init__(self, path: str = "state/index.json"):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        data = json.loads(self.path.read_text(encoding="utf-8")) if self.path.exists() else {}
        self.index = data.get("index", {})
        self.meta = data.get("meta", {})

    def write_news(self, items: list[dict]) -> None:
        log.info("[local] news doc would hold %d items", len(items))

    def save_new(self, programs: list[Program]) -> None:
        for p in programs:
            self._touch(p)

    def save_updated(self, programs: list[Program]) -> None:
        for p in programs:
            self._touch(p)

    def prune_stale(self, stale_days: int) -> list[str]:
        ids = self.stale_ids(stale_days)
        for d in ids:
            self.index.pop(d, None)
        return ids

    def flush(self) -> None:
        self.path.write_text(json.dumps({"index": self.index, "meta": self.meta}, indent=0),
                             encoding="utf-8")

    def notify_new(self, programs: list[Program]) -> None:
        self._print(programs, "NEW")

    def notify_updated(self, programs: list[Program]) -> None:
        self._print(programs, "UPDATE")

    def _print(self, programs: list[Program], kind: str) -> None:
        if config.NO_NOTIFY:
            return
        for p in programs:
            title = p.notification_title() if kind == "NEW" else p.update_title()
            line = f"[{kind} PUSH] {title} — {p.url}"
            enc = sys.stdout.encoding or "utf-8"
            sys.stdout.write(line.encode(enc, errors="replace").decode(enc) + "\n")


# --------------------------------------------------------------------------- #
# Firebase store — Firestore DB + FCM push to a topic.                          #
# --------------------------------------------------------------------------- #
class FirebaseStore(Store):
    def __init__(self):
        import firebase_admin
        from firebase_admin import credentials, firestore, messaging

        self._messaging = messaging
        self._fs = firestore
        cred = self._load_credentials(credentials)
        if not firebase_admin._apps:
            firebase_admin.initialize_app(cred)
        self.db = firestore.client()
        self.col = self.db.collection(config.PROGRAMS_COLLECTION)
        self.state_ref = self.db.collection(config.STATE_COLLECTION).document(config.STATE_DOC)
        self._load_index()

    @staticmethod
    def _load_credentials(credentials):
        if config.FIREBASE_SERVICE_ACCOUNT_JSON:
            return credentials.Certificate(json.loads(config.FIREBASE_SERVICE_ACCOUNT_JSON))
        if config.FIREBASE_CREDENTIALS_PATH and os.path.exists(config.FIREBASE_CREDENTIALS_PATH):
            return credentials.Certificate(config.FIREBASE_CREDENTIALS_PATH)
        raise RuntimeError(
            "No Firebase credentials. Set FIREBASE_SERVICE_ACCOUNT (inline JSON) "
            "or GOOGLE_APPLICATION_CREDENTIALS (path)."
        )

    def _load_index(self) -> None:
        """ONE document read. If the index doc is missing (first run of this
        version), migrate from the collection once — seeding `seen` from each
        doc's own updated_at/first_seen so genuinely-stale programs get pruned."""
        snap = self.state_ref.get()  # 1 read
        self.meta = {}
        if snap.exists:
            doc = snap.to_dict() or {}
            raw = doc.get("json")
            self.index = json.loads(raw) if raw else {}
            self.meta = {k: doc[k] for k in ("news_at", "news_hash") if doc.get(k)}
            log.info("loaded hash_index: %d entries (1 read)", len(self.index))
            return
        log.warning("no hash_index doc — one-time migration from programs collection")
        self.index = {}
        for doc in self.col.select(["content_hash", "updated_at", "first_seen"]).stream():
            if doc.id.startswith("_"):       # internal docs (e.g. the news feed)
                continue
            x = doc.to_dict() or {}
            seen =(x.get("updated_at") or x.get("first_seen") or "")[:10] or _today()
            self.index[doc.id] = {"h": x.get("content_hash"), "seen": seen}
        log.info("migrated %d entries into hash_index", len(self.index))

    def _batch(self, ops) -> None:
        batch = self.db.batch()
        n = 0
        for op in ops:
            op(batch)
            n += 1
            if n % 400 == 0:
                batch.commit()
                batch = self.db.batch()
        if n % 400:
            batch.commit()

    def save_new(self, programs: list[Program]) -> None:
        self._batch([(lambda b, p=p: b.set(self.col.document(p.doc_id), p.to_firestore(), merge=True))
                     for p in programs])
        for p in programs:
            self._touch(p)

    def save_updated(self, programs: list[Program]) -> None:
        self._batch([(lambda b, p=p: b.set(self.col.document(p.doc_id), p.to_firestore_update(), merge=True))
                     for p in programs])
        for p in programs:
            self._touch(p)

    def prune_stale(self, stale_days: int) -> list[str]:
        ids = self.stale_ids(stale_days)
        if ids:
            self._batch([(lambda b, d=d: b.delete(self.col.document(d))) for d in ids])
            for d in ids:
                self.index.pop(d, None)
        return ids

    def flush(self) -> None:
        self.state_ref.set({
            **self.meta,
            "json":json.dumps(self.index, separators=(",", ":")),
            "count": len(self.index),
            "updated": datetime.now(timezone.utc).isoformat(),
        })

    def write_news(self, items: list[dict]) -> None:
        self.col.document(config.NEWS_DOC).set({
            "kind": "news",
            "json": json.dumps(items, separators=(",", ":"), ensure_ascii=False),
            "count": len(items),
            "updated": datetime.now(timezone.utc).isoformat(),
        })

    # Each alert goes to a per-platform topic so the app can mute platforms.
    # The legacy topic is OR-ed in, so older app builds keep receiving everything
    # (a condition message is delivered once per device even if both match).
    def notify_new(self, programs: list[Program]) -> None:
        self._notify(programs, new=True)

    def notify_updated(self, programs: list[Program]) -> None:
        self._notify(programs, new=False)

    def _notify(self, programs: list[Program], new: bool) -> None:
        if config.NO_NOTIFY or not programs:
            return
        summarize = len(programs) > config.MAX_INDIVIDUAL_NOTIFICATIONS
        groups: dict[str, list[Program]] = {}
        for p in programs:
            groups.setdefault(platform_key(p.platform), []).append(p)
        for key, group in groups.items():
            if summarize:
                label = key.replace("_", " ").title()
                if new:
                    self._send(key, f"🚨 {len(group)} new programs on {label}",
                               "Open BountyRadar to see them and pick a target.",
                               {"type": "batch", "platform": key, "count": str(len(group))})
                else:
                    self._send(key, f"🔄 {len(group)} programs updated on {label}",
                               "Scope or rewards changed — open BountyRadar to review.",
                               {"type": "batch_update", "platform": key, "count": str(len(group))})
                continue
            for p in group:
                self._send(key,
                           p.notification_title() if new else p.update_title(),
                           p.notification_body() if new else p.update_body(),
                           {"type": "program" if new else "update", "doc_id": p.doc_id,
                            "platform": key, "url": p.url})

    def _send(self, key: str, title: str, body: str, data: dict) -> None:
        msg = self._messaging.Message(
            condition=f"'plat_{key}' in topics || '{config.FCM_TOPIC}' in topics",
            notification=self._messaging.Notification(title=title, body=body),
            data={k: str(v) for k, v in data.items()},
            android=self._messaging.AndroidConfig(priority="high"),
        )
        log.info("FCM sent %s: %s", self._messaging.send(msg), title)


def get_store() -> Store:
    if config.DRY_RUN:
        log.info("DRY_RUN: using LocalStore (no Firebase writes/push)")
        return LocalStore()
    if config.FIREBASE_SERVICE_ACCOUNT_JSON or (
        config.FIREBASE_CREDENTIALS_PATH and os.path.exists(config.FIREBASE_CREDENTIALS_PATH)
    ):
        return FirebaseStore()
    log.warning("No Firebase credentials found — falling back to LocalStore.")
    return LocalStore()
