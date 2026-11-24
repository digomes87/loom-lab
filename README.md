# Loom Lab — A Java 26 Concurrency Playground

An educational project to **visualize how the Java language has matured in JDK 26**
(GA March 17, 2026): virtual threads (Project Loom), Structured Concurrency, Scoped
Values, records, sealed interfaces, record patterns, pattern matching for `switch`,
Stream Gatherers and AOT caching — served by a **Spring Boot 4.2** app with
**Postgres** on Docker and a **Next.js 16** dashboard.

```
┌──────────────┐   /api/*    ┌────────────────────┐   JDBC   ┌──────────────┐
│  Next.js 16  │ ──────────► │  Spring Boot 4.2   │ ───────► │  Postgres 18 │
│  (dashboard) │  (rewrite)  │  Java 26 + preview │          │  (docker)    │
└──────────────┘             └────────────────────┘          └──────────────┘
                                      │
                                      ├─ Tomcat on virtual threads (JEP 444)
                                      ├─ StructuredTaskScope fan-out (JEP 525)
                                      └─ ScopedValue traceId (JEP 506)
```

## The idea

A **flight-quote aggregator** bolted onto a **threading benchmark lab**:

- **`/` (dashboard)** — runs the same blocking-I/O workload on a *platform thread
  pool* and with *one virtual thread per task*; shows speedup, throughput,
  percentiles and a persisted run history.
- **`/search`** — a single search fans out to 5 simulated suppliers inside a
  `StructuredTaskScope` with a 900 ms budget; whoever doesn't answer in time is
  cancelled as a group. Variants of the `sealed interface QuoteResult` paint the
  result cards.

## Java 26 feature map

| Feature | Status in JDK 26 | Where to see it |
|---|---|---|
| Virtual threads | Final (JEP 444, v21) | `spring.threads.virtual.enabled=true` (every request runs on a `VirtualThread[#N,tomcat-handler-*]` — visible in the UI); `BenchmarkEngine` |
| Structured Concurrency | Preview (JEP 525) | `QuoteAggregator.fanOut()`: `StructuredTaskScope.open(Joiner.awaitAll(), cf -> cf.withTimeout(...))` |
| Scoped Values | Final (JEP 506, v25) | `TraceContext.TRACE_ID` — traceId inherited by subtasks without `ThreadLocal` |
| Records + compact constructors | Final (v16) | The whole domain: `Airport`, `SearchRequest`, `Quote`, DTOs |
| Sealed interfaces | Final (v17) | `sealed interface QuoteResult permits Success, Failure, Timeout` |
| Record patterns | Final (JEP 440, v21) | `case Success(Quote quote)` in `QuoteAnalyzer` |
| Pattern matching for switch | Final (JEP 441, v21) | `QuoteAnalyzer.summarize()` — exhaustive switch with **no** `default` |
| Unnamed variables `_` | Final (JEP 456, v22) | `case Failure(String supplier, String _)` |
| Stream Gatherers | Final (JEP 485, v24) | `TopQuotes.cheapest(k)` (custom gatherer with a min-heap) + `Gatherers.windowSliding` |
| Instance main + compact source files | Final (JEP 512, v25) | `demo/LoomLabDemo.java` |
| Module import declarations | Final (JEP 511, v25) | `import module java.base;` in the demo |
| AOT caching (Project Leyden) | Final (JEP 483/514/516) | Startup experiment below |

> **Preview in the build:** JEP 525 requires `--enable-preview` — wired into the
> `maven-compiler-plugin` (`<enablePreview>`), surefire (`argLine`) and
> `spring-boot-maven-plugin` (`jvmArguments`), plus the Docker `ENTRYPOINT`.

## How to run

Prerequisites: JDK 26 (e.g. Temurin 26), Maven 3.9+, Node 20.9+, Docker via colima
(or any Docker daemon).

```bash
# 1. database (host port 5433 — 5432 is often taken by a native Postgres)
colima start
docker compose up -d

# 2. backend (http://localhost:8080)
cd backend && mvn spring-boot:run

# 3. frontend (http://localhost:3000)
cd frontend && npm install && npm run dev
```

Or everything in containers:

```bash
docker compose -f docker-compose.fullstack.yml up --build   # → http://localhost:3000
```

Standalone demo — no Spring, no build step (compact source file + instance main +
`import module` + 100k virtual threads + structured concurrency):

```bash
java --enable-preview --source 26 demo/LoomLabDemo.java
```

## API

| Method | Route | What it does |
|---|---|---|
| POST | `/api/search` | Structured fan-out to suppliers (`{origin, destination}`) |
| POST | `/api/benchmark` | `{taskCount, ioDelayMs, platformPoolSize}` → platform vs virtual comparison |
| GET | `/api/runs` | Run history (Postgres) |
| GET | `/actuator/health` | Health check |

