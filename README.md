# Loom Lab — Laboratório de Concorrência Java 26

Projeto educacional para **visualizar o amadurecimento da linguagem Java no JDK 26**
(GA 17/03/2026): virtual threads (Project Loom), Structured Concurrency, Scoped Values,
records, sealed interfaces, record patterns, pattern matching para `switch`, Stream
Gatherers e AOT caching — servido por um **Spring Boot 4.2** com **Postgres** via Docker
(colima) e um dashboard **Next.js 16**.

```
┌──────────────┐   /api/*    ┌────────────────────┐   JDBC   ┌──────────────┐
│  Next.js 16  │ ──────────► │  Spring Boot 4.2   │ ───────► │  Postgres 18 │
│  (dashboard) │  (rewrite)  │  Java 26 + preview │          │  (docker)    │
└──────────────┘             └────────────────────┘          └──────────────┘
                                      │
                                      ├─ Tomcat em virtual threads (JEP 444)
                                      ├─ StructuredTaskScope fan-out (JEP 525)
                                      └─ ScopedValue traceId (JEP 506)
```

## A ideia

Um **agregador de cotações de voos** acoplado a um **laboratório de benchmark**:

- **`/` (dashboard)** — executa o mesmo workload de I/O bloqueante em um *pool de
  threads de plataforma* e com *uma virtual thread por tarefa*; mostra speedup,
  throughput, percentis e histórico persistido.
- **`/search`** — uma busca dispara consultas concorrentes a 5 fornecedores simulados
  dentro de um `StructuredTaskScope` com orçamento de 900 ms; quem não responde é
  cancelado em bloco. As variantes do `sealed interface QuoteResult` pintam os cards.

## Mapa de features do Java 26

| Feature | Status no JDK 26 | Onde aparece |
|---|---|---|
| Virtual threads | Final (JEP 444, v21) | `spring.threads.virtual.enabled=true` (cada request roda numa `VirtualThread[#N,tomcat-handler-*]` — visível na UI); `BenchmarkEngine` |
| Structured Concurrency | Preview (JEP 525) | `QuoteAggregator.fanOut()`: `StructuredTaskScope.open(Joiner.awaitAll(), cf -> cf.withTimeout(...))` |
| Scoped Values | Final (JEP 506, v25) | `TraceContext.TRACE_ID` — traceId herdado pelas subtasks sem `ThreadLocal` |
| Records + compact constructors | Final (v16) | Domínio inteiro: `Airport`, `SearchRequest`, `Quote`, DTOs |
| Sealed interfaces | Final (v17) | `sealed interface QuoteResult permits Success, Failure, Timeout` |
| Record patterns | Final (JEP 440, v21) | `case Success(Quote quote)` no `QuoteAnalyzer` |
| Pattern matching for switch | Final (JEP 441, v21) | `QuoteAnalyzer.summarize()` — switch exaustivo **sem** `default` |
| Variáveis sem nome `_` | Final (JEP 456, v22) | `case Failure(String supplier, String _)` |
| Stream Gatherers | Final (JEP 485, v24) | `TopQuotes.cheapest(k)` (gatherer custom com min-heap) + `Gatherers.windowSliding` |
| Instance main + compact source files | Final (JEP 512, v25) | `demo/LoomLabDemo.java` |
| Module import declarations | Final (JEP 511, v25) | `import module java.base;` no demo |
| AOT caching (Project Leyden) | Final (JEP 483/514/516) | Experimento de startup abaixo |

> **Preview no build:** JEP 525 exige `--enable-preview` — configurado no
> `maven-compiler-plugin` (`<enablePreview>`), no surefire (`argLine`) e no
> `spring-boot-maven-plugin` (`jvmArguments`), além do `ENTRYPOINT` do Docker.

## Como rodar

Pré-requisitos: JDK 26 (ex.: Temurin 26), Maven 3.9+, Node 20.9+, Docker via colima.

```bash
# 1. banco (porta 5433 no host — a 5432 costuma ser ocupada por um Postgres nativo)
colima start
docker compose up -d

# 2. backend (http://localhost:8080)
cd backend && mvn spring-boot:run

# 3. frontend (http://localhost:3000)
cd frontend && npm install && npm run dev
```

Ou tudo em containers:

```bash
docker compose -f docker-compose.fullstack.yml up --build   # → http://localhost:3000
```

Demo standalone, sem Spring e sem build (compact source file + instance main +
`import module` + 100 mil virtual threads + structured concurrency):

```bash
java --enable-preview --source 26 demo/LoomLabDemo.java
```

## Endpoints

| Método | Rota | O que faz |
|---|---|---|
| POST | `/api/search` | Fan-out estruturado aos fornecedores (`{origin, destination}`) |
| POST | `/api/benchmark` | `{taskCount, ioDelayMs, platformPoolSize}` → comparativo platform vs virtual |
| GET | `/api/runs` | Histórico das rodadas (Postgres) |
| GET | `/actuator/health` | Health check |

