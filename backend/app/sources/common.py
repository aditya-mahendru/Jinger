import html
import re
from datetime import datetime, timezone
from typing import Optional

from pydantic import BaseModel

HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; Jinger/0.1; +https://github.com)"}

REMOTE_RE = re.compile(r"\bremote\b", re.IGNORECASE)
TAG_RE = re.compile(r"<[^>]+>")
WS_RE = re.compile(r"[ \t\r\f\v]+")
NL_RE = re.compile(r"\n{3,}")


class Job(BaseModel):
    id: str
    source: str
    company: str
    title: str
    location: str
    remote: bool
    description: str
    apply_url: str
    posted_at: Optional[str] = None


def html_to_text(raw: Optional[str]) -> str:
    """Strip HTML (Greenhouse double-escapes it) into readable plain text."""
    if not raw:
        return ""
    text = html.unescape(raw)
    if "<" in text:
        text = re.sub(r"</?(p|div|br|li|h[1-6]|ul|ol|tr)[^>]*>", "\n", text, flags=re.IGNORECASE)
        text = TAG_RE.sub("", text)
        text = html.unescape(text)
    text = WS_RE.sub(" ", text)
    text = "\n".join(line.strip() for line in text.split("\n"))
    return NL_RE.sub("\n\n", text).strip()


def is_remote(*hints: Optional[str]) -> bool:
    return any(h and REMOTE_RE.search(h) for h in hints)


def iso_from_ms(ms: Optional[int]) -> Optional[str]:
    if not ms:
        return None
    return datetime.fromtimestamp(ms / 1000, tz=timezone.utc).isoformat()
