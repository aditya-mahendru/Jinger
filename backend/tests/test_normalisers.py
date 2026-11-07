from app.sources import ashby, greenhouse, lever

GREENHOUSE_JOB = {
    "id": 8172510, "title": "Abuse Investigator", "company_name": "Stripe",
    "location": {"name": "Remote, US"}, "first_published": "2026-09-09T10:50:29-04:00",
    "content": "&lt;h2&gt;&lt;strong&gt;Who we are&lt;/strong&gt;&lt;/h2&gt;\n&lt;p&gt;About Stripe &amp; payments&lt;/p&gt;",
    "absolute_url": "https://stripe.com/jobs/search?gh_jid=8172510",
}
LEVER_JOB = {
    "id": "abc-123", "text": "Backend Engineer", "createdAt": 1700000000000, "workplaceType": "remote",
    "categories": {"location": "Stockholm, Sweden", "team": "Platform"},
    "descriptionPlain": "Build things.", "lists": [{"text": "You will", "content": "<li>Ship code</li>"}],
    "applyUrl": "https://jobs.lever.co/spotify/abc-123/apply",
}
ASHBY_JOB = {
    "id": "1fc3", "title": "Software Engineer", "location": "San Francisco", "isRemote": False, "isListed": True,
    "secondaryLocations": [{"location": "New York"}], "publishedAt": "2026-08-24T14:44:49.699+00:00",
    "descriptionPlain": "Notion is a workspace.", "applyUrl": "https://jobs.ashbyhq.com/notion/1fc3/application",
}

REQUIRED_KEYS = {"id", "source", "company", "title", "location", "remote", "description", "apply_url", "posted_at"}


def test_greenhouse_normalise():
    job = greenhouse.normalise(GREENHOUSE_JOB, "stripe", "Stripe")
    assert set(job.model_dump()) == REQUIRED_KEYS
    assert job.id == "greenhouse:stripe:8172510"
    assert job.remote is True
    assert "Who we are" in job.description and "&lt;" not in job.description and "<" not in job.description
    assert "About Stripe & payments" in job.description
    assert job.apply_url == "https://job-boards.greenhouse.io/stripe/jobs/8172510"


def test_lever_normalise():
    job = lever.normalise(LEVER_JOB, "spotify", "Spotify")
    assert set(job.model_dump()) == REQUIRED_KEYS
    assert job.id == "lever:spotify:abc-123"
    assert job.company == "Spotify" and job.location == "Stockholm, Sweden"
    assert job.remote is True
    assert "Ship code" in job.description and "<li>" not in job.description
    assert job.posted_at.startswith("2023-11-14")


def test_ashby_normalise():
    job = ashby.normalise(ASHBY_JOB, "notion", "Notion")
    assert set(job.model_dump()) == REQUIRED_KEYS
    assert job.id == "ashby:notion:1fc3"
    assert job.location == "San Francisco, New York"
    assert job.remote is False
    assert job.apply_url.endswith("/application")
