"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { fetchRuns, num, runBenchmark } from "@/lib/api";
import type { BenchmarkResponse, RunSummary } from "@/lib/types";

const ORANGE = "#f78166";
const GRAY = "#8b949e";

export default function BenchmarkPage() {
  const [taskCount, setTaskCount] = useState(1000);
  const [ioDelayMs, setIoDelayMs] = useState(100);
  const [platformPoolSize, setPlatformPoolSize] = useState(100);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<BenchmarkResponse | null>(null);
  const [runs, setRuns] = useState<RunSummary[]>([]);

  const refreshRuns = useCallback(async () => {
    try {
      setRuns(await fetchRuns());
    } catch {
      // histórico é complementar; falha aqui não bloqueia o dashboard
    }
  }, []);

  useEffect(() => {
    void refreshRuns();
  }, [refreshRuns]);

  async function handleRun() {
    setLoading(true);
    setError(null);
    try {
      setResult(await runBenchmark({ taskCount, ioDelayMs, platformPoolSize }));
      await refreshRuns();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }

  const report = result?.report;
  const tempoData = report
    ? [
        { modelo: "Platform pool", ms: report.platform.totalMs },
        { modelo: "Virtual threads", ms: report.virtual.totalMs },
      ]
    : [];
  const throughputData = report
    ? [
        { modelo: "Platform pool", valor: Math.round(report.platform.throughputPerSec) },
        { modelo: "Virtual threads", valor: Math.round(report.virtual.throughputPerSec) },
      ]
    : [];

  return (
    <>
      <h1 className="page-title">Platform threads vs Virtual threads</h1>
      <p className="page-subtitle">
        O mesmo workload de I/O bloqueante roda duas vezes: num pool fixo de threads de
        plataforma e com uma virtual thread por tarefa (Project Loom). Ajuste os
        parâmetros, execute e veja a diferença nos gráficos — cada rodada fica salva no
        Postgres.
      </p>

      <div className="panel">
        <div className="controls">
          <div className="field">
            <label htmlFor="taskCount">Tarefas</label>
            <input
              id="taskCount"
              type="number"
              min={10}
              max={20000}
              step={10}
              value={taskCount}
              onChange={(e) => setTaskCount(Number(e.target.value))}
            />
          </div>
          <div className="field">
            <label htmlFor="ioDelayMs">I/O por tarefa (ms)</label>
            <input
              id="ioDelayMs"
              type="number"
              min={10}
              max={5000}
              step={10}
              value={ioDelayMs}
              onChange={(e) => setIoDelayMs(Number(e.target.value))}
            />
          </div>
          <div className="field">
            <label htmlFor="platformPoolSize">Pool de plataforma</label>
            <input
              id="platformPoolSize"
              type="number"
              min={10}
              max={1000}
              step={10}
              value={platformPoolSize}
              onChange={(e) => setPlatformPoolSize(Number(e.target.value))}
            />
          </div>
          <button className="primary" onClick={handleRun} disabled={loading}>
            {loading ? "Executando…" : "▶ Executar comparação"}
          </button>
        </div>
        <p className="hint" style={{ marginTop: "0.9rem" }}>
          Dica: com {num(taskCount)} tarefas × {ioDelayMs}ms de I/O e pool de{" "}
          {platformPoolSize}, o modelo de plataforma precisa de ~
          {Math.ceil(taskCount / platformPoolSize)} levas; as virtual threads fazem todas
          de uma vez.
        </p>
        {error && <div className="error-msg">{error}</div>}
      </div>

      {report && (
        <>
          <div className="stat-grid">
            <div className="stat">
              <div className="label">Speedup (virtual / platform)</div>
              <div className="value speedup">{report.speedup.toFixed(1)}×</div>
              <div className="sub">
                {report.platform.totalMs}ms → {report.virtual.totalMs}ms
              </div>
            </div>
            <div className="stat">
              <div className="label">Throughput virtual</div>
              <div className="value">{num(Math.round(report.virtual.throughputPerSec))}</div>
              <div className="sub">tarefas/s</div>
            </div>
            <div className="stat">
              <div className="label">Throughput platform</div>
              <div className="value">{num(Math.round(report.platform.throughputPerSec))}</div>
              <div className="sub">tarefas/s</div>
            </div>
            <div className="stat">
              <div className="label">Threads usadas</div>
              <div className="value">
                {report.platform.threadsUsed} → {report.virtual.threadsUsed}
              </div>
              <div className="sub">
                pool fixo vs uma virtual thread por tarefa
              </div>
            </div>
          </div>

          <div className="charts">
            <div className="chart-card">
              <h3>Tempo total (ms) — menor é melhor</h3>
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={tempoData}>
                  <CartesianGrid stroke="#2d3646" strokeDasharray="3 3" />
                  <XAxis dataKey="modelo" stroke="#8b949e" />
                  <YAxis stroke="#8b949e" />
                  <Tooltip
                    contentStyle={{
                      background: "#161b22",
                      border: "1px solid #2d3646",
                      borderRadius: 8,
                    }}
                  />
                  <Bar dataKey="ms" radius={[6, 6, 0, 0]}>
                    {tempoData.map((entry, i) => (
                      <Cell key={entry.modelo} fill={i === 1 ? ORANGE : GRAY} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
            <div className="chart-card">
              <h3>Throughput (tarefas/s) — maior é melhor</h3>
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={throughputData}>
                  <CartesianGrid stroke="#2d3646" strokeDasharray="3 3" />
                  <XAxis dataKey="modelo" stroke="#8b949e" />
                  <YAxis stroke="#8b949e" />
                  <Tooltip
                    contentStyle={{
                      background: "#161b22",
                      border: "1px solid #2d3646",
                      borderRadius: 8,
                    }}
                  />
                  <Bar dataKey="valor" radius={[6, 6, 0, 0]}>
                    {throughputData.map((entry, i) => (
                      <Cell key={entry.modelo} fill={i === 1 ? ORANGE : GRAY} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>

          <h2 className="section-title">Latência por tarefa (ms)</h2>
          <div className="panel" style={{ padding: 0, overflowX: "auto" }}>
            <table className="data">
              <thead>
                <tr>
                  <th>Percentil</th>
                  <th>Platform pool</th>
                  <th>Virtual threads</th>
                </tr>
              </thead>
              <tbody>
                {(
                  [
                    ["p50", "p50Ms"],
                    ["p95", "p95Ms"],
                    ["p99", "p99Ms"],
                  ] as const
                ).map(([label, key]) => (
                  <tr key={label}>
                    <td>{label}</td>
                    <td>{report.platform[key]}</td>
                    <td>{report.virtual[key]}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="footnote">
            Percentis semelhantes nos dois modelos: a virtual thread não deixa a tarefa
            mais lenta — ela só elimina a fila do pool. O ganho vem de{" "}
            <code>Executors.newVirtualThreadPerTaskExecutor()</code> (JEP 444).
          </p>
        </>
      )}

      <h2 className="section-title">Histórico (persistido no Postgres)</h2>
      <div className="panel" style={{ padding: 0, overflowX: "auto" }}>
        <table className="data">
          <thead>
            <tr>
              <th>#</th>
              <th>Quando</th>
              <th>Tarefas</th>
              <th>I/O (ms)</th>
              <th>Pool</th>
              <th>Platform (ms)</th>
              <th>Virtual (ms)</th>
              <th>Speedup</th>
            </tr>
          </thead>
          <tbody>
            {runs.length === 0 && (
              <tr>
                <td colSpan={8} style={{ color: "#8b949e" }}>
                  Nenhuma rodada registrada ainda.
                </td>
              </tr>
            )}
            {runs.map((run) => (
              <tr key={run.id}>
                <td>{run.id}</td>
                <td>{new Date(run.ranAt).toLocaleString("pt-BR")}</td>
                <td>{num(run.taskCount)}</td>
                <td>{run.ioDelayMs}</td>
                <td>{run.platformPoolSize}</td>
                <td className="lose">{run.platformTotalMs}</td>
                <td className="win">{run.virtualTotalMs}</td>
                <td className="win">{run.speedup.toFixed(1)}×</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
