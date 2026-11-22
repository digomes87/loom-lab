// Espelha os records do backend (dev.loomlab) — o tipo "sealed" do Java
// vira uma union discriminada no TypeScript.

export interface Quote {
  supplier: string;
  amountBrl: number;
  departure: string;
  duration: string;
}

export interface SuccessResult {
  quote: Quote;
}

export interface FailureResult {
  supplier: string;
  reason: string;
}

export interface TimeoutResult {
  supplier: string;
}

/** sealed interface QuoteResult permits Success, Failure, Timeout */
export type QuoteResult = SuccessResult | FailureResult | TimeoutResult;

// "pattern matching" no TypeScript: type guards espelhando o switch exaustivo do Java
export function isSuccess(r: QuoteResult): r is SuccessResult {
  return "quote" in r;
}

export function isFailure(r: QuoteResult): r is FailureResult {
  return "reason" in r;
}

export function isTimeout(r: QuoteResult): r is TimeoutResult {
  return !isSuccess(r) && !isFailure(r);
}

export interface SearchSummary {
  total: number;
  successCount: number;
  failureCount: number;
  timeoutCount: number;
  bestQuote: Quote | null;
  failedSuppliers: string[];
}

export interface ThreadDetails {
  name: string;
  virtual: boolean;
}

export interface AggregatedSearch {
  traceId: string;
  request: { origin: { code: string }; destination: { code: string } };
  results: QuoteResult[];
  summary: SearchSummary;
  topQuotes: Quote[];
  movingAverages: number[];
  elapsed: string;
  handlerThread: ThreadDetails;
}

export interface PhaseResult {
  threadModel: "PLATFORM" | "VIRTUAL";
  taskCount: number;
  threadsUsed: number;
  totalMs: number;
  throughputPerSec: number;
  p50Ms: number;
  p95Ms: number;
  p99Ms: number;
}

export interface ComparisonReport {
  spec: { taskCount: number; ioDelayMs: number; platformPoolSize: number };
  platform: PhaseResult;
  virtual: PhaseResult;
  speedup: number;
}

export interface BenchmarkResponse {
  runId: number;
  report: ComparisonReport;
}

export interface RunSummary {
  id: number;
  ranAt: string;
  taskCount: number;
  ioDelayMs: number;
  platformPoolSize: number;
  platformTotalMs: number;
  virtualTotalMs: number;
  speedup: number;
}