## Resultados medidos (Apple Silicon, Temurin 26, Nov/2026)

**Benchmark** — 1000 tarefas × 100 ms de I/O simulado, pool de 100 platform threads:

| Modelo | Threads | Total | Throughput | p50/p95/p99 |
|---|---|---|---|---|
| Platform pool | 100 | ~1048 ms | ~954/s | 103/105/105 ms |
| Virtual threads | 1000 | ~107 ms | ~9346/s | 104/105/105 ms |

**~9,8× mais rápido**, com percentis de latência idênticos — a virtual thread não
acelera a tarefa, ela elimina a fila do pool.

**Startup com AOT cache (JEP 516, sem native image)** — média de 3 execuções:

| Modo | "Started" (Spring) | Processo |
|---|---|---|
| Baseline (`java -jar`) | ~1,99 s | ~2,16 s |
| `java -XX:AOTCache=app.jsa -jar` | ~1,17 s | ~1,32 s |

≈ **38% de startup mais rápido** no JDK padrão. Receita:

```bash
java -XX:AOTMode=record  -XX:AOTConfiguration=app.aot.conf -Dspring.context.exit=onRefresh --enable-preview -jar app.jar
java -XX:AOTMode=create  -XX:AOTConfiguration=app.aot.conf -XX:AOTCacheOutput=app.jsa  -Dspring.context.exit=onRefresh --enable-preview -jar app.jar
java -XX:AOTCache=app.jsa --enable-preview -jar app.jar
```

## E o GraalVM?

O GraalVM foi avaliado como pedido, e o veredito prático deste projeto:

- **O que é**: distribuição JDK da Oracle com dois trunfos — o **Graal JIT**
  (compilador de alta performance para código de vida longa) e o **Native Image**
  (compilação AOT: startup em milissegundos, memória reduzida).
- **O que descobrimos (Nov/2026)**: as builds do GraalVM hoje cobrem apenas
  **LTS (25/21/17) + releases "innovation" sobre o LTS 25** — não existe GraalVM
  para o JDK 26. E bytecode compilado com `--enable-preview` (JEP 525) **exige o
  runtime da versão exata** — rodar este jar no GraalVM 25 não é possível.
- **A decisão**: runtime com `eclipse-temurin:26-jre` (versão exata, compatível com
  preview) e, no lugar do native image, o **AOT cache do JDK 26** (JEP 516, Project
  Leyden), que entregou 38% de startup a mais rápido acima — a evolução "AOT gradual"
  dentro do próprio OpenJDK. Quando a Structured Concurrency finalizar e/ou sair
  GraalVM 26+, o native image volta a ser viável para este app.
- **Caso de uso onde o GraalVM brilha**: funções/serverless com cold-start crítico,
  ou apps LTS sem preview APIs — lá o Native Image segue imbatível em startup/memória.

## Lições de campo (decisões registradas nos commits)

1. **Postgres 18** mudou o layout de volume: monte em `/var/lib/postgresql` (não
   `/data`), senão o container aborta (etapa 1).
2. **Postgres nativo na porta 5432**: o host já tinha um — o container do projeto
   atende na **5433** (etapa 2). Sintoma clássico: `role "loomlab" does not exist`
   vindo do banco errado.
3. **Spring Boot 4 = Jackson 3** (`tools.jackson.*`, não `com.fasterxml.*`), e
   `write-durations-as-timestamps` não existe mais — durations já saem ISO-8601 (etapa 4).
4. **Rewrites do Next são resolvidos no build** (`routes-manifest.json`): `BACKEND_URL`
   precisa ser **build arg** do Docker, não env de runtime (etapa 7).
5. **Preview APIs travam a versão**: `--enable-preview` é uma dependência de build,
   teste, runtime e container — e o motivo de native image não se aplicar aqui.

## Estrutura

```
├── docker-compose.yml              # Postgres 18 (dev, porta 5433)
├── docker-compose.fullstack.yml    # stack completa em containers
├── demo/LoomLabDemo.java           # demo standalone (instance main + import module)
├── backend/                        # Spring Boot 4.2 / Java 26 (Maven)
│   └── src/main/java/dev/loomlab/
│       ├── domain/                 # records, sealed, pattern matching, gatherers
│       ├── supplier/               # fornecedores simulados (I/O bloqueante)
│       ├── aggregator/             # StructuredTaskScope + ScopedValue
│       ├── benchmark/              # engine platform vs virtual
│       ├── run/                    # JPA + repository + DTO record
│       └── api/                    # controllers REST
└── frontend/                       # Next.js 16 (App Router, TS, recharts)
    └── src/app/                    # / (benchmark) e /search (agregador)
```

---

Construído em 8 etapas, um commit por etapa — `git log --oneline` conta a história.
