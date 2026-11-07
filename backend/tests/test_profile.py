from app.profile import heuristic_parse


def test_heuristic_parse_extracts_contacts_and_links():
    p = heuristic_parse(
        "Ada Lovelace\nada@example.com | +44 7700 900123\n"
        "github.com/adal | linkedin.com/in/adalovelace\nSKILLS\nPython, Kotlin"
    )
    assert p.name == "Ada Lovelace"
    assert p.email == "ada@example.com"
    assert p.links.github == "https://github.com/adal"
    assert p.links.linkedin == "https://linkedin.com/in/adalovelace"
    assert p.links.website == ""  # email domain must not be mistaken for a website
    assert {"python", "kotlin"} <= set(p.skills)
    assert p.work_authorization.requires_sponsorship is None  # never guessed


def test_llm_nulls_and_numbers_are_tolerated():
    from app.profile import Profile
    p = Profile.model_validate({
        "name": "Ada", "email": None, "summary": None,
        "links": {"linkedin": None, "website": None, "other": None},
        "education": [{"school": "UCD", "start": 2020, "end": None}],
        "experience": None, "skills": None,
        "work_authorization": {"authorized_in": None, "requires_sponsorship": None, "notes": None},
    })
    assert p.email == "" and p.links.website == "" and p.skills == []
    assert p.education[0].start == "2020" and p.education[0].end == ""
    assert p.work_authorization.requires_sponsorship is None


def test_heuristic_name_strips_glued_contact_details():
    from app.profile import heuristic_parse, _split_skills
    text = "Aditya MahendruEmail : a@x.com\nMobile : +353 89 252 6120\nLinkedIn — GitHub\nSummary\n..."
    assert heuristic_parse(text).name == "Aditya Mahendru"
    assert _split_skills(["Python", "Languages: TypeScript, SQL, Python"]) == ["Python", "TypeScript", "SQL"]


def test_heuristic_ignores_year_ranges_as_phone_and_tel_inside_surnames():
    from app.profile import heuristic_parse
    p = heuristic_parse("Ada Patel\nEmail: ada@x.com\nExperience\nAcme 2020 - 2024\n")
    assert p.name == "Ada Patel" and p.phone == ""
    assert heuristic_parse("Ada Patel\nTel: +353 89 252 6120\n2020 - 2024").phone == "+353 89 252 6120"
    assert heuristic_parse("Ada Patel\nTel: 22 33 44 55").phone == "22 33 44 55"  # 8-digit local numbers (DK/NO/SG)
