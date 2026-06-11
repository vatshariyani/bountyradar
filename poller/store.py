"""Persistence + push. Two backends:

  FirebaseStore — production: Firestore as the program DB, FCM for push.
  LocalStore    — dev/testing: a JSON file on disk, push printed to console.

The engine picks LocalStore automatically when no Firebase credentials are
present, so you can run the whole pipeline locally with zero setup.
"""
from __future__ import annotations

import json
import logging
import os
import sys
from pathlib import Path
from typing import Iterable

import config
from models import Program

log = logging.getLogger("bountyradar.store")


class Store:
    def known_ids(self) -> set[str]:
        raise NotImplementedError

    def save(self, programs: list[Program]) -> None:
        raise NotImplementedError

    def notify(self, programs: list[Program]) -> None:
        raise NotImplementedError

    @property
    def is_empty(self) -> bool:
        return len(self.known_ids()) == 0


# --------------------------------------------------------------------------- #
# Local JSON store — no Firebase needed. Great for `DRY_RUN` and first tests.   #
# --------------------------------------------------------------------------- #
class LocalStore(Store):
    def __init__(self, path: str = "state/seen.json"):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._data = self._load()

    def _load(self) -> dict:
        if self.path.exists():
            return json.loads(self.path.read_text(encoding="utf-8"))
        return {"ids": []}

    def known_ids(self) -> set[str]:
        return set(self._data.get("ids", []))

    def save(self, programs: list[Program]) -> None:
        ids = set(self._data.get("ids", []))
        ids.update(p.doc_id for p in programs)
        self._data["ids"] = sorted(ids)
        self.path.write_text(json.dumps(self._data, indent=2), encoding="utf-8")

    def notify(self, programs: list[Program]) -> None:
        for p in programs:
            line = f"[PUSH] {p.notification_title()} — {p.notification_body()} — {p.url}"
            # Windows consoles default to cp1252 and choke on emoji; degrade safely.
            enc = sys.stdout.encoding or "utf-8"
            sys.stdout.write(line.encode(enc, errors="replace").decode(enc) + "\n")


# --------------------------------------------------------------------------- #
# Firebase store — Firestore DB + FCM push to a topic.                          #
# --------------------------------------------------------------------------- #
class FirebaseStore(Store):
    def __init__(self):
        import firebase_admin
        from firebase_admin import credentials, firestore, messaging

        self._fs_module = firestore
        self._messaging = messaging

        cred = self._load_credentials(credentials)
        if not firebase_admin._apps:
            firebase_admin.initialize_app(cred)
        self.db = firestore.client()
        self.col = self.db.collection(config.PROGRAMS_COLLECTION)
        self._known_cache: set[str] | None = None

    @staticmethod
    def _load_credentials(credentials):
        # Prefer inline JSON (GitHub secret), else a file path.
        if config.FIREBASE_SERVICE_ACCOUNT_JSON:
            info = json.loads(config.FIREBASE_SERVICE_ACCOUNT_JSON)
            return credentials.Certificate(info)
        if config.FIREBASE_CREDENTIALS_PATH and os.path.exists(config.FIREBASE_CREDENTIALS_PATH):
            return credentials.Certificate(config.FIREBASE_CREDENTIALS_PATH)
        raise RuntimeError(
            "No Firebase credentials. Set FIREBASE_SERVICE_ACCOUNT (inline JSON) "
            "or GOOGLE_APPLICATION_CREDENTIALS (path)."
        )

    def known_ids(self) -> set[str]:
        if self._known_cache is None:
            # We only need the IDs, so select() nothing -> doc.id is enough.
            self._known_cache = {doc.id for doc in self.col.select([]).stream()}
        return self._known_cache

    def save(self, programs: list[Program]) -> None:
        batch = self.db.batch()
        count = 0
        for p in programs:
            ref = self.col.document(p.doc_id)
            batch.set(ref, p.to_firestore(), merge=True)
            self._known_cache and self._known_cache.add(p.doc_id)
            count += 1
            if count % 400 == 0:  # Firestore batch limit is 500
                batch.commit()
                batch = self.db.batch()
        if count % 400 != 0:
            batch.commit()

    def notify(self, programs: list[Program]) -> None:
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
                title=p.notification_title(),
                body=p.notification_body(),
                data={
                    "type": "program",
                    "doc_id": p.doc_id,
                    "platform": p.platform,
                    "url": p.url,
                },
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
