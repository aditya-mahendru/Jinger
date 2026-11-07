import os
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent
load_dotenv(BASE_DIR / ".env")

LLM_PROVIDER = os.getenv("LLM_PROVIDER", "openai").lower()
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_MODEL = os.getenv("LLM_MODEL", "gpt-4o-mini")
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "")
LLM_TIMEOUT = float(os.getenv("LLM_TIMEOUT", "180"))
# Jobs scored per LLM call; keep small for local models (prompt size drives latency).
LLM_RANK_BATCH = int(os.getenv("LLM_RANK_BATCH", "25"))
DB_PATH = Path(os.getenv("JINGER_DB", BASE_DIR / "jinger.db"))
COMPANIES_FILE = BASE_DIR / "companies.json"
UPLOAD_DIR = BASE_DIR / "uploads"
