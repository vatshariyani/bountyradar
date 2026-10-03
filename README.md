# BountyRadar

An Android app that alerts you the moment a **new bug bounty program launches or an existing one changes scope**, across every major platform, so you can start testing before the crowd arrives.

One login, one feed, one push notification. It runs entirely on free tiers: no server to rent and no credit card.

## What it does

- **Instant alerts.** A push notification for every new program and for every program whose scope, reward or rules change. Alerts can be muted per platform.
- **One feed for every platform.** About 1,800 programs from 11 platforms, searchable by name or by scope (for example `*.example.com`).
- **Built for picking targets.** Filter by platform, paid or VDP, minimum reward, wildcard / API / mobile scope, Web3, and recency. Sort by a "best target" score, newest, recently updated, highest reward or scope size.
- **Program detail.** Reward range, in-scope assets (copyable), wildcard count, first-seen date, private notes, and a button that opens the program page.
- **Shortlist.** Bookmark programs and keep per-program notes on the device.
- **Learn tab.** A security feed with an in-app detail view for each entry:
  - 0-day and pre-CVE: Zero Day Initiative upcoming and unpatched advisories, Full Disclosure, GitHub advisories with no CVE assigned
  - Actively exploited: CISA Known Exploited Vulnerabilities
  - New CVEs: high and critical CVEs from the last 48 hours (NVD), oss-security
  - Disclosed reports: HackerOne Hacktivity
  - Exploits: Exploit-DB
  - Research and write-ups: PortSwigger Research, Project Zero, Intigriti, InfoSec Write-ups, The Hacker News
- **Connected accounts (optional).** Add your own HackerOne or Intigriti API token to see balance, invites and recent reports. Tokens are stored encrypted on the device and never leave it.

## Platforms covered

| Source | Platforms |
|---|---|
| [bounty-targets-data](https://github.com/arkadiyt/bounty-targets-data) | HackerOne, Bugcrowd, Intigriti, YesWeHack, Federacy |
| Direct | Immunefi, Sherlock, Cantina, HackenProof, Standoff 365 |
| [diodb](https://github.com/disclose/diodb) | Independent, self-hosted paying programs |

## How it works

```
POLLER (Python, GitHub Actions cron)        FIREBASE (free Spark plan)          ANDROID APP (Kotlin, Compose)
  every 15 minutes:                           Firestore = program database         Login (Firebase Auth)
   fetch all sources                 ──────►  FCM       = push delivery     ────►  Push: "New program: X"
   hash each program                 writes   Auth      = app login                Feed, filters, detail, notes
   diff against the stored index     only                                          Learn tab
   write only new / changed ones     changes
   delete programs that disappeared
   refresh the news feed hourly
```

- **Change detection.** Each program gets a content hash of its name, link, reward, scope and tags. A new hash means a new program or an update, and only those are written and pushed.
- **Cheap on quota.** All known hashes live in a single Firestore document, so a poll costs one read regardless of how many programs exist. The news feed is also a single document.
- **Self-cleaning.** Programs not seen on any source for 2 days are deleted. If a source fails or returns less than half of what is known, that poll skips deletion so an outage cannot wipe the database.
- **No stored device tokens.** The app subscribes to FCM topics (one per platform), so the backend never keeps a list of devices.
- **No platform passwords.** The public feed needs no platform login.

## Repository layout

```
poller/                  Python backend
  sources/               one module per platform or data source
  engine.py              fetch -> diff -> persist -> prune -> notify
  store.py               Firestore + FCM (and a local JSON store for dry runs)
  news.py                security feed for the Learn tab
  models.py, config.py   normalized program model, tunables
android/                 Kotlin + Jetpack Compose app
  app/src/main/...       screens, components, theme, data layer
  app/src/test/...       JVM screenshot tests (Robolectric + Roborazzi)
design-system/           HTML reference cards for colors, type and components
docs/SETUP.md            full setup guide
.github/workflows/       poll.yml (scheduled poller), build-apk.yml (cloud APK build)
```

## Setup

Full steps are in [docs/SETUP.md](docs/SETUP.md). In short:

1. Create a Firebase project on the free Spark plan. Enable Firestore, Authentication (email and password) and Cloud Messaging.
2. Generate a service account key and add its contents as the `FIREBASE_SERVICE_ACCOUNT` Actions secret.
3. Add your `google-services.json` contents as the `GOOGLE_SERVICES_JSON` Actions secret (for cloud APK builds), or place the file in `android/app/` for local builds.
4. Set Firestore rules so signed-in users can read and nobody can write from a client:
   ```
   allow read: if request.auth != null;
   allow write: if false;
   ```
5. Run the **BountyRadar poll** workflow once with the `seed` input to baseline the database silently, then leave the schedule on.
6. Build the app and install it.

### Run the poller locally

```bash
cd poller
pip install -r requirements.txt
DRY_RUN=1 python main.py
```

`DRY_RUN=1` uses a local JSON store and prints alerts to the console, so no Firebase project is needed.

### Build the app

```bash
cd android
./gradlew :app:assembleRelease
```

The APK is written to `android/app/build/outputs/apk/release/`. Every push to `main` also builds one in GitHub Actions (Actions → Build APK → Artifacts).

### UI tests

```bash
cd android
./gradlew :app:testDebugUnitTest
```

Renders the main screens to PNG on the JVM (no device needed) into `android/app/build/outputs/roborazzi/`.

## Configuration

Environment variables read by the poller:

| Variable | Default | Purpose |
|---|---|---|
| `ENABLED_SOURCES` | all | Comma-separated list of sources to poll |
| `SEED_MODE` | off | Store everything without sending alerts (first run, or after adding a source) |
| `NO_NOTIFY` | off | Write changes but send no push |
| `STALE_DAYS` | 2 | Days a program may be missing from every source before it is deleted |
| `PRUNE_MIN_RATIO` | 0.5 | Skip writes and deletion if a poll returns less than this share of known programs |
| `NEWS_INTERVAL_MIN` | 60 | Minimum minutes between news refreshes |

## Tech stack

Python (requests, feedparser, firebase-admin) · Kotlin, Jetpack Compose, Material 3, Navigation, DataStore · Firebase Firestore, Auth and Cloud Messaging · GitHub Actions

## Disclaimer

Only test assets that a program explicitly lists as in scope, and follow each program's rules. Program data comes from public sources and may be incomplete or out of date; always confirm scope on the program's own page before testing.
