import React from "react";

export function Header() {
  return (
    <header className="flex justify-between items-center border-b-[3px] border-double border-[var(--line)] pb-3">
      <div className="font-['Rozha_One',serif] text-2xl tracking-[0.5px]">
        HINGLI<span className="text-[var(--accent)]">FY</span>
      </div>
      <div className="text-sm text-[var(--muted)]">Vol. 1 | Aaj ka edition</div>
    </header>
  );
}
