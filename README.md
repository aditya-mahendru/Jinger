# Jinger

Tinder-style job applications for Android. Swipe left to skip, swipe right to apply.
On a right swipe Jinger opens the job's real application form in a WebView, fills in
everything it can from your CV, highlights what it isn't sure about, and then
**you** review and tap Submit.

```
android/   Kotlin · Jetpack Compose · MVVM · Retrofit · Room
backend/   Python · FastAPI · SQLite · one swappable LLM client
```

## How it works

1. **Backend** pulls jobs from public ATS job boards (no auth, no scraping of LinkedIn):
   Greenhouse, Lever and Ashby, for the ~30 companies in `backend/companies.json`.
   Jobs are normalised to one schema and cached in SQLite.
2. **Onboarding**: upload a CV PDF → `/profile/parse` extracts text and asks the LLM
   for a structured profile → you edit and save it.
3. **Feed**: `/jobs` returns unseen jobs ranked for your profile (`/rank`), each with a
   0–100 fit score and a one-line "why it fits".
4. **Apply**: the WebView loads `apply_url`, `autofill.js` collects every form field
   (label, name, type, options, required) and sends them to `/fill-plan`. High-confidence
   values are filled with React-friendly `input`/`change` events; anything uncertain
   gets a yellow outline and a bottom sheet with drafted answers you can edit or accept.
   Tapping the site's resume upload returns your stored CV automatically
   (`WebChromeClient.onShowFileChooser`).
5. **Tracker**: Applied / Skipped tabs; tap to reopen a posting.

### Safety rules (enforced in code)
- Submit is never clicked by the app; the user always submits.
- CAPTCHAs are never touched.
- The fill plan never invents experience, qualifications or work authorisation:
  anything not in the CV comes back with `needs_user: true`.
- API keys live only in `backend/.env`; the app never sees them.

## Backend setup

```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env            # optional: add LLM_API_KEY (see below)
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

On first start the backend fetches all boards (~1,800 jobs, 10–20 s). Then:

```bash
curl localhost:8000/health
curl "localhost:8000/jobs?limit=5"
curl -X POST localhost:8000/jobs/refresh
```

### LLM configuration (`backend/.env`)

| Variable | Meaning |
| --- | --- |
| `LLM_PROVIDER` | `openai` (any OpenAI-compatible chat API) or `anthropic` |
| `LLM_API_KEY` | API key. **Leave empty to run without an LLM**: parsing, ranking and fill plans fall back to deterministic heuristics (no drafted answers). |
| `LLM_MODEL` | e.g. `gpt-4o-mini`, `claude-3-5-haiku-latest` |
| `LLM_BASE_URL` | Override for OpenAI-compatible hosts (Groq, Together, Ollama, …) |
| `JINGER_DB` | SQLite path (default `jinger.db`) |

The provider lives in one class, `app/llm.py::LLMClient`; swap it by changing env vars.

#### Local model with Ollama (no API key)

```bash
ollama pull qwen2.5:3b          # ~2 GB; good at structured JSON. Alternatives: llama3.2:3b, qwen2.5:7b
```

`backend/.env`:

```
LLM_PROVIDER=openai
LLM_API_KEY=
LLM_MODEL=qwen2.5:3b
LLM_BASE_URL=http://localhost:11434/v1
LLM_RANK_BATCH=8
LLM_TIMEOUT=180
```

Ollama speaks the OpenAI chat API, so no code changes are needed. `/health` reports `"llm": true`
once configured. Small models are slow on CPU: the first ranking/parse call can take a minute,
and any call that fails or times out falls back to the heuristics, so the app keeps working.

### Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/jobs?limit=20&exclude=id1,id2` | Next unseen jobs, ranked for the saved profile |
| `POST` | `/jobs/refresh` | Re-fetch all boards |
| `POST` | `/jobs/seen` | Record `{job_ids, status: skipped|applied}` |
| `POST` | `/profile` | Save structured profile |
| `POST` | `/profile/parse` | Multipart CV PDF → structured profile |
| `POST` | `/rank` | `{profile, job_ids}` → `{scores: {id: {score, reason}}}` |
| `POST` | `/fill-plan` | `{profile, job, fields}` → per-field `{selector, value, confidence, needs_user}` + drafts |

### Tests

```bash
cd backend && pytest
```

Covers the three normalisers and the fill-plan output shape (no network needed).

## Android setup

Requirements: Android Studio (or SDK 34 + JDK 17), an emulator or device on Android 8+.

```bash
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Emulator networking

The app talks to `http://10.0.2.2:8000/` by default — on the Android emulator
`10.0.2.2` is the host machine's `localhost`. Make sure the backend is started with
`--host 0.0.0.0` (or at least reachable on localhost). Cleartext HTTP to that address is
allowed in `res/xml/network_security_config.xml`.

### Physical device

1. Put phone and laptop on the same Wi-Fi; find the laptop's LAN IP (`ipconfig getifaddr en0` on macOS,
   `hostname -I` on Linux, `ipconfig` on Windows).
2. Start the backend with `--host 0.0.0.0` (as above) and allow port 8000 through the firewall.
3. Build with that address baked in and install over USB (enable USB debugging on the phone):

   ```bash
   cd android
   ./gradlew :app:assembleDebug -PbackendUrl=http://192.168.1.20:8000/
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

   Check from the phone's browser that `http://192.168.1.20:8000/health` loads if the feed is empty.

Cleartext HTTP is allowed app-wide in `res/xml/network_security_config.xml` (dev build only).

## Demo script

1. Start the backend, launch the app.
2. Onboarding: pick a CV PDF, check the parsed fields, **Save & start swiping**.
3. Swipe left to skip, right to apply (or use the buttons). Tap a card to read the full description.
4. On the apply screen wait for "Auto-filled N field(s)…", open **N to review**, accept or edit drafts,
   tap the site's resume upload (your CV is attached automatically), then tap the site's Submit.
5. Confirmation pages are detected automatically; otherwise tap **I submitted it**.
6. Open the tracker (top-right) to see Applied / Skipped.

## Project layout

```
backend/app/sources/{greenhouse,lever,ashby}.py   fetch + normalise per ATS
backend/app/{jobs,db}.py                          aggregation + SQLite cache
backend/app/{profile,ranking,fill_plan,llm}.py    CV parsing, ranking, fill plans, LLM client
backend/tests/                                    pytest
android/app/src/main/java/com/jinger/app/
  data/api        Retrofit API + DTOs
  data/db         Room (jobs with NEW/SKIPPED/APPLIED status)
  data/repo       JobRepository, ProfileRepository
  ui/onboarding   CV upload + editable profile
  ui/feed         swipe stack (SwipeCard, JobCard, FeedScreen)
  ui/apply        WebView apply flow + needs-user sheet
  ui/tracker      Applied / Skipped tabs
android/app/src/main/assets/autofill.js           field extraction / fill / highlight bridge
```
