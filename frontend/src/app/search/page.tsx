"use client";

import { useState } from "react";
import { brl, searchFlights } from "@/lib/api";
import { isFailure, isSuccess } from "@/lib/types";
import type { AggregatedSearch, QuoteResult } from "@/lib/types";

/**
 * O "pattern matching" do backend (switch exaustivo sobre a sealed interface
 * QuoteResult) aparece aqui como cores: verde = Success, vermelho = Failure,
 * âmbar = Timeout. Cada card abaixo é uma variante da union.
 */
function ResultCard({ result }: { result: QuoteResult }) {
  if (isSuccess(result)) {
    const q = result.quote;
    return (
      <div className="supplier-card">
        <div className="head">
          <span className="name">{q.supplier}</span>
          <span className="badge success">Success</span>
        </div>
        <div className="price">{brl(q.amountBrl)}</div>
        <div className="meta">
          partida {q.departure.slice(0, 5)} · duração {q.duration.replace("PT", "").toLowerCase()}
        </div>
      </div>
    );
  }
  if (isFailure(result)) {
    return (
      <div className="supplier-card failure">
        <div className="head">
          <span className="name">{result.supplier}</span>
          <span className="badge failure">Failure</span>
        </div>
        <div className="meta">{result.reason}</div>
      </div>
    );
  }
  return (
    <div className="supplier-card timeout">
      <div className="head">
        <span className="name">{result.supplier}</span>
        <span className="badge timeout">Timeout</span>
      </div>
      <div className="meta">
        não respondeu dentro do orçamento de 900ms — cancelado pela Structured
        Concurrency
      </div>
    </div>
  );
}

export default function SearchPage() {
  const [origin, setOrigin] = useState("GRU");
  const [destination, setDestination] = useState("LIS");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState<AggregatedSearch | null>(null);

  async function handleSearch() {
    setLoading(true);
    setError(null);
    try {
      setSearch(await searchFlights(origin, destination));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <h1 className="page-title">Agregador de cotações de voos</h1>
      <p className="page-subtitle">
        Uma busca dispara consultas concorrentes a 5 fornecedores simulados dentro de um{" "}
        <code>StructuredTaskScope</code> (JEP 525) com orçamento de 900ms: quem não
        responde a tempo é cancelado em bloco — sem thread leak. As variantes do{" "}
        <code>sealed interface QuoteResult</code> pintam os cards abaixo.
      </p>

      <div className="panel">
        <div className="controls">
          <div className="field">
            <label htmlFor="origin">Origem (IATA)</label>
            <input
              id="origin"
              type="text"
              maxLength={3}
              value={origin}
              onChange={(e) => setOrigin(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="destination">Destino (IATA)</label>
            <input
              id="destination"
              type="text"
              maxLength={3}
              value={destination}
              onChange={(e) => setDestination(e.target.value)}
            />
          </div>
          <button className="primary" onClick={handleSearch} disabled={loading}>
            {loading ? "Consultando fornecedores…" : "✈ Buscar cotações"}
          </button>
        </div>
        {error && <div className="error-msg">{error}</div>}
      </div>

      {search && (
        <>
          <div className="stat-grid">
            <div className="stat">
              <div className="label">Fornecedores</div>
              <div className="value">{search.summary.total}</div>
              <div className="sub">
                {search.summary.successCount} com cotação ·{" "}
                {search.summary.failureCount} falha · {search.summary.timeoutCount} timeout
              </div>
            </div>
            <div className="stat">
              <div className="label">Melhor preço</div>
              <div className="value">
                {search.summary.bestQuote ? brl(search.summary.bestQuote.amountBrl) : "—"}
              </div>
              <div className="sub">{search.summary.bestQuote?.supplier ?? "sem cotação"}</div>
            </div>
            <div className="stat">
              <div className="label">Duração total</div>
              <div className="value">{search.elapsed.replace("PT", "").replace("S", "s")}</div>
              <div className="sub">limitada pelo orçamento estruturado</div>
            </div>
            <div className="stat">
              <div className="label">Request atendido por</div>
              <div
                style={{
                  fontFamily: "var(--font-geist-mono), monospace",
                  fontSize: "0.72rem",
                  lineHeight: 1.5,
                  wordBreak: "break-all",
                }}
              >
                {search.handlerThread.name}
              </div>
              <div className="sub">
                {search.handlerThread.virtual ? (
                  <span className="badge virtual">virtual thread ✓</span>
                ) : (
                  "platform thread"
                )}
              </div>
            </div>
          </div>

          <h2 className="section-title">Resultado por fornecedor</h2>
          <div className="supplier-grid">
            {search.results.map((result, i) => (
              <ResultCard key={i} result={result} />
            ))}
          </div>

          {search.topQuotes.length > 0 && (
            <>
              <h2 className="section-title">
                Top cotações — gatherer top-K em uma passada (JEP 485)
              </h2>
              <div className="top-quotes">
                {search.topQuotes.map((quote) => (
                  <div key={quote.supplier} className="supplier-card">
                    <div className="head">
                      <span className="name">{quote.supplier}</span>
                      <span className="badge info">top</span>
                    </div>
                    <div className="price">{brl(quote.amountBrl)}</div>
                    <div className="meta">
                      partida {quote.departure.slice(0, 5)} · duração{" "}
                      {quote.duration.replace("PT", "").toLowerCase()}
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}

          <p className="footnote">
            trace <code>{search.traceId}</code> propagado às subtasks via{" "}
            <code>ScopedValue</code> (JEP 506), sem ThreadLocal · média móvel dos preços:{" "}
            {search.movingAverages.map((v) => brl(v)).join(" · ")}
          </p>
        </>
      )}
    </>
  );
}
