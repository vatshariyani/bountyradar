"""Persistence + push. Two backends:

  FirebaseStore — production: Firestore as the program DB, FCM for push.
  LocalStore    — dev/testing: a JSON file on disk, push printed to console.

The engine picks LocalStore automatically when no Firebase credentials are
present, so you can run the whole pipeline locally with zero setup.

Each program stores a `content_hash` so we can tell three things apart:
  NEW       — doc_id not seen before
  UPDATED   — doc_id seen, but content_hash changed (scope/reward/etc.)
  unchanged — same content_hash
"""
from __future__ import annotations

import json
import logging
import os
import sys
from pathlib import Path

import config
from models import Program

log = logging.getLogger("bountyradar.store")


class Store:
    def known_hashes(self) -> dict[str, str | None]:
        """{doc_id: stored content_hash (or None if the doc predates hashing)}."""
        raise NotImplementedError

    def save_new(self, programs: list[Program]) -> None:
        raise NotImplementedError

    def save_updated(self, programs: list[Program]) -> None:
        raise NotImplementedError

    def backfill_hashes(self, programs: list[Program]) -> None:
        raise NotImplementedError

    def notify_new(self, programs: list[Program]) -> None:
        raise NotImplementedError

    def notify_updated(self, programs: list[Program]) -> None:
        raise NotImplementedError

    @property
    def is_empty(self) -> bool:
        return len(self.known_hashes()) == 0


# --------------------------------------------------------------------------- #
# Local JSON store — no Firebase needed. Great for `DRY_RUN` and first tests.   #
# --------------------------------------------------------------------------- #
class LocalStore(Store):
    def __init__(self, path: str = "state/seen.json"):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._hashes: dict[str, str | None] = self._load()

    def _load(self) -> dict:
        if self.path.exists():
            data = json.loads(self.path.read_text(encoding="utf-8"))
            return data.get("hashes", {})
        return {}

    def _flush(self) -> None:
        self.path.write_text(json.dumps({"hashes": self._hashes}, indent=2), encoding="utf-8")

    def known_hashes(self) -> dict[str, str | None]:
        return dict(self._hashes)

    def save_new(self, programs: list[Program]) -> None:
        for p in programs:
            self._hashes[p.doc_id] = p.content_hash
        self._flush()

    def save_updated(self, programs: list[Program]) -> None:
        self.save_new(programs)

    def backfill_hashes(self, programs: list[Program]) -> None:
        self.save_new(programs)

    def notify_new(self, programs: list[Program]) -> None:
        self._print(programs, "NEW")

    def notify_updated(self, programs: list[Program]) -> None:
        self._print(programs, "UPDATE")

    def _print(self, programs: list[Program], kind: str) -> None:
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
        cred = self._load_credentials(credentials)
        if not firebase_admin._apps:
            firebase_admin.initialize_app(cred)
        self.db = firestore.client()
        self.col = self.db.collection(config.PROGRAMS_COLLECTION)

    @staticmethod
    def _load_credentials(credentials):
        if config.FIREBASE_SERVICE_ACCOUNT_JSON:
            info = json.loads(config.FIREBASE_SERVICE_ACCOUNT_JSON)
            return credentials.Certificate(info)
        if config.FIREBASE_CREDENTIALS_PATH and os.path.exists(config.FIREBASE_CREDENTIALS_PATH):
            return credentials.Certificate(config.FIREBASE_CREDENTIALS_PATH)
        raise RuntimeError(
            "No Firebase credentials. Set FIREBASE_SERVICE_ACCOUNT (inline JSON) "
            "or GOOGLE_APPLICATION_CREDENTIALS (path)."
        )

    def known_hashes(self) -> dict[str, str | None]:
        out: dict[str, str | None] = {}
        for doc in self.col.select(["content_hash"]).stream():
            out[doc.id] = (doc.to_dict() or {}).get("content_hash")
        return out

    def _batch_set(self, programs, to_dict) -> None:
        batch = self.db.batch()
        n = 0
        for p in programs:
            batch.set(self.col.document(p.doc_id), to_dict(p), merge=True)
            n += 1
            if n % 400 == 0:
                batch.commit()
                batch = self.db.batch()
        if n % 400 != 0:
            batch.commit()

    def save_new(self, programs: list[Program]) -> None:
        self._batch_set(programs, lambda p: p.to_firestore())

    def save_updated(self, programs: list[Program]) -> None:
        self._batch_set(programs, lambda p: p.to_firestore_update())

    def backfill_hashes(self, programs: list[Program]) -> None:
        # Only stamp the content_hash on legacy docs; don't bump updated_at.
        self._batch_set(programs, lambda p: {"content_hash": p.content_hash})

    def notify_new(self, programs: list[Program]) -> None:
        if not programs:
            return
        if len(programs) > config.MAX_INDIVIDUAL_NOTIFICATIONS:
            self._send(
                title=f"🚨 {len(programs)} new bug bounty programs",
                body="Open BountyRadar to see them all and pick a target.",
                data={"type": "batch", "count": str(len(programs))},
            )
            return
        for p in programs:
            self._send(
                title=p.notification_title(), body=p.notification_body(),
                data={"type": "program", "doc_id": p.doc_id, "platform": p.platform, "url": p.url},
            )

    def notify_updated(self, programs: list[Program]) -> None:
        if not programs:
            return
        if len(programs) > config.MAX_INDIVIDUAL_NOTIFICATIONS:
            self._send(
                title=f"🔄 {len(programs)} programs updated",
                body="Scope or rewards changed — open BountyRadar to review.",
                data={"type": "batch_update", "count": str(len(programs))},
            )
            return
        for p in programs:
            self._send(
                title=p.update_title(), body=p.update_body(),
                data={"type": "update", "doc_id": p.doc_id, "platform": p.platform, "url": p.url},
            )

    def _send(self, title: str, body: str, data: dict) -> None:
        msg = self._messaging.Message(
            topic=config.FCM_TOPIC,
            notification=self._messaging.Notification(title=title, body=body),
            data={k: str(v) for k, v in data.items()},
            android=self._messaging.AndroidConfig(priority="high"),
        )
        msg_id = self._messaging.send(msg)
        log.info("FCM sent %s: %s", msg_id, title)


def get_store() -> Store:
    """Pick the backend based on available credentials / dry-run."""
    if config.DRY_RUN:
        log.info("DRY_RUN: using LocalStore (no writes/push to Firebase)")
        return LocalStore()
    if config.FIREBASE_SERVICE_ACCOUNT_JSON or (
        config.FIREBASE_CREDENTIALS_PATH and os.path.exists(config.FIREBASE_CREDENTIALS_PATH)
    ):
        return FirebaseStore()
    log.warning("No Firebase credentials found — falling back to LocalStore.")
    return LocalStore()
