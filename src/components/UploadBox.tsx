"use client";

import React, { useState, useRef } from "react";

interface UploadBoxProps {
  selectedFile: File | null;
  onFileSelect: (file: File) => void;
  onClear: () => void;
  onConvert: () => void;
}

export function UploadBox({
  selectedFile,
  onFileSelect,
  onClear,
  onConvert,
}: UploadBoxProps) {
  const [isOver, setIsOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleDragEnter = (e: React.DragEvent) => {
    e.preventDefault();
    setIsOver(true);
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsOver(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    setIsOver(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const file = e.dataTransfer.files[0];
      if (file.type === "application/pdf") {
        onFileSelect(file);
      }
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      const file = e.target.files[0];
      if (file.type === "application/pdf") {
        onFileSelect(file);
      }
    }
  };

  const handleClear = () => {
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
    onClear();
  };

  return (
    <section className="border-[3px] border-[var(--line)] p-6">
      <label
        htmlFor="pdf"
        onDragEnter={handleDragEnter}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        className={`block border-[3px] border-dashed border-[var(--line)] py-10 px-5 text-center cursor-pointer transition-colors duration-150 ${
          isOver ? "bg-[var(--hl)]" : ""
        }`}
      >
        <strong className="font-['Rozha_One',serif] font-normal text-[28px] block">
          Yahan PDF daalo
        </strong>
        <small className="text-[var(--muted)] text-sm">
          Drag and drop, or tap to pick a file
        </small>
        <input
          id="pdf"
          ref={fileInputRef}
          type="file"
          accept="application/pdf"
          className="hidden"
          onChange={handleFileChange}
        />
      </label>

      {selectedFile && (
        <div className="flex items-center justify-between gap-3 mt-4 border-[3px] border-[var(--line)] py-2.5 px-3.5">
          <span className="overflow-hidden text-ellipsis whitespace-nowrap">
            {selectedFile.name}
          </span>
          <button
            type="button"
            onClick={handleClear}
            className="bg-transparent text-[var(--text)] border-[3px] border-[var(--line)] rounded-none px-3 py-1.5 font-semibold cursor-pointer hover:-translate-x-[2px] hover:-translate-y-[2px] hover:[box-shadow:4px_4px_0_var(--line)] active:translate-x-0 active:translate-y-0 active:shadow-none transition-transform"
          >
            Hatao
          </button>
        </div>
      )}

      <div className="flex gap-3 mt-5 flex-wrap">
        <button
          type="button"
          disabled={!selectedFile}
          onClick={onConvert}
          className="bg-[var(--accent)] text-[#1A1A1A] font-semibold border-[3px] border-[var(--line)] rounded-none px-6 py-3 cursor-pointer hover:-translate-x-[2px] hover:-translate-y-[2px] hover:[box-shadow:4px_4px_0_var(--line)] active:translate-x-0 active:translate-y-0 active:shadow-none disabled:opacity-40 disabled:cursor-not-allowed disabled:transform-none disabled:shadow-none transition-transform"
        >
          Convert karo
        </button>
      </div>
    </section>
  );
}
