# BountyRadar

Get notified on your Android phone the moment a **new bug bounty program or scope** launches across platforms — so you can flip on your laptop and grab the low-hanging fruit before the crowd arrives.

> Single login (your app account) → one feed of new programs from many platforms → instant push alert → tap to see scope and go.

---

## How it works

```
ALWAYS-ON POLLER (Python)                  FIREBASE (free Spark plan)         ANDROID APP (Kotlin)
  polls every ~10 min                        Firestore  = program database      Login (Firebase Auth)
   - Firebounty (aggregator)        ──────►  FCM        = push delivery   ────► Push: "New program: X"
   - YesWeHack API                  writes   Auth       = your one login        Browse / search programs
   - Open Bug Bounty RSS            new                                          Tap -> scope + link
   - HackerOne directory            progs    sends push to topic                 (later) track my reports
   - (phased) Bugcrowd, Intigriti,
     Immunefi, Web3 contests
  diffs vs Firestore -> only NEW ones notify
```

- **Poller host:** GitHub Actions cron now (free, no credit card, ~10–15 min). Portable to an Oracle Cloud Free Tier VM later for near-real-time (1–2 min) with no code changes.
- **Push:** Firebase Cloud Messaging — free forever. The app subscribes to an FCM **topic**, so we never have to store device tokens.
- **Database + login:** Firestore + Firebase Auth on the free Spark plan.
- **No platform passwords stored.** The public new-program feed needs no platform login. Personal/private programs (Phase 3) use *your own API tokens*, not passwords.

## Repository layout

```
bugbounty/
├─ poller/                 # Python backend (the brain)
│  ├─ sources/             # one module per platform/source
│  ├─ models.py            # normalized Program object
│  ├─ store.py             # Firestore + FCM via Firebase Admin SDK
│  ├─ engine.py            # fetch -> diff -> persist -> notify
│  ├─ main.py              # entry point (run once per cron tick)
│  ├─ config.py            # which sources are enabled, tunables
│  └─ requirements.txt
├─ android/                # Kotlin app (added in Phase 1 step 2)
└─ .github/workflows/      # GitHub Actions cron that runs the poller
```

## Build phases

| Phase | Scope |
|---|---|
| **1 — core value** | Poller for easy sources (Firebounty, YesWeHack, Open Bug Bounty, HackerOne directory) → Firestore → FCM → Android app: login, push alerts, searchable program list. |
| **2 — coverage** | Add scrapers for Bugcrowd, Intigriti, Immunefi, Web3 contests (Code4rena/Sherlock/Cantina). |
| **3 — personal tracking** | Add *your* API tokens → private/invited programs + track your own reports & payouts across platforms. |

## Setup (summary — full steps in `docs/SETUP.md`, added with the app)

1. Create a Firebase project (free Spark plan). Enable **Firestore**, **Authentication** (Email + Google), and **Cloud Messaging**.
2. Generate a **service account key** (JSON) — used by the poller to write Firestore + send FCM.
3. Put the poller secrets into GitHub repo **Actions secrets** (`FIREBASE_SERVICE_ACCOUNT`, optional platform tokens).
4. Enable the GitHub Actions workflow — it runs the poller on a schedule.
5. Build the Android app in `android/`, drop in your `google-services.json`, install on your phone, log in. Done.

## Status

- [x] Architecture decided
- [ ] Phase 1 poller (in progress)
- [ ] GitHub Actions deploy
- [ ] Android app
- [ ] Phase 2 scrapers
- [ ] Phase 3 personal tracking
