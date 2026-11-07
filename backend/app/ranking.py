"""Fit scoring: a cheap keyword heuristic for every job, then an optional LLM pass for a better one-liner."""
import hashlib
import json
import re

from . import db
from . import config
from .llm import llm
from .profile import Profile
from .sources.common import Job

SENIORITY = ["intern", "junior", "senior", "staff", "principal", "lead", "manager", "director", "vp", "head"]


def profile_hash(profile: Profile) -> str:
    key = json.dumps(profile.model_dump(exclude={"cv_text"}), sort_keys=True)
    return hashlib.sha1(key.encode()).hexdigest()[:12]


def heuristic_score(profile: Profile, job: Job) -> tuple[int, str]:
    text = f"{job.title}\n{job.description}".lower()
    skills = [s for s in profile.skills if s]
    matched = [s for s in skills if re.search(rf"(?<![\w+#]){re.escape(s.lower())}(?![\w+#])", text)]
    skill_part = (len(matched) / max(len(skills), 1)) if skills else 0

    titles = " ".join(e.title.lower() for e in profile.experience)
    title_words = {w for w in re.findall(r"[a-z]+", job.title.lower()) if len(w) > 3 and w not in SENIORITY}
    title_hits = [w for w in title_words if w in titles or w in " ".join(skills).lower()]
    title_part = min(len(title_hits) / 2, 1.0)

    location_part = 0.0
    if job.remote:
        location_part = 1.0
    elif profile.location and any(tok in job.location.lower() for tok in profile.location.lower().split(",") if len(tok.strip()) > 2):
        location_part = 1.0

    score = int(round(20 + 50 * skill_part + 20 * title_part + 10 * location_part))
    if matched:
        reason = f"Mentions your skills: {', '.join(matched[:4])}"
    elif title_hits:
        reason = f"Title overlaps with your experience ({', '.join(title_hits[:2])})"
    elif job.remote:
        reason = "Remote role; skills overlap is unclear"
    else:
        reason = "Little overlap with your CV found"
    return min(score, 100), reason


RANK_SYSTEM = """You score how well job postings fit a candidate.
For each job return a fit score 0-100 and ONE short sentence (max 15 words) explaining why it fits the candidate,
grounded only in the candidate profile and job text. Do not invent experience the candidate doesn't have.
Return JSON: {"scores": [{"id": str, "score": int, "reason": str}]}"""


async def rank_jobs(profile: Profile, jobs: list[Job]) -> dict[str, tuple[int, str]]:
    phash = profile_hash(profile)
    results = db.cached_scores([j.id for j in jobs], phash)
    missing = [j for j in jobs if j.id not in results]
    if not missing:
        return results

    fresh = {j.id: heuristic_score(profile, j) for j in missing}
    llm_scored: set[str] = set()
    if llm.enabled:
        # Only the most promising jobs (by heuristic) go to the LLM: the batch is capped so the
        # prompt stays small enough for local models, and those are the cards the user sees first.
        shortlist = sorted(missing, key=lambda j: fresh[j.id][0], reverse=True)[:config.LLM_RANK_BATCH]
        summary = profile.model_dump(include={"summary", "skills", "experience", "education", "location"})
        job_blobs = [{"id": j.id, "title": j.title, "company": j.company, "location": j.location,
                      "description": j.description[:1200]} for j in shortlist]
        data = await llm.complete_json(
            RANK_SYSTEM,
            f"CANDIDATE:\n{json.dumps(summary)}\n\nJOBS:\n{json.dumps(job_blobs)}",
            max_tokens=3000,
        )
        if data is None:
            # Model failed/timed out: keep the heuristic scores for this batch so the next feed
            # load doesn't sit through the same timeout again.
            llm_scored.update(j.id for j in shortlist)
        for item in (data or {}).get("scores", []):
            jid = item.get("id")
            if jid in fresh and isinstance(item.get("score"), (int, float)):
                fresh[jid] = (max(0, min(100, int(item["score"]))), str(item.get("reason", ""))[:160])
                llm_scored.add(jid)
    # Heuristic scores are cheap to recompute; only cache them when no LLM will ever improve them,
    # otherwise jobs that missed this batch would be stuck with heuristic scores forever.
    db.save_scores(phash, {k: v for k, v in fresh.items() if k in llm_scored} if llm.enabled else fresh)
    results.update(fresh)
    return results
