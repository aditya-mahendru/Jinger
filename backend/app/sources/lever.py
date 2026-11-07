"""Lever Postings API: https://github.com/lever/postings-api"""
import httpx

from .common import HEADERS, Job, html_to_text, is_remote, iso_from_ms

API = "https://api.lever.co/v0/postings/{slug}?mode=json"


def normalise(raw: dict, slug: str, company: str) -> Job:
    cats = raw.get("categories") or {}
    location = cats.get("location") or ""
    parts = [html_to_text(raw.get("descriptionPlain") or raw.get("description"))]
    for section in raw.get("lists") or []:
        parts.append(f"\n{section.get('text', '')}\n{html_to_text(section.get('content'))}")
    if raw.get("additionalPlain"):
        parts.append(raw["additionalPlain"])
    return Job(
        id=f"lever:{slug}:{raw['id']}",
        source="lever",
        company=company,
        title=raw.get("text", "").strip(),
        location=location,
        remote=raw.get("workplaceType") == "remote" or is_remote(location, raw.get("text")),
        description="\n".join(p for p in parts if p).strip(),
        apply_url=raw.get("applyUrl") or f"https://jobs.lever.co/{slug}/{raw['id']}/apply",
        posted_at=iso_from_ms(raw.get("createdAt")),
    )


async def fetch(client: httpx.AsyncClient, slug: str, company: str) -> list[Job]:
    resp = await client.get(API.format(slug=slug), headers=HEADERS)
    resp.raise_for_status()
    data = resp.json()
    if not isinstance(data, list):  # {"ok": false, "error": "Document not found"}
        return []
    return [normalise(j, slug, company) for j in data]
