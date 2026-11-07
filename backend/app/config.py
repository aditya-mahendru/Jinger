import os
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent
load_dotenv(BASE_DIR / ".env")

LLM_PROVIDER = os.getenv("LLM_PROVIDER", "openai").lower()
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_MODEL = os.getenv("LLM_MODEL", "gpt-4o-mini")
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "")
DB_PATH = Path(os.getenv("JINGER_DB", BASE_DIR / "jinger.db"))
COMPANIES_FILE = BASE_DIR / "companies.json"
UPLOAD_DIR = BASE_DIR / "uploads"
