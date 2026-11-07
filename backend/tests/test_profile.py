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
