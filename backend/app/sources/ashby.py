"""Ashby Job Posting API: https://developers.ashbyhq.com/docs/public-job-posting-api
Note: Ashby returns 403 unless a browser-like User-Agent is sent."""
import httpx

from .common import HEADERS, Job, html_to_text, is_remote

API = "https://api.ashbyhq.com/posting-api/job-board/{slug}"


def normalise(raw: dict, slug: str, company: str) -> Job:
    location = raw.get("location") or ""
    secondary = [s.get("location") for s in raw.get("secondaryLocations") or [] if s.get("location")]
    if secondary:
        location = ", ".join([location, *secondary]) if location else ", ".join(secondary)
    return Job(
        id=f"ashby:{slug}:{raw['id']}",
        source="ashby",
        company=company,
        title=raw.get("title", "").strip(),
        location=location,
        remote=bool(raw.get("isRemote")) or is_remote(location, raw.get("workplaceType")),
        description=(raw.get("descriptionPlain") or html_to_text(raw.get("descriptionHtml"))).strip(),
        apply_url=raw.get("applyUrl") or f"https://jobs.ashbyhq.com/{slug}/{raw['id']}/application",
        posted_at=raw.get("publishedAt"),
    )


async def fetch(client: httpx.AsyncClient, slug: str, company: str) -> list[Job]:
    resp = await client.get(API.format(slug=slug), headers=HEADERS)
    resp.raise_for_status()
    jobs = resp.json().get("jobs", [])
    return [normalise(j, slug, company) for j in jobs if j.get("isListed", True)]
