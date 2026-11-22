package dev.loomlab.aggregator;

import dev.loomlab.domain.Quote;
import dev.loomlab.domain.QuoteAnalyzer;
import dev.loomlab.domain.QuoteResult;
import dev.loomlab.domain.SearchRequest;
import dev.loomlab.domain.TopQuotes;

import dev.loomlab.domain.QuoteResult.Success;
import dev.loomlab.supplier.FlightSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.function.Consumer;

/**
 * Fan-out estruturado para todos os fornecedores — a vitrine da
 * <b>Structured Concurrency</b> (JEP 525, preview no JDK 26).
 *
 * <p>O que este código garante <em>por construção</em>:</p>
 * <ul>
 *   <li><b>Orçamento único</b> ({@code withTimeout}): nenhuma busca leva mais que
 *       {@value #SEARCH_BUDGET_MS}ms — os lentos são cancelados em bloco;</li>
 *   <li><b>Sem thread leak</b>: o {@code try-with-resources} só fecha quando todas
 *       as subtasks terminaram (ou foram interrompidas);</li>
 *   <li><b>Hierarquia visível</b>: thread dumps mostram as subtasks como filhas
 *       do escopo "busca-voos";</li>
 *   <li><b>Erro como dado</b>: falhas viram {@code QuoteResult.Failure}/
 *       {@code Timeout} — o tipo de retorno é honesto, sem exceção estourando o fluxo.</li>
 * </ul>
 */
@Component
public class QuoteAggregator {

    private static final Logger log = LoggerFactory.getLogger(QuoteAggregator.class);

    static final long SEARCH_BUDGET_MS = 900;

    /** Resposta completa da busca — um record agregando outros records. */
    public record AggregatedSearch(
        String traceId,
        SearchRequest request,
        List<QuoteResult> results,
        QuoteAnalyzer.Summary summary,
        List<Quote> topQuotes,
        List<BigDecimal> movingAverages,
        Duration elapsed,
        ThreadDetails handlerThread
    ) {
        /** Prova viva de onde o request rodou: nome + se é virtual thread. */
        public record ThreadDetails(String name, boolean virtual) {
            static ThreadDetails current() {
                var thread = Thread.currentThread();
                return new ThreadDetails(thread.toString(), thread.isVirtual());
            }
        }
    }

    /** Pareamento fornecedor ↔ subtask para saber de quem é cada resultado. */
    private record Forked(String supplier, Subtask<QuoteResult> subtask) {
    }

    public AggregatedSearch search(String traceId, SearchRequest request, List<FlightSupplier> suppliers)
            throws InterruptedException {

        var start = System.nanoTime();
        var results = ScopedValue.where(TraceContext.TRACE_ID, traceId)
            .call(() -> fanOut(request, suppliers));

        var successful = results.stream()
            .mapMulti((QuoteResult r, Consumer<Quote> downstream) -> {
                // record pattern no instanceof: só emite quem teve sucesso
                if (r instanceof Success(Quote quote)) {
                    downstream.accept(quote);
                }
            })
            .toList();

        return new AggregatedSearch(
            traceId,
            request,
            results,
            QuoteAnalyzer.summarize(results),
            successful.stream().gather(TopQuotes.cheapest(3)).toList(),
            TopQuotes.movingAverages(successful, 3),
            Duration.ofMillis((System.nanoTime() - start) / 1_000_000),
            AggregatedSearch.ThreadDetails.current()
        );
    }

    /**
     * O fan-out: abre um escopo, fork uma virtual thread por fornecedor,
     * espera todos (ou o timeout) e devolve a lista de {@link QuoteResult}.
     */
    private List<QuoteResult> fanOut(SearchRequest request, List<FlightSupplier> suppliers)
            throws InterruptedException {

        var forked = new ArrayList<Forked>();

        try (var scope = StructuredTaskScope.open(
                 Joiner.<QuoteResult>awaitAll(),
                 config -> config.withName("busca-voos")
                                 .withTimeout(Duration.ofMillis(SEARCH_BUDGET_MS)))) {

            for (FlightSupplier supplier : suppliers) {
                forked.add(new Forked(supplier.name(), scope.fork(() -> query(supplier, request))));
            }

            try {
                scope.join();
            } catch (StructuredTaskScope.TimeoutException e) {
                // Orçamento estourado: o escopo já cancelou (interrompeu) os lentos.
                log.warn("[{}] orçamento de {}ms estourado — fornecedores lentos cancelados",
                    TraceContext.TRACE_ID.orElse("sem-trace"), SEARCH_BUDGET_MS);
            }
        }
        // Aqui o escopo já fechou: toda subtask terminou ou foi cancelada. Sem leaks.

        return forked.stream().map(QuoteAggregator::toResult).toList();
    }

    /** Uma consulta a um fornecedor; erros de negócio viram dado (Failure), interrupção propaga. */
    private static QuoteResult query(FlightSupplier supplier, SearchRequest request)
            throws InterruptedException {
        try {
            return new QuoteResult.Success(supplier.fetch(request));
        } catch (InterruptedException e) {
            throw e;   // cancelamento estruturado — nunca engolir
        } catch (RuntimeException e) {
            return new QuoteResult.Failure(supplier.name(), e.getMessage());
        }
    }

    /** Mapeia o estado final de cada subtask para a variante correspondente de QuoteResult. */
    private static QuoteResult toResult(Forked forked) {
        var subtask = forked.subtask();
        return switch (subtask.state()) {
            case SUCCESS -> subtask.get();
            case FAILED -> {
                // após join+close, FAILED quase sempre significa "interrompida pelo timeout"
                yield subtask.exception() instanceof InterruptedException
                    ? new QuoteResult.Timeout(forked.supplier())
                    : new QuoteResult.Failure(forked.supplier(), subtask.exception().getMessage());
            }
            case UNAVAILABLE -> new QuoteResult.Timeout(forked.supplier());   // cancelada antes de iniciar
        };
    }
}
