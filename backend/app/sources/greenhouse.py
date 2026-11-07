"""Greenhouse Job Board API: https://developers.greenhouse.io/job-board.html"""
import httpx

from .common import HEADERS, Job, html_to_text, is_remote

API = "https://boards-api.greenhouse.io/v1/boards/{slug}/jobs?content=true"


def normalise(raw: dict, slug: str, company: str) -> Job:
    location = (raw.get("location") or {}).get("name") or ""
    job_id = str(raw["id"])
    return Job(
        id=f"greenhouse:{slug}:{job_id}",
        source="greenhouse",
        company=raw.get("company_name") or company,
        title=raw.get("title", "").strip(),
        location=location,
        remote=is_remote(location, raw.get("title")),
        description=html_to_text(raw.get("content")),
        # The hosted board page always renders the Greenhouse application form,
        # unlike absolute_url which may point at a company-specific careers site.
        apply_url=f"https://job-boards.greenhouse.io/{slug}/jobs/{job_id}",
        posted_at=raw.get("first_published") or raw.get("updated_at"),
    )


async def fetch(client: httpx.AsyncClient, slug: str, company: str) -> list[Job]:
    resp = await client.get(API.format(slug=slug), headers=HEADERS)
    resp.raise_for_status()
    return [normalise(j, slug, company) for j in resp.json().get("jobs", [])]
