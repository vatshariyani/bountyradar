"""Security news / learning feed for the app's News tab.

Collected at most once per NEWS_INTERVAL_MIN and stored as ONE Firestore
document (a JSON string), so the app reads a single document and the poller
writes at most one — no per-article reads or writes.

Sources (each isolated; a failure just drops that source for this refresh):
  exploited — CISA Known Exploited Vulnerabilities catalogue
  advisory  — GitHub reviewed security advisories (critical/high)
  research / writeup / news — RSS feeds
"""
from __future__ import annotations

import hashlib
import json
import logging
import re
import time
from datetime import datetime, timezone

import feedparser
import requests

import config

log = logging.getLogger("bountyradar.news")
UA = {"User-Agent": "BountyRadar/0.2 (news)"}
MAX_ITEMS = 140

RSS_FEEDS = [
    # (source label, kind, url, how many)
    ("PortSwigger Research", "research", "https://portswigger.net/research/rss", 12),
    ("InfoSec Write-ups", "writeup", "https://infosecwriteups.com/feed", 15),
    ("The Hacker News", "news", "https://feeds.feedburner.com/TheHackersNews", 12),
]

_TAGS = re.compile(r"<[^>]+>")
_WS = re.compile(r"\s+")


def _clean(text: str, limit: int = 380) -> str:
    text = _WS.sub(" ", _TAGS.sub(" ", text or "")).strip()
    return text if len(text) <= limit else text[: limit - 1].rstrip() + "…"


def _item(kind, src, title, summary, url, date, sev="", tags=()):
    ident = hashlib.sha1(f"{src}|{url}|{title}".encode("utf-8")).hexdigest()[:14]
    return {"id": ident, "kind": kind, "src": src, "title": _clean(title, 160),
            "summary": _clean(summary), "url": url, "date": date, "sev": sev,
            "tags": [t for t in tags if t][:4]}


def _kev() -> list[dict]:
    data = requests.get(
        "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json",
        timeout=30, headers=UA).json()
    rows = sorted(data.get("vulnerabilities", []), key=lambda v: v.get("dateAdded", ""), reverse=True)
    out = []
    for v in rows[:25]:
        cve = v.get("cveID", "")
        tags = [v.get("vendorProject", "")] + list(v.get("cwes") or [])[:2]
        if str(v.get("knownRansomwareCampaignUse", "")).lower() == "known":
            tags.append("ransomware")
        out.append(_item("exploited", "CISA KEV", f"{cve} — {v.get('vulnerabilityName', '')}",
                         v.get("shortDescription", ""), f"https://nvd.nist.gov/vuln/detail/{cve}",
                         v.get("dateAdded", ""), "exploited", tags))
    return out


def _ghsa() -> list[dict]:
    rows = requests.get(
        "https://api.github.com/advisories",
        params={"per_page": 60, "type": "reviewed", "sort": "published", "direction": "desc"},
        timeout=30, headers={**UA, "Accept": "application/vnd.github+json"}).json()
    out = []
    for a in rows if isinstance(rows, list) else []:
        sev = (a.get("severity") or "").lower()
        if sev not in ("critical", "high"):
            continue
        pkg = ((a.get("vulnerabilities") or [{}])[0].get("package") or {})
        tags = [pkg.get("ecosystem", ""), pkg.get("name", "")] + \
               [c.get("name", "") for c in (a.get("cwes") or [])[:1]]
        title = a.get("summary") or a.get("ghsa_id", "")
        if a.get("cve_id"):
            title = f"{a['cve_id']} — {title}"
        out.append(_item("advisory", "GitHub Advisory", title, a.get("description", ""),
                         a.get("html_url", ""), (a.get("published_at") or "")[:19], sev, tags))
        if len(out) >= 30:
            break
    return out


def _rss(src: str, kind: str, url: str, n: int) -> list[dict]:
    feed = feedparser.parse(url, agent=UA["User-Agent"])
    out = []
    for e in feed.entries[:n]:
        ts = e.get("published_parsed") or e.get("updated_parsed")
        date = time.strftime("%Y-%m-%dT%H:%M:%S", ts) if ts else ""
        tags = [t.get("term", "") for t in (e.get("tags") or [])[:3]]
        out.append(_item(kind, src, e.get("title", ""), e.get("summary", ""),
                         e.get("link", ""), date, "", tags))
    return out


def collect_news() -> list[dict]:
    items: list[dict] = []
    jobs = [("CISA KEV", _kev), ("GitHub Advisory", _ghsa)] + \
           [(s, (lambda s=s, k=k, u=u, n=n: _rss(s, k, u, n))) for s, k, u, n in RSS_FEEDS]
    for label, fn in jobs:
        try:
            got = fn()
            log.info("news %s: %d items", label, len(got))
            items += got
        except Exception as exc:  # noqa: BLE001 — isolate each feed
            log.warning("news %s failed: %s", label, exc)
    seen: set[str] = set()
    items = [i for i in items if i["url"] and i["title"]
             and not (i["id"] in seen or seen.add(i["id"]))]
    items.sort(key=lambda i: i["date"], reverse=True)
    return items[:MAX_ITEMS]


def maybe_refresh(store) -> int:
    """Refresh the news document if it is older than NEWS_INTERVAL_MIN.
    Returns the number of items written (0 if skipped/unchanged)."""
    if not config.NEWS:
        return 0
    now = datetime.now(timezone.utc)
    last = store.meta.get("news_at")
    if last:
        try:
            age_min = (now - datetime.fromisoformat(last)).total_seconds() / 60
            if age_min < config.NEWS_INTERVAL_MIN:
                return 0
        except ValueError:
            pass
    items = collect_news()
    if not items:
        return 0
    digest = hashlib.sha1(json.dumps([i["id"] for i in items]).encode()).hexdigest()
    store.meta["news_at"] = now.isoformat()
    if digest == store.meta.get("news_hash"):
        return 0
    store.write_news(items)
    store.meta["news_hash"] = digest
    log.info("news: wrote %d items", len(items))
    return len(items)
