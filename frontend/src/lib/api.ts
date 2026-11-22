import type { AggregatedSearch, BenchmarkResponse, RunSummary } from "./types";

async function post<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`Falha (${response.status}): ${detail.slice(0, 200)}`);
  }
  return response.json() as Promise<T>;
}

export function runBenchmark(spec: { taskCount: number; ioDelayMs: number; platformPoolSize: number }) {
  return post<BenchmarkResponse>("/api/benchmark", spec);
}

export function searchFlights(origin: string, destination: string) {
  return post<AggregatedSearch>("/api/search", { origin, destination });
}

export async function fetchRuns(): Promise<RunSummary[]> {
  const response = await fetch("/api/runs", { cache: "no-store" });
  if (!response.ok) throw new Error(`Falha ao carregar histórico (${response.status})`);
  return response.json();
}

export const brl = (value: number) =>
  new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);

export const num = (value: number) => new Intl.NumberFormat("pt-BR").format(value);
