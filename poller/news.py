"""Security news / learning feed for the app's News tab.

Collected at most once per NEWS_INTERVAL_MIN and stored as ONE Firestore
document (a JSON string), so the app reads a single document and the poller
writes at most one — no per-article reads or writes.

Sources (each isolated; a failure just drops that source for this refresh):
  exploited  — CISA Known Exploited Vulnerabilities catalogue
  zeroday    — reported but not yet public / no CVE: ZDI upcoming + ZDI (0Day)
               advisories, Full Disclosure, GitHub advisories without a CVE id
  cve        — CVEs published in the last days (NVD, high/critical) + oss-security
  disclosure — HackerOne publicly disclosed reports (Hacktivity)
  exploit    — Exploit-DB
  advisory   — GitHub reviewed security advisories (critical/high)
  research / writeup / news — RSS feeds (incl. the Intigriti blog; Intigriti
               has no public feed of disclosed reports)
"""
from __future__ import annotations

import hashlib
import html
import json
import logging
import re
import time
from datetime import datetime, timedelta, timezone

import feedparser
import requests

import config

log = logging.getLogger("bountyradar.news")
UA = {"User-Agent": "BountyRadar/0.2 (news)"}
MAX_ITEMS = 420
MAX_BYTES = 850_000  # a Firestore document tops out at 1 MiB
SUMMARY_LIMIT = 1200

RSS_FEEDS = [
    # (source label, kind, url, how many)
    ("PortSwigger Research", "research", "https://portswigger.net/research/rss", 12),
    ("Intigriti", "research", "https://www.intigriti.com/blog/feed", 12),
    ("Project Zero", "research", "https://googleprojectzero.blogspot.com/feeds/posts/default", 8),
    ("InfoSec Write-ups", "writeup", "https://infosecwriteups.com/feed", 15),
    ("The Hacker News", "news", "https://feeds.feedburner.com/TheHackersNews", 12),
    ("Full Disclosure", "zeroday", "https://seclists.org/rss/fulldisclosure.rss", 15),
    ("oss-security", "cve", "https://seclists.org/rss/oss-sec.rss", 15),
    ("Exploit-DB", "exploit", "https://www.exploit-db.com/rss.xml", 30),
]

_TAGS = re.compile(r"<[^>]+>")
_WS = re.compile(r"\s+")


def _clean(text: str, limit: int = SUMMARY_LIMIT) -> str:
    text = _WS.sub(" ", html.unescape(_TAGS.sub(" ", text or ""))).strip()
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
        kind = "advisory"
        if a.get("cve_id"):
            title = f"{a['cve_id']} — {title}"
        else:
            kind, tags = "zeroday", ["No CVE yet"] + tags
        out.append(_item(kind, "GitHub Advisory", title, a.get("description", ""),
                         a.get("html_url", ""), (a.get("published_at") or "")[:19], sev, tags))
        if len(out) >= 30:
            break
    return out


def _sev(score: float) -> str:
    return "critical" if score >= 9 else "high" if score >= 7 else "medium" if score >= 4 else "low"


def _nvd() -> list[dict]:
    """CVEs published in the last 48h that already carry a high/critical score."""
    now = datetime.now(timezone.utc)
    fmt = "%Y-%m-%dT%H:%M:%S.000"
    data = requests.get(
        "https://services.nvd.nist.gov/rest/json/cves/2.0",
        params={"pubStartDate": (now - timedelta(hours=48)).strftime(fmt),
                "pubEndDate": now.strftime(fmt), "resultsPerPage": 2000},
        timeout=60, headers=UA).json()
    out = []
    for row in data.get("vulnerabilities", []):
        cve = row.get("cve") or {}
        score, vector = 0.0, ""
        for group in (cve.get("metrics") or {}).values():
            for m in group:
                d = m.get("cvssData") or {}
                if float(d.get("baseScore") or 0) > score:
                    score, vector = float(d["baseScore"]), d.get("vectorString", "")
        if score < 7:
            continue
        desc = next((d.get("value", "") for d in cve.get("descriptions", []) if d.get("lang") == "en"), "")
        cwes = [w.get("value", "") for weak in cve.get("weaknesses", []) for w in weak.get("description", [])
                if w.get("value", "").startswith("CWE-")]
        first = _clean(desc, 110)
        out.append(_item("cve", "NVD", f"{cve.get('id', '')} — {first}", desc,
                         f"https://nvd.nist.gov/vuln/detail/{cve.get('id', '')}",
                         (cve.get("published") or "")[:19], _sev(score),
                         [f"CVSS {score:g}"] + cwes[:1] + [vector]))
    out.sort(key=lambda i: i["date"], reverse=True)
    return out[:60]


_CVSS = re.compile(r"CVSS score (\d+(?:\.\d+)?)")
_VECTOR = re.compile(r">((?:AV|CVSS):[^<]+)<")


