import React from "react";

interface ProgressBarProps {
  progressPercent: number;
  statusText: string;
}

export function ProgressBar({ progressPercent, statusText }: ProgressBarProps) {
  return (
    <section className="border-[3px] border-[var(--line)] p-6">
      <strong className="font-['Rozha_One',serif] font-normal text-[28px] block">
        Ruko zara...
      </strong>
      <div className="h-[22px] border-[3px] border-[var(--line)] my-4 mb-2 bg-transparent">
        <div
          className="h-full bg-[var(--accent)] transition-[width] duration-300 ease-out"
          style={{ width: `${Math.min(100, Math.max(0, progressPercent))}%` }}
        />
      </div>
      <div className="text-[var(--muted)] text-base">{statusText}</div>
    </section>
  );
}
