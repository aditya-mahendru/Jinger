"""Profile schema + CV parsing (LLM with a regex fallback)."""
import logging
import re
from typing import Any, Optional

from pydantic import BaseModel, Field, model_validator

from .llm import llm

log = logging.getLogger("jinger")


class Lenient(BaseModel):
    """Small LLMs often emit null for unknown strings/lists; treat null as "not stated"."""

    @model_validator(mode="before")
    @classmethod
    def _nulls_to_defaults(cls, data: Any) -> Any:
        if not isinstance(data, dict):
            return data
        cleaned = {}
        for key, value in data.items():
            field = cls.model_fields.get(key)
            if value is None and field is not None and field.annotation is not Optional[bool]:
                continue  # drop it so the field default ("" / []) applies
            if isinstance(value, (int, float)) and field is not None and field.annotation is str:
                value = str(value)  # e.g. "start": 2020
            cleaned[key] = value
        return cleaned


class Education(Lenient):
    school: str = ""
    degree: str = ""
    field: str = ""
    start: str = ""
    end: str = ""


class Experience(Lenient):
    company: str = ""
    title: str = ""
    start: str = ""
    end: str = ""
    summary: str = ""


class Links(Lenient):
    linkedin: str = ""
    github: str = ""
    website: str = ""
    other: list[str] = Field(default_factory=list)


class WorkAuthorization(Lenient):
    authorized_in: list[str] = Field(default_factory=list)
    requires_sponsorship: Optional[bool] = None
    notes: str = ""


class Profile(Lenient):
    name: str = ""
    email: str = ""
    phone: str = ""
    location: str = ""
    summary: str = ""
    links: Links = Field(default_factory=Links)
    education: list[Education] = Field(default_factory=list)
    experience: list[Experience] = Field(default_factory=list)
    skills: list[str] = Field(default_factory=list)
    work_authorization: WorkAuthorization = Field(default_factory=WorkAuthorization)
    cv_text: str = ""


PARSE_SYSTEM = """You extract structured candidate profiles from CV text.
Only use information explicitly present in the CV. Leave fields empty ("" / [] / null) when the CV does not state them.
Return JSON matching exactly this schema:
{"name": str, "email": str, "phone": str, "location": str, "summary": str,
 "links": {"linkedin": str, "github": str, "website": str, "other": [str]},
 "education": [{"school": str, "degree": str, "field": str, "start": str, "end": str}],
 "experience": [{"company": str, "title": str, "start": str, "end": str, "summary": str}],
 "skills": [str],
 "work_authorization": {"authorized_in": [str], "requires_sponsorship": bool|null, "notes": str}}"""

EMAIL_RE = re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+")
PHONE_RE = re.compile(r"(\+?\d[\d \t().-]{8,}\d)")  # no newlines: a number must sit on one line
URL_RE = re.compile(r"(?:https?://)?(?:www\.)?(linkedin\.com/in/[\w-]+|github\.com/[\w-]+|[\w-]+\.(?:dev|io|me|com)/?[\w/-]*)", re.I)
COMMON_SKILLS = [
    "python", "java", "kotlin", "swift", "typescript", "javascript", "react", "node", "go", "rust", "c++", "c#",
    "sql", "postgres", "mysql", "mongodb", "redis", "aws", "gcp", "azure", "docker", "kubernetes", "terraform",
    "android", "ios", "flutter", "fastapi", "django", "flask", "spring", "pytorch", "tensorflow", "machine learning",
    "llm", "nlp", "data science", "pandas", "spark", "kafka", "graphql", "rest", "git", "ci/cd", "linux",
]


# Case-sensitive on purpose: "Tel" must not match inside "Patel".
NAME_STOP_RE = re.compile(r"\s*(?:\||•|Email|E-mail|Mobile|Phone|Tel|LinkedIn|GitHub|Address|[+(]?\d)")


DATE_LIKE_RE = re.compile(r"\d{4}\s*-\s*\d{4}|\d{1,2}[./-]\d{1,2}[./-]\d{2,4}")  # "2020 - 2024", "01.09.2020"


def _find_phone(text: str) -> str:
    """PHONE_RE also matches year ranges like "2020 - 2024"; skip those and prefer +country numbers."""
    first = ""
    for m in PHONE_RE.finditer(text):
        cand = m.group(0).strip()
        if len(re.sub(r"\D", "", cand)) < 8 or DATE_LIKE_RE.fullmatch(cand):
            continue
        if cand.startswith("+"):
            return cand
        first = first or cand
    return first


def _schema_error_summary(exc: Exception) -> str:
    """Field paths and error types only; never the rejected values (they are CV personal data)."""
    errors = getattr(exc, "errors", None)
    if callable(errors):
        return "; ".join(".".join(map(str, e["loc"])) + ": " + e["type"] for e in errors())
    return type(exc).__name__


def _guess_name(lines: list[str]) -> str:
    """The name is almost always the first line; strip contact details glued onto it
    (pdf text extraction often yields "Jane DoeEmail : jane@x.com")."""
    for line in lines[:5]:
        head = NAME_STOP_RE.split(line, maxsplit=1)[0].strip(" :,-")
        words = head.split()
        if 1 < len(words) <= 4 and all(w[0].isalpha() for w in words) and "@" not in head:
            return head
    return ""


def _split_skills(skills: list[str]) -> list[str]:
    """Flatten "Languages: Python, SQL" style entries into individual skills."""
    out: list[str] = []
    for raw in skills:
        body = raw.split(":", 1)[1] if ":" in raw and len(raw) > 30 else raw
        for part in re.split(r",|;|/(?=\s)", body):
            part = part.strip(" .")
            if part and part.lower() not in {s.lower() for s in out}:
                out.append(part)
    return out


def heuristic_parse(text: str) -> Profile:
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    email = EMAIL_RE.search(text)
    links = Links()
    for m in URL_RE.finditer(text):
        if m.start() > 0 and text[m.start() - 1] == "@":
            continue  # that's an email domain, not a link
        url = m.group(0) if m.group(0).startswith("http") else "https://" + m.group(0)
        low = url.lower()
        if "linkedin.com" in low and not links.linkedin:
            links.linkedin = url
        elif "github.com" in low and not links.github:
            links.github = url
        elif not links.website and "@" not in url:
            links.website = url
    lower = text.lower()
    skills = [s for s in COMMON_SKILLS if re.search(rf"(?<![\w+#]){re.escape(s)}(?![\w+#])", lower)]
    name = _guess_name(lines)
    return Profile(
        name=name, email=email.group(0) if email else "", phone=_find_phone(text),
        links=links, skills=skills, cv_text=text,
    )


async def parse_cv_text(text: str) -> Profile:
    text = text[:15000]
    data = await llm.complete_json(PARSE_SYSTEM, f"CV TEXT:\n{text}")
    if data:
        try:
            profile = Profile.model_validate(data)
            profile.cv_text = text
            profile.skills = _split_skills(profile.skills)
            fallback = heuristic_parse(text)  # the regexes are reliable; never lose contact details
            profile.email = profile.email or fallback.email
            profile.phone = profile.phone or fallback.phone
            profile.name = profile.name or fallback.name
            return profile
        except Exception as exc:
            log.warning("LLM profile did not match the schema, using heuristics: %s", _schema_error_summary(exc))
    return heuristic_parse(text)
