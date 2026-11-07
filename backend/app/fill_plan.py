"""Turns (profile, job, form fields) into a fill plan: deterministic mapping for standard fields,
LLM for everything else. Unknown => needs_user=True; we never invent facts."""
import json
import re
from typing import Any, Optional

from pydantic import BaseModel, Field

from .llm import llm
from .profile import Profile
from .sources.common import Job


class FormField(BaseModel):
    selector: str
    label: str = ""
    name: str = ""
    id: str = ""
    type: str = "text"  # text, email, tel, url, textarea, select, radio, checkbox, file, ...
    options: list[str] = Field(default_factory=list)
    required: bool = False
    value: str = ""


class FillPlanRequest(BaseModel):
    profile: Profile
    job: Job
    fields: list[FormField]


class FieldPlan(BaseModel):
    selector: str
    label: str = ""
    value: str = ""
    confidence: float = 0.0
    needs_user: bool = True
    reason: str = ""


class FillPlan(BaseModel):
    fields: list[FieldPlan]
    drafts: dict[str, str] = Field(default_factory=dict)  # selector -> drafted long-form answer
    llm_used: bool = False


SKIP_TYPES = {"file", "hidden", "submit", "button", "password"}

# Fields about another person/organisation (employer, referee, manager...): the generic
# name/email/phone rules must never fill "Company name" with the candidate's own details.
# School/degree are not listed: those are filled from the candidate's education by RULES.
THIRD_PARTY_RE = re.compile(
    r"\b(company|employer|organi[sz]ation|reference|referee|referr?er|manager|supervisor|"
    r"recruiter|emergency|contact person)\b", re.I)
SELF_RE = re.compile(r"\b(current|most recent|latest|your)\b", re.I)

# (regex on label/name/id, profile getter, confidence)
RULES: list[tuple[re.Pattern, Any, float]] = [
    (re.compile(r"\b(first|given)[\s_-]*name\b", re.I), lambda p: p.name.split()[0] if p.name else "", 0.95),
    (re.compile(r"\b(last|family|sur)[\s_-]*name\b", re.I), lambda p: p.name.split()[-1] if len(p.name.split()) > 1 else "", 0.95),
    (re.compile(r"\b(full[\s_-]*)?name\b", re.I), lambda p: p.name, 0.9),
    (re.compile(r"e[\s_-]*mail", re.I), lambda p: p.email, 0.98),
    (re.compile(r"\b(phone|mobile|tel)", re.I), lambda p: p.phone, 0.95),
    (re.compile(r"linked[\s_-]*in", re.I), lambda p: p.links.linkedin, 0.95),
    (re.compile(r"git[\s_-]*hub", re.I), lambda p: p.links.github, 0.95),
    (re.compile(r"\b(portfolio|website|personal site|url)\b", re.I), lambda p: p.links.website, 0.8),
    (re.compile(r"\b(location|city|where (are you|do you) (based|live)|current address|address)\b", re.I), lambda p: p.location, 0.8),
    (re.compile(r"\b(current|most recent|latest)[\s_-]*(company|employer)\b", re.I), lambda p: p.experience[0].company if p.experience else "", 0.85),
    (re.compile(r"\b(current|most recent|latest)[\s_-]*(title|role|position)\b", re.I), lambda p: p.experience[0].title if p.experience else "", 0.85),
    (re.compile(r"\b(school|university|college)\b", re.I), lambda p: p.education[0].school if p.education else "", 0.7),
    (re.compile(r"\bdegree\b", re.I), lambda p: p.education[0].degree if p.education else "", 0.7),
]
SENSITIVE_RE = re.compile(
    r"gender|race|ethnic|veteran|disab|sexual|pronoun|salary|compensation|date of birth|\bdob\b|age\b|"
    r"sponsor|authori[sz]|visa|legally|eligib|work permit|right to work|relocat|notice period|start date|"
    r"how did you hear|referr|consent|privacy|agree|acknowledg|signature|cover letter",
    re.I,
)


def _match_option(value: str, options: list[str]) -> Optional[str]:
    if not value or not options:
        return None
    low = value.lower()
    for o in options:
        if o.lower() == low:
            return o
    for o in options:
        if low in o.lower() or o.lower() in low:
            return o
    return None


