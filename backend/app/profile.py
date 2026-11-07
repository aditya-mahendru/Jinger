"""Profile schema + CV parsing (LLM with a regex fallback)."""
import re
from typing import Optional

from pydantic import BaseModel, Field

from .llm import llm


class Education(BaseModel):
    school: str = ""
    degree: str = ""
    field: str = ""
    start: str = ""
    end: str = ""


class Experience(BaseModel):
    company: str = ""
    title: str = ""
    start: str = ""
    end: str = ""
    summary: str = ""


class Links(BaseModel):
    linkedin: str = ""
    github: str = ""
    website: str = ""
    other: list[str] = Field(default_factory=list)


class WorkAuthorization(BaseModel):
    authorized_in: list[str] = Field(default_factory=list)
    requires_sponsorship: Optional[bool] = None
    notes: str = ""


class Profile(BaseModel):
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
PHONE_RE = re.compile(r"(\+?\d[\d\s().-]{8,}\d)")
URL_RE = re.compile(r"(?:https?://)?(?:www\.)?(linkedin\.com/in/[\w-]+|github\.com/[\w-]+|[\w-]+\.(?:dev|io|me|com)/?[\w/-]*)", re.I)
COMMON_SKILLS = [
    "python", "java", "kotlin", "swift", "typescript", "javascript", "react", "node", "go", "rust", "c++", "c#",
    "sql", "postgres", "mysql", "mongodb", "redis", "aws", "gcp", "azure", "docker", "kubernetes", "terraform",
    "android", "ios", "flutter", "fastapi", "django", "flask", "spring", "pytorch", "tensorflow", "machine learning",
    "llm", "nlp", "data science", "pandas", "spark", "kafka", "graphql", "rest", "git", "ci/cd", "linux",
]


def heuristic_parse(text: str) -> Profile:
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    email = EMAIL_RE.search(text)
    phone = PHONE_RE.search(text)
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
    name = next((l for l in lines[:5] if "@" not in l and not PHONE_RE.search(l) and len(l.split()) <= 4), "")
    return Profile(
        name=name, email=email.group(0) if email else "", phone=phone.group(0).strip() if phone else "",
        links=links, skills=skills, cv_text=text,
    )


async def parse_cv_text(text: str) -> Profile:
    text = text[:15000]
    data = await llm.complete_json(PARSE_SYSTEM, f"CV TEXT:\n{text}")
    if data:
        try:
            profile = Profile.model_validate(data)
            profile.cv_text = text
            if not profile.email:  # the regex is reliable; make sure we never lose it
                profile.email = heuristic_parse(text).email
            return profile
        except Exception:
            pass
    return heuristic_parse(text)
