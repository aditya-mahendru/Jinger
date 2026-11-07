"""One small LLM client so the provider is swappable.

Returns parsed JSON, or None when no LLM is configured. Configured means either an API key
(OpenAI/Anthropic/any hosted OpenAI-compatible API) or a base URL for a keyless local server
such as Ollama (`LLM_BASE_URL=http://localhost:11434/v1`).
"""
import json
import logging
import re
from typing import Optional

import httpx

from . import config

log = logging.getLogger("jinger.llm")


class LLMClient:
    def __init__(self, provider: str = config.LLM_PROVIDER, api_key: str = config.LLM_API_KEY,
                 model: str = config.LLM_MODEL, base_url: str = config.LLM_BASE_URL):
        self.provider = provider
        self.api_key = api_key
        self.model = model
        self.base_url = base_url.rstrip("/") if base_url else None

    @property
    def enabled(self) -> bool:
        return bool(self.api_key or self.base_url)

    async def complete_json(self, system: str, user: str, max_tokens: int = 2000) -> Optional[dict]:
        """Ask for a JSON object. Returns None if the LLM is disabled or the call fails."""
        if not self.enabled:
            return None
        try:
            text = await self._complete(system + "\nRespond with a single JSON object and nothing else.", user, max_tokens)
            return _extract_json(text)
        except Exception as exc:
            log.warning("LLM call failed: %s", exc)
            return None

    async def _complete(self, system: str, user: str, max_tokens: int) -> str:
        # Local models (Ollama on a laptop CPU) can take a while, especially on first load.
        async with httpx.AsyncClient(timeout=config.LLM_TIMEOUT) as client:
            if self.provider == "anthropic":
                resp = await client.post(
                    (self.base_url or "https://api.anthropic.com") + "/v1/messages",
                    headers={"x-api-key": self.api_key, "anthropic-version": "2023-06-01"},
                    json={"model": self.model, "max_tokens": max_tokens, "system": system,
                          "messages": [{"role": "user", "content": user}]},
                )
                resp.raise_for_status()
                return "".join(b.get("text", "") for b in resp.json()["content"])
            resp = await client.post(
                (self.base_url or "https://api.openai.com/v1") + "/chat/completions",
                headers={"Authorization": f"Bearer {self.api_key or 'none'}"},
                json={"model": self.model, "max_tokens": max_tokens, "temperature": 0.2,
                      "response_format": {"type": "json_object"},
                      "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}]},
            )
            resp.raise_for_status()
            return resp.json()["choices"][0]["message"]["content"]


def _extract_json(text: str) -> Optional[dict]:
    text = text.strip()
    text = re.sub(r"^```(?:json)?\s*|\s*```$", "", text)
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", text, re.DOTALL)
        return json.loads(match.group(0)) if match else None


llm = LLMClient()
