"""Runtime configuration. Everything is overridable by environment variable so
the same code runs identically on GitHub Actions and on an Oracle VM."""
from __future__ import annotations

import os

# --- Which sources run. Comma-separated env override, else all enabled. -------
# e.g.  ENABLED_SOURCES="yeswehack,firebounty"
_env_sources = os.environ.get("ENABLED_SOURCES", "").strip()
ENABLED_SOURCES = (
    [s.strip() for s in _env_sources.split(",") if s.strip()]
    if _env_sources else None  # None == all registered sources
)

# --- Firestore collection that holds every program we have ever seen ----------
PROGRAMS_COLLECTION = os.environ.get("PROGRAMS_COLLECTION", "programs")

# --- FCM topic the Android app subscribes to. Topic = no device-token storage.-
FCM_TOPIC = os.environ.get("FCM_TOPIC", "new_programs")

# --- Notification behavior ----------------------------------------------------
# If a single poll discovers more than this many new programs, send ONE summary
# push instead of spamming. Protects against a source hiccup flooding you.
MAX_INDIVIDUAL_NOTIFICATIONS = int(os.environ.get("MAX_INDIVIDUAL_NOTIFICATIONS", "8"))

# --- Seed mode ----------------------------------------------------------------
# First ever run would mark EVERY program as "new" -> notification storm.
# When the programs collection is empty we silently seed the baseline instead.
# Force with SEED_MODE=1 (writes everything, notifies nothing).
SEED_MODE = os.environ.get("SEED_MODE", "").strip() in {"1", "true", "yes"}

# --- Dry run: fetch + diff + log, but DO NOT write Firestore or send push -----
DRY_RUN = os.environ.get("DRY_RUN", "").strip() in {"1", "true", "yes"}

# --- Firebase credentials -----------------------------------------------------
# Path to the service-account JSON, OR the JSON itself in FIREBASE_SERVICE_ACCOUNT.
FIREBASE_CREDENTIALS_PATH = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS", "")
FIREBASE_SERVICE_ACCOUNT_JSON = os.environ.get("FIREBASE_SERVICE_ACCOUNT", "")