> The flight quotes are **simulated in real time** — each search generates fresh
> prices/latencies/failures to exercise virtual threads. No external APIs are
> called and no flight data is stored. Swapping in real suppliers is a matter of
> implementing `FlightSupplier` (the JDK 26 HTTP/3 client, JEP 517, would fit right in).

## Measured results (Apple Silicon, Temurin 26, Nov/2026)

**Benchmark** — 1,000 tasks × 100 ms simulated I/O, pool of 100 platform threads:

| Model | Threads | Total | Throughput | p50/p95/p99 |
|---|---|---|---|---|
| Platform pool | 100 | ~1048 ms | ~954/s | 103/105/105 ms |
| Virtual threads | 1000 | ~107 ms | ~9346/s | 104/105/105 ms |

**~9.8× faster** with identical latency percentiles — a virtual thread doesn't make
the task faster; it removes the pool queue.

**Startup with AOT cache (JEP 516, no native image)** — average of 3 runs:

| Mode | "Started" (Spring) | Process |
|---|---|---|
| Baseline (`java -jar`) | ~1.99 s | ~2.16 s |
| `java -XX:AOTCache=app.jsa -jar` | ~1.17 s | ~1.32 s |

≈ **38% faster startup** on the stock JDK. Recipe:

```bash
java -XX:AOTMode=record  -XX:AOTConfiguration=app.aot.conf -Dspring.context.exit=onRefresh --enable-preview -jar app.jar
java -XX:AOTMode=create  -XX:AOTConfiguration=app.aot.conf -XX:AOTCacheOutput=app.jsa  -Dspring.context.exit=onRefresh --enable-preview -jar app.jar
java -XX:AOTCache=app.jsa --enable-preview -jar app.jar
```

## What about GraalVM?

GraalVM was evaluated for this project; the practical verdict:

- **What it is**: Oracle's JDK distribution with two aces — the **Graal JIT**
  (high-performance compiler for long-running code) and **Native Image**
  (AOT compilation: millisecond startup, lower memory).
- **What we found (Nov/2026)**: current GraalVM builds only cover **LTS (25/21/17)
  + "innovation" releases on top of LTS 25** — there is no GraalVM for JDK 26. And
  bytecode compiled with `--enable-preview` (JEP 525) **requires the exact same
  feature version at runtime** — this jar cannot run on GraalVM 25.
- **The decision**: runtime is `eclipse-temurin:26-jre` (exact version,
  preview-compatible), and instead of native image we used the **JDK 26 AOT cache**
  (JEP 516, Project Leyden), which delivered the 38% startup win above — the
  "incremental AOT" path inside OpenJDK itself. Once Structured Concurrency
  finalizes and/or GraalVM 26+ ships, native image becomes viable for this app again.
- **Where GraalVM shines**: cold-start-critical functions/serverless, or LTS apps
  without preview APIs — there, Native Image is still unbeatable on startup/memory.

## Field notes (decisions recorded in the commits)

1. **Postgres 18** changed its volume layout: mount at `/var/lib/postgresql`
   (not `/data`) or the container aborts (stage 1).
2. **Native Postgres on port 5432**: the host already had one — the project
   container listens on **5433** (stage 2). Classic symptom: `role "loomlab" does
   not exist` coming from the wrong database.
3. **Spring Boot 4 = Jackson 3** (`tools.jackson.*`, not `com.fasterxml.*`), and
   `write-durations-as-timestamps` is gone — durations already serialize as
   ISO-8601 (stage 4).
4. **Next rewrites are resolved at build time** (`routes-manifest.json`):
   `BACKEND_URL` must be a Docker **build arg**, not a runtime env var (stage 7).
5. **Preview APIs lock the version**: `--enable-preview` becomes a dependency of
   build, tests, runtime and container — and the reason native image doesn't apply here.

## Project structure

```
├── docker-compose.yml              # Postgres 18 (dev, port 5433)
├── docker-compose.fullstack.yml    # full stack in containers
├── demo/LoomLabDemo.java           # standalone demo (instance main + import module)
├── backend/                        # Spring Boot 4.2 / Java 26 (Maven)
│   └── src/main/java/dev/loomlab/
│       ├── domain/                 # records, sealed, pattern matching, gatherers
│       ├── supplier/               # simulated suppliers (blocking I/O)
│       ├── aggregator/             # StructuredTaskScope + ScopedValue
│       ├── benchmark/              # platform vs virtual engine
│       ├── run/                    # JPA + repository + record DTO
│       └── api/                    # REST controllers
└── frontend/                       # Next.js 16 (App Router, TS, recharts)
    └── src/app/                    # / (benchmark) and /search (aggregator)
```

---

Built in 8 stages, one commit per stage — `git log --oneline` tells the story.
