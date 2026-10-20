"use client";

import React from "react";

interface ResultSplitProps {
  englishText: string;
  hinglishHtml: string;
  onDownload: () => void;
  onReset: () => void;
}

export function ResultSplit({
  englishText,
  hinglishHtml,
  onDownload,
  onReset,
}: ResultSplitProps) {
  return (
    <section>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-0 border-[3px] border-[var(--line)] mt-2">
        <div className="p-5">
          <h3 className="m-0 mb-2.5 font-['Rozha_One',serif] font-normal text-[22px] border-b-[3px] border-double border-[var(--line)] pb-1.5">
            English
          </h3>
          <p className="m-0 leading-relaxed whitespace-pre-wrap">{englishText}</p>
        </div>
        <div className="p-5 sm:border-l-[3px] sm:border-t-0 border-t-[3px] border-[var(--line)]">
          <h3 className="m-0 mb-2.5 font-['Rozha_One',serif] font-normal text-[22px] border-b-[3px] border-double border-[var(--line)] pb-1.5">
            Hinglish
          </h3>
          <p
            className="m-0 leading-relaxed whitespace-pre-wrap"
            dangerouslySetInnerHTML={{ __html: hinglishHtml }}
          />
        </div>
      </div>

      <p className="text-sm text-[var(--muted)] mt-2.5 mb-0">
        Highlighted words are the ones that changed.
      </p>

      <div className="flex gap-3 mt-5 flex-wrap">
        <button
          type="button"
          onClick={onDownload}
          className="bg-[var(--accent)] text-[#1A1A1A] font-semibold border-[3px] border-[var(--line)] rounded-none px-6 py-3 cursor-pointer hover:-translate-x-[2px] hover:-translate-y-[2px] hover:[box-shadow:4px_4px_0_var(--line)] active:translate-x-0 active:translate-y-0 active:shadow-none transition-transform"
        >
          Download Hinglish PDF
        </button>
        <button
          type="button"
          onClick={onReset}
          className="bg-transparent text-[var(--text)] border-[3px] border-[var(--line)] rounded-none px-3 py-1.5 font-semibold cursor-pointer hover:-translate-x-[2px] hover:-translate-y-[2px] hover:[box-shadow:4px_4px_0_var(--line)] active:translate-x-0 active:translate-y-0 active:shadow-none transition-transform"
        >
          Naya PDF
        </button>
      </div>
    </section>
  );
}