def deterministic_plan(profile: Profile, fields: list[FormField]) -> tuple[list[FieldPlan], list[FormField]]:
    """Fill the obvious contact/link fields; return (planned, unresolved)."""
    planned, unresolved = [], []
    for f in fields:
        if f.type in SKIP_TYPES:
            continue
        haystack = f"{f.label} {f.name} {f.id}"
        if SENSITIVE_RE.search(haystack) or f.type in {"textarea", "radio", "checkbox"} or (f.type == "select" and f.options):
            unresolved.append(f)
            continue
        if THIRD_PARTY_RE.search(haystack) and not SELF_RE.search(haystack):
            unresolved.append(f)
            continue
        hit = None
        for pattern, getter, conf in RULES:
            if pattern.search(haystack):
                value = getter(profile)
                if value:
                    hit = (value, conf)
                break
        if hit:
            planned.append(FieldPlan(selector=f.selector, label=f.label, value=hit[0], confidence=hit[1],
                                     needs_user=False, reason="From your profile"))
        else:
            unresolved.append(f)
    return planned, unresolved


LLM_SYSTEM = """You help a candidate fill a job application form. You get the candidate's profile (from their CV),
the job posting, and a list of form fields. For each field decide the value.
Rules:
- Use ONLY facts in the profile/CV. Never invent experience, qualifications, dates, salary, or work authorisation.
- For select/radio/checkbox fields, the value MUST be exactly one of the given options (or "" if unsure).
- For demographic, compensation, work-authorisation, visa/sponsorship, legal consent, or "how did you hear" fields:
  set needs_user=true, confidence 0, value "" unless the profile explicitly answers it.
- For free-text questions (textarea), write a concise, specific, first-person draft (max ~120 words) grounded
  in the CV and job description; set needs_user=true so the candidate reviews it.
- confidence is 0..1. Set needs_user=true whenever confidence < 0.8.
Return JSON: {"fields": [{"selector": str, "value": str, "confidence": number, "needs_user": bool, "reason": str}]}"""


async def build_fill_plan(req: FillPlanRequest) -> FillPlan:
    planned, unresolved = deterministic_plan(req.profile, req.fields)
    drafts: dict[str, str] = {}
    llm_used = False

    if unresolved and llm.enabled:
        payload = {
            "profile": req.profile.model_dump(exclude={"cv_text"}),
            "cv_excerpt": req.profile.cv_text[:6000],
            "job": {"title": req.job.title, "company": req.job.company, "description": req.job.description[:4000]},
            "fields": [f.model_dump() for f in unresolved],
        }
        data = await llm.complete_json(LLM_SYSTEM, json.dumps(payload), max_tokens=3000)
        if data and isinstance(data.get("fields"), list):
            llm_used = True
            by_selector = {f.selector: f for f in unresolved}
            for item in data["fields"]:
                f = by_selector.pop(item.get("selector"), None)
                if not f:
                    continue
                value = str(item.get("value") or "")
                if f.options:
                    value = _match_option(value, f.options) or ""
                conf = float(item.get("confidence") or 0)
                needs_user = bool(item.get("needs_user", True)) or conf < 0.8 or not value
                if f.type == "textarea" and value:
                    drafts[f.selector] = value
                    needs_user = True
                planned.append(FieldPlan(selector=f.selector, label=f.label, value=value, confidence=conf,
                                         needs_user=needs_user, reason=str(item.get("reason", ""))[:160]))
            unresolved = list(by_selector.values())

    for f in unresolved:  # whatever is left we honestly don't know
        reason = "Needs your answer"
        value = ""
        if f.type == "select" and f.options:
            guess = _match_option(req.profile.location, f.options) if re.search(r"country|location", f.label, re.I) else None
            if guess:
                value, reason = guess, "Guessed from your location"
        planned.append(FieldPlan(selector=f.selector, label=f.label, value=value, confidence=0.3 if value else 0.0,
                                 needs_user=True, reason=reason))
    return FillPlan(fields=planned, drafts=drafts, llm_used=llm_used)
