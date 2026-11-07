"""Tiny SQLite layer. One connection per call keeps this trivially thread-safe for a demo."""
import json
import sqlite3
import time
from contextlib import contextmanager
from typing import Iterable, Optional

from .config import DB_PATH
from .sources.common import Job

SCHEMA = """
CREATE TABLE IF NOT EXISTS jobs (
    id TEXT PRIMARY KEY, source TEXT, company TEXT, title TEXT, location TEXT,
    remote INTEGER, description TEXT, apply_url TEXT, posted_at TEXT, fetched_at REAL
);
CREATE TABLE IF NOT EXISTS seen (job_id TEXT PRIMARY KEY, status TEXT, ts REAL);
CREATE TABLE IF NOT EXISTS profile (id INTEGER PRIMARY KEY CHECK (id = 1), data TEXT, updated_at REAL);
CREATE TABLE IF NOT EXISTS scores (
    job_id TEXT, profile_hash TEXT, score INTEGER, reason TEXT,
    PRIMARY KEY (job_id, profile_hash)
);
CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT);
"""


@contextmanager
def connect():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init():
    with connect() as c:
        c.executescript(SCHEMA)


def upsert_jobs(jobs: Iterable[Job]) -> int:
    now = time.time()
    rows = [
        (j.id, j.source, j.company, j.title, j.location, int(j.remote), j.description, j.apply_url, j.posted_at, now)
        for j in jobs
    ]
    with connect() as c:
        c.executemany(
            "INSERT OR REPLACE INTO jobs VALUES (?,?,?,?,?,?,?,?,?,?)", rows
        )
        c.execute("INSERT OR REPLACE INTO meta VALUES ('last_refresh', ?)", (str(now),))
    return len(rows)


def job_count() -> int:
    with connect() as c:
        return c.execute("SELECT COUNT(*) FROM jobs").fetchone()[0]


def last_refresh() -> Optional[float]:
    with connect() as c:
        row = c.execute("SELECT value FROM meta WHERE key='last_refresh'").fetchone()
        return float(row[0]) if row else None


def _row_to_job(row: sqlite3.Row) -> Job:
    return Job(
        id=row["id"], source=row["source"], company=row["company"], title=row["title"],
        location=row["location"], remote=bool(row["remote"]), description=row["description"],
        apply_url=row["apply_url"], posted_at=row["posted_at"],
    )


def unseen_jobs(exclude: Iterable[str] = (), limit: int = 200) -> list[Job]:
    exclude = list(exclude)
    placeholders = ",".join("?" * len(exclude)) if exclude else "''"
    with connect() as c:
        rows = c.execute(
            f"""SELECT * FROM jobs WHERE id NOT IN (SELECT job_id FROM seen)
                AND id NOT IN ({placeholders}) ORDER BY posted_at DESC LIMIT ?""",
            [*exclude, limit],
        ).fetchall()
    return [_row_to_job(r) for r in rows]


def get_job(job_id: str) -> Optional[Job]:
    with connect() as c:
        row = c.execute("SELECT * FROM jobs WHERE id=?", (job_id,)).fetchone()
    return _row_to_job(row) if row else None


def mark_seen(job_ids: Iterable[str], status: str):
    now = time.time()
    with connect() as c:
        c.executemany("INSERT OR REPLACE INTO seen VALUES (?,?,?)", [(i, status, now) for i in job_ids])


def clear_seen():
    with connect() as c:
        c.execute("DELETE FROM seen")


def save_profile(profile: dict):
    with connect() as c:
        c.execute("INSERT OR REPLACE INTO profile VALUES (1, ?, ?)", (json.dumps(profile), time.time()))


def load_profile() -> Optional[dict]:
    with connect() as c:
        row = c.execute("SELECT data FROM profile WHERE id=1").fetchone()
    return json.loads(row[0]) if row else None


def cached_scores(job_ids: list[str], profile_hash: str) -> dict[str, tuple[int, str]]:
    if not job_ids:
        return {}
    placeholders = ",".join("?" * len(job_ids))
    with connect() as c:
        rows = c.execute(
            f"SELECT job_id, score, reason FROM scores WHERE profile_hash=? AND job_id IN ({placeholders})",
            [profile_hash, *job_ids],
        ).fetchall()
    return {r["job_id"]: (r["score"], r["reason"]) for r in rows}


def save_scores(profile_hash: str, scored: dict[str, tuple[int, str]]):
    with connect() as c:
        c.executemany(
            "INSERT OR REPLACE INTO scores VALUES (?,?,?,?)",
            [(jid, profile_hash, s, r) for jid, (s, r) in scored.items()],
        )
