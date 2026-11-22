import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import Link from "next/link";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Loom Lab — Java 26",
  description:
    "Virtual threads, Structured Concurrency, records e pattern matching do Java 26 em ação",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR" className={`${geistSans.variable} ${geistMono.variable}`}>
      <body>
        <header className="topbar">
          <div className="brand">
            <span className="logo">☕</span>
            <strong>Loom&nbsp;Lab</strong>
            <small>Java 26 · Spring Boot 4.2 · Next.js 16</small>
          </div>
          <nav>
            <Link href="/">Benchmark</Link>
            <Link href="/search">Agregador de voos</Link>
          </nav>
        </header>
        <main className="content">{children}</main>
        <footer className="footer">
          Um experimento do amadurecimento do Java — veja <code>README.md</code> para o
          mapa de JEPs.
        </footer>
      </body>
    </html>
  );
}
