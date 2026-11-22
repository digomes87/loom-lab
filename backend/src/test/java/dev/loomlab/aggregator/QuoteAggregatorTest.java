package dev.loomlab.aggregator;

import dev.loomlab.domain.Quote;
import dev.loomlab.domain.QuoteResult;
import dev.loomlab.domain.SearchRequest;
import dev.loomlab.supplier.FlightSupplier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteAggregatorTest {

    private final QuoteAggregator aggregator = new QuoteAggregator();

    private static Quote quote(String supplier, String amount) {
        return new Quote(supplier, new BigDecimal(amount), LocalTime.of(9, 0), Duration.ofHours(2));
    }

    private static FlightSupplier immediate(String name, Quote quote) {
        return new FlightSupplier() {
            @Override public String name() { return name; }
            @Override public Quote fetch(SearchRequest request) { return quote; }
        };
    }

    private static FlightSupplier failing(String name) {
        return new FlightSupplier() {
            @Override public String name() { return name; }
            @Override public Quote fetch(SearchRequest request) {
                throw new IllegalStateException("fora do ar");
            }
        };
    }

    private static FlightSupplier slow(String name, long ms) {
        return new FlightSupplier() {
            @Override public String name() { return name; }
            @Override public Quote fetch(SearchRequest request) throws InterruptedException {
                Thread.sleep(ms);
                return quote(name, "100");
            }
        };
    }

    @Test
    void fanOutColetaCotacoesETop3MaisBaratas() throws InterruptedException {
        var search = aggregator.search("trace-1", SearchRequest.of("GRU", "LIS"), List.of(
            immediate("A", quote("A", "800")),
            immediate("B", quote("B", "600")),
            immediate("C", quote("C", "700"))
        ));

        assertThat(search.summary().successCount()).isEqualTo(3);
        assertThat(search.summary().failureCount()).isZero();
        // gatherer top-K devolve em ordem crescente de preço
        assertThat(search.topQuotes()).extracting(Quote::supplier).containsExactly("B", "C", "A");
        assertThat(search.traceId()).isEqualTo("trace-1");
    }

    @Test
    void falhaDeFornecedorViraDadoFailureNaoExcecao() throws InterruptedException {
        var search = aggregator.search("trace-2", SearchRequest.of("GRU", "LIS"), List.of(
            immediate("A", quote("A", "800")),
            failing("B")
        ));

        assertThat(search.summary().successCount()).isEqualTo(1);
        assertThat(search.summary().failureCount()).isEqualTo(1);
        assertThat(search.summary().failedSuppliers()).containsExactly("B");
    }

    @Test
    void fornecedorLentoECanceladoPeloOrcamentoEstruturado() throws InterruptedException {
        var start = System.nanoTime();
        var search = aggregator.search("trace-3", SearchRequest.of("GRU", "LIS"), List.of(
            immediate("A", quote("A", "800")),
            slow("Lento", 5_000)   // orçamento é 900ms
        ));
        var elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // o cancelamento estruturado devolve o controle em ~900ms, não em 5s
        assertThat(elapsedMs).isLessThan(2_000);
        assertThat(search.summary().timeoutCount()).isEqualTo(1);
        assertThat(search.results()).contains(new QuoteResult.Timeout("Lento"));
    }

    @Test
    void scopedValuePropagaTraceIdEAsubtaskRodaEmVirtualThread() throws InterruptedException {
        var seenTraceId = new AtomicReference<String>();
        var seenThread = new AtomicReference<Thread>();

        var spy = new FlightSupplier() {
            @Override public String name() { return "Spy"; }
            @Override public Quote fetch(SearchRequest request) {
                seenTraceId.set(TraceContext.TRACE_ID.get());   // herdado via ScopedValue
                seenThread.set(Thread.currentThread());
                return quote("Spy", "500");
            }
        };

        aggregator.search("trace-xyz", SearchRequest.of("GRU", "LIS"), List.of(spy));

        assertThat(seenTraceId.get()).isEqualTo("trace-xyz");
        // StructuredTaskScope fork em virtual threads por padrão (JEP 525 + JEP 444)
        assertThat(seenThread.get().isVirtual()).isTrue();
    }
}
