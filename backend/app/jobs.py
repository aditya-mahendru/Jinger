"""Aggregates postings from every company in companies.json into the SQLite cache."""
import asyncio
import json
import logging

import httpx

from . import db
from .config import COMPANIES_FILE
from .sources import FETCHERS
from .sources.common import Job

log = logging.getLogger("jinger.jobs")


def load_companies() -> list[dict]:
    return json.loads(COMPANIES_FILE.read_text())


async def fetch_all(max_per_company: int = 60) -> list[Job]:
    companies = load_companies()
    async with httpx.AsyncClient(timeout=30, follow_redirects=True) as client:
        sem = asyncio.Semaphore(8)

        async def one(company: dict) -> list[Job]:
            fetcher = FETCHERS[company["platform"]]
            async with sem:
                try:
                    jobs = await fetcher(client, company["slug"], company["name"])
                    log.info("%s/%s: %d jobs", company["platform"], company["slug"], len(jobs))
                    # Newest first, capped so one huge board doesn't dominate the feed.
                    jobs.sort(key=lambda j: j.posted_at or "", reverse=True)
                    return jobs[:max_per_company]
                except Exception as exc:  # one broken board shouldn't kill the refresh
                    log.warning("%s/%s failed: %s", company["platform"], company["slug"], exc)
                    return []

        results = await asyncio.gather(*(one(c) for c in companies))
    return [j for batch in results for j in batch]


async def refresh() -> int:
    jobs = await fetch_all()
    return db.upsert_jobs(jobs)