def _zdi_upcoming() -> list[dict]:
    """Bugs ZDI has reported to a vendor that have no advisory or CVE yet."""
    feed = feedparser.parse("https://www.zerodayinitiative.com/rss/upcoming/", agent=UA["User-Agent"])
    out = []
    for e in feed.entries[:40]:
        raw = e.get("summary", "")
        m = _CVSS.search(raw)
        score = float(m.group(1)) if m else 0.0
        v = _VECTOR.search(raw)
        vendor = e.get("title", "").split(":", 1)[-1].strip()
        ts = e.get("published_parsed")
        date = time.strftime("%Y-%m-%dT%H:%M:%S", ts) if ts else ""
        summary = (f"Reported to {vendor} through the Zero Day Initiative and not yet public: no advisory, "
                   f"no CVE, no patch announced. {_clean(raw, 500)}")
        out.append(_item("zeroday", "ZDI upcoming", e.get("title", ""), summary,
                         "https://www.zerodayinitiative.com/advisories/upcoming/", date,
                         _sev(score) if score else "",
                         [vendor, f"CVSS {score:g}" if score else "", v.group(1) if v else ""]))
    return out


def _zdi_published() -> list[dict]:
    """Published ZDI advisories; the ones titled (0Day) shipped without a vendor patch."""
    feed = feedparser.parse("https://www.zerodayinitiative.com/rss/published/", agent=UA["User-Agent"])
    out = []
    for e in feed.entries[:40]:
        title = e.get("title", "")
        zero = "(0day)" in title.lower()
        m = _CVSS.search(e.get("summary", ""))
        score = float(m.group(1)) if m else 0.0
        ts = e.get("published_parsed")
        date = time.strftime("%Y-%m-%dT%H:%M:%S", ts) if ts else ""
        out.append(_item("zeroday" if zero else "advisory", "ZDI", title, e.get("summary", ""),
                         e.get("link", ""), date, _sev(score) if score else "",
                         ["Unpatched 0-day" if zero else "", f"CVSS {score:g}" if score else ""]))
    return out


_H1_QUERY = """query($q: String!, $size: Int, $sort: SortInput) {
  search(index: CompleteHacktivityReportIndex, query_string: $q, from: 0, size: $size, sort: $sort) {
    nodes { ... on HacktivityDocument { reporter { username } cwe cve_ids severity_rating
      report { title url disclosed_at } team { handle name } total_awarded_amount } } } }"""


def _hackerone() -> list[dict]:
    """Reports publicly disclosed on HackerOne (Hacktivity), newest first."""
    data = requests.post(
        "https://hackerone.com/graphql",
        json={"query": _H1_QUERY, "variables": {
            "q": "disclosed:true", "size": 40, "sort": {"field": "disclosed_at", "direction": "DESC"}}},
        timeout=30, headers={**UA, "Content-Type": "application/json"}).json()
    out = []
    for n in (((data.get("data") or {}).get("search") or {}).get("nodes") or []):
        report = n.get("report") or {}
        if not report.get("url"):
            continue
        team = (n.get("team") or {}).get("name") or (n.get("team") or {}).get("handle") or ""
        sev = (n.get("severity_rating") or "").lower()
        if sev == "none":
            sev = ""
        cwe = n.get("cwe") or ""
        who = (n.get("reporter") or {}).get("username") or ""
        bounty = n.get("total_awarded_amount")
        cves = n.get("cve_ids") or []
        parts = [f"Disclosed report to {team}." if team else "Disclosed report.",
                 f"Weakness: {cwe}." if cwe else "",
                 f"Severity: {sev}." if sev else "",
                 f"Bounty: ${bounty:,.0f}." if bounty else "",
                 f"Found by @{who}." if who else "",
                 f"CVE: {', '.join(cves)}." if cves else "",
                 "Open it to read the full write-up, timeline and the triage discussion."]
        out.append(_item("disclosure", "HackerOne", report.get("title", ""), " ".join(x for x in parts if x),
                         report["url"], (report.get("disclosed_at") or "")[:19], sev,
                         [team, cwe, f"${bounty:,.0f}" if bounty else ""]))
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
    jobs = [("CISA KEV", _kev), ("GitHub Advisory", _ghsa), ("NVD", _nvd), ("ZDI upcoming", _zdi_upcoming),
            ("ZDI", _zdi_published), ("HackerOne", _hackerone)] + \
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
    items = items[:MAX_ITEMS]
    # Stay well inside the document size limit: shorten summaries if needed.
    limit = SUMMARY_LIMIT
    while len(json.dumps(items, ensure_ascii=False).encode("utf-8")) > MAX_BYTES and limit > 200:
        limit //= 2
        for i in items:
            i["summary"] = _clean(i["summary"], limit)
    return items


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
