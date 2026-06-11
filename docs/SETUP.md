# BountyRadar — full setup guide

End result: your Android phone buzzes within ~10–15 minutes of a new bug bounty
program launching, you tap it, see the scope, and open your laptop to hunt.

There are three things to set up: **Firebase** (free), the **poller on GitHub
Actions** (free), and the **Android app**. Budget ~45 minutes the first time.

---

## Part 1 — Firebase (the free backend: push + database + login)

1. Go to <https://console.firebase.google.com> and **Add project** (you said you
   already have a Firebase account — reuse it). Name it e.g. `bountyradar`.
   You can disable Google Analytics.
2. **Firestore Database** → *Create database* → Production mode → pick a region.
3. **Authentication** → *Get started* → enable **Email/Password** (and optionally
   Google) sign-in.
4. **Project settings → Cloud Messaging** — nothing to configure; it's on.
5. Firestore security rules — paste these so only signed-in users can read, and
   only the poller (Admin SDK, which bypasses rules) can write:

   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{db}/documents {
       match /programs/{doc} {
         allow read: if request.auth != null;
         allow write: if false;        // only the Admin SDK poller writes
       }
     }
   }
   ```

### Service account key (lets the poller write + push)

6. **Project settings → Service accounts → Generate new private key**. A JSON file
   downloads. Treat it like a password — never commit it.

---

## Part 2 — The poller on GitHub Actions (free, no credit card)

1. Create a **private** GitHub repo and push this project to it.
2. Repo → **Settings → Secrets and variables → Actions → New repository secret**:
   - Name: `FIREBASE_SERVICE_ACCOUNT`
   - Value: paste the **entire contents** of the service-account JSON from step 6.
   - (Optional, Phase 3) `H1_API_USERNAME` / `H1_API_TOKEN` for HackerOne.
3. The workflow `.github/workflows/poll.yml` is already set to run every 10 min.
4. **First run = seed the baseline silently** (so you don't get 1,000 alerts for
   programs that already exist):
   - Repo → **Actions → BountyRadar poll → Run workflow** → tick **seed** → Run.
   - This records every current program WITHOUT notifying.
5. After that, leave it alone. Every 10 min it checks for genuinely new programs
   and pushes only those.

> Want true real-time (1–2 min) later? Copy the `poller/` folder to an Oracle
> Cloud Free Tier VM and run `python main.py --loop 90`. Same code, no rewrite.

### Test the poller locally first (optional, recommended)

```bash
cd poller
pip install -r requirements.txt
DRY_RUN=1 ENABLED_SOURCES=yeswehack python main.py   # uses a local file, prints "pushes"
```

---

## Part 3 — The Android app

1. Open the `android/` folder in **Android Studio** (it will download the Gradle
   wrapper and sync automatically).
2. In Firebase console → **Project settings → Your apps → Add app → Android**:
   - Package name: `com.bountyradar.app`
   - Download the generated **`google-services.json`** and drop it into
     `android/app/` (it is gitignored — never commit it).
3. Plug in your phone (USB debugging on) and click **Run**. The app installs.
4. **Sign up** with email + password (your single BountyRadar login), allow
   notifications when asked. The app auto-subscribes to the `new_programs` topic.
5. Done. You'll now see the live program list and get a push for each new program.

### Verify push works end-to-end

- Firebase console → **Messaging → New campaign → Notifications → test**, OR
- In the Actions tab, re-run the poll workflow WITHOUT seed after manually
  deleting a few docs from the `programs` collection — those reappear as "new"
  and trigger a real push to your phone.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| No pushes ever | Confirm the app subscribed (reinstall), check the poll workflow is green, confirm `FIREBASE_SERVICE_ACCOUNT` secret is the full JSON. |
| Flooded on first run | You skipped the **seed** step — clear the `programs` collection and re-run with seed ticked. |
| A source shows 0 | That platform changed its layout/endpoint; the engine isolates it so others keep working. Tune the source module. |
| Build fails: missing google-services.json | Add it to `android/app/` (Part 3, step 2). |
