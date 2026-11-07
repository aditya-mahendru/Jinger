import logging
import time
from typing import Optional

from fastapi import FastAPI, File, HTTPException, Query, UploadFile
from pydantic import BaseModel
from pypdf import PdfReader

from . import db, jobs
from .config import UPLOAD_DIR
from .fill_plan import FillPlan, FillPlanRequest, build_fill_plan
from .llm import llm
from .profile import Profile, parse_cv_text
from .ranking import rank_jobs
from .sources.common import Job

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
log = logging.getLogger("jinger")

app = FastAPI(title="Jinger backend", version="0.1.0")
STALE_AFTER = 6 * 3600


class RankedJob(Job):
    fit_score: Optional[int] = None
    fit_reason: Optional[str] = None


class SeenRequest(BaseModel):
    job_ids: list[str]
    status: str = "skipped"  # skipped | applied


class RankRequest(BaseModel):
    profile: Optional[Profile] = None
    job_ids: Optional[list[str]] = None
    jobs: Optional[list[Job]] = None


@app.on_event("startup")
async def startup():
    db.init()
    UPLOAD_DIR.mkdir(exist_ok=True)
    last = db.last_refresh()
    if db.job_count() == 0 or not last or time.time() - last > STALE_AFTER:
        log.info("Job cache empty/stale, refreshing...")
        n = await jobs.refresh()
        log.info("Cached %d jobs", n)
    log.info("LLM %s (%s/%s)", "enabled" if llm.enabled else "DISABLED - using heuristics", llm.provider, llm.model)


@app.get("/health")
def health():
    return {"ok": True, "jobs": db.job_count(), "llm": llm.enabled, "last_refresh": db.last_refresh()}


@app.post("/jobs/refresh")
async def refresh_jobs():
    return {"cached": await jobs.refresh()}


@app.get("/jobs", response_model=list[RankedJob])
async def list_jobs(limit: int = Query(20, le=100), exclude: str = ""):
    """Next unseen jobs, ranked for the saved profile (if any)."""
    excluded = [e for e in exclude.split(",") if e]
    candidates = db.unseen_jobs(exclude=excluded, limit=max(limit * 5, 100))
    profile_data = db.load_profile()
    if not profile_data:
        return [RankedJob(**j.model_dump()) for j in candidates[:limit]]
    profile = Profile.model_validate(profile_data)
    scores = await rank_jobs(profile, candidates)
    ranked = sorted(candidates, key=lambda j: scores.get(j.id, (0, ""))[0], reverse=True)[:limit]
    return [RankedJob(**j.model_dump(), fit_score=scores[j.id][0], fit_reason=scores[j.id][1]) for j in ranked]


@app.get("/jobs/{job_id}", response_model=Job)
def get_job(job_id: str):
    job = db.get_job(job_id)
    if not job:
        raise HTTPException(404, "job not found")
    return job


@app.post("/jobs/seen")
def mark_seen(req: SeenRequest):
    db.mark_seen(req.job_ids, req.status)
    return {"ok": True}


@app.delete("/jobs/seen")
def reset_seen():
    db.clear_seen()
    return {"ok": True}


@app.get("/profile", response_model=Optional[Profile])
def get_profile():
    data = db.load_profile()
    return Profile.model_validate(data) if data else None


@app.post("/profile", response_model=Profile)
def save_profile(profile: Profile):
    db.save_profile(profile.model_dump())
    return profile


@app.post("/profile/parse", response_model=Profile)
async def parse_profile(file: UploadFile = File(...)):
    raw = await file.read()
    if not raw:
        raise HTTPException(400, "empty file")
    (UPLOAD_DIR / "cv.pdf").write_bytes(raw)
    try:
        reader = PdfReader(UPLOAD_DIR / "cv.pdf")
        text = "\n".join((page.extract_text() or "") for page in reader.pages)
    except Exception as exc:
        raise HTTPException(400, f"could not read PDF: {exc}")
    if not text.strip():
        raise HTTPException(422, "PDF has no extractable text (scanned image?)")
    return await parse_cv_text(text)


@app.post("/rank")
async def rank(req: RankRequest):
    profile = req.profile or (Profile.model_validate(db.load_profile()) if db.load_profile() else None)
    if not profile:
        raise HTTPException(400, "no profile saved; POST /profile first or include one")
    target = req.jobs or [j for j in (db.get_job(i) for i in req.job_ids or []) if j]
    scores = await rank_jobs(profile, target)
    return {"scores": [{"id": jid, "score": s, "reason": r} for jid, (s, r) in scores.items()]}


@app.post("/fill-plan", response_model=FillPlan)
async def fill_plan(req: FillPlanRequest):
    return await build_fill_plan(req)
