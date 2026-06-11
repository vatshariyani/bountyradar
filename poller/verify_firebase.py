"""One-shot Firebase connectivity check.

Usage (from the poller/ folder):
    python verify_firebase.py                       # uses ./firebase-service-account.json
    python verify_firebase.py C:\\path\\to\\key.json  # or pass an explicit path

Verifies, in order: credentials load -> Firestore write+read -> FCM topic send.
Prints a clear OK/FAIL for each step so we know exactly what (if anything) is wrong.
"""
from __future__ import annotations

import datetime
import os
import sys

import firebase_admin
from firebase_admin import credentials, firestore, messaging


def main(argv: list[str]) -> int:
    path = argv[0] if argv else "firebase-service-account.json"
    if not os.path.exists(path):
        print(f"FAIL: service-account JSON not found at: {os.path.abspath(path)}")
        print("      Download it from Firebase console -> Project settings ->")
        print("      Service accounts -> Generate new private key, save it there.")
        return 1

    cred = credentials.Certificate(path)
    firebase_admin.initialize_app(cred)
    print(f"OK  : credentials loaded (project = {cred.project_id})")

    db = firestore.client()
    ref = db.collection("_meta").document("connectivity")
    now = datetime.datetime.now(datetime.timezone.utc).isoformat()
    ref.set({"last_check": now, "by": "verify_firebase.py"})
    echo = ref.get().to_dict()
    print(f"OK  : Firestore write + read works ({echo['last_check']})")

    msg = messaging.Message(
        topic="new_programs",
        notification=messaging.Notification(
            title="BountyRadar connected",
            body="Firebase + FCM are wired up. New-program alerts will land here.",
        ),
        android=messaging.AndroidConfig(priority="high"),
    )
    msg_id = messaging.send(msg)
    print(f"OK  : FCM accepted a message for topic 'new_programs' (id={msg_id})")
    print("      (You'll SEE this notification only after the app is installed and")
    print("       subscribed — FCM accepting it now proves the credential works.)")

    print("\nALL GOOD — Firebase is connected. Next: seed Firestore with programs.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
