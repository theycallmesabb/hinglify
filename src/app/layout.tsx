import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Hinglify — Convert English PDFs to Natural Hinglish Online",
  description:
    "Upload any English PDF and convert it into fluent, natural spoken Hinglish (Roman Hindi) with sentence-by-sentence accuracy. Preserves technical terms and numbers. Free instant download.",
  keywords: [
    "English to Hinglish PDF",
    "Hinglish PDF converter",
    "PDF translator Hinglish",
    "English to Roman Hindi translator",
    "Convert PDF to Hinglish online",
    "Hinglish AI translation",
  ],
  authors: [{ name: "Hinglify" }],
  creator: "Hinglify",
  publisher: "Hinglify",
  metadataBase: new URL("https://hinglify.vercel.app"),
  openGraph: {
    title: "Hinglify — Apna PDF, अपनी language mein",
    description:
      "Convert English PDFs to natural spoken Hinglish with AI-powered sentence preservation and clean formatting.",
    url: "https://hinglify.vercel.app",
    siteName: "Hinglify",
    locale: "en_IN",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: "Hinglify — English to Hinglish PDF Converter",
    description: "Upload an English PDF. Get it back in natural Hinglish.",
  },
  robots: {
    index: true,
    follow: true,
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Rozha+One&family=Hind:wght@400;600&display=swap"
          rel="stylesheet"
        />
      </head>
      <body suppressHydrationWarning>{children}</body>
    </html>
  );
}
