# Loom Lab — Laboratório de Concorrência Java 26

Projeto educacional para **visualizar o amadurecimento da linguagem Java no JDK 26** (GA 17/03/2026):
virtual threads (Project Loom), Structured Concurrency, Scoped Values, records, sealed interfaces,
record patterns, pattern matching para `switch` e Stream Gatherers — tudo servido por um
**Spring Boot 4.2** com banco **Postgres** via Docker e um dashboard **Next.js**.

## A ideia

Um **agregador de cotações de voos** acoplado a um **laboratório de benchmark de threads**:

- `/api/search` — fan-out estruturado (*Structured Concurrency*) para vários fornecedores simulados
  (latência e falhas aleatórias), com timeout, cancelamento e agregação via *pattern matching*.
- `/api/benchmark` — o mesmo workload de I/O bloqueante executado em **pool de threads de plataforma**
  vs. **virtual threads**; mede tempo total, throughput e percentis (p50/p95/p99) e persiste no Postgres.
- Dashboard Next.js com gráficos comparativos e histórico das execuções.

## Mapa de features do Java 26

| Feature | Status no JDK 26 | Onde aparece |
|---|---|---|
| Virtual threads | Final (JEP 444, JDK 21) | Benchmark engine, Tomcat (`spring.threads.virtual.enabled=true`) |
| Structured Concurrency | Preview (JEP 525) | `QuoteAggregator` — `StructuredTaskScope.open(Joiner...)` com timeout |
| Scoped Values | Final (JEP 506, JDK 25) | `traceId` propagado às subtasks sem `ThreadLocal` |
| Records + record patterns | Final | Domínio (`SearchRequest`, `Quote`, `QuoteResult`...) |
| Sealed interfaces + pattern matching `switch` | Final | `sealed interface QuoteResult permits Success, Failure, Timeout` |
| Stream Gatherers | Final (JDK 24) | Gatherer top-K no pós-processamento das cotações |
| Instance main / compact source files | Final (JEP 512, JDK 25) | Demo standalone |
| GraalVM | — | Runtime do container (JIT Graal); Native Image como etapa opcional |

## Estrutura do repositório

```
├── docker-compose.yml        # Postgres 18 (dev)
├── backend/                  # Spring Boot 4.2 / Java 26 (Maven, --enable-preview)
└── frontend/                 # Next.js 16 + recharts
```

## Como rodar

> Instruções completas serão adicionadas na etapa final. Em resumo:
> `docker compose up -d` (Postgres) → `mvn spring-boot:run` no `backend/` → `npm run dev` no `frontend/`.

---

Construído em etapas, um commit por etapa — veja `git log --oneline`.
