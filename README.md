# Hinglify (HinglishPDF)

Convert English PDFs into fluent, natural spoken Hinglish (Hindi grammar in Roman script, technical terms in English) with sentence-by-sentence precision and download a beautifully styled PDF.

![Hinglify Preview](https://raw.githubusercontent.com/user/repo/main/preview.png) *(or local preview)*

---

## Features

- **Exact Editorial UI**: Built with Next.js & Tailwind CSS featuring Rozha One & Hind typography, thick 3px brutalist borders, and full dark/light mode support.
- **Side-by-side Translation Comparison**: View original English text on the left and fluent Hinglish on the right with changed/translated words highlighted (`<mark>`).
- **Smart Document Extraction**: Python FastAPI service powered by PyMuPDF (`fitz`) handles small-caps span merging, drop-shadow deduplication, running header/footer filtering, and cross-page sentence continuations.
- **Cross-Page Overlap Detection**: Detects and trims duplicate lines or catchwords across adjacent PDF page boundaries.
- **Intelligent Number Preservation**: Automatically standardizes spelled-out numbers (e.g. `thirty-four` -> `34`, `twenty-six` -> `26`) and audits numbers to ensure 100% preservation.
- **Meaning Preservation & Polarity Auditing**: Validates sentence coverage and ensures negative polarities are never flipped.
- **Mixed-Language Validation**: Detects and auto-corrects untranslated English leftovers, broken trailing connectors, and character stuttering artifacts.
- **Clean PDF Generation**: Reassembles translated Hinglish chunks into downloadable PDFs using OpenPDF with embedded Unicode typography (`DejaVuSans.ttf`).
- **Resilient AI Pipeline**: Multi-model pool fallback with Google Gemini (`gemini-3.5-flash-lite`, `gemini-3.8-flash`, `gemini-3.1-flash-lite`) and intelligent backoff.

---

## Architecture Overview

```
┌─────────────────────────────────┐
│     Next.js Frontend (3000)     │
│  (Tailwind, Rozha One & Hind)   │
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│   Spring Boot Backend (8080)    │
│  (Job queue, Chunking, Quality  │
│   Validator, OpenPDF Generator) │
└───────┬─────────────────┬───────┘
        │                 │
        ▼                 ▼
┌──────────────┐   ┌──────────────┐
│  FastAPI     │   │  PostgreSQL  │
│  Extractor   │   │   Database   │
│    (8000)    │   │    (5432)    │
└──────────────┘   └──────────────┘
```

---

## Getting Started

### 1. Prerequisites
- Docker & Docker Compose
- Node.js 18+ & npm
- Java 21 & Maven (for local backend development)

### 2. Environment Setup
Copy the example environment file inside `hinglishpdf/`:
```bash
cp hinglishpdf/.env.example hinglishpdf/.env
```
Add your Google Gemini API key:
```env
LLM_PROVIDER=gemini
LLM_API_KEY=your_gemini_api_key_here
LLM_MODEL=gemini-3.5-flash-lite
```

### 3. Run Backend Services (Docker Compose)
From the project root:
```bash
docker compose -f hinglishpdf/docker-compose.yml up -d --build
```
This starts:
- PostgreSQL on port `5432`
- FastAPI PDF Extractor on port `8000`
- Spring Boot Backend on port `8080`

### 4. Run Frontend (Next.js)
Install dependencies and run dev server:
```bash
npm install
npm run dev
```
Open [http://localhost:3000](http://localhost:3000) in your browser.

---

## API Endpoints

- `POST /jobs` — Upload PDF and enqueue translation job (`multipart/form-data`)
- `GET /jobs/{id}` — Poll job progress, chunk stats, and status
- `GET /jobs/{id}/chunks` — Fetch source English and translated Hinglish chunks for live preview
- `GET /jobs/{id}/download` — Download final converted Hinglish PDF

---

## License
MIT
