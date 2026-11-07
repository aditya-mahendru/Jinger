import asyncio

from app.fill_plan import FillPlanRequest, FormField, build_fill_plan
from app.profile import Experience, Links, Profile
from app.sources.common import Job

PROFILE = Profile(
    name="Ada Lovelace", email="ada@example.com", phone="+44 7700 900000", location="London, UK",
    links=Links(linkedin="https://linkedin.com/in/ada", github="https://github.com/ada"),
    skills=["Python", "Kotlin"], experience=[Experience(company="Analytical Engines", title="Engineer")],
)
JOB = Job(id="greenhouse:x:1", source="greenhouse", company="X", title="Engineer", location="London",
          remote=False, description="Python role", apply_url="https://example.com")
FIELDS = [
    FormField(selector="#first_name", label="First Name", type="text", required=True),
    FormField(selector="#last_name", label="Last Name", type="text"),
    FormField(selector="#email", label="Email", type="email"),
    FormField(selector="#phone", label="Phone", type="tel"),
    FormField(selector="#linkedin", label="LinkedIn Profile", type="text"),
    FormField(selector="#resume", label="Resume/CV", type="file"),
    FormField(selector="#why", label="Why do you want to work here?", type="textarea"),
    FormField(selector="#auth", label="Are you legally authorised to work in the UK?", type="select", options=["Yes", "No"]),
    FormField(selector="#gender", label="Gender", type="select", options=["Male", "Female", "Decline"]),
]


def test_fill_plan_shape_without_llm():
    plan = asyncio.run(build_fill_plan(FillPlanRequest(profile=PROFILE, job=JOB, fields=FIELDS)))
    by_sel = {f.selector: f for f in plan.fields}
    assert plan.llm_used is False
    for f in plan.fields:
        assert set(f.model_dump()) == {"selector", "label", "value", "confidence", "needs_user", "reason"}
        assert 0.0 <= f.confidence <= 1.0
    assert "#resume" not in by_sel  # file inputs are handled natively, never in the plan
    assert by_sel["#first_name"].value == "Ada" and not by_sel["#first_name"].needs_user
    assert by_sel["#last_name"].value == "Lovelace"
    assert by_sel["#email"].value == "ada@example.com" and by_sel["#email"].confidence >= 0.9
    assert by_sel["#linkedin"].value == "https://linkedin.com/in/ada"
    # Anything we can't ground in the CV must be left to the user with no invented value.
    for sel in ("#why", "#auth", "#gender"):
        assert by_sel[sel].needs_user is True
        assert by_sel[sel].value == ""
