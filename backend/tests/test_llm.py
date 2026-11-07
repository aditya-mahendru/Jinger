from app.llm import LLMClient


def test_llm_disabled_without_key_or_base_url():
    assert not LLMClient(api_key="", base_url="").enabled


def test_keyless_local_server_counts_as_enabled():
    client = LLMClient(api_key="", base_url="http://localhost:11434/v1/", model="qwen2.5:3b")
    assert client.enabled
    assert client.base_url == "http://localhost:11434/v1"
