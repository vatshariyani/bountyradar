"""Entry point.

Run once (default — what GitHub Actions calls each tick):
    python main.py

Run forever every N seconds (what an Oracle VM would use for near-real-time):
    python main.py --loop 90

Local smoke test without Firebase (uses LocalStore + console "push"):
    DRY_RUN=1 python main.py
"""
from __future__ import annotations

import argparse
import logging
import sys
import time

import config
from engine import run_once

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
log = logging.getLogger("bountyradar")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="BountyRadar poller")
    parser.add_argument(
        "--loop", type=int, default=0,
        help="run continuously every N seconds (0 = run once and exit)",
    )
    args = parser.parse_args(argv)

    if args.loop <= 0:
        result = run_once()
        log.info("done: %s", result)
        return 0

    log.info("loop mode: every %ds (dry_run=%s)", args.loop, config.DRY_RUN)
    while True:
        try:
            result = run_once()
            log.info("tick: %s", result)
        except Exception as exc:  # noqa: BLE001 — keep the loop alive
            log.exception("tick failed: %s", exc)
        time.sleep(args.loop)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
