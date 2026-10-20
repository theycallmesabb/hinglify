# HinglishPDF

HinglishPDF converts text-based PDF documents into clear, natural **Hinglish** (Hindi written strictly in Roman letters, mixed with English technical terms) and generates a brand-new, clean, beautifully formatted output PDF with headings and paragraphs.

---

## Tech Stack
- **API & Worker**: Java 21, Spring Boot 3.3.4, Spring Data JPA, OpenPDF (Unicode font support)
- **PDF Extractor**: Python 3.11, FastAPI, PyMuPDF
- **Database**: PostgreSQL 16
- **LLM**: Swappable LLM Provider (OpenAI / OpenAI-compatible / Mock Provider for local testing)
- **Deployment**: Docker Compose

---

## Quick Start (Docker Compose)

### 1. Clone & Configure
```bash
cd hinglishpdf
cp .env.example .env
```

*(Optional)* If you want to use a live OpenAI key for translation, edit `.env`:
```env
LLM_PROVIDER=openai
LLM_API_KEY=sk-...
LLM_MODEL=gpt-4o-mini
```
*(If left as `LLM_API_KEY=mock-key`, HinglishPDF automatically uses the built-in offline Hinglish translator at zero cost).*

### 2. Start Services
```bash
docker compose up --build
```

### 3. Open Web UI
Open your browser and navigate to:
👉 **http://localhost:8080**

- Drag and drop any text PDF (up to 20 MB, up to 100 pages).
- Select your preferred Hinglish tone (Level 1: Light, Level 2: Balanced, Level 3: Deep).
- Watch real-time progress: Extraction → Text Cleaning → Sentence-Aware Chunking → Glossary Generation → Parallel Translation → PDF Compilation.
- Click **"Download Translated Hinglish PDF"** when done!

---

## API Documentation & Sample cURL Commands

### 1. Health Check
```bash
curl http://localhost:8080/health
```
**Response:**
```json
{
  "status": "ok",
  "service": "hinglishpdf-backend"
}
```

### 2. Submit a PDF Conversion Job (`POST /jobs`)
```bash
curl -X POST http://localhost:8080/jobs \
  -H "X-API-Key: dev-secret-key" \
  -F "file=@sample.pdf" \
  -F "hinglish_level=2"
```
**Response:**
```json
{
  "job_id": "3d687b0a-1ead-444c-ba51-5b2b25455116",
  "status": "QUEUED",
  "message": "Job queued successfully"
}
```

### 3. Check Job Status & Live Progress (`GET /jobs/{id}`)
```bash
curl http://localhost:8080/jobs/3d687b0a-1ead-444c-ba51-5b2b25455116 \
  -H "X-API-Key: dev-secret-key"
```
**Response:**
```json
{
  "id": "3d687b0a-1ead-444c-ba51-5b2b25455116",
  "status": "DONE",
  "original_filename": "sample.pdf",
  "page_count": 3,
  "hinglish_level": 2,
  "progress": {
    "chunks_done": 4,
    "total_chunks": 4,
    "percent": 100
  },
  "tokens": {
    "in": 1150,
    "out": 1220
  },
  "error": null
}
```

### 4. Download Translated PDF (`GET /jobs/{id}/download`)
```bash
curl -O -J -L http://localhost:8080/jobs/3d687b0a-1ead-444c-ba51-5b2b25455116/download \
  -H "X-API-Key: dev-secret-key"
```
*(Or via browser query parameter: `http://localhost:8080/jobs/{id}/download?api_key=dev-secret-key`)*

---

## Core Features & Pipeline

1. **Scanned PDF Protection**: Automatically calculates average character density across all pages. If `< 50 chars/page`, rejects with:
   `"This PDF looks scanned. Scanned PDFs are not supported yet."`
2. **Text Cleaning**:
   - Joins hyphenated line breaks (`archi-\ntecture` → `architecture`).
   - Discards standalone page numbers (`Page 1 of 3`, `1`, etc.).
   - Discards running headers & footers repeated across pages.
3. **Sentence-Aware Chunking**:
   - Chunks text into 800–1200 tokens (estimated as `chars / 4`).
   - Never splits in the middle of a sentence.
4. **Context & Glossary Continuity**:
   - Extracts document-wide technical terms from headings and sample text.
   - Forwards the glossary and the trailing context of the previous chunk with each translation request.
5. **Parallel Resilient Worker**:
   - Translates chunks in parallel (bounded to 5 concurrent threads).
   - Retries failed chunks with backoff (up to 3 tries).
   - Automatically resumes any interrupted jobs on server restart (`@EventListener(ApplicationReadyEvent.class)`).
6. **Embedded Unicode Font**:
   - Bundles `DejaVuSans.ttf` inside the JAR to render any Latin/Roman character, punctuation, or diacritic reliably without depending on system fonts.

---

## Running Automated Tests
```bash
cd backend
mvn test
```
Runs tests for:
- Scanned PDF detection (`ScannedCheckServiceTest`)
- Text cleaning & line joining (`TextCleanerServiceTest`)
- Sentence-aware chunking (`ChunkerServiceTest`)
- Glossary extraction & term merge (`GlossaryMergeTest`)
