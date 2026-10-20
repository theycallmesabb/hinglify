"use client";

import React, { useState, useRef, useEffect } from "react";
import { Header } from "@/components/Header";
import { UploadBox } from "@/components/UploadBox";
import { ProgressBar } from "@/components/ProgressBar";
import { ResultSplit } from "@/components/ResultSplit";

type ScreenState = "upload" | "converting" | "result";

interface JobProgressResponse {
  id: string;
  status: string;
  original_filename?: string;
  page_count?: number;
  progress?: {
    chunks_done: number;
    total_chunks: number;
    percent: number;
  };
  error?: string;
}

interface ChunkItem {
  index: number;
  sourceText: string;
  translatedText: string;
  status: string;
}

function highlightChangedWords(sourceText: string, translatedText: string): string {
  if (!translatedText) return "";
  const sourceWords = new Set(
    sourceText
      .toLowerCase()
      .replace(/[^a-z0-9\s]/g, " ")
      .split(/\s+/)
      .filter(Boolean)
  );

  return translatedText.replace(/\b([a-zA-Z]+)\b/g, (match) => {
    const lower = match.toLowerCase();
    if (!sourceWords.has(lower)) {
      return `<mark>${match}</mark>`;
    }
    return match;
  });
}

export default function Home() {
  const [screen, setScreen] = useState<ScreenState>("upload");
  const [file, setFile] = useState<File | null>(null);
  const [jobId, setJobId] = useState<string | null>(null);
  const [progressPercent, setProgressPercent] = useState<number>(0);
  const [statusText, setStatusText] = useState<string>("Reading your PDF");

  const [englishText, setEnglishText] = useState<string>("");
  const [hinglishHtml, setHinglishHtml] = useState<string>("");

  const pollIntervalRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    return () => {
      if (pollIntervalRef.current) {
        clearInterval(pollIntervalRef.current);
      }
    };
  }, []);

  const handleFileSelect = (selectedFile: File) => {
    setFile(selectedFile);
  };

  const handleClear = () => {
    setFile(null);
  };

  const handleReset = () => {
    if (pollIntervalRef.current) {
      clearInterval(pollIntervalRef.current);
      pollIntervalRef.current = null;
    }
    setFile(null);
    setJobId(null);
    setProgressPercent(0);
    setStatusText("Reading your PDF");
    setScreen("upload");
  };

  const API_BASE = "http://localhost:8080";

  const handleConvert = async () => {
    if (!file) return;

    setScreen("converting");
    setProgressPercent(5);
    setStatusText("Uploading your PDF...");

    try {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("hinglish_level", "2");

      const response = await fetch(`${API_BASE}/jobs`, {
        method: "POST",
        headers: {
          "X-API-Key": "dev-secret-key",
        },
        body: formData,
      });

      if (!response.ok) {
        const errJson = await response.json().catch(() => ({ detail: "Upload failed" }));
        throw new Error(errJson.detail || `Upload failed with status ${response.status}`);
      }

      const jobData = await response.json();
      const currentJobId = jobData.job_id || jobData.id;
      if (!currentJobId) {
        throw new Error("Job ID missing from backend response");
      }
      setJobId(currentJobId);

      startPolling(currentJobId);
    } catch (err: unknown) {
      const errorMsg = err instanceof Error ? err.message : "Error connecting to backend.";
      alert(errorMsg);
      handleReset();
    }
  };

  const startPolling = (id: string) => {
    if (!id || id === "undefined") return;

    if (pollIntervalRef.current) {
      clearInterval(pollIntervalRef.current);
    }

    pollIntervalRef.current = setInterval(async () => {
      try {
        const res = await fetch(`${API_BASE}/jobs/${id}`, {
          headers: {
            "X-API-Key": "dev-secret-key",
          },
        });
        if (!res.ok) return;

        const data: JobProgressResponse = await res.json();

        if (data.progress) {
          setProgressPercent(data.progress.percent);
        }

        if (data.status === "QUEUED") {
          setStatusText("Queued... preparing worker");
        } else if (data.status === "EXTRACTING") {
          setStatusText("Reading your PDF and extracting text...");
        } else if (data.status === "TRANSLATING") {
          const done = data.progress?.chunks_done ?? 0;
          const total = data.progress?.total_chunks ?? 0;
          setStatusText(
            total > 0
              ? `Converting to Hinglish... (Chunk ${done} of ${total})`
              : "Converting to Hinglish..."
          );
        } else if (data.status === "GENERATING_PDF") {
          setStatusText("Putting it back together and generating clean PDF...");
        } else if (data.status === "FAILED") {
          if (pollIntervalRef.current) clearInterval(pollIntervalRef.current);
          alert(data.error || "Translation failed. Please try again.");
          handleReset();
        } else if (data.status === "DONE") {
          if (pollIntervalRef.current) clearInterval(pollIntervalRef.current);
          setProgressPercent(100);
          setStatusText("Done!");

          await fetchPreview(id);
        }
      } catch (e) {
        console.error("Polling error", e);
      }
    }, 1200);
  };

  const fetchPreview = async (id: string) => {
    try {
      const chunksRes = await fetch(`${API_BASE}/jobs/${id}/chunks`, {
        headers: {
          "X-API-Key": "dev-secret-key",
        },
      });
      if (chunksRes.ok) {
        const chunks: ChunkItem[] = await chunksRes.json();
        if (chunks && chunks.length > 0) {
          const first = chunks[0];
          setEnglishText(first.sourceText);
          setHinglishHtml(highlightChangedWords(first.sourceText, first.translatedText));
          setScreen("result");
          return;
        }
      }
    } catch {
      // Fallback
    }

    setEnglishText("Press the button to download your translated PDF.");
    setHinglishHtml("<mark>Aapki file</mark> download ke liye <mark>ready hai</mark>.");
    setScreen("result");
  };

  const handleDownload = () => {
    if (!jobId) {
      alert("No completed job found to download.");
      return;
    }
    window.location.href = `${API_BASE}/jobs/${jobId}/download`;
  };

  return (
    <div className="max-w-[960px] mx-auto py-6 px-5 pb-16" suppressHydrationWarning>
      <Header />

      <h1 className="font-['Rozha_One',serif] font-normal text-[clamp(40px,9vw,84px)] leading-[1.05] mt-10 mb-3 text-left">
        Apna PDF,<br />
        <em className="not-italic text-[var(--accent)]">अपनी</em> language mein
      </h1>

      <p className="mt-0 mb-7 text-[var(--muted)] max-w-[520px]">
        Upload an English PDF. Get it back in Hinglish. Text PDFs only for now.
      </p>

      {screen === "upload" && (
        <UploadBox
          selectedFile={file}
          onFileSelect={handleFileSelect}
          onClear={handleClear}
          onConvert={handleConvert}
        />
      )}

      {screen === "converting" && (
        <ProgressBar
          progressPercent={progressPercent}
          statusText={statusText}
        />
      )}

      {screen === "result" && (
        <ResultSplit
          englishText={englishText}
          hinglishHtml={hinglishHtml}
          onDownload={handleDownload}
          onReset={handleReset}
        />
      )}
    </div>
  );
}
